package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.File
import java.util.Date
import java.util.EnumSet

class SftpRemoteClient(
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private var ssh = SSHClient()
    private var sftp: SFTPClient? = null

    override fun connect() {
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(conn.host, conn.port)
        ssh.authPassword(conn.username, conn.password)
        sftp = ssh.newSFTPClient()
    }

    override fun disconnect() {
        try { sftp?.close() } catch (_: Exception) {}
        try { ssh.disconnect() } catch (_: Exception) {}
        sftp = null
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val client = sftp ?: throw Exception("SFTP not connected")
        val targetPath = if (path.isEmpty()) "." else path
        val list = client.ls(targetPath) ?: emptyList()
        return list.map { entry ->
            val cleanName = entry.name
            val fullPath = if (path == "/" || path.isEmpty()) "/$cleanName" else "$path/$cleanName"
            val attributes = entry.attributes
            val isDir = attributes.type == FileMode.Type.DIRECTORY
            val size = attributes.size
            val mtime = attributes.mtime
            val modifiedDate = if (mtime != null) Date(mtime * 1000L) else Date()
            RemoteFileItem(
                name = cleanName,
                path = fullPath,
                isDirectory = isDir,
                size = size,
                modified = modifiedDate
            )
        }.filter { it.name != "." && it.name != ".." }
    }

    override fun createDirectory(path: String) {
        val client = sftp ?: throw Exception("SFTP not connected")
        client.mkdir(path)
    }

    override fun delete(path: String, isDir: Boolean) {
        val client = sftp ?: throw Exception("SFTP not connected")
        if (isDir) {
            client.rmdir(path)
        } else {
            client.rm(path)
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val client = sftp ?: throw Exception("SFTP not connected")
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()

        // Fetch size for progress calculation if attributes are readable
        val size = try { client.stat(remotePath).size } catch (_: Exception) { 0L }
        client.get(remotePath, localPath)
        onProgress(1.0)
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val client = sftp ?: throw Exception("SFTP not connected")
        client.put(localPath, remotePath)
        onProgress(1.0)
    }

    override fun createFile(path: String) {
        val client = sftp ?: throw Exception("SFTP not connected")
        client.open(path, EnumSet.of(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC)).close()
    }

    override fun rename(fromPath: String, toPath: String) {
        val client = sftp ?: throw Exception("SFTP not connected")
        client.rename(fromPath, toPath)
    }

    override fun exists(path: String): Boolean {
        val client = sftp ?: throw Exception("SFTP not connected")
        return try {
            client.stat(path)
            true
        } catch (_: Exception) {
            false
        }
    }
}
