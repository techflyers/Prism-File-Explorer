package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.security.Security
import java.util.Date
import java.util.EnumSet

class SftpRemoteClient(
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private var ssh: SSHClient? = null
    private var sftp: SFTPClient? = null

    init {
        ensureBouncyCastle()
    }

    companion object {
        @Volatile
        private var bcInitialized = false

        fun ensureBouncyCastle() {
            if (!bcInitialized) {
                synchronized(this) {
                    if (!bcInitialized) {
                        try {
                            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                            Security.insertProviderAt(BouncyCastleProvider(), 1)
                            SecurityUtils.setRegisterBouncyCastle(false)
                            SecurityUtils.setSecurityProvider(BouncyCastleProvider.PROVIDER_NAME)
                            bcInitialized = true
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    @Synchronized
    override fun connect() {
        disconnect()
        ensureBouncyCastle()
        val client = SSHClient()
        client.addHostKeyVerifier(PromiscuousVerifier())
        client.connectTimeout = 15000
        client.timeout = 15000

        client.connect(conn.host, conn.port)
        val user = if (conn.username.isEmpty()) "root" else conn.username
        client.authPassword(user, conn.password)

        if (!client.isAuthenticated) {
            try { client.disconnect() } catch (_: Exception) {}
            throw Exception("SFTP authentication failed for user '$user'")
        }

        ssh = client
        sftp = client.newSFTPClient()
    }

    @Synchronized
    override fun disconnect() {
        try { sftp?.close() } catch (_: Exception) {}
        try { ssh?.disconnect() } catch (_: Exception) {}
        try { ssh?.close() } catch (_: Exception) {}
        sftp = null
        ssh = null
    }

    private fun getSftp(): SFTPClient {
        val client = sftp
        if (client == null || ssh == null || !ssh!!.isConnected) {
            connect()
        }
        return sftp ?: throw Exception("SFTP client not connected")
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val client = getSftp()
        val targetPath = if (path.isEmpty()) (conn.rootPath.ifEmpty { "/" }) else path
        val list = client.ls(targetPath) ?: emptyList()
        return list.mapNotNull { entry ->
            val cleanName = entry.name ?: return@mapNotNull null
            if (cleanName == "." || cleanName == "..") return@mapNotNull null
            val fullPath = RemotePaths.join(targetPath, cleanName)
            val attributes = entry.attributes
            val isDir = attributes.type == FileMode.Type.DIRECTORY ||
                    (attributes.type == FileMode.Type.SYMLINK && isSymlinkToDirectory(client, fullPath))
            val size = if (isDir) 0L else (attributes.size ?: 0L)
            val mtime = attributes.mtime
            val modifiedDate = if (mtime != null && mtime > 0) Date(mtime * 1000L) else Date()
            RemoteFileItem(
                name = cleanName,
                path = fullPath,
                isDirectory = isDir,
                size = size,
                modified = modifiedDate
            )
        }
    }

    private fun isSymlinkToDirectory(client: SFTPClient, path: String): Boolean {
        return try {
            client.stat(path).type == FileMode.Type.DIRECTORY
        } catch (_: Exception) {
            false
        }
    }

    override fun createDirectory(path: String) {
        val client = getSftp()
        client.mkdirs(path)
    }

    override fun delete(path: String, isDir: Boolean) {
        val client = getSftp()
        if (isDir) {
            client.rmdir(path)
        } else {
            client.rm(path)
        }
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val client = getSftp()
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()
        if (localFile.exists()) {
            localFile.delete()
        }

        val stat = try { client.stat(remotePath) } catch (_: Exception) { null }
        val totalSize = stat?.size ?: 0L

        onProgress(0.0)

        val remoteFile = client.open(remotePath, EnumSet.of(OpenMode.READ))
        try {
            FileOutputStream(localFile).use { fos ->
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                var bytesRead: Int
                while (true) {
                    bytesRead = remoteFile.read(offset, buffer, 0, buffer.size)
                    if (bytesRead <= 0) break
                    fos.write(buffer, 0, bytesRead)
                    offset += bytesRead
                    if (totalSize > 0) {
                        onProgress((offset.toDouble() / totalSize).coerceIn(0.0, 1.0))
                    }
                }
            }
            onProgress(1.0)
        } finally {
            try { remoteFile.close() } catch (_: Exception) {}
        }
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val client = getSftp()
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found: $localPath")
        val totalSize = localFile.length()

        val parent = RemotePaths.parent(remotePath)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            ensureDirectory(parent)
        }

        onProgress(0.0)

        val remoteFile = client.open(remotePath, EnumSet.of(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC))
        try {
            FileInputStream(localFile).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    remoteFile.write(offset, buffer, 0, bytesRead)
                    offset += bytesRead
                    if (totalSize > 0) {
                        onProgress((offset.toDouble() / totalSize).coerceIn(0.0, 1.0))
                    }
                }
            }
            onProgress(1.0)
        } finally {
            try { remoteFile.close() } catch (_: Exception) {}
        }
    }

    override fun createFile(path: String) {
        val client = getSftp()
        val parent = RemotePaths.parent(path)
        if (parent != null && parent.isNotEmpty() && parent != "/") {
            ensureDirectory(parent)
        }
        client.open(path, EnumSet.of(OpenMode.CREAT, OpenMode.WRITE, OpenMode.TRUNC)).close()
    }

    override fun rename(fromPath: String, toPath: String) {
        val client = getSftp()
        client.rename(fromPath, toPath)
    }

    override fun exists(path: String): Boolean {
        val client = getSftp()
        return try {
            client.statExistence(path) != null
        } catch (_: Exception) {
            false
        }
    }
}
