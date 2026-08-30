package com.raival.compose.file.explorer.screen.viewer.image

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.image.ui.ImageViewerScreen
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import java.io.File

class ImageViewerActivity : ViewerActivity() {

    companion object {
        /** Ordered absolute paths of images in the current UI context (Recent, Images, folder, …). */
        const val EXTRA_IMAGE_LIST = "extra_image_list"
    }

    override fun onCreateNewInstance(uri: Uri, uid: String): ViewerInstance {
        val currentPath = resolveFilePath(uri)
        val contextPaths = intent.getStringArrayListExtra(EXTRA_IMAGE_LIST)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && File(it).isFile }
            ?.distinct()

        val (paths, uris) = when {
            !contextPaths.isNullOrEmpty() -> {
                // Prefer the list the user is actually browsing
                val list = ensureContains(contextPaths, currentPath)
                list to list.map { pathToUri(it) }
            }
            currentPath != null -> {
                val list = buildFolderImagePaths(currentPath)
                list to list.map { pathToUri(it) }
            }
            else -> {
                emptyList<String>() to listOf(uri)
            }
        }

        val initialIndex = when {
            paths.isNotEmpty() && currentPath != null ->
                paths.indexOfFirst { pathsEqual(it, currentPath) }.coerceAtLeast(0)
            else -> 0
        }

        val finalUris = if (uris.isEmpty()) listOf(uri) else uris
        return ImageViewerInstance(
            uri = finalUris.getOrElse(initialIndex) { uri },
            id = uid,
            imageList = finalUris,
            imagePaths = paths,
            initialIndex = initialIndex.coerceIn(0, finalUris.lastIndex)
        )
    }

    override fun onReady(instance: ViewerInstance) {
        setContent {
            FileExplorerTheme {
                SafeSurface(enableStatusBarsPadding = false) {
                    ImageViewerScreen(instance as ImageViewerInstance)
                }
            }
        }
    }

    private fun ensureContains(paths: List<String>, current: String?): List<String> {
        if (current.isNullOrEmpty()) return paths
        if (paths.any { pathsEqual(it, current) }) return paths
        // Newly downloaded / not yet in context list — append so user can still open it
        return paths + current
    }

    private fun pathsEqual(a: String, b: String): Boolean {
        return try {
            File(a).canonicalPath == File(b).canonicalPath
        } catch (_: Exception) {
            a == b
        }
    }

    private fun pathToUri(path: String): Uri {
        val file = File(path)
        return try {
            FileProvider.getUriForFile(this, "${packageName}.provider", file)
        } catch (_: Exception) {
            Uri.fromFile(file)
        }
    }

    private fun buildFolderImagePaths(filePath: String): List<String> {
        return try {
            val currentFile = File(filePath)
            val parentDir = currentFile.parentFile ?: return listOf(filePath)
            if (!parentDir.exists() || !parentDir.isDirectory) return listOf(filePath)

            val imageExtensions = FileMimeType.imageFileType
            val files = parentDir.listFiles()
                ?.filter { it.isFile && imageExtensions.contains(it.extension.lowercase()) }
                ?.sortedBy { it.name.lowercase() }
                ?.map { it.absolutePath }
                ?: return listOf(filePath)

            if (files.isEmpty()) listOf(filePath) else ensureContains(files, filePath)
        } catch (_: Exception) {
            listOf(filePath)
        }
    }

    private fun resolveFilePath(uri: Uri): String? {
        val extraPath = intent.getStringExtra("extra_file_path")
        if (!extraPath.isNullOrEmpty() && File(extraPath).isFile) {
            return extraPath
        }

        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty() && File(path).isFile) return path
        }

        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    val pathIndex = cursor.getColumnIndex("_data")
                    if (pathIndex >= 0 && cursor.moveToFirst()) {
                        val path = cursor.getString(pathIndex)
                        if (!path.isNullOrEmpty() && File(path).isFile) return path
                    }
                }
            } catch (_: Exception) {
            }

            val uriPath = uri.path ?: return null
            val externalStorage = android.os.Environment.getExternalStorageDirectory().absolutePath
            val prefixMappings = listOf(
                "/external_files_path/" to externalStorage,
                "/external-path/" to externalStorage,
                "/root_path/" to "",
                "/files/" to externalStorage,
                "/storage/" to "/storage"
            )
            for ((prefix, basePath) in prefixMappings) {
                val idx = uriPath.indexOf(prefix)
                if (idx >= 0) {
                    val relativePart = uriPath.substring(idx + prefix.length)
                    val candidate =
                        if (basePath.isEmpty()) "/$relativePart" else "$basePath/$relativePart"
                    if (File(candidate).isFile) return candidate
                }
            }
        }
        return null
    }
}
