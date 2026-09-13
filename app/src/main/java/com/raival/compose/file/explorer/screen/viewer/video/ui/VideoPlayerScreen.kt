package com.raival.compose.file.explorer.screen.viewer.video.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.toFormattedTime
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerInstance
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.PictureInPictureParams
import android.os.Build
import androidx.compose.material3.ModalBottomSheet
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import kotlin.math.abs

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    videoPlayerInstance: VideoPlayerInstance,
    onBackPressed: () -> Unit,
    onDelete: (Uri) -> Boolean
) {
    val context = LocalContext.current
    val playerState by videoPlayerInstance.playerState.collectAsState()
    val scope = rememberCoroutineScope()

    // Screen Dimensions for gestures
    var playerWidth by remember { mutableStateOf(0) }
    var playerHeight by remember { mutableStateOf(0) }

    // Lock State
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var unlockInteractionCount by remember { mutableIntStateOf(0) }

    // Auto-hide controls timer (PDF viewer pattern)
    var interactionCount by remember { mutableIntStateOf(0) }
    var isInteractingWithGestures by remember { mutableStateOf(false) }
    var isDraggingSeekSlider by remember { mutableStateOf(false) }

    val autoHideEnabled = globalClass.preferencesManager.autoHideVideoControls

    // ── Auto-hide control bars after 3 seconds of inactivity ──────────────────
    LaunchedEffect(
        playerState.showControls,
        isInteractingWithGestures,
        isDraggingSeekSlider,
        isLocked,
        interactionCount
    ) {
        if (autoHideEnabled &&
            playerState.showControls &&
            !isInteractingWithGestures &&
            !isDraggingSeekSlider &&
            !isLocked
        ) {
            delay(3000)
            videoPlayerInstance.setControlsVisible(false)
        }
    }

    // ── Auto-hide floating unlock button after 3 seconds ──────────────────────
    LaunchedEffect(showUnlockButton, unlockInteractionCount) {
        if (showUnlockButton) {
            delay(3000)
            showUnlockButton = false
        }
    }

    // Back handlers: hide controls first if visible, or show unlock button if locked
    BackHandler(enabled = isLocked) {
        showUnlockButton = true
        unlockInteractionCount++
        globalClass.showMsg(R.string.controls_locked_hint)
    }

    BackHandler(enabled = !isLocked && playerState.showControls) {
        videoPlayerInstance.setControlsVisible(false)
    }

    // Gesture State HUD
    var activeGestureType by remember { mutableStateOf<String?>(null) } // "brightness" or "volume"
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var gestureHudCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeGestureType, gestureHudCount, isInteractingWithGestures) {
        if (activeGestureType != null && !isInteractingWithGestures) {
            delay(1000)
            activeGestureType = null
        }
    }

    // Accumulators for smooth gestures
    var gestureAccumulatedVolume by remember { mutableFloatStateOf(0f) }
    var gestureAccumulatedBrightness by remember { mutableFloatStateOf(0f) }

    // Playlist bottom sheet visibility
    var showPlaylist by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun adjustBrightness(delta: Float) {
        val act = context as? Activity ?: return
        val lp = act.window.attributes
        
        gestureAccumulatedBrightness = (gestureAccumulatedBrightness - delta).coerceIn(0.01f, 1f)
        lp.screenBrightness = gestureAccumulatedBrightness
        act.window.attributes = lp

        activeGestureType = "brightness"
        gestureValue = gestureAccumulatedBrightness
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
            // Video Player View with pinch-to-zoom
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
                        .zoomable(zoomState = rememberZoomState())
                )
            }

            // Gesture Detector Overlay (handles single tap to show controls, vertical drags for brightness/volume)
            if (!playerState.isInPictureInPicture && playerHeight > 0 && playerWidth > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .videoPlayerGestureDetector(
                            enabled = !playerState.showControls,
                            playerWidth = playerWidth,
                            playerHeight = playerHeight,
                            onTap = {
                                if (isLocked) {
                                    showUnlockButton = true
                                    unlockInteractionCount++
                                    globalClass.showMsg(R.string.controls_locked_hint)
                                } else {
                                    videoPlayerInstance.setControlsVisible(true)
                                    interactionCount++
                                }
                            },
                            onDragStart = { positionX ->
                                if (!isLocked) {
                                    isInteractingWithGestures = true
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
                                }
                            },
                            onVerticalDrag = { positionX, dragAmount ->
                                if (!isLocked) {
                                    val fraction = dragAmount / playerHeight
                                    if (positionX < playerWidth / 2) {
                                        adjustBrightness(fraction)
                                    } else {
                                        adjustVolume(fraction)
                                    }
                                }
                            },
                            onDragEnd = {
                                if (!isLocked) {
                                    isInteractingWithGestures = false
                                    interactionCount++
                                    gestureHudCount++
                                }
                            }
                        )
                )
            }

            // Controls Overlay (animated toolbars sliding in/out like PDF viewer)
            if (!playerState.isInPictureInPicture && !isLocked) {
                VideoControls(
                    state = playerState,
                    visible = playerState.showControls && !playerState.isLoading,
                    onBackgroundTap = {
                        videoPlayerInstance.setControlsVisible(false)
                    },
                    onPlayPause = {
                        videoPlayerInstance.playPause()
                        interactionCount++
                    },
                    onSeekForward = {
                        val newPos =
                            (playerState.currentPosition + 10000).coerceAtMost(playerState.duration)
                        videoPlayerInstance.seekTo(newPos)
                        interactionCount++
                    },
                    onSeekBackward = {
                        val newPos = (playerState.currentPosition - 10000).coerceAtLeast(0)
                        videoPlayerInstance.seekTo(newPos)
                        interactionCount++
                    },
                    onSeek = { position ->
                        videoPlayerInstance.seekTo(position)
                        interactionCount++
                    },
                    onBackPressed = onBackPressed,
                    onToggleMute = {
                        videoPlayerInstance.toggleMute()
                        interactionCount++
                    },
                    onSpeedChange = { speed ->
                        videoPlayerInstance.setPlaybackSpeed(speed)
                        interactionCount++
                    },
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
                        interactionCount++
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
                        interactionCount++
                    },
                    onPlayNext = {
                        videoPlayerInstance.playNext()
                        interactionCount++
                    },
                    onPlayPrevious = {
                        videoPlayerInstance.playPrevious()
                        interactionCount++
                    },
                    onDelete = {
                        showDeleteConfirmation = true
                        interactionCount++
                    },
                    onOpenWith = {
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            data = videoUri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(openIntent, context.getString(com.raival.compose.file.explorer.R.string.open_with))
                        )
                        interactionCount++
                    },
                    onLock = {
                        isLocked = true
                        videoPlayerInstance.setControlsVisible(false)
                        showUnlockButton = true
                        unlockInteractionCount++
                        globalClass.showMsg(R.string.lock_controls)
                    },
                    onToggleRepeat = {
                        val newMode = videoPlayerInstance.toggleRepeatMode()
                        val msg = when (newMode) {
                            Player.REPEAT_MODE_ONE -> context.getString(R.string.loop_one)
                            Player.REPEAT_MODE_ALL -> context.getString(R.string.loop_all)
                            else -> context.getString(R.string.loop_off)
                        }
                        globalClass.showMsg(msg)
                        interactionCount++
                    },
                    onSeekingChanged = { isDragging ->
                        isDraggingSeekSlider = isDragging
                        interactionCount++
                    },
                    onUserInteraction = {
                        interactionCount++
                    }
                )
            }

            // Floating Unlock Button when screen is locked
            AnimatedVisibility(
                visible = isLocked && showUnlockButton,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp)
            ) {
                IconButton(
                    onClick = {
                        isLocked = false
                        showUnlockButton = false
                        videoPlayerInstance.setControlsVisible(true)
                        interactionCount++
                        globalClass.showMsg(R.string.unlock_controls)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.unlock_controls),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
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

            if (showDeleteConfirmation && !playerState.isInPictureInPicture) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text(context.getString(com.raival.compose.file.explorer.R.string.delete_confirmation)) },
                    text = { Text(context.getString(com.raival.compose.file.explorer.R.string.delete_confirmation_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirmation = false
                            val deletedIndex = playerState.currentPlaylistIndex
                            val currentVideoUri = playerState.playlist
                                .getOrNull(deletedIndex) ?: videoUri
                            if (onDelete(currentVideoUri)) {
                                val remaining = videoPlayerInstance.removePlaylistItem(deletedIndex)
                                if (remaining.isEmpty()) {
                                    onBackPressed()
                                } else {
                                    videoPlayerInstance.playPlaylistItem(
                                        deletedIndex.coerceAtMost(remaining.lastIndex)
                                    )
                                }
                            }
                        }) {
                            Text(context.getString(com.raival.compose.file.explorer.R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text(context.getString(com.raival.compose.file.explorer.R.string.cancel))
                        }
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

// Modifier helper to detect vertical drags and single taps, respecting multi-touch pinch-to-zoom
fun Modifier.videoPlayerGestureDetector(
    enabled: Boolean,
    playerWidth: Int,
    playerHeight: Int,
    onTap: () -> Unit,
    onDragStart: (positionX: Float) -> Unit,
    onVerticalDrag: (positionX: Float, dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier = if (!enabled || playerWidth <= 0 || playerHeight <= 0) this else this.pointerInput(enabled, playerWidth, playerHeight) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerId = down.id
        var isDragging = false
        var hasMultiTouch = false
        val touchSlop = viewConfiguration.touchSlop
        val startY = down.position.y
        val startX = down.position.x

        while (true) {
            val event = awaitPointerEvent()

            // If multi-touch detected (e.g. pinch to zoom), abort without consuming so zoomable can handle it
            if (event.changes.size > 1) {
                hasMultiTouch = true
                if (isDragging) {
                    onDragEnd()
                    isDragging = false
                }
                break
            }

            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null || change.changedToUp()) {
                if (!isDragging && !hasMultiTouch) {
                    onTap()
                } else if (isDragging) {
                    onDragEnd()
                }
                break
            }

            if (!isDragging) {
                val dragY = change.position.y - startY
                val dragX = change.position.x - startX
                if (abs(dragY) > touchSlop && abs(dragY) > abs(dragX)) {
                    isDragging = true
                    onDragStart(startX)
                    change.consume()
                }
            } else {
                val deltaY = change.positionChange().y
                onVerticalDrag(startX, deltaY)
                change.consume()
            }
        }
    }
}

@Composable
fun VideoControls(
    state: VideoPlayerState,
    visible: Boolean,
    onBackgroundTap: () -> Unit,
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
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeekingChanged: (Boolean) -> Unit = {},
    onUserInteraction: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Tap anywhere on the empty video area dismisses controls immediately (like PDF viewer)
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onBackgroundTap() })
                    }
            )
        }

        // Top bar with title and action buttons (slide-in from top + fade)
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(350)
            ) + fadeIn(animationSpec = tween(350)),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopBar(
                playerState = state,
                onBackPressed = onBackPressed,
                onToggleMute = onToggleMute,
                onSpeedChange = onSpeedChange,
                onRotateScreen = onRotateScreen,
                onEnterPiP = onEnterPiP,
                onPlaylistClick = onPlaylistClick,
                onOpenWith = onOpenWith,
                onDelete = onDelete,
                onLock = onLock,
                onToggleRepeat = onToggleRepeat,
                onUserInteraction = onUserInteraction,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Center controls (scale + fade)
        AnimatedVisibility(
            visible = visible,
            enter = scaleIn(
                initialScale = 0.8f,
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300)),
            exit = scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            CenterControls(
                isPlaying = state.isPlaying,
                hasPrevious = state.playlist.size > 1 && (state.repeatMode == Player.REPEAT_MODE_ALL || state.currentPlaylistIndex > 0),
                hasNext = state.playlist.size > 1 && (state.repeatMode == Player.REPEAT_MODE_ALL || state.currentPlaylistIndex < state.playlist.size - 1),
                onPlayPause = {
                    onPlayPause()
                    onUserInteraction()
                },
                onSeekForward = {
                    onSeekForward()
                    onUserInteraction()
                },
                onSeekBackward = {
                    onSeekBackward()
                    onUserInteraction()
                },
                onPlayPrevious = {
                    onPlayPrevious()
                    onUserInteraction()
                },
                onPlayNext = {
                    onPlayNext()
                    onUserInteraction()
                }
            )
        }

        // Bottom progress bar (slide-in from bottom + fade)
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeIn(animationSpec = tween(350)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(350)
            ) + fadeOut(animationSpec = tween(350)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControls(
                currentPosition = state.currentPosition,
                duration = state.duration,
                onSeek = { pos ->
                    onSeek(pos)
                    onUserInteraction()
                },
                onSeekingChanged = onSeekingChanged,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = playerState.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
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
                    color = if (playerState.playbackSpeed != 1.0f) colorScheme.primary else Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // Screen Rotation
            IconButton(onClick = onRotateScreen) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotate Screen",
                    tint = Color.White
                )
            }

            // Picture in Picture
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                IconButton(onClick = onEnterPiP) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = "PiP Mode",
                        tint = Color.White
                    )
                }
            }

            // Playlist queue (Only if playlist size > 1)
            if (playerState.playlist.size > 1) {
                IconButton(onClick = onPlaylistClick) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "Playlist",
                        tint = Color.White
                    )
                }
            }

            // Loop / Repeat button
            IconButton(onClick = {
                onToggleRepeat()
                onUserInteraction()
            }) {
                val repeatIcon = if (playerState.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                val repeatTint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF) {
                    colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.6f)
                }
                val repeatDesc = when (playerState.repeatMode) {
                    Player.REPEAT_MODE_ONE -> stringResource(R.string.loop_one)
                    Player.REPEAT_MODE_ALL -> stringResource(R.string.loop_all)
                    else -> stringResource(R.string.loop_off)
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = repeatDesc,
                    tint = repeatTint
                )
            }

            IconButton(
                onClick = {
                    onToggleMute()
                    onUserInteraction()
                },
            ) {
                Icon(
                    imageVector = if (playerState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            // Lock button
            IconButton(onClick = onLock) {
                Icon(
                    imageVector = Icons.Rounded.LockOpen,
                    contentDescription = stringResource(R.string.lock_controls),
                    tint = Color.White
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }

            // Open With button
            onOpenWith?.let { onClick ->
                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open with",
                        tint = Color.White
                    )
                }
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
        // Previous Video
        IconButton(
            onClick = onPlayPrevious,
            enabled = hasPrevious,
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = if (hasPrevious) 0.6f else 0.2f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Video",
                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(24.dp)
            )
        }

        // Seek backward 10s
        IconButton(
            onClick = onSeekBackward,
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = null,
                tint = Color.White,
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
                    Color.Black.copy(alpha = 0.6f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Next Video
        IconButton(
            onClick = onPlayNext,
            enabled = hasNext,
            modifier = Modifier
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = if (hasNext) 0.6f else 0.2f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Video",
                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
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
    onSeekingChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.45f),
                        Color.Black.copy(alpha = 0.85f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                if (!isDragging) {
                    isDragging = true
                    onSeekingChanged(true)
                }
                pendingSeekPosition = (value * duration).toLong()
            },
            onValueChangeFinished = {
                hasUncommittedSeek = true
                onSeek(pendingSeekPosition)
                isDragging = false
                onSeekingChanged(false)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.primary,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
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
                color = Color.White
            )
            Text(
                text = duration.toFormattedTime(),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
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