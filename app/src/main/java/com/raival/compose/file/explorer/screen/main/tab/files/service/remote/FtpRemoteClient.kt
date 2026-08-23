package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.io.CopyStreamAdapter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.Date

class FtpRemoteClient(
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private var ftp: FTPClient? = null

    @Synchronized
    override fun connect() {
        disconnect()
        val client = FTPClient()
        client.connectTimeout = 15000
        client.defaultTimeout = 15000
        client.dataTimeout = java.time.Duration.ofMillis(15000)
        client.controlEncoding = "UTF-8"
        client.bufferSize = 64 * 1024

        client.connect(conn.host, conn.port)
        val reply = client.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("FTP server refused connection. Reply code: $reply (${client.replyString.trim()})")
        }

        val user = if (conn.username.isEmpty()) "anonymous" else conn.username
        val pass = if (conn.password.isEmpty()) "anonymous@" else conn.password
        if (!client.login(user, pass)) {
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("FTP login failed for user '$user'. Reply: ${client.replyString.trim()}")
        }

        client.enterLocalPassiveMode()
        client.setFileType(FTP.BINARY_FILE_TYPE)
        try {
            client.controlKeepAliveTimeout = 300L
        } catch (_: Throwable) {}
        try {
            client.sendCommand("OPTS UTF8", "ON")
        } catch (_: Exception) {}

        ftp = client
    }

    @Synchronized
    override fun disconnect() {
        val client = ftp ?: return
        try {
            if (client.isConnected) {
                try { client.logout() } catch (_: Exception) {}
                try { client.disconnect() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        ftp = null
    }

    private fun getClient(): FTPClient {
        val client = ftp
        if (client == null || !client.isConnected) {
            connect()
        }
        return ftp ?: throw Exception("FTP client not connected")
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val client = getClient()
        val targetPath = if (path.isEmpty()) "/" else path

        val changed = try {
            if (targetPath == "/") client.changeWorkingDirectory("/")
            else client.changeWorkingDirectory(targetPath)
        } catch (_: Exception) {
            false
        }

        val files: Array<FTPFile> = try {
            if (changed) {
                client.listFiles() ?: emptyArray()
            } else {
                client.listFiles(targetPath) ?: emptyArray()
            }
        } catch (e: Exception) {
            client.listFiles(targetPath) ?: emptyArray()
        }

        return files.mapNotNull { file ->
            if (file == null) return@mapNotNull null
            val cleanName = file.name ?: return@mapNotNull null
            if (cleanName == "." || cleanName == "..") return@mapNotNull null
            val isDir = file.isDirectory || file.type == FTPFile.DIRECTORY_TYPE || file.isSymbolicLink
            val fullPath = RemotePaths.join(targetPath, cleanName)
            RemoteFileItem(
                name = cleanName,
                path = fullPath,
                isDirectory = isDir,
                size = if (isDir) 0L else file.size,
                modified = file.timestamp?.time ?: Date()
            )
        }
    }

    override fun createDirectory(path: String) {
        val client = getClient()
        var ok = client.makeDirectory(path)
        if (!ok) {
            val parent = RemotePaths.parent(path)
            val name = RemotePaths.name(path)
            if (parent != null && parent.isNotEmpty()) {
                client.changeWorkingDirectory(parent)
                ok = client.makeDirectory(name)
            }
        }
        if (!ok) {
            throw Exception("Failed to create FTP directory: $path (${client.replyString.trim()})")
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        val client = getClient()
        var ok = if (isDir) {
            client.removeDirectory(path)
        } else {
            client.deleteFile(path)
        }
        if (!ok) {
            val parent = RemotePaths.parent(path)
            val name = RemotePaths.name(path)
            if (parent != null && parent.isNotEmpty()) {
                client.changeWorkingDirectory(parent)
                ok = if (isDir) client.removeDirectory(name) else client.deleteFile(name)
            }
        }
        if (!ok) {
            throw Exception("Failed to delete remote item: $path (${client.replyString.trim()})")
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val client = getClient()
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()
        if (localFile.exists()) {
            localFile.delete()
        }

        var totalSize = 0L
        val parent = RemotePaths.parent(remotePath) ?: "/"
        val name = RemotePaths.name(remotePath)
        try {
            val files = listDirectory(parent)
            val matched = files.firstOrNull { it.name == name }
            if (matched != null) {
                totalSize = matched.size
            }
        } catch (_: Exception) {}

        onProgress(0.0)

        val listener = object : CopyStreamAdapter() {
            override fun bytesTransferred(totalBytesTransferred: Long, bytesTransferred: Int, streamSize: Long) {
                if (totalSize > 0) {
                    onProgress((totalBytesTransferred.toDouble() / totalSize).coerceIn(0.0, 1.0))
                }
            }
        }
        client.copyStreamListener = listener

        try {
            var ok = FileOutputStream(localFile).use { fos ->
                client.retrieveFile(remotePath, fos)
            }
            if (!ok) {
                if (parent.isNotEmpty()) {
                    client.changeWorkingDirectory(parent)
                    ok = FileOutputStream(localFile).use { fos ->
                        client.retrieveFile(name, fos)
                    }
                }
            }
            if (!ok) {
                throw Exception("Failed to download FTP file: $remotePath (${client.replyString.trim()})")
            }
            onProgress(1.0)
        } finally {
            client.copyStreamListener = null
        }
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val client = getClient()
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found: $localPath")
        val totalSize = localFile.length()

        val parent = RemotePaths.parent(remotePath)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            ensureDirectory(parent)
        }

        onProgress(0.0)

        val listener = object : CopyStreamAdapter() {
            override fun bytesTransferred(totalBytesTransferred: Long, bytesTransferred: Int, streamSize: Long) {
                if (totalSize > 0) {
                    onProgress((totalBytesTransferred.toDouble() / totalSize).coerceIn(0.0, 1.0))
                }
            }
        }
        client.copyStreamListener = listener

        try {
            var ok = FileInputStream(localFile).use { fis ->
                client.storeFile(remotePath, fis)
            }
            if (!ok) {
                val name = RemotePaths.name(remotePath)
                if (parent != null && parent.isNotEmpty()) {
                    client.changeWorkingDirectory(parent)
                    ok = FileInputStream(localFile).use { fis ->
                        client.storeFile(name, fis)
                    }
                }
            }
            if (!ok) {
                throw Exception("Failed to upload FTP file to: $remotePath (${client.replyString.trim()})")
            }
            onProgress(1.0)
        } finally {
            client.copyStreamListener = null
        }
    }

    override fun createFile(path: String) {
        val client = getClient()
        val parent = RemotePaths.parent(path)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            ensureDirectory(parent)
        }
        var ok = client.storeFile(path, ByteArrayInputStream(ByteArray(0)))
        if (!ok) {
            val name = RemotePaths.name(path)
            if (parent != null && parent.isNotEmpty()) {
                client.changeWorkingDirectory(parent)
                ok = client.storeFile(name, ByteArrayInputStream(ByteArray(0)))
            }
        }
        if (!ok) {
            throw Exception("Failed to create FTP file: $path (${client.replyString.trim()})")
        }
    }

    override fun rename(fromPath: String, toPath: String) {
        val client = getClient()
        var ok = client.rename(fromPath, toPath)
        if (!ok) {
            val parent = RemotePaths.parent(fromPath)
            if (parent != null && parent.isNotEmpty()) {
                client.changeWorkingDirectory(parent)
                ok = client.rename(RemotePaths.name(fromPath), RemotePaths.name(toPath))
            }
        }
        if (!ok) {
            throw Exception("Failed to rename FTP item: $fromPath to $toPath (${client.replyString.trim()})")
        }
    }

    override fun exists(path: String): Boolean {
        return try {
            val parent = RemotePaths.parent(path) ?: return true
            val name = RemotePaths.name(path)
            listDirectory(parent).any { it.name == name }
        } catch (_: Exception) {
            false
        }
    }
}
