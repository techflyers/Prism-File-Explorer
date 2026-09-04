package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import android.content.Context
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.File
import java.io.FileNotFoundException
import java.util.Date
import java.util.Properties

class LanRemoteClient(
    private val context: Context,
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private var cifsContext: CIFSContext? = null

    private fun getContext(): CIFSContext {
        cifsContext?.let { return it }
        val prop = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.disableSMB1", "false")
            setProperty("jcifs.smb.client.responseTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "10000")
            setProperty("jcifs.smb.client.connTimeout", "8000")
        }
        val base = BaseContext(PropertyConfiguration(prop))
        val auth = if (conn.username.isNotBlank()) {
            val domain = if (conn.username.contains("\\")) conn.username.substringBefore("\\") else null
            val user = if (conn.username.contains("\\")) conn.username.substringAfter("\\") else conn.username
            NtlmPasswordAuthenticator(domain, user, conn.password)
        } else {
            NtlmPasswordAuthenticator()
        }
        val ctx = base.withCredentials(auth)
        cifsContext = ctx
        return ctx
    }

    private fun buildUrl(path: String, isDirectory: Boolean = false): String {
        val cleanHost = conn.host.trim()
        val port = conn.port
        val portPart = if (port == 445 || port <= 0) "" else ":$port"

        val configuredRoot = conn.rootPath.trim().removePrefix("/").removeSuffix("/")
        val requestPath = path.trim().removePrefix("/").removeSuffix("/")

        val combined = when {
            configuredRoot.isEmpty() && requestPath.isEmpty() -> ""
            configuredRoot.isEmpty() -> requestPath
            requestPath.isEmpty() -> configuredRoot
            else -> "$configuredRoot/$requestPath"
        }

        val trailing = if (isDirectory && combined.isNotEmpty()) "/" else if (combined.isEmpty()) "/" else ""
        return "smb://$cleanHost$portPart/$combined$trailing"
    }

    override fun connect() {
        val ctx = getContext()
        val testUrl = buildUrl("", isDirectory = true)
        val rootFile = SmbFile(testUrl, ctx)
        rootFile.connect()
    }

    override fun disconnect() {
        cifsContext = null
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val ctx = getContext()
        val url = buildUrl(path, isDirectory = true)
        val dir = SmbFile(url, ctx)
        val files = dir.listFiles() ?: emptyArray()

        val normalizedParent = if (path == "/" || path.isBlank()) "" else path.trimEnd('/')
        return files.mapNotNull { file ->
            val rawName = file.name
            val cleanName = rawName.trimEnd('/')
            if (cleanName.isBlank()) return@mapNotNull null

            val isDir = try {
                file.isDirectory
            } catch (_: Throwable) {
                rawName.endsWith("/")
            }

            val size = if (isDir) 0L else try { file.length() } catch (_: Throwable) { 0L }
            val modified = try { Date(file.lastModified()) } catch (_: Throwable) { Date() }
            val itemPath = "$normalizedParent/$cleanName"

            RemoteFileItem(
                name = cleanName,
                path = itemPath,
                isDirectory = isDir,
                size = size,
                modified = modified
            )
        }
    }

    override fun createDirectory(path: String) {
        val ctx = getContext()
        val url = buildUrl(path, isDirectory = true)
        val smbFile = SmbFile(url, ctx)
        smbFile.mkdirs()
    }

    override fun delete(path: String, isDir: Boolean) {
        val ctx = getContext()
        val url = buildUrl(path, isDirectory = isDir)
        val smbFile = SmbFile(url, ctx)
        smbFile.delete()
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val ctx = getContext()
        val url = buildUrl(remotePath, isDirectory = false)
        val smbFile = SmbFile(url, ctx)
        val totalSize = try { smbFile.length() } catch (_: Throwable) { -1L }
        val localFile = File(localPath)
        localFile.parentFile?.mkdirs()

        var bytesCopied = 0L
        smbFile.inputStream.use { input ->
            localFile.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesCopied += read
                    if (totalSize > 0) {
                        onProgress((bytesCopied.toDouble() / totalSize).coerceIn(0.0, 1.0))
                    }
                }
            }
        }
        onProgress(1.0)
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val ctx = getContext()
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found: $localPath")
        val totalSize = localFile.length()

        val url = buildUrl(remotePath, isDirectory = false)
        val smbFile = SmbFile(url, ctx)

        var bytesCopied = 0L
        localFile.inputStream().use { input ->
            smbFile.outputStream.use { output ->
                val buffer = ByteArray(64 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    bytesCopied += read
                    if (totalSize > 0) {
                        onProgress((bytesCopied.toDouble() / totalSize).coerceIn(0.0, 1.0))
                    }
                }
            }
        }
        onProgress(1.0)
    }

    override fun createFile(path: String) {
        val ctx = getContext()
        val url = buildUrl(path, isDirectory = false)
        val smbFile = SmbFile(url, ctx)
        smbFile.createNewFile()
    }

    override fun rename(fromPath: String, toPath: String) {
        val ctx = getContext()
        val fromUrl = buildUrl(fromPath)
        val toUrl = buildUrl(toPath)
        val fromFile = SmbFile(fromUrl, ctx)
        val toFile = SmbFile(toUrl, ctx)
        fromFile.renameTo(toFile)
    }

    override fun exists(path: String): Boolean {
        return try {
            val ctx = getContext()
            val url = buildUrl(path)
            val smbFile = SmbFile(url, ctx)
            smbFile.exists()
        } catch (_: Throwable) {
            false
        }
    }
}
