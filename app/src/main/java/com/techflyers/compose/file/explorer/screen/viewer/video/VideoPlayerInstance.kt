package com.techflyers.compose.file.explorer.screen.viewer.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.name
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
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

class VideoPlayerInstance(
    override val uri: Uri,
    override val id: String,
    val playlist: List<Uri> = listOf(),
    val archiveSession: ArchiveMediaSession? = null
) : ViewerInstance {
    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()
    private var exoPlayer: ExoPlayer? = null
    private var positionTrackingJob: Job? = null

    suspend fun initializePlayer(context: Context, uri: Uri) {
        _playerState.update {
            it.copy(
                isLoading = true,
                isReady = false,
                playlist = playlist
            )
        }

        withContext(Dispatchers.Main) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            exoPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .build().apply {
                val uris = playlist.ifEmpty { listOf(uri) }
                val mediaItems = uris.map { itemUri ->
                    MediaItem.Builder().setUri(itemUri).build()
                }
                setMediaItems(mediaItems)
                repeatMode = _playerState.value.repeatMode

                val startIndex = uris.indexOfFirst { it == uri }.coerceAtLeast(0)
                seekTo(startIndex, 0)
                prepare()
                playWhenReady = true

                volume = 1.0f

                val initialTitle = if (uri.scheme == "file") {
                    uri.path?.let { File(it).name } ?: uri.name
                } else {
                    uri.name
                } ?: globalClass.getString(R.string.unknown)

                _playerState.update { currentState ->
                    currentState.copy(
                        title = initialTitle,
                        currentPlaylistIndex = startIndex,
                        isMuted = false
                    )
                }

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.update { currentState -> currentState.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _playerState.update { currentState ->
                            currentState.copy(
                                isLoading = playbackState == Player.STATE_BUFFERING
                            )
                        }

                        if (playbackState == Player.STATE_READY) {
                            _playerState.update { currentState ->
                                currentState.copy(
                                    duration = duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L,
                                    isLoading = false,
                                    isReady = true
                                )
                            }
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val player = exoPlayer ?: return
                        val currentIndex = player.currentMediaItemIndex
                        val currentUri = mediaItem?.localConfiguration?.uri ?: uris.getOrNull(currentIndex) ?: uri
                        val trackTitle = if (currentUri.scheme == "file") {
                            currentUri.path?.let { File(it).name } ?: currentUri.name
                        } else {
                            currentUri.name
                        } ?: globalClass.getString(R.string.unknown)
                        _playerState.update { currentState ->
                            currentState.copy(
                                currentPlaylistIndex = currentIndex,
                                title = trackTitle,
                                currentPosition = 0L,
                                duration = player.duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L
                            )
                        }
                        archiveSession?.let { session ->
                            ArchiveMediaQueueManager.prefetchWindow(session, currentIndex, windowSize = 2)
                        }
                    }
                })
            }
        }
        startPositionTracking()
    }

    private fun startPositionTracking() {
        positionTrackingJob?.cancel()
        positionTrackingJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                exoPlayer?.let { player ->
                    _playerState.update { currentState ->
                        currentState.copy(
                            currentPosition = player.currentPosition,
                            duration = player.duration.takeIf { it isNot TIME_UNSET } ?: 0L
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

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.setPlaybackSpeed(speed)
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    fun toggleMute() {
        exoPlayer?.let { player ->
            val currentVolume = player.volume
            val newVolume = if (currentVolume > 0f) 0f else 1f
            player.volume = newVolume
            _playerState.update { it.copy(isMuted = newVolume == 0f) }
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.let { player ->
            val clamped = volume.coerceIn(0f, 1f)
            player.volume = clamped
            _playerState.update { it.copy(isMuted = clamped == 0f) }
        }
    }

    fun toggleControls() {
        _playerState.update { currentState ->
            currentState.copy(showControls = !currentState.showControls)
        }
    }

    fun setControlsVisible(visible: Boolean) {
        _playerState.update { currentState ->
            currentState.copy(showControls = visible)
        }
    }

    fun toggleRepeatMode(): Int {
        val currentMode = _playerState.value.repeatMode
        val hasMultiple = playlist.size > 1 || (exoPlayer?.mediaItemCount ?: 0) > 1
        val newMode = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> if (hasMultiple) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.repeatMode = newMode
        _playerState.update { it.copy(repeatMode = newMode) }
        return newMode
    }

    fun removePlaylistItem(index: Int): List<Uri> {
        val player = exoPlayer ?: return _playerState.value.playlist
        if (index !in 0 until player.mediaItemCount) return _playerState.value.playlist

        player.removeMediaItem(index)
        val remaining = List(player.mediaItemCount) { itemIndex ->
            player.getMediaItemAt(itemIndex).localConfiguration?.uri ?: uri
        }
        _playerState.update { state ->
            state.copy(
                playlist = remaining,
                currentPlaylistIndex = state.currentPlaylistIndex.coerceAtMost((remaining.size - 1).coerceAtLeast(0))
            )
        }
        return remaining
    }

    fun playPlaylistItem(index: Int) {
        archiveSession?.let { session ->
            ArchiveMediaQueueManager.prefetchWindow(session, index, windowSize = 2)
        }
        exoPlayer?.let { player ->
            if (index in 0 until player.mediaItemCount) {
                player.seekTo(index, 0)
                player.play()
            }
        }
    }

    fun playNext() {
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

    fun playPrevious() {
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

    fun setPiPMode(isInPiP: Boolean) {
        _playerState.update { it.copy(isInPictureInPicture = isInPiP) }
    }

    fun getPlayer() = exoPlayer

    override fun onClose() {
        positionTrackingJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }
}