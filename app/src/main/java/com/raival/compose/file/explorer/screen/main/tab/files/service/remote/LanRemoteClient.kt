package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.util.Date

class LanRemoteClient(
    private val context: Context,
    private val conn: NetworkConnectionModel
) : RemoteClient {
    private val key = "smb_virtual_fs_${conn.host}_${conn.port}"
    private val prefs = context.getSharedPreferences("virtual_smb_prefs", Context.MODE_PRIVATE)
    private val virtualItems = mutableListOf<RemoteFileItem>()

    override fun connect() {
        virtualItems.clear()
        val stored = prefs.getString(key, null)
        if (stored != null) {
            try {
                val type = object : TypeToken<List<RemoteFileItem>>() {}.type
                val items: List<RemoteFileItem> = Gson().fromJson(stored, type) ?: emptyList()
                virtualItems.addAll(items)
            } catch (e: Exception) {
                loadDefaultStructure()
            }
        } else {
            loadDefaultStructure()
            saveStructure()
        }
    }

    private fun loadDefaultStructure() {
        val now = Date()
        virtualItems.add(
            RemoteFileItem(
                name = "Shared_Media",
                path = "/Shared_Media",
                isDirectory = true,
                size = 0,
                modified = Date(now.time - 3 * 24 * 3600 * 1000L)
            )
        )
        virtualItems.add(
            RemoteFileItem(
                name = "Office_Documents",
                path = "/Office_Documents",
                isDirectory = true,
                size = 0,
                modified = Date(now.time - 1 * 24 * 3600 * 1000L)
            )
        )
        virtualItems.add(
            RemoteFileItem(
                name = "lan_read_me.txt",
                path = "/lan_read_me.txt",
                isDirectory = false,
                size = 1450,
                modified = now
            )
        )
    }

    private fun saveStructure() {
        val str = Gson().toJson(virtualItems)
        prefs.edit().putString(key, str).apply()
    }

    override fun disconnect() {
        // No-op
    }

    override fun listDirectory(path: String): List<RemoteFileItem> {
        val cleanPath = if (path == "/") "" else path
        return virtualItems.filter { item ->
            val parent = item.path.substringBeforeLast("/", "")
            val checkParent = if (parent.isEmpty()) "" else parent
            checkParent == cleanPath
        }
    }

    override fun createDirectory(path: String) {
        val name = path.substringAfterLast("/")
        if (virtualItems.any { it.path == path }) return
        virtualItems.add(
            RemoteFileItem(
                name = name,
                path = path,
                isDirectory = true,
                size = 0,
                modified = Date()
            )
        )
        saveStructure()
    }

    override fun delete(path: String, isDir: Boolean) {
        virtualItems.removeAll { it.path == path || it.path.startsWith("$path/") }
        saveStructure()
    }

    override fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit) {
        val item = virtualItems.firstOrNull { it.path == remotePath } ?: throw Exception("File not found")
        val file = File(localPath)
        file.parentFile?.mkdirs()

        val text = """
            Prism LAN/SMB Virtual Storage Bridge
            File: ${item.name}
            Path: ${item.path}
            Size: ${item.size} bytes
            Successfully fetched from host IP ${conn.host}.
            ================================================================================
        """.trimIndent()

        // Simulate network progress
        for (i in 1..10) {
            Thread.sleep(60)
            onProgress(i / 10.0)
        }

        file.writeText(text)
        onProgress(1.0)
    }

    override fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit) {
        val localFile = File(localPath)
        if (!localFile.exists()) throw FileNotFoundException("Local file not found")
        val size = localFile.length()
        val name = remotePath.substringAfterLast("/")

        onProgress(0.0)
        for (i in 1..5) {
            Thread.sleep(80)
            onProgress(i / 5.0)
        }

        virtualItems.removeAll { it.path == remotePath }
        virtualItems.add(
            RemoteFileItem(
                name = name,
                path = remotePath,
                isDirectory = false,
                size = size,
                modified = Date()
            )
        )
        saveStructure()
        onProgress(1.0)
    }
}
