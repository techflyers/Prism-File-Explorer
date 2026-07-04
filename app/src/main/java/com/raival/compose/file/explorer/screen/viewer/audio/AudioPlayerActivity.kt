package com.raival.compose.file.explorer.screen.viewer.audio

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.audio.ui.MusicPlayerScreen
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import java.io.File

class AudioPlayerActivity : ViewerActivity() {
    override fun onCreateNewInstance(
        uri: Uri,
        uid: String
    ): ViewerInstance {
        // Scan parent folder for sibling audio files
        val filePath = resolveFilePath(uri)
        android.util.Log.d("AudioPlayerActivity", "onCreateNewInstance: uri=$uri, resolvedPath=$filePath")
        val playlist = if (filePath != null) buildFolderPlaylist(filePath) else listOf(uri)
        android.util.Log.d("AudioPlayerActivity", "Playlist size: ${playlist.size}")
        return AudioPlayerInstance(uri, uid, playlist)
    }

    override fun onReady(instance: ViewerInstance) {
        setContent {
            FileExplorerTheme {
                MusicPlayerScreen(
                    audioPlayerInstance = instance as AudioPlayerInstance,
                    onClosed = { onBackPressedDispatcher.onBackPressed() }
                )
            }
        }
    }

    /**
     * Scans the parent directory of the resolved file path for audio files,
     * creating a sorted playlist of content URIs.
     */
    private fun buildFolderPlaylist(filePath: String): List<Uri> {
        return try {
            val currentFile = File(filePath)
            val parentDir = currentFile.parentFile ?: return listOf(Uri.fromFile(currentFile))

            if (!parentDir.exists() || !parentDir.isDirectory) {
                android.util.Log.w("AudioPlayerActivity", "Parent dir not accessible: ${parentDir.absolutePath}")
                return listOf(Uri.fromFile(currentFile))
            }

            val audioExtensions = FileMimeType.audioFileType.toSet()
            val audioFiles = parentDir.listFiles()
                ?.filter { it.isFile && audioExtensions.contains(it.extension.lowercase()) }
                ?.sortedBy { it.name.lowercase() }
                ?: run {
                    android.util.Log.w("AudioPlayerActivity", "listFiles returned null for: ${parentDir.absolutePath}")
                    return listOf(Uri.fromFile(currentFile))
                }

            android.util.Log.d("AudioPlayerActivity", "Found ${audioFiles.size} audio files in ${parentDir.absolutePath}")

            if (audioFiles.isEmpty()) return listOf(Uri.fromFile(currentFile))

            audioFiles.map { file ->
                try {
                    FileProvider.getUriForFile(
                        this,
                        "${packageName}.provider",
                        file
                    )
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerActivity", "buildFolderPlaylist failed: ${e.message}")
            listOf(Uri.fromFile(File(filePath)))
        }
    }

    /**
     * Attempts to resolve a file:// or content:// URI to an absolute file path.
     * Tries multiple strategies in order of reliability.
     */
    private fun resolveFilePath(uri: Uri): String? {
        // 1. Check if the caller provided the real path directly (via "extra_file_path" Intent extra)
        val extraPath = intent.getStringExtra("extra_file_path")
        if (!extraPath.isNullOrEmpty() && File(extraPath).exists()) {
            android.util.Log.d("AudioPlayerActivity", "Path resolved from Intent extra: $extraPath")
            return extraPath
        }

        // 2. file:// scheme — path is directly in the URI
        if (uri.scheme == "file") {
            val path = uri.path
            android.util.Log.d("AudioPlayerActivity", "Path resolved from file URI: $path")
            return path
        }

        // 3. content:// scheme — try MediaStore _data column
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    val pathIndex = cursor.getColumnIndex("_data")
                    if (pathIndex >= 0 && cursor.moveToFirst()) {
                        val path = cursor.getString(pathIndex)
                        if (!path.isNullOrEmpty() && File(path).exists()) {
                            android.util.Log.d("AudioPlayerActivity", "Path resolved from MediaStore: $path")
                            return path
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerActivity", "MediaStore query failed: ${e.message}")
            }

            // 4. FileProvider URI — parse the path segment
            val uriPath = uri.path ?: return null
            val externalStorage = android.os.Environment.getExternalStorageDirectory().absolutePath

            // FileProvider paths configured in file_paths.xml typically use these prefixes
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
                    val candidate = if (basePath.isEmpty()) "/$relativePart" else "$basePath/$relativePart"
                    if (File(candidate).exists()) {
                        android.util.Log.d("AudioPlayerActivity", "Path resolved from FileProvider heuristic: $candidate")
                        return candidate
                    }
                }
            }

            // 5. Last resort: open input stream and copy to a temp file so we at least have something
            android.util.Log.w("AudioPlayerActivity", "Could not resolve path for URI: $uri")
        }

        return null
    }
}