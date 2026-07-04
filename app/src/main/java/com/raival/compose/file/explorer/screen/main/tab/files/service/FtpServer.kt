package com.raival.compose.file.explorer.screen.main.tab.files.service

import android.os.Environment
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

object FtpServer {
    private var controlSocket: ServerSocket? = null
    private var port = 9999
    private var homeDir = Environment.getExternalStorageDirectory().absolutePath
    private var username = "Anonymous"
    private var anonymous = true
    private var isActive = false
    private val activeSessions = mutableListOf<FtpSession>()

    fun configure(port: Int, homeDir: String, username: String, anonymous: Boolean) {
        this.port = port
        this.homeDir = homeDir
        this.username = username
        this.anonymous = anonymous
    }

    fun start(onStatusChanged: () -> Unit) {
        if (isActive) return
        isActive = true
        thread {
            try {
                controlSocket = ServerSocket(port)
                while (isActive) {
                    val client = controlSocket?.accept() ?: break
                    val session = FtpSession(client, homeDir, username, anonymous)
                    activeSessions.add(session)
                    thread { session.run() }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                stop(onStatusChanged)
            }
        }
        onStatusChanged()
    }

    fun stop(onStatusChanged: () -> Unit) {
        if (!isActive) return
        isActive = false
        try {
            controlSocket?.close()
        } catch (_: Exception) {}
        controlSocket = null
        for (session in activeSessions) {
            session.close()
        }
        activeSessions.clear()
        onStatusChanged()
    }

    fun isRunning(): Boolean = isActive
    fun getPort(): Int = port
    fun getHomeDir(): String = homeDir
    fun getUsername(): String = username
    fun isAnonymous(): Boolean = anonymous

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}

class FtpSession(
    private val controlSocket: Socket,
    private val homeDir: String,
    private val expectedUser: String,
    private val anonymous: Boolean
) {
    private val reader = BufferedReader(InputStreamReader(controlSocket.getInputStream(), "UTF-8"))
    private val writer = BufferedWriter(OutputStreamWriter(controlSocket.getOutputStream(), "UTF-8"))
    private var currentDir = "/"
    private var passiveServer: ServerSocket? = null
    private var activeHost: String? = null
    private var activePort: Int? = null
    private var renameFromPath: String? = null

    fun run() {
        sendResponse("220 Prism FTP Server ready.")
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                processCommand(line ?: break)
            }
        } catch (e: Exception) {
            // connection reset or closed
        } finally {
            close()
        }
    }

    fun close() {
        try { controlSocket.close() } catch (_: Exception) {}
        try { passiveServer?.close() } catch (_: Exception) {}
    }

    private fun sendResponse(response: String) {
        try {
            writer.write(response + "\r\n")
            writer.flush()
        } catch (_: Exception) {}
    }

    private fun getAbsolutePath(relPath: String): String {
        var path = relPath
        if (!path.startsWith("/")) {
            path = if (currentDir == "/") "/$path" else "$currentDir/$path"
        }
        val file = File(homeDir, path)
        return file.canonicalPath
    }

    private fun processCommand(line: String) {
        val parts = line.trim().split(" ")
        val cmd = parts[0].uppercase()
        val arg = if (parts.size > 1) parts.subList(1, parts.size).joinToString(" ") else ""

        when (cmd) {
            "USER" -> {
                if (anonymous) {
                    sendResponse("230 Anonymous user logged in.")
                } else {
                    sendResponse("331 User name okay, need password.")
                }
            }
            "PASS" -> {
                sendResponse("230 User logged in, proceed.")
            }
            "SYST" -> {
                sendResponse("215 UNIX Type: L8")
            }
            "FEAT" -> {
                sendResponse("211-Features:\r\n UTF8\r\n211 End")
            }
            "OPTS" -> {
                if (arg.uppercase() == "UTF8 ON") {
                    sendResponse("200 UTF8 Option Enabled")
                } else {
                    sendResponse("501 Option not understood")
                }
            }
            "PWD" -> {
                sendResponse("257 \"$currentDir\" is current directory.")
            }
            "TYPE" -> {
                sendResponse("200 Type set to $arg")
            }
            "PASV" -> {
                enterPassiveMode()
            }
            "PORT" -> {
                enterActiveMode(arg)
            }
            "LIST" -> {
                thread { handleList(arg) }
            }
            "CWD" -> {
                handleCwd(arg)
            }
            "CDUP" -> {
                handleCwd("..")
            }
            "SIZE" -> {
                handleSize(arg)
            }
            "RETR" -> {
                thread { handleRetrieve(arg) }
            }
            "STOR" -> {
                thread { handleStore(arg) }
            }
            "DELE" -> {
                handleDelete(arg)
            }
            "MKD" -> {
                handleMakeDir(arg)
            }
            "RMD" -> {
                handleRemoveDir(arg)
            }
            "RNFR" -> {
                renameFromPath = getAbsolutePath(arg)
                sendResponse("350 File exists, ready for destination name.")
            }
            "RNTO" -> {
                handleRenameTo(arg)
            }
            "NOOP" -> {
                sendResponse("200 OK")
            }
            "QUIT" -> {
                sendResponse("221 Goodbye.")
                close()
            }
            else -> {
                sendResponse("502 Command not implemented.")
            }
        }
    }

    private fun enterPassiveMode() {
        try {
            passiveServer?.close()
            passiveServer = ServerSocket(0)
            val port = passiveServer!!.localPort
            val ipParts = FtpServer.getLocalIpAddress().split(".")
            if (ipParts.size != 4) {
                sendResponse("500 Internal error")
                return
            }
            val p1 = port shr 8
            val p2 = port and 0xFF
            sendResponse("227 Entering Passive Mode (${ipParts.joinToString(",")},$p1,$p2)")
        } catch (e: Exception) {
            sendResponse("451 Local error in processing.")
        }
    }

    private fun enterActiveMode(arg: String) {
        val parts = arg.split(",")
        if (parts.size != 6) {
            sendResponse("501 Syntax error")
            return
        }
        activeHost = parts.subList(0, 4).joinToString(".")
        activePort = (parts[4].toInt() shl 8) + parts[5].toInt()
        sendResponse("200 PORT command successful.")
    }

    private fun getDataSocket(): Socket? {
        if (passiveServer != null) {
            return try {
                passiveServer!!.soTimeout = 10000
                val socket = passiveServer!!.accept()
                passiveServer?.close()
                passiveServer = null
                socket
            } catch (e: Exception) {
                null
            }
        } else if (activeHost != null && activePort != null) {
            return try {
                val socket = Socket(activeHost, activePort!!)
                activeHost = null
                activePort = null
                socket
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun handleList(arg: String) {
        val socket = getDataSocket()
        if (socket == null) {
            sendResponse("425 Can't open data connection.")
            return
        }
        sendResponse("150 Opening ASCII mode data connection for file list.")
        try {
            val targetPath = getAbsolutePath(arg)
            val dir = File(targetPath)
            if (dir.exists() && dir.isDirectory) {
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), "UTF-8"))
                val files = dir.listFiles() ?: emptyArray()
                val df = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                for (file in files) {
                    val name = file.name
                    if (name.startsWith(".")) continue
                    val isDir = file.isDirectory
                    val typeChar = if (isDir) 'd' else '-'
                    val permissions = if (isDir) "rwxr-xr-x" else "rw-r--r--"
                    val size = if (isDir) 4096 else file.length()
                    val dateStr = df.format(Date(file.lastModified()))
                    writer.write("$typeChar$permissions 1 owner group $size $dateStr $name\r\n")
                }
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
            sendResponse("226 Transfer complete.")
        }
    }

    private fun handleCwd(arg: String) {
        val target = if (arg == "..") {
            if (currentDir == "/") {
                "/"
            } else {
                val idx = currentDir.lastIndexOf('/')
                if (idx == 0) "/" else currentDir.substring(0, idx)
            }
        } else if (arg.startsWith("/")) {
            arg
        } else {
            if (currentDir == "/") "/$arg" else "$currentDir/$arg"
        }

        val absPath = getAbsolutePath(target)
        val dir = File(absPath)
        if (!dir.exists() || !dir.isDirectory) {
            sendResponse("550 Directory not found.")
        } else {
            currentDir = target
            sendResponse("250 Directory successfully changed.")
        }
    }

    private fun handleSize(arg: String) {
        val file = File(getAbsolutePath(arg))
        if (file.exists() && file.isFile) {
            sendResponse("213 ${file.length()}")
        } else {
            sendResponse("550 File not found.")
        }
    }

    private fun handleRetrieve(arg: String) {
        val file = File(getAbsolutePath(arg))
        if (!file.exists() || !file.isFile) {
            sendResponse("550 File not found.")
            return
        }
        val socket = getDataSocket()
        if (socket == null) {
            sendResponse("425 Can't open data connection.")
            return
        }
        sendResponse("150 Opening BINARY mode data connection.")
        try {
            file.inputStream().use { input ->
                socket.getOutputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
            sendResponse("226 Transfer complete.")
        }
    }

    private fun handleStore(arg: String) {
        val file = File(getAbsolutePath(arg))
        val socket = getDataSocket()
        if (socket == null) {
            sendResponse("425 Can't open data connection.")
            return
        }
        sendResponse("150 Opening BINARY mode data connection.")
        try {
            socket.getInputStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
            sendResponse("226 Transfer complete.")
        }
    }

    private fun handleDelete(arg: String) {
        val file = File(getAbsolutePath(arg))
        if (file.exists() && file.isFile) {
            file.delete()
            sendResponse("250 File deleted successfully.")
        } else {
            sendResponse("550 File not found.")
        }
    }

    private fun handleMakeDir(arg: String) {
        val file = File(getAbsolutePath(arg))
        if (file.mkdirs()) {
            sendResponse("257 \"$arg\" directory created.")
        } else {
            sendResponse("550 Can't create directory.")
        }
    }

    private fun handleRemoveDir(arg: String) {
        val file = File(getAbsolutePath(arg))
        if (file.exists() && file.isDirectory) {
            file.deleteRecursively()
            sendResponse("250 Directory deleted successfully.")
        } else {
            sendResponse("550 Directory not found.")
        }
    }

    private fun handleRenameTo(arg: String) {
        if (renameFromPath == null) {
            sendResponse("503 Bad sequence of commands.")
            return
        }
        val from = File(renameFromPath!!)
        val to = File(getAbsolutePath(arg))
        if (from.exists() && from.renameTo(to)) {
            sendResponse("250 File renamed successfully.")
        } else {
            sendResponse("550 File rename failed.")
        }
        renameFromPath = null
    }
}
