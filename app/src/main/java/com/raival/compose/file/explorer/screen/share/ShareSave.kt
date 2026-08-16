package com.raival.compose.file.explorer.screen.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import java.io.File

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
        val localFolder = tab.activeFolder as? LocalFileHolder ?: return false
        val destDir = localFolder.file
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
        true
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
