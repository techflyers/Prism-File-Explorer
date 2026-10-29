package com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.io.CopyStreamAdapter
import org.apache.commons.net.util.TrustManagerUtils
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

    private val asciiExtensions = setOf(
        "txt", "md", "csv", "json", "xml", "html", "htm", "css", "js", "ts",
        "py", "kt", "java", "c", "cpp", "h", "sh", "bat", "ini", "conf", "yaml", "yml", "log", "srt", "vtt"
    )

    @Synchronized
    override fun connect() {
        disconnect()
        val securityMode = conn.effectiveFtpSecurityMode
        val client: FTPClient = when (securityMode) {
            "FTPS_IMPLICIT" -> FTPSClient(true).apply {
                if (!conn.effectiveVerifySslCert) {
                    trustManager = TrustManagerUtils.getAcceptAllTrustManager()
                }
            }
            "FTPS_EXPLICIT" -> FTPSClient(false).apply {
                if (!conn.effectiveVerifySslCert) {
                    trustManager = TrustManagerUtils.getAcceptAllTrustManager()
                }
            }
            else -> FTPClient()
        }

        val connectTimeoutMs = conn.effectiveConnectTimeoutSec * 1000
        val readTimeoutMs = conn.effectiveReadTimeoutSec * 1000
        client.connectTimeout = connectTimeoutMs
        client.defaultTimeout = readTimeoutMs
        client.dataTimeout = java.time.Duration.ofMillis(readTimeoutMs.toLong())
        client.controlEncoding = conn.effectiveEncoding
        client.bufferSize = 64 * 1024

        client.connect(conn.host, conn.port)
        val reply = client.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("FTP server refused connection. Reply code: $reply (${client.replyString.trim()})")
        }

        val isAnonymous = conn.effectiveAuthMethod == "ANONYMOUS"
        val user = if (isAnonymous || conn.username.isEmpty()) "anonymous" else conn.username
        val pass = if (isAnonymous || conn.password.isEmpty()) "anonymous@" else conn.password
        if (!client.login(user, pass)) {
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("FTP login failed for user '$user'. Reply: ${client.replyString.trim()}")
        }

        if (client is FTPSClient) {
            try {
                client.execPBSZ(0)
                client.execPROT("P")
            } catch (_: Exception) {}
        }

        if (conn.effectiveFtpConnectionMode == "ACTIVE") {
            client.enterLocalActiveMode()
        } else {
            client.enterLocalPassiveMode()
        }

        applyTransferModeForPath(client, null)

        val keepAliveSec = conn.effectiveKeepAliveSec
        if (keepAliveSec > 0) {
            try {
                client.controlKeepAliveTimeout = keepAliveSec.toLong()
                client.controlKeepAliveReplyTimeout = 5000
            } catch (_: Throwable) {}
        }

        if (conn.effectiveEncoding.equals("UTF-8", ignoreCase = true)) {
            try {
                client.sendCommand("OPTS UTF8", "ON")
            } catch (_: Exception) {}
        }

        ftp = client
    }

    private fun applyTransferModeForPath(client: FTPClient, filePath: String?) {
        val mode = conn.effectiveTransferMode
        val fileType = when (mode) {
            "ASCII" -> FTP.ASCII_FILE_TYPE
            "AUTO" -> {
                val ext = filePath?.substringAfterLast('.', "")?.lowercase() ?: ""
                if (ext in asciiExtensions) FTP.ASCII_FILE_TYPE else FTP.BINARY_FILE_TYPE
            }
            else -> FTP.BINARY_FILE_TYPE
        }
        try {
            client.setFileType(fileType)
        } catch (_: Exception) {}
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
        } else if (conn.effectiveKeepAliveSec > 0) {
            try {
                if (!client.sendNoOp()) {
                    connect()
                }
            } catch (_: Exception) {
                connect()
            }
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
        applyTransferModeForPath(client, remotePath)
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
        applyTransferModeForPath(client, localPath)
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
        applyTransferModeForPath(client, path)
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
