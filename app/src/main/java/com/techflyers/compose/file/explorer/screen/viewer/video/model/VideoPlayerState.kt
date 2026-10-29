package com.techflyers.compose.file.explorer.screen.viewer.video.model

import android.net.Uri
import androidx.media3.common.Player

data class SubtitleItem(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val language: String = "en",
    val isAutoMatched: Boolean = false
)

data class VideoPlayerState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isMuted: Boolean = false,
    val showControls: Boolean = true,
    val title: String = "",
    val playlist: List<Uri> = emptyList(),
    val currentPlaylistIndex: Int = 0,
    val isInPictureInPicture: Boolean = false,
    val subtitlesEnabled: Boolean = true,
    val availableSubtitles: List<SubtitleItem> = emptyList(),
    val selectedSubtitleUri: Uri? = null,
    val subtitleTextSizeSp: Float = 20f
)
