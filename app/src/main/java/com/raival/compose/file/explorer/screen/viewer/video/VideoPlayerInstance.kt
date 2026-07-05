package com.raival.compose.file.explorer.screen.viewer.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.common.name
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
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

class VideoPlayerInstance(
    override val uri: Uri,
    override val id: String,
    val playlist: List<Uri> = listOf()
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
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                val uris = playlist.ifEmpty { listOf(uri) }
                val mediaItems = uris.map { itemUri ->
                    MediaItem.Builder().setUri(itemUri).build()
                }
                setMediaItems(mediaItems)

                val startIndex = uris.indexOfFirst { it == uri }.coerceAtLeast(0)
                seekTo(startIndex, 0)
                prepare()

                volume = 1.0f

                _playerState.update { currentState ->
                    currentState.copy(
                        title = uri.name ?: globalClass.getString(R.string.unknown),
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
                                    duration = duration,
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
                        _playerState.update { currentState ->
                            currentState.copy(
                                currentPlaylistIndex = currentIndex,
                                title = currentUri.name ?: globalClass.getString(R.string.unknown)
                            )
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

    fun toggleRepeatMode() {
        val newMode = when (_playerState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer?.repeatMode = newMode
        _playerState.update { it.copy(repeatMode = newMode) }
    }

    fun playPlaylistItem(index: Int) {
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
            }
        }
    }

    fun playPrevious() {
        exoPlayer?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
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