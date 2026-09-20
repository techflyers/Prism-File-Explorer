package com.techflyers.compose.file.explorer.screen.viewer.audio

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.audio.model.AudioMetadata
import com.techflyers.compose.file.explorer.screen.viewer.audio.model.AudioPlayerColorScheme
import com.techflyers.compose.file.explorer.screen.viewer.audio.model.PlayerState
import com.techflyers.compose.file.explorer.screen.viewer.audio.ui.extractColorsFromBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.techflyers.compose.file.explorer.screen.viewer.archive.ArchiveMediaQueueManager
import com.techflyers.compose.file.explorer.screen.viewer.archive.ArchiveMediaSession
import java.io.File

class AudioPlayerInstance(
    override val uri: Uri,
    override val id: String,
    val playlist: List<Uri> = listOf(),
    val archiveSession: ArchiveMediaSession? = null
) : ViewerInstance {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _metadata = MutableStateFlow(AudioMetadata())
    val metadata: StateFlow<AudioMetadata> = _metadata.asStateFlow()

    private val _isEqualizerVisible = MutableStateFlow(false)
    val isEqualizerVisible: StateFlow<Boolean> = _isEqualizerVisible.asStateFlow()

    private val _isVolumeVisible = MutableStateFlow(false)
    val isVolumeVisible: StateFlow<Boolean> = _isVolumeVisible.asStateFlow()

    private val _colorScheme = MutableStateFlow(AudioPlayerColorScheme())
    val audioPlayerColorScheme: StateFlow<AudioPlayerColorScheme> = _colorScheme.asStateFlow()

    private val _isSleepTimerVisible = MutableStateFlow(false)
    val isSleepTimerVisible: StateFlow<Boolean> = _isSleepTimerVisible.asStateFlow()

    private var defaultColorScheme: AudioPlayerColorScheme = AudioPlayerColorScheme()
    private var exoPlayer: ExoPlayer? = null
    private var positionTrackingJob: Job? = null
    private var sleepTimerJob: Job? = null

    @OptIn(UnstableApi::class)
    suspend fun initializePlayer(context: Context, uri: Uri) {
        withContext(Dispatchers.Main) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .build().apply {
                repeatMode = _playerState.value.repeatMode
                // Add all playlist items (or just the single uri)
                val uris = playlist.ifEmpty { listOf(uri) }
                val mediaItems = uris.map { itemUri ->
                    MediaItem.Builder().setUri(itemUri).build()
                }
                setMediaItems(mediaItems)

                // Start from the item matching the originally-opened URI
                val startIndex = uris.indexOfFirst { it == uri }.coerceAtLeast(0)
                seekTo(startIndex, 0)

                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.update {
                            it.copy(isPlaying = isPlaying)
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _playerState.update {
                            it.copy(
                                isLoading = playbackState == Player.STATE_BUFFERING
                            )
                        }

                        if (playbackState == Player.STATE_READY) {
                            _playerState.update {
                                it.copy(
                                    duration = duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L
                                )
                            }
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val player = exoPlayer ?: return
                        val currentIndex = player.currentMediaItemIndex
                        val currentUri = mediaItem?.localConfiguration?.uri ?: uris.getOrNull(currentIndex) ?: uri
                        _playerState.update {
                            it.copy(
                                currentTrackIndex = currentIndex,
                                totalTracks = player.mediaItemCount,
                                currentPosition = 0L,
                                duration = player.duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L
                            )
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            extractMetadata(context, currentUri)
                        }
                        archiveSession?.let { session ->
                            ArchiveMediaQueueManager.prefetchWindow(session, currentIndex, windowSize = 2)
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                        val title = mediaMetadata.title?.toString()
                        val artist = mediaMetadata.artist?.toString()
                        val album = mediaMetadata.albumTitle?.toString()
                        val artworkData = mediaMetadata.artworkData
                        if (!title.isNullOrBlank()) {
                            val art = artworkData?.let { data ->
                                runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
                            }
                            _metadata.update { current ->
                                current.copy(
                                    title = title,
                                    artist = artist ?: current.artist,
                                    album = album ?: current.album,
                                    albumArt = art ?: current.albumArt
                                )
                            }
                            art?.let { bitmap ->
                                val colorScheme = extractColorsFromBitmap(bitmap, defaultColorScheme)
                                _colorScheme.value = colorScheme
                            }
                        }
                    }
                })
            }
        }

        // Update track count
        _playerState.update {
            it.copy(
                totalTracks = exoPlayer?.mediaItemCount ?: 1,
                currentTrackIndex = exoPlayer?.currentMediaItemIndex ?: 0
            )
        }

        extractMetadata(context, uri)
        startPositionTracking()
    }

    fun setDefaultColorScheme(colorScheme: AudioPlayerColorScheme) {
        defaultColorScheme = colorScheme
        _colorScheme.value = colorScheme
    }

    private suspend fun extractMetadata(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                val filePath = resolveLocalPath(context, uri)
                if (!filePath.isNullOrEmpty() && File(filePath).exists()) {
                    retriever.setDataSource(filePath)
                } else if (uri.scheme == "file") {
                    val p = uri.path
                    if (p != null) retriever.setDataSource(p) else retriever.setDataSource(context, uri)
                } else if (uri.scheme == "content") {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                        } ?: retriever.setDataSource(context, uri)
                    } catch (_: Exception) {
                        retriever.setDataSource(context, uri)
                    }
                } else {
                    retriever.setDataSource(context, uri)
                }

                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: getFallbackTitle(uri)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: ""
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: ""
                val durationStr =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val duration = durationStr?.toLongOrNull() ?: 0L

                // Extract album art
                val albumArtData = retriever.embeddedPicture
                val albumArt = albumArtData?.let { data ->
                    runCatching { BitmapFactory.decodeByteArray(data, 0, data.size) }.getOrNull()
                }

                val metadata = AudioMetadata(
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    albumArt = albumArt
                )

                _metadata.value = metadata

                // Extract colors from album art if available, otherwise revert to default
                if (albumArt != null) {
                    val colorScheme = extractColorsFromBitmap(albumArt, defaultColorScheme)
                    _colorScheme.value = colorScheme
                } else {
                    _colorScheme.value = defaultColorScheme
                }
            } catch (e: Exception) {
                logger.logError(e)
                // Fallback metadata
                _metadata.value = AudioMetadata(
                    title = getFallbackTitle(uri)
                )
                _colorScheme.value = defaultColorScheme
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    private fun getFallbackTitle(uri: Uri): String {
        val rawName = if (uri.scheme == "file") {
            uri.path?.let { File(it).name }
        } else {
            uri.lastPathSegment
        }
        val decoded = rawName?.let { Uri.decode(it) } ?: globalClass.getString(R.string.unknown_title)
        return decoded.substringBeforeLast('.').ifBlank { decoded }
    }

    private fun resolveLocalPath(context: Context, uri: Uri): String? {
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
            } catch (_: Exception) {}

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

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                exoPlayer?.let { player ->
                    _playerState.update {
                        it.copy(
                            currentPosition = player.currentPosition,
                            duration = player.duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L
                        )
                    }
                }
                delay(100)
            }
        }
    }

    fun playPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekToDefaultPosition()
                }
                player.play()
            }
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun skipNext() {
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.play()
            } else if (_playerState.value.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.play()
            }
        }
    }

    fun skipPrevious() {
        exoPlayer?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
                player.play()
            } else if (_playerState.value.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(player.mediaItemCount - 1, 0)
                player.play()
            }
        }
    }

    /**
     * Jump directly to a specific track index in the playlist.
     */
    fun seekToTrack(index: Int) {
        archiveSession?.let { session ->
            ArchiveMediaQueueManager.prefetchWindow(session, index, windowSize = 2)
        }
        exoPlayer?.let { player ->
            player.seekTo(index, 0)
            player.play()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleRepeatMode() {
        val newMode = when (_playerState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.repeatMode = newMode
        _playerState.update { it.copy(repeatMode = newMode) }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        _playerState.update { it.copy(volume = volume) }
    }

    fun toggleEqualizer() {
        _isEqualizerVisible.value = !_isEqualizerVisible.value
    }

    fun toggleVolume() {
        _isVolumeVisible.value = !_isVolumeVisible.value
    }

    fun toggleSleepTimerPanel() {
        _isSleepTimerVisible.value = !_isSleepTimerVisible.value
    }

    fun toggleShuffle() {
        val newState = !_playerState.value.isShuffleEnabled
        exoPlayer?.shuffleModeEnabled = newState
        _playerState.update { it.copy(isShuffleEnabled = newState) }
    }

    fun startSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        _playerState.update { it.copy(sleepTimerRemainingMs = durationMs) }
        sleepTimerJob = CoroutineScope(Dispatchers.Main).launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000L)
                remaining -= 1000L
                _playerState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            // Timer expired — pause playback
            exoPlayer?.pause()
            _playerState.update { it.copy(sleepTimerRemainingMs = 0L) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _playerState.update { it.copy(sleepTimerRemainingMs = 0L) }
    }

    override fun onClose() {
        positionTrackingJob?.cancel()
        sleepTimerJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}