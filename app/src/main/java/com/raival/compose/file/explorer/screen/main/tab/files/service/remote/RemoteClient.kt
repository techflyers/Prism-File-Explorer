package com.raival.compose.file.explorer.screen.main.tab.files.service.remote

import java.util.Date
import java.util.Locale

data class RemoteFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Date
) {
    val formattedSize: String
        get() {
            if (isDirectory) return ""
            if (size <= 0) return "0 B"
            val suffixes = arrayOf("B", "KB", "MB", "GB", "TB")
            var i = 0
            var doubleSize = size.toDouble()
            while (doubleSize >= 1024 && i < suffixes.size - 1) {
                doubleSize /= 1024
                i++
            }
            return "%.1f %s".format(Locale.US, doubleSize, suffixes[i])
        }
}

interface RemoteClient {
    fun connect()
    fun disconnect()
    fun listDirectory(path: String): List<RemoteFileItem>
    fun createDirectory(path: String)
    fun delete(path: String, isDir: Boolean)
    fun downloadFile(remotePath: String, localPath: String, onProgress: (Double) -> Unit)
    fun uploadFile(localPath: String, remotePath: String, onProgress: (Double) -> Unit)
}
