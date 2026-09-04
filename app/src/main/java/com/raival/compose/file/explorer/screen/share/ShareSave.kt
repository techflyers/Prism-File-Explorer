package com.raival.compose.file.explorer.screen.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.RemoteFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteClient
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemotePaths
import java.io.File
import java.util.UUID

fun getSharedFileName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment
}

fun saveSharedFilesToFolder(
    context: Context,
    uris: List<Uri>,
    tab: FilesTab,
    textContent: String? = null
): Boolean {
    return try {
        when (val folder = tab.activeFolder) {
            is LocalFileHolder -> {
                val destDir = folder.file
                if (!destDir.exists() || !destDir.isDirectory) return false
                if (!textContent.isNullOrEmpty()) {
                    uniqueFile(destDir, "shared_text_${System.currentTimeMillis()}.txt")
                        .writeText(textContent)
                }
                uris.forEach { uri ->
                    val originalName = getSharedFileName(context, uri) ?: "shared_${System.currentTimeMillis()}"
                    val destFile = uniqueFile(destDir, originalName)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { input.copyTo(it) }
                    }
                }
                globalClass.preferencesManager.addSaveLocationToHistory(
                    folder.uniquePath,
                    folder.displayName,
                    isRemote = false
                )
                true
            }
            is RemoteFileHolder -> {
                val client = folder.client
                if (!textContent.isNullOrEmpty()) {
                    val textFileName = "shared_text_${System.currentTimeMillis()}.txt"
                    val destRemotePath = uniqueRemotePath(client, RemotePaths.join(folder.remotePath, textFileName))
                    val temp = File(globalClass.cleanOnExitDir.file, "share_txt_${UUID.randomUUID()}.txt")
                    try {
                        temp.writeText(textContent)
                        client.uploadFile(temp.absolutePath, destRemotePath) {}
                    } finally {
                        temp.delete()
                    }
                }
                uris.forEach { uri ->
                    val originalName = getSharedFileName(context, uri) ?: "shared_${System.currentTimeMillis()}"
                    val destRemotePath = uniqueRemotePath(client, RemotePaths.join(folder.remotePath, originalName))
                    val temp = File(globalClass.cleanOnExitDir.file, "share_upload_${UUID.randomUUID()}")
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { input.copyTo(it) }
                        }
                        if (temp.exists() && temp.length() > 0) {
                            client.uploadFile(temp.absolutePath, destRemotePath) {}
                        }
                    } finally {
                        temp.delete()
                    }
                }
                globalClass.preferencesManager.addSaveLocationToHistory(
                    folder.uniquePath,
                    folder.displayName,
                    isRemote = true
                )
                true
            }
            else -> false
        }
    } catch (_: Exception) {
        false
    }
}

private fun uniqueFile(dir: File, fileName: String): File {
    val dot = fileName.lastIndexOf('.')
    val name = if (dot > 0) fileName.substring(0, dot) else fileName
    val ext = if (dot > 0) fileName.substring(dot) else ""
    var file = File(dir, fileName)
    var counter = 1
    while (file.exists()) {
        file = File(dir, "$name ($counter)$ext")
        counter++
    }
    return file
}

private fun uniqueRemotePath(client: RemoteClient, remotePath: String): String {
    if (!client.exists(remotePath)) return remotePath
    val parent = RemotePaths.parent(remotePath) ?: "/"
    val name = RemotePaths.name(remotePath)
    val dot = name.lastIndexOf('.')
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var counter = 1
    var candidate = RemotePaths.join(parent, "$base ($counter)$ext")
    while (client.exists(candidate)) {
        counter++
        candidate = RemotePaths.join(parent, "$base ($counter)$ext")
    }
    return candidate
}
