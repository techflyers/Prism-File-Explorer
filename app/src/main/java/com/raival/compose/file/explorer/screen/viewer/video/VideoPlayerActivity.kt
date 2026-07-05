package com.raival.compose.file.explorer.screen.viewer.video

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.viewer.ViewerActivity
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.video.ui.VideoPlayerScreen
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import java.io.File
import kotlinx.coroutines.launch

class VideoPlayerActivity : ViewerActivity() {
    private var activeInstance: VideoPlayerInstance? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTION_MEDIA_CONTROL") {
                activeInstance?.playPause()
                updatePictureInPictureParams()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter("ACTION_MEDIA_CONTROL")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(pipReceiver, filter)
            }
        }
    }

    override fun onCreateNewInstance(
        uri: Uri,
        uid: String
    ): ViewerInstance {
        val filePath = resolveFilePath(uri)
        android.util.Log.d("VideoPlayerActivity", "onCreateNewInstance: uri=$uri, resolvedPath=$filePath")
        val playlist = if (filePath != null) buildFolderPlaylist(filePath) else listOf(uri)
        android.util.Log.d("VideoPlayerActivity", "Playlist size: ${playlist.size}")
        return VideoPlayerInstance(uri, uid, playlist).also { activeInstance = it }
    }

    override fun onReady(instance: ViewerInstance) {
        val videoPlayerInstance = instance as VideoPlayerInstance
        activeInstance = videoPlayerInstance

        // Collect playerState updates to change PiP actions reactively
        lifecycleScope.launch {
            videoPlayerInstance.playerState.collect { state ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                    updatePictureInPictureParams()
                }
            }
        }

        setContent {
            FileExplorerTheme {
                VideoPlayerScreen(
                    videoUri = videoPlayerInstance.uri,
                    videoPlayerInstance = videoPlayerInstance,
                    onBackPressed = { finish() }
                )
            }
        }
    }

    private fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlayingNow = activeInstance?.playerState?.value?.isPlaying ?: false
            val iconId = if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val title = if (isPlayingNow) "Pause" else "Play"

            val intent = Intent("ACTION_MEDIA_CONTROL")
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val icon = Icon.createWithResource(this, iconId)
            val action = RemoteAction(icon, title, title, pendingIntent)

            val params = PictureInPictureParams.Builder()
                .setActions(listOf(action))
                .build()
            setPictureInPictureParams(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        activeInstance?.setPiPMode(isInPictureInPictureMode)
        activeInstance?.setControlsVisible(!isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            updatePictureInPictureParams()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlaying = activeInstance?.playerState?.value?.isPlaying ?: false
            if (isPlaying) {
                try {
                    val isPlayingNow = activeInstance?.playerState?.value?.isPlaying ?: false
                    val iconId = if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    val title = if (isPlayingNow) "Pause" else "Play"

                    val intent = Intent("ACTION_MEDIA_CONTROL")
                    val pendingIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val icon = Icon.createWithResource(this, iconId)
                    val action = RemoteAction(icon, title, title, pendingIntent)

                    val pipParams = PictureInPictureParams.Builder()
                        .setActions(listOf(action))
                        .build()
                    enterPictureInPictureMode(pipParams)
                } catch (e: Exception) {
                    android.util.Log.e("VideoPlayerActivity", "Failed to enter PiP: ${e.message}")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {}
        if (activeInstance != null) {
            activeInstance = null
        }
    }

    /**
     * Scans the parent directory of the resolved file path for video files,
     * creating a sorted playlist of content URIs.
     */
    private fun buildFolderPlaylist(filePath: String): List<Uri> {
        return try {
            val currentFile = File(filePath)
            val parentDir = currentFile.parentFile ?: return listOf(Uri.fromFile(currentFile))

            if (!parentDir.exists() || !parentDir.isDirectory) {
                android.util.Log.w("VideoPlayerActivity", "Parent dir not accessible: ${parentDir.absolutePath}")
                return listOf(Uri.fromFile(currentFile))
            }

            val videoExtensions = FileMimeType.videoFileType.toSet()
            val videoFiles = parentDir.listFiles()
                ?.filter { it.isFile && videoExtensions.contains(it.extension.lowercase()) }
                ?.sortedBy { it.name.lowercase() }
                ?: run {
                    android.util.Log.w("VideoPlayerActivity", "listFiles returned null for: ${parentDir.absolutePath}")
                    return listOf(Uri.fromFile(currentFile))
                }

            android.util.Log.d("VideoPlayerActivity", "Found ${videoFiles.size} video files in ${parentDir.absolutePath}")

            if (videoFiles.isEmpty()) return listOf(Uri.fromFile(currentFile))

            videoFiles.map { file ->
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
            android.util.Log.e("VideoPlayerActivity", "buildFolderPlaylist failed: ${e.message}")
            listOf(Uri.fromFile(File(filePath)))
        }
    }

    /**
     * Attempts to resolve a file:// or content:// URI to an absolute file path.
     */
    private fun resolveFilePath(uri: Uri): String? {
        val extraPath = intent.getStringExtra("extra_file_path")
        if (!extraPath.isNullOrEmpty() && File(extraPath).exists()) {
            return extraPath
        }

        if (uri.scheme == "file") {
            return uri.path
        }

        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    val pathIndex = cursor.getColumnIndex("_data")
                    if (pathIndex >= 0 && cursor.moveToFirst()) {
                        val path = cursor.getString(pathIndex)
                        if (!path.isNullOrEmpty() && File(path).exists()) {
                            return path
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("VideoPlayerActivity", "MediaStore query failed: ${e.message}")
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
                    val candidate = if (basePath.isEmpty()) "/$relativePart" else "$basePath/$relativePart"
                    if (File(candidate).exists()) {
                        return candidate
                    }
                }
            }
        }

        return null
    }
}