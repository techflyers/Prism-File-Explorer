package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileNotFoundException
import java.util.Date

class FtpRemoteClient(
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private var ftp = FTPClient()

    override fun connect() {
        ftp.connect(conn.host, conn.port)
        val reply = ftp.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftp.disconnect()
            throw Exception("FTP server refused connection. Reply code: $reply")
        }
        val user = if (conn.username.isEmpty()) "anonymous" else conn.username
        val pass = if (conn.password.isEmpty()) "anonymous@" else conn.password
        if (!ftp.login(user, pass)) {
            throw Exception("FTP login failed.")
        }
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
    }

    override fun disconnect() {
        if (ftp.isConnected) {
            try { ftp.logout() } catch (_: Exception) {}
            try { ftp.disconnect() } catch (_: Exception) {}
        }
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val targetPath = if (path.isEmpty()) "/" else path
        val files = ftp.listFiles(targetPath) ?: emptyArray()
        return files.map { file ->
            val cleanName = file.name
            val fullPath = if (targetPath.endsWith("/")) "$targetPath$cleanName" else "$targetPath/$cleanName"
            RemoteFileItem(
                name = cleanName,
                path = fullPath,
                isDirectory = file.isDirectory,
                size = file.size,
                modified = file.timestamp?.time ?: Date()
            )
        }.filter { it.name != "." && it.name != ".." }
    }

    override fun createDirectory(path: String) {
        if (!ftp.makeDirectory(path)) {
            throw Exception("Failed to create FTP directory: $path")
        }
    }

    override fun delete(path: String, isDir: Boolean) {
        val ok = if (isDir) {
            ftp.removeDirectory(path)
        } else {
            ftp.deleteFile(path)
        }
        if (!ok) {
            throw Exception("Failed to delete remote item: $path")
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()

        // Fetch size to calculate progress
        var totalSize = 0L
        val parent = remotePath.substringBeforeLast("/", "/")
        val name = remotePath.substringAfterLast("/")
        val files = ftp.listFiles(parent) ?: emptyArray()
        for (f in files) {
            if (f.name == name) {
                totalSize = f.size
                break
            }
        }

        FileOutputStream(localFile).use { fos ->
            ftp.retrieveFile(remotePath, fos)
        }
        onProgress(1.0)
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found")
        FileInputStream(localFile).use { fis ->
            ftp.storeFile(remotePath, fis)
        }
        onProgress(1.0)
    }
}
