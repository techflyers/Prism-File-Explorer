package com.raival.compose.file.explorer.screen.viewer.video.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.raival.compose.file.explorer.common.toFormattedTime
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerInstance
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.PictureInPictureParams
import android.os.Build
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ModalBottomSheet
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
    val scope = rememberCoroutineScope()

    // Screen Dimensions for gestures
    var playerWidth by remember { mutableStateOf(0) }
    var playerHeight by remember { mutableStateOf(0) }

    // Gesture State HUD
    var activeGestureType by remember { mutableStateOf<String?>(null) } // "brightness" or "volume"
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var gestureTimerJob by remember { mutableStateOf<Job?>(null) }

    // Accumulators for smooth gestures
    var gestureAccumulatedVolume by remember { mutableFloatStateOf(0f) }
    var gestureAccumulatedBrightness by remember { mutableFloatStateOf(0f) }

    // Playlist bottom sheet visibility
    var showPlaylist by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun adjustBrightness(delta: Float) {
        val act = context as? Activity ?: return
        val lp = act.window.attributes
        
        gestureAccumulatedBrightness = (gestureAccumulatedBrightness - delta).coerceIn(0.01f, 1f)
        lp.screenBrightness = gestureAccumulatedBrightness
        act.window.attributes = lp

        activeGestureType = "brightness"
        gestureValue = gestureAccumulatedBrightness

        gestureTimerJob?.cancel()
        gestureTimerJob = scope.launch {
            delay(1000)
            activeGestureType = null
        }
    }

    fun adjustVolume(delta: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        gestureAccumulatedVolume = (gestureAccumulatedVolume - delta).coerceIn(0f, 1f)
        val targetVolume = (gestureAccumulatedVolume * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)

        activeGestureType = "volume"
        gestureValue = gestureAccumulatedVolume

        if (targetVolume > 0 && playerState.isMuted) {
            videoPlayerInstance.toggleMute()
        }

        gestureTimerJob?.cancel()
        gestureTimerJob = scope.launch {
            delay(1000)
            activeGestureType = null
        }
    }

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
        color = Color.Black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    playerWidth = coordinates.size.width
                    playerHeight = coordinates.size.height
                }
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
                                if (!playerState.isInPictureInPicture) {
                                    videoPlayerInstance.toggleControls()
                                }
                            }
                        )
                )
            }

            // Gesture Detector Overlay (only active when not in Picture-in-Picture)
            if (!playerState.isInPictureInPicture && playerHeight > 0 && playerWidth > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .detectVerticalDrags(
                            onDragStart = { positionX ->
                                if (positionX < playerWidth / 2) {
                                    val act = context as? Activity
                                    val lp = act?.window?.attributes
                                    val currentBrightness = if (lp == null || lp.screenBrightness < 0) {
                                        try {
                                            android.provider.Settings.System.getInt(
                                                context.contentResolver,
                                                android.provider.Settings.System.SCREEN_BRIGHTNESS
                                            ) / 255f
                                        } catch (_: Exception) {
                                            0.5f
                                        }
                                    } else {
                                        lp.screenBrightness
                                    }
                                    gestureAccumulatedBrightness = currentBrightness
                                } else {
                                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    gestureAccumulatedVolume = currentVolume.toFloat() / maxVolume.toFloat()
                                }
                            },
                            onVerticalDrag = { positionX, dragAmount ->
                                val fraction = dragAmount / playerHeight
                                if (positionX < playerWidth / 2) {
                                    adjustBrightness(fraction)
                                } else {
                                    adjustVolume(fraction)
                                }
                            }
                        )
                )
            }

            // Controls Overlay
            AnimatedVisibility(
                visible = playerState.showControls && !playerState.isLoading && !playerState.isInPictureInPicture,
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
                    onRotateScreen = {
                        val act = context as? Activity
                        act?.let {
                            val currentOrientation = it.resources.configuration.orientation
                            it.requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            }
                        }
                    },
                    onEnterPiP = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val isPlayingNow = videoPlayerInstance.playerState.value.isPlaying
                                val iconId = if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                                val title = if (isPlayingNow) "Pause" else "Play"

                                val intent = Intent("ACTION_MEDIA_CONTROL")
                                val pendingIntent = android.app.PendingIntent.getBroadcast(
                                    context,
                                    0,
                                    intent,
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                                )

                                val icon = android.graphics.drawable.Icon.createWithResource(context, iconId)
                                val action = android.app.RemoteAction(icon, title, title, pendingIntent)

                                val pipParams = PictureInPictureParams.Builder()
                                    .setActions(listOf(action))
                                    .build()
                                (context as? Activity)?.enterPictureInPictureMode(pipParams)
                            } catch (e: Exception) {
                                android.util.Log.e("VideoPlayerScreen", "Failed to enter PiP: ${e.message}")
                            }
                        }
                    },
                    onPlaylistClick = {
                        showPlaylist = true
                    },
                    onPlayNext = { videoPlayerInstance.playNext() },
                    onPlayPrevious = { videoPlayerInstance.playPrevious() },
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

            // Gesture HUD Overlay
            AnimatedVisibility(
                visible = activeGestureType != null && !playerState.isInPictureInPicture,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(
                    if (activeGestureType == "brightness") Alignment.CenterStart else Alignment.CenterEnd
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = if (activeGestureType == "brightness") {
                            Icons.Default.Brightness5
                        } else {
                            if (gestureValue == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${(gestureValue * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Playlist bottom sheet
            if (showPlaylist && !playerState.isInPictureInPicture) {
                VideoPlaylistSheet(
                    playlist = playerState.playlist,
                    currentIndex = playerState.currentPlaylistIndex,
                    onItemClick = { index ->
                        videoPlayerInstance.playPlaylistItem(index)
                        showPlaylist = false
                    },
                    onDismiss = { showPlaylist = false }
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

// Simple modifier helper to detect vertical drags
fun Modifier.detectVerticalDrags(
    onDragStart: (positionX: Float) -> Unit,
    onVerticalDrag: (positionX: Float, dragAmount: Float) -> Unit
): Modifier = this.pointerInput(Unit) {
    var startX = 0f
    detectVerticalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            onDragStart(startX)
        },
        onVerticalDrag = { change, dragAmount ->
            onVerticalDrag(startX, dragAmount)
        }
    )
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
    onRotateScreen: () -> Unit,
    onEnterPiP: () -> Unit,
    onPlaylistClick: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
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
            onRotateScreen = onRotateScreen,
            onEnterPiP = onEnterPiP,
            onPlaylistClick = onPlaylistClick,
            onOpenWith = onOpenWith,
            modifier = Modifier.align(Alignment.TopStart)
        )

        // Center controls
        CenterControls(
            isPlaying = state.isPlaying,
            hasPrevious = state.playlist.size > 1 && state.currentPlaylistIndex > 0,
            hasNext = state.playlist.size > 1 && state.currentPlaylistIndex < state.playlist.size - 1,
            onPlayPause = onPlayPause,
            onSeekForward = onSeekForward,
            onSeekBackward = onSeekBackward,
            onPlayPrevious = onPlayPrevious,
            onPlayNext = onPlayNext,
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
    onRotateScreen: () -> Unit,
    onEnterPiP: () -> Unit,
    onPlaylistClick: () -> Unit,
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

        // Screen Rotation
        IconButton(onClick = onRotateScreen) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.RotateRight,
                contentDescription = "Rotate Screen",
                tint = colorScheme.onSurface
            )
        }

        // Picture in Picture
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            IconButton(onClick = onEnterPiP) {
                Icon(
                    imageVector = Icons.Default.PictureInPicture,
                    contentDescription = "PiP Mode",
                    tint = colorScheme.onSurface
                )
            }
        }

        // Playlist queue (Only if playlist size > 1)
        if (playerState.playlist.size > 1) {
            IconButton(onClick = onPlaylistClick) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = "Playlist",
                    tint = colorScheme.onSurface
                )
            }
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
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onPlayPrevious: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Seek backward 10s
        IconButton(
            onClick = onPlayPrevious,
            enabled = hasPrevious,
            modifier = Modifier
                .size(48.dp)
                .background(
                    colorScheme.surface.copy(alpha = if (hasPrevious) 0.6f else 0.2f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Video",
                tint = if (hasPrevious) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Seek backward 10s
        IconButton(
            onClick = onSeekBackward,
            modifier = Modifier
                .size(48.dp)
                .background(
                    colorScheme.surface.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Play/Pause
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(64.dp)
                .background(
                    colorScheme.primary,
                    CircleShape
                )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        // Seek forward 10s
        IconButton(
            onClick = onSeekForward,
            modifier = Modifier
                .size(48.dp)
                .background(
                    colorScheme.surface.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = null,
                tint = colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // Seek forward 10s
        IconButton(
            onClick = onPlayNext,
            enabled = hasNext,
            modifier = Modifier
                .size(48.dp)
                .background(
                    colorScheme.surface.copy(alpha = if (hasNext) 0.6f else 0.2f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Video",
                tint = if (hasNext) colorScheme.onSurface else colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlaylistSheet(
    playlist: List<Uri>,
    currentIndex: Int,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (playlist.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, playlist.lastIndex))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Playlist (${playlist.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            }

            androidx.compose.material3.HorizontalDivider(
                color = colorScheme.onSurface.copy(alpha = 0.1f)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                itemsIndexed(playlist) { index, trackUri ->
                    val isCurrentTrack = index == currentIndex
                    val trackName = trackUri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?: "Video ${index + 1}"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(index) }
                            .background(
                                if (isCurrentTrack) colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCurrentTrack) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    color = colorScheme.onSurface.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = trackName,
                            color = if (isCurrentTrack) colorScheme.primary else colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (index < playlist.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            color = colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}