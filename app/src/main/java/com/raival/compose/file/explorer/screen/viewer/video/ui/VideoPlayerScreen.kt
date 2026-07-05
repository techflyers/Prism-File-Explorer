package com.raival.compose.file.explorer.screen.viewer.video.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import androidx.media3.ui.PlayerView
import com.raival.compose.file.explorer.common.toFormattedTime
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerInstance
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import kotlin.math.abs

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    videoPlayerInstance: VideoPlayerInstance,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val playerState by videoPlayerInstance.playerState.collectAsState()

    LaunchedEffect(videoUri) {
        videoPlayerInstance.initializePlayer(context, videoUri)
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayerInstance.onClose()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Video Player View
            if (playerState.isReady) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = videoPlayerInstance.getPlayer()
                            useController = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomable(
                            zoomState = rememberZoomState(),
                            onTap = {
                                videoPlayerInstance.toggleControls()
                            }
                        )
                )
            }

            // Controls Overlay
            AnimatedVisibility(
                visible = playerState.showControls && !playerState.isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                VideoControls(
                    state = playerState,
                    onPlayPause = { videoPlayerInstance.playPause() },
                    onSeekForward = {
                        val newPos =
                            (playerState.currentPosition + 10000).coerceAtMost(playerState.duration)
                        videoPlayerInstance.seekTo(newPos)
                    },
                    onSeekBackward = {
                        val newPos = (playerState.currentPosition - 10000).coerceAtLeast(0)
                        videoPlayerInstance.seekTo(newPos)
                    },
                    onSeek = { position ->
                        videoPlayerInstance.seekTo(position)
                    },
                    onBackPressed = onBackPressed,
                    onToggleMute = { videoPlayerInstance.toggleMute() },
                    onSpeedChange = { speed -> videoPlayerInstance.setPlaybackSpeed(speed) },
                    onOpenWith = {
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = videoUri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(openIntent, context.getString(com.raival.compose.file.explorer.R.string.open_with))
                        )
                    }
                )
            }

            // Loading indicator
            if (playerState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoControls(
    state: VideoPlayerState,
    onToggleMute: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeek: (Long) -> Unit,
    onBackPressed: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOpenWith: (() -> Unit)? = null,
) {
    val defaultColor = colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        defaultColor.copy(alpha = 0.9f),
                        Color.Transparent,
                        Color.Transparent,
                        Color.Transparent,
                        defaultColor.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        // Top bar with title and back button
        TopBar(
            playerState = state,
            onBackPressed = onBackPressed,
            onToggleMute = onToggleMute,
            onSpeedChange = onSpeedChange,
            onOpenWith = onOpenWith,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Center controls
        CenterControls(
            isPlaying = state.isPlaying,
            onPlayPause = onPlayPause,
            onSeekForward = onSeekForward,
            onSeekBackward = onSeekBackward,
            modifier = Modifier.align(Alignment.Center)
        )

        // Bottom progress bar
        BottomControls(
            currentPosition = state.currentPosition,
            duration = state.duration,
            onSeek = onSeek,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}

@Composable
fun TopBar(
    playerState: VideoPlayerState,
    onBackPressed: () -> Unit,
    onToggleMute: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onOpenWith: (() -> Unit)? = null,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackPressed,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = playerState.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Speed button
        Surface(
            onClick = {
                val currentIndex = SPEED_OPTIONS.indexOf(playerState.playbackSpeed)
                val nextIndex = if (currentIndex < 0 || currentIndex >= SPEED_OPTIONS.size - 1) 0 else currentIndex + 1
                onSpeedChange(SPEED_OPTIONS[nextIndex])
            },
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Text(
                text = if (playerState.playbackSpeed == playerState.playbackSpeed.toLong().toFloat())
                    "${playerState.playbackSpeed.toInt()}\u00d7"
                else
                    "${playerState.playbackSpeed}\u00d7",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (playerState.playbackSpeed != 1.0f) colorScheme.primary else colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        IconButton(
            onClick = onToggleMute,
        ) {
            Icon(
                imageVector = if (playerState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = colorScheme.onSurface
            )
        }

        // Open With button
        onOpenWith?.let { onClick ->
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.OpenInNew,
                    contentDescription = "Open with",
                    tint = colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun CenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Seek backward
        IconButton(
            onClick = onSeekBackward,
            modifier = Modifier
                .size(56.dp)
                .background(
                    colorScheme.surface.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }

        // Play/Pause
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(72.dp)
                .background(
                    colorScheme.primary,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colorScheme.onPrimary.copy(alpha = 0.7f),
                modifier = Modifier.size(36.dp)
            )
        }

        // Seek forward
        IconButton(
            onClick = onSeekForward,
            modifier = Modifier
                .size(56.dp)
                .background(
                    colorScheme.surface.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomControls(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .padding(bottom = 24.dp),
        ) {
            var isDragging by remember { mutableStateOf(false) }
            var pendingSeekPosition by remember { mutableLongStateOf(0L) }
            var hasUncommittedSeek by remember { mutableStateOf(false) }

            // Only show media player position when not actively seeking
            val displayPosition = when {
                isDragging || hasUncommittedSeek -> pendingSeekPosition
                else -> currentPosition
            }

            val progress = if (duration > 0) displayPosition.toFloat() / duration.toFloat() else 0f

            LaunchedEffect(currentPosition) {
                // Reset uncommitted seek flag when media player catches up
                if (hasUncommittedSeek && abs(currentPosition - pendingSeekPosition) < 1000) {
                    hasUncommittedSeek = false
                }
            }

            Slider(
                value = progress,
                onValueChange = { value ->
                    isDragging = true
                    pendingSeekPosition = (value * duration).toLong()
                },
                onValueChangeFinished = {
                    hasUncommittedSeek = true
                    onSeek(pendingSeekPosition)
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = colorScheme.primary,
                    activeTrackColor = colorScheme.primary,
                    inactiveTrackColor = colorScheme.onSurface.copy(alpha = 0.3f)
                )
            )

            // Time labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayPosition.toFormattedTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface
                )
                Text(
                    text = duration.toFormattedTime(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurface
                )
            }
        }
    }
}