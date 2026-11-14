package com.techflyers.compose.file.explorer.screen.viewer.video

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
import android.util.Rational
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.video.ui.VideoPlayerScreen
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme
import java.io.File
import kotlinx.coroutines.launch

import com.techflyers.compose.file.explorer.screen.viewer.archive.ArchiveMediaQueueManager

class VideoPlayerActivity : ViewerActivity() {
    private var activeInstance: VideoPlayerInstance? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_CONTROL) {
                when (intent.getStringExtra(EXTRA_PIP_ACTION)) {
                    PIP_ACTION_REWIND -> activeInstance?.seekBy(-10_000L)
                    PIP_ACTION_PLAY_PAUSE -> activeInstance?.playPause()
                    PIP_ACTION_FORWARD -> activeInstance?.seekBy(10_000L)
                }
                updatePictureInPictureParams()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        configureImmersiveWindow(showBars = false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter(ACTION_PIP_CONTROL)
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                pipReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    fun configureImmersiveWindow(showBars: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showBars) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreateNewInstance(
        uri: Uri,
        uid: String
    ): ViewerInstance {
        val archiveSession = ArchiveMediaQueueManager.getOrCreateSession(intent)
        if (archiveSession != null && archiveSession.items.isNotEmpty()) {
            val playlist = archiveSession.items.map { Uri.fromFile(File(it.destinationPath)) }
            val initialIndex = intent.getIntExtra(
                ArchiveMediaQueueManager.EXTRA_ARCHIVE_INITIAL_INDEX,
                0
            ).coerceIn(0, playlist.lastIndex)
            val initialUri = playlist.getOrElse(initialIndex) { uri }
            ArchiveMediaQueueManager.prefetchWindow(archiveSession, initialIndex, windowSize = 2)
            return VideoPlayerInstance(initialUri, uid, playlist, archiveSession).also { activeInstance = it }
        }

        val filePath = resolveFilePath(uri)
        android.util.Log.d("VideoPlayerActivity", "onCreateNewInstance: uri=$uri, resolvedPath=$filePath")
        val initialUri = if (filePath != null) Uri.fromFile(File(filePath)) else uri
        val playlist = if (filePath != null) buildFolderPlaylist(filePath) else listOf(initialUri)
        android.util.Log.d("VideoPlayerActivity", "Playlist size: ${playlist.size}")
        return VideoPlayerInstance(initialUri, uid, playlist).also { activeInstance = it }
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
                    onBackPressed = { finish() },
                    onDelete = { uri ->
                        val path = resolveFilePath(uri)
                        val deleted = if (path != null) {
                            File(path).delete()
                        } else {
                            runCatching { contentResolver.delete(uri, null, null) }.getOrDefault(0) > 0
                        }
                        if (deleted) onFileDeleted(path)
                        deleted
                    }
                )
            }
        }
    }

    fun buildPipParams(): PictureInPictureParams {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw UnsupportedOperationException("PiP requires Android O+")
        }
        val isPlaying = activeInstance?.playerState?.value?.isPlaying ?: false

        val rewindIntent = Intent(ACTION_PIP_CONTROL).apply {
            `package` = packageName
            putExtra(EXTRA_PIP_ACTION, PIP_ACTION_REWIND)
        }
        val rewindPending = PendingIntent.getBroadcast(
            this,
            101,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rewindAction = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_media_rew),
            "Rewind 10s",
            "Rewind 10s",
            rewindPending
        )

        val playPauseIntent = Intent(ACTION_PIP_CONTROL).apply {
            `package` = packageName
            putExtra(EXTRA_PIP_ACTION, PIP_ACTION_PLAY_PAUSE)
        }
        val playPausePending = PendingIntent.getBroadcast(
            this,
            102,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseAction = RemoteAction(
            Icon.createWithResource(this, playPauseIcon),
            playPauseTitle,
            playPauseTitle,
            playPausePending
        )

        val forwardIntent = Intent(ACTION_PIP_CONTROL).apply {
            `package` = packageName
            putExtra(EXTRA_PIP_ACTION, PIP_ACTION_FORWARD)
        }
        val forwardPending = PendingIntent.getBroadcast(
            this,
            103,
            forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val forwardAction = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_media_ff),
            "Forward 10s",
            "Forward 10s",
            forwardPending
        )

        val builder = PictureInPictureParams.Builder()
            .setActions(listOf(rewindAction, playPauseAction, forwardAction))

        val width = activeInstance?.getPlayer()?.videoSize?.width ?: 0
        val height = activeInstance?.getPlayer()?.videoSize?.height ?: 0
        if (width > 0 && height > 0) {
            val rational = Rational(width, height)
            val floatRatio = rational.toFloat()
            if (floatRatio in 0.418410f..2.390000f) {
                builder.setAspectRatio(rational)
            }
        }

        return builder.build()
    }

    fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(buildPipParams())
            } catch (e: Exception) {
                android.util.Log.e("VideoPlayerActivity", "Failed to enter PiP: ${e.message}")
            }
        }
    }

    fun updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                setPictureInPictureParams(buildPipParams())
            } catch (_: Exception) {}
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
                enterPipMode()
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
     * creating a sorted playlist of file URIs for direct playback.
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

            videoFiles.map { Uri.fromFile(it) }
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayerActivity", "buildFolderPlaylist failed: ${e.message}")
            listOf(Uri.fromFile(File(filePath)))
        }
    }

    private fun resolveFilePath(uri: Uri): String? {
        return resolveFilePath(this, uri, intent.getStringExtra("extra_file_path"))
    }

    companion object {
        const val ACTION_PIP_CONTROL = "com.techflyers.compose.file.explorer.ACTION_PIP_CONTROL"
        const val EXTRA_PIP_ACTION = "extra_pip_action"
        const val PIP_ACTION_REWIND = "pip_rewind"
        const val PIP_ACTION_PLAY_PAUSE = "pip_play_pause"
        const val PIP_ACTION_FORWARD = "pip_forward"

        fun resolveFilePath(context: Context, uri: Uri, extraPath: String? = null): String? {
            if (!extraPath.isNullOrEmpty() && File(extraPath).exists()) {
                return extraPath
            }

            if (uri.scheme == "file") {
                return uri.path
            }

            if (uri.scheme == "content") {
                try {
                    context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
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
                    "/storage_root/" to "",
                    "/root_path/" to "",
                    "/package_root/" to externalStorage,
                    "/external_files_path/" to externalStorage,
                    "/external-path/" to externalStorage,
                    "/files/" to externalStorage,
                    "/storage/" to "/storage"
                )

                for ((prefix, basePath) in prefixMappings) {
                    val idx = uriPath.indexOf(prefix)
                    if (idx >= 0) {
                        val relativePart = uriPath.substring(idx + prefix.length)
                        val candidate = if (basePath.isEmpty()) {
                            if (relativePart.startsWith("/")) relativePart else "/$relativePart"
                        } else {
                            "$basePath/$relativePart"
                        }
                        if (File(candidate).exists()) {
                            return candidate
                        }
                    }
                }
            }

            return null
        }
    }
}