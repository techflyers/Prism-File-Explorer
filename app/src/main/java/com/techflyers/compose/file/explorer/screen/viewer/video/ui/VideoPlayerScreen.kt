package com.techflyers.compose.file.explorer.screen.viewer.video.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.TypedValue
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
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.core.content.FileProvider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.common.toFormattedTime
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.ExtendedMetadataSheet
import com.techflyers.compose.file.explorer.screen.main.tab.nfile_tools.ui.FileSelectionDialog
import com.techflyers.compose.file.explorer.screen.preferences.constant.VideoDecoderMode
import com.techflyers.compose.file.explorer.screen.viewer.video.VideoPlayerActivity
import com.techflyers.compose.file.explorer.screen.viewer.video.VideoPlayerInstance
import com.techflyers.compose.file.explorer.screen.viewer.video.model.SubtitleItem
import com.techflyers.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs

private val SPEED_OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    videoPlayerInstance: VideoPlayerInstance,
    onBackPressed: () -> Unit,
    onDelete: (Uri) -> Boolean
) {
    val context = LocalContext.current
    val playerState by videoPlayerInstance.playerState.collectAsState()

    // Screen Dimensions for gestures
    var playerWidth by remember { mutableStateOf(0) }
    var playerHeight by remember { mutableStateOf(0) }

    // Infinite Zoom & Pan state for the video surface
    var videoZoomScale by remember { mutableFloatStateOf(1f) }
    var videoZoomOffsetX by remember { mutableFloatStateOf(0f) }
    var videoZoomOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(videoUri, playerState.currentPlaylistIndex) {
        videoZoomScale = 1f
        videoZoomOffsetX = 0f
        videoZoomOffsetY = 0f
    }

    fun applyVideoZoomTransform(zoomChange: Float, panChange: Offset, centroid: Offset) {
        if (playerWidth <= 0 || playerHeight <= 0) return
        val oldScale = videoZoomScale
        val newScale = (oldScale * zoomChange).coerceIn(1f, 1000f)
        if (newScale <= 1.001f) {
            videoZoomScale = 1f
            videoZoomOffsetX = 0f
            videoZoomOffsetY = 0f
            return
        }
        val actualZoom = newScale / oldScale
        val centerX = playerWidth / 2f
        val centerY = playerHeight / 2f
        val focusX = centroid.x - centerX
        val focusY = centroid.y - centerY

        val rawOffsetX = (videoZoomOffsetX - focusX) * actualZoom + focusX + panChange.x
        val rawOffsetY = (videoZoomOffsetY - focusY) * actualZoom + focusY + panChange.y

        val maxOffsetX = (playerWidth * (newScale - 1f)) / 2f
        val maxOffsetY = (playerHeight * (newScale - 1f)) / 2f

        videoZoomScale = newScale
        videoZoomOffsetX = rawOffsetX.coerceIn(-maxOffsetX, maxOffsetX)
        videoZoomOffsetY = rawOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    // Lock State
    var isLocked by remember { mutableStateOf(false) }
    var showUnlockButton by remember { mutableStateOf(false) }
    var unlockInteractionCount by remember { mutableIntStateOf(0) }

    // Auto-hide controls timer
    var interactionCount by remember { mutableIntStateOf(0) }
    var isInteractingWithGestures by remember { mutableStateOf(false) }
    var isDraggingSeekSlider by remember { mutableStateOf(false) }
    var isSpeedMenuExpanded by remember { mutableStateOf(false) }

    val autoHideEnabled = globalClass.preferencesManager.autoHideVideoControls

    // ── Auto-hide control bars after 3 seconds of inactivity ──────────────────
    LaunchedEffect(
        playerState.showControls,
        isInteractingWithGestures,
        isDraggingSeekSlider,
        isSpeedMenuExpanded,
        isLocked,
        interactionCount
    ) {
        if (autoHideEnabled &&
            playerState.showControls &&
            !isInteractingWithGestures &&
            !isDraggingSeekSlider &&
            !isSpeedMenuExpanded &&
            !isLocked
        ) {
            delay(3000)
            videoPlayerInstance.setControlsVisible(false)
        }
    }

    LaunchedEffect(playerState.showControls) {
        (context as? VideoPlayerActivity)?.configureImmersiveWindow(playerState.showControls)
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
    var gestureSystemBrightness by remember { mutableFloatStateOf(0.5f) }
    var gestureBrightnessIsAutomatic by remember { mutableStateOf(false) }
    var gestureReachedAutomaticDuringGesture by remember { mutableStateOf(false) }

    // Playlist & subtitle bottom sheet visibility
    var showPlaylist by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showSubtitleFilePicker by remember { mutableStateOf(false) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    fun adjustBrightness(delta: Float) {
        val act = context as? Activity ?: return
        val lp = act.window.attributes

        if (gestureBrightnessIsAutomatic && gestureReachedAutomaticDuringGesture && delta >= 0f) {
            lp.screenBrightness = -1f
            act.window.attributes = lp
            activeGestureType = "brightness"
            gestureValue = gestureSystemBrightness
            return
        }

        val baseBrightness = if (gestureBrightnessIsAutomatic) {
            gestureSystemBrightness
        } else {
            gestureAccumulatedBrightness
        }
        val nextBrightness = (baseBrightness - delta).coerceIn(0f, 1f)
        val useAutomaticBrightness = nextBrightness <= 0f
        lp.screenBrightness = if (useAutomaticBrightness) -1f else nextBrightness
        act.window.attributes = lp

        gestureBrightnessIsAutomatic = useAutomaticBrightness
        gestureReachedAutomaticDuringGesture = useAutomaticBrightness
        gestureAccumulatedBrightness = if (useAutomaticBrightness) 0f else nextBrightness

        activeGestureType = "brightness"
        gestureValue = if (useAutomaticBrightness) gestureSystemBrightness else nextBrightness
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
            val activeVideoUri = playerState.playlist.getOrNull(playerState.currentPlaylistIndex) ?: videoUri
            if (playerState.usePlatformPlayerFallback) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setOnPreparedListener { mp ->
                                mp.isLooping = playerState.repeatMode == Player.REPEAT_MODE_ONE
                                start()
                            }
                            setOnErrorListener { _, what, extra ->
                                videoPlayerInstance.onPlatformPlayerError(what, extra)
                                true
                            }
                            setVideoURI(activeVideoUri)
                        }
                    },
                    update = { view ->
                        if (view.tag != activeVideoUri) {
                            view.tag = activeVideoUri
                            view.setVideoURI(activeVideoUri)
                            view.start()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = videoZoomScale
                            scaleY = videoZoomScale
                            translationX = videoZoomOffsetX
                            translationY = videoZoomOffsetY
                        }
                )
            } else {
                val activePlayer = remember(playerState.playerInstanceId) {
                    videoPlayerInstance.getPlayer()
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = activePlayer
                            useController = false
                            subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, playerState.subtitleTextSizeSp)
                        }
                    },
                    update = { view ->
                        val instanceVersion = playerState.playerInstanceId
                        val latestPlayer = if (instanceVersion >= 0) videoPlayerInstance.getPlayer() else activePlayer
                        if (view.player !== latestPlayer) {
                            view.player = latestPlayer
                        }
                        view.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, playerState.subtitleTextSizeSp)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = videoZoomScale
                            scaleY = videoZoomScale
                            translationX = videoZoomOffsetX
                            translationY = videoZoomOffsetY
                        }
                )
            }

            // Gesture Detector Overlay
            if (!playerState.isInPictureInPicture && playerHeight > 0 && playerWidth > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .videoPlayerGestureDetector(
                            enabled = !playerState.showControls,
                            playerWidth = playerWidth,
                            playerHeight = playerHeight,
                            isZoomed = { videoZoomScale > 1.01f },
                            onTransform = { zoomChange, panChange, centroid ->
                                if (!isLocked) {
                                    applyVideoZoomTransform(zoomChange, panChange, centroid)
                                    interactionCount++
                                }
                            },
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
                            onDoubleTap = { positionX ->
                                if (isLocked) {
                                    showUnlockButton = true
                                    unlockInteractionCount++
                                    globalClass.showMsg(R.string.controls_locked_hint)
                                } else {
                                    val offsetMs = if (positionX < playerWidth / 2f) -10_000L else 10_000L
                                    videoPlayerInstance.seekBy(offsetMs)
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
                                        val normalizedBrightness = currentBrightness.coerceIn(0f, 1f)
                                        gestureSystemBrightness = normalizedBrightness
                                        gestureAccumulatedBrightness = normalizedBrightness
                                        gestureBrightnessIsAutomatic = lp == null || lp.screenBrightness < 0f
                                        gestureReachedAutomaticDuringGesture = false
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

            // Controls Overlay
            if (!playerState.isInPictureInPicture && !isLocked) {
                VideoControls(
                    state = playerState,
                    visible = playerState.showControls && !playerState.isLoading,
                    onBackgroundTap = {
                        videoPlayerInstance.setControlsVisible(false)
                    },
                    onBackgroundDoubleTap = { positionX ->
                        val offsetMs = if (positionX < playerWidth / 2f) -10_000L else 10_000L
                        videoPlayerInstance.seekBy(offsetMs)
                        interactionCount++
                    },
                    onBackgroundTransform = { zoomChange, panChange, centroid ->
                        applyVideoZoomTransform(zoomChange, panChange, centroid)
                        interactionCount++
                    },
                    onSpeedMenuExpandedChanged = { expanded ->
                        isSpeedMenuExpanded = expanded
                        interactionCount++
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
                    onToggleDecoderMode = {
                        videoPlayerInstance.toggleDecoderMode(context)
                        interactionCount++
                    },
                    onSelectDecoderMode = { mode ->
                        videoPlayerInstance.setDecoderMode(context, mode)
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
                        val videoActivity = context as? VideoPlayerActivity
                        if (videoActivity != null) {
                            videoActivity.enterPipMode()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            try {
                                val isPlayingNow = videoPlayerInstance.playerState.value.isPlaying
                                val iconId = if (isPlayingNow) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                                val title = if (isPlayingNow) "Pause" else "Play"

                                val intent = Intent("ACTION_MEDIA_CONTROL").apply {
                                    `package` = context.packageName
                                }
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
                    onShare = {
                        val targetUri = playerState.playlist.getOrNull(playerState.currentPlaylistIndex) ?: videoUri
                        val shareUri = if (targetUri.scheme == "file") {
                            val file = File(targetUri.path ?: "")
                            if (file.exists()) {
                                try {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                } catch (_: Exception) {
                                    targetUri
                                }
                            } else targetUri
                        } else targetUri
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, context.getString(R.string.share))
                        )
                        interactionCount++
                    },
                    onPlaylistClick = {
                        showPlaylist = true
                        interactionCount++
                    },
                    onSubtitleClick = {
                        showSubtitleSheet = true
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
                            data = activeVideoUri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(openIntent, context.getString(R.string.open_with))
                        )
                        interactionCount++
                    },
                    onInfoClick = {
                        showMetadataSheet = true
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
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    )
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
                        text = if (activeGestureType == "brightness" && gestureBrightnessIsAutomatic) {
                            stringResource(R.string.auto_brightness)
                        } else {
                            "${(gestureValue * 100).toInt()}%"
                        },
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

            // Subtitle selector & settings bottom sheet
            if (showSubtitleSheet && !playerState.isInPictureInPicture) {
                VideoSubtitleSheet(
                    state = playerState,
                    onToggleSubtitles = { enabled ->
                        videoPlayerInstance.toggleSubtitles(enabled)
                    },
                    onSelectSubtitle = { item ->
                        videoPlayerInstance.selectSubtitle(context, item)
                        showSubtitleSheet = false
                    },
                    onBrowseExternalSubtitle = {
                        showSubtitleSheet = false
                        showSubtitleFilePicker = true
                    },
                    onChangeTextSize = { sizeSp ->
                        videoPlayerInstance.setSubtitleTextSize(sizeSp)
                    },
                    onDismiss = { showSubtitleSheet = false }
                )
            }

            if (showSubtitleFilePicker && !playerState.isInPictureInPicture) {
                val currentVideoUri = playerState.playlist
                    .getOrNull(playerState.currentPlaylistIndex) ?: videoUri
                val currentVideoDir = remember(currentVideoUri) {
                    val path = VideoPlayerActivity.resolveFilePath(context, currentVideoUri) ?: currentVideoUri.path
                    path?.let { File(it).parentFile }
                }
                FileSelectionDialog(
                    show = showSubtitleFilePicker,
                    title = "Select Subtitle (.srt, .vtt, .ass, .ttml)",
                    initialDirectory = currentVideoDir,
                    allowedExtensions = VideoPlayerInstance.SUBTITLE_EXTENSIONS,
                    onDismissRequest = { showSubtitleFilePicker = false },
                    onItemsSelected = { files ->
                        showSubtitleFilePicker = false
                        val chosen = files.firstOrNull()
                        if (chosen != null && chosen.isFile) {
                            videoPlayerInstance.addAndSelectExternalSubtitle(context, chosen)
                        }
                    }
                )
            }

            if (showMetadataSheet && !playerState.isInPictureInPicture) {
                val currentVideoUri = playerState.playlist
                    .getOrNull(playerState.currentPlaylistIndex) ?: videoUri
                val currentPath = remember(currentVideoUri) {
                    VideoPlayerActivity.resolveFilePath(context, currentVideoUri)
                        ?: com.techflyers.compose.file.explorer.common.resolveUriToPath(context, currentVideoUri)
                }
                ExtendedMetadataSheet(
                    uri = currentVideoUri,
                    extraFilePath = currentPath.takeIf { it.isNotBlank() },
                    displayNameHint = playerState.title,
                    onDismiss = { showMetadataSheet = false }
                )
            }

            if (showDeleteConfirmation && !playerState.isInPictureInPicture) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmation = false },
                    title = { Text(context.getString(R.string.delete_confirmation)) },
                    text = { Text(context.getString(R.string.delete_confirmation_message)) },
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
                            Text(context.getString(R.string.confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteConfirmation = false }) {
                            Text(context.getString(R.string.cancel))
                        }
                    }
                )
            }

            // Non-blocking track warning banner
            playerState.trackWarningMessage?.let { warning ->
                Surface(
                    color = Color(0xCC1E1E1E),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
                        .padding(horizontal = 16.dp, vertical = 72.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { videoPlayerInstance.dismissTrackWarning() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Codec / Playback Error Overlay (fixed button overflow with FlowRow & bounded scrollable diagnostics)
            if (playerState.playbackErrorTitle != null && !playerState.isInPictureInPicture) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f))
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colorScheme.surface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = playerState.playbackErrorTitle ?: "Playback Error",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }

                            playerState.playbackErrorDetails?.let { details ->
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            playerState.codecDiagnosticLog?.takeIf { it.isNotBlank() }?.let { diag ->
                                Surface(
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = diag,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(onClick = onBackPressed) {
                                    Text(
                                        text = "Close",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                OutlinedButton(
                                    onClick = { videoPlayerInstance.toggleDecoderMode(context) }
                                ) {
                                    Icon(
                                        imageVector = if (playerState.preferSoftwareDecoder) Icons.Rounded.DeveloperBoard else Icons.Rounded.Memory,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (playerState.preferSoftwareDecoder) "Try HW Decoder" else "Try SW Decoder",
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (!playerState.usePlatformPlayerFallback) {
                                    OutlinedButton(
                                        onClick = { videoPlayerInstance.switchToPlatformPlayerFallback() }
                                    ) {
                                        Text(
                                            text = "Try Native Player",
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val currentVideoUri = playerState.playlist
                                            .getOrNull(playerState.currentPlaylistIndex) ?: videoUri
                                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(currentVideoUri, "video/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try {
                                            context.startActivity(
                                                Intent.createChooser(
                                                    openIntent,
                                                    context.getString(R.string.open_with)
                                                )
                                            )
                                        } catch (_: Exception) {
                                            globalClass.showMsg(R.string.no_app_can_open_file)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.open_with),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (playerState.isLoading && playerState.playbackErrorTitle == null) {
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

fun Modifier.videoPlayerGestureDetector(
    enabled: Boolean,
    playerWidth: Int,
    playerHeight: Int,
    isZoomed: () -> Boolean,
    onTransform: (zoomChange: Float, panChange: Offset, centroid: Offset) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (positionX: Float) -> Unit,
    onDragStart: (positionX: Float) -> Unit,
    onVerticalDrag: (positionX: Float, dragAmount: Float) -> Unit,
    onDragEnd: () -> Unit
): Modifier {
    if (!enabled || playerWidth <= 0 || playerHeight <= 0) return this

    return this
        .pointerInput(enabled, playerWidth, playerHeight) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { onDoubleTap(it.x) }
            )
        }
        .pointerInput(enabled, playerWidth, playerHeight) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId = down.id
                var isDragging = false
                var isPanningZoom = false
                var wasMultiTouch = false
                val touchSlop = viewConfiguration.touchSlop
                val startY = down.position.y
                val startX = down.position.x

                while (true) {
                    val event = awaitPointerEvent()
                    val pressedCount = event.changes.count { it.pressed }
                    if (pressedCount == 0) {
                        if (isDragging) onDragEnd()
                        break
                    }

                    if (pressedCount > 1 || event.changes.size > 1) {
                        if (isDragging) {
                            isDragging = false
                            onDragEnd()
                        }
                        wasMultiTouch = true
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (zoomChange != 1f || panChange != Offset.Zero) {
                            onTransform(zoomChange, panChange, centroid)
                        }
                        event.changes.forEach { change ->
                            if (change.positionChange() != Offset.Zero) {
                                change.consume()
                            }
                        }
                        continue
                    }

                    if (wasMultiTouch) {
                        continue
                    }

                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null || change.changedToUp()) {
                        if (isDragging) onDragEnd()
                        break
                    }

                    if (!isDragging && !isPanningZoom) {
                        val dragY = change.position.y - startY
                        val dragX = change.position.x - startX
                        if (abs(dragY) > touchSlop && abs(dragY) > abs(dragX)) {
                            isDragging = true
                            onDragStart(startX)
                            change.consume()
                        } else if (isZoomed() && abs(dragX) > touchSlop && abs(dragX) >= abs(dragY)) {
                            isPanningZoom = true
                            onTransform(1f, change.positionChange(), change.position)
                            change.consume()
                        }
                    } else if (isDragging) {
                        val deltaY = change.positionChange().y
                        onVerticalDrag(startX, deltaY)
                        change.consume()
                    } else if (isPanningZoom) {
                        val pan = change.positionChange()
                        if (pan != Offset.Zero) {
                            onTransform(1f, pan, change.position)
                        }
                        change.consume()
                    }
                }
            }
        }
}

@Composable
fun VideoControls(
    state: VideoPlayerState,
    visible: Boolean,
    onBackgroundTap: () -> Unit,
    onBackgroundDoubleTap: (positionX: Float) -> Unit = {},
    onBackgroundTransform: (zoomChange: Float, panChange: Offset, centroid: Offset) -> Unit = { _, _, _ -> },
    onSpeedMenuExpandedChanged: (Boolean) -> Unit = {},
    onToggleMute: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeek: (Long) -> Unit,
    onBackPressed: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleDecoderMode: () -> Unit = {},
    onSelectDecoderMode: (VideoDecoderMode) -> Unit = {},
    onRotateScreen: () -> Unit,
    onEnterPiP: () -> Unit,
    onPlaylistClick: () -> Unit,
    onSubtitleClick: () -> Unit = {},
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onOpenWith: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeekingChanged: (Boolean) -> Unit = {},
    onUserInteraction: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (visible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onBackgroundTap() },
                            onDoubleTap = { onBackgroundDoubleTap(it.x) }
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount == 0) break
                                if (pressedCount > 1 || event.changes.size > 1) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    if (zoomChange != 1f || panChange != Offset.Zero) {
                                        onBackgroundTransform(zoomChange, panChange, centroid)
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChange() != Offset.Zero) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // Top bar with title and dynamic overflow menu
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
                onToggleDecoderMode = onToggleDecoderMode,
                onSelectDecoderMode = onSelectDecoderMode,
                onSpeedMenuExpandedChanged = onSpeedMenuExpandedChanged,
                onRotateScreen = onRotateScreen,
                onEnterPiP = onEnterPiP,
                onPlaylistClick = onPlaylistClick,
                onSubtitleClick = onSubtitleClick,
                onOpenWith = onOpenWith,
                onShare = onShare,
                onInfoClick = onInfoClick,
                onDelete = onDelete,
                onLock = onLock,
                onToggleRepeat = onToggleRepeat,
                onUserInteraction = onUserInteraction,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Center controls
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

        // Bottom progress bar
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

private data class VideoTopBarAction(
    val id: String,
    val label: String,
    val badgeText: String? = null,
    val icon: ImageVector,
    val tint: Color = Color.White,
    val onClick: () -> Unit
)

@Composable
fun TopBar(
    playerState: VideoPlayerState,
    onBackPressed: () -> Unit,
    onToggleMute: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleDecoderMode: () -> Unit = {},
    onSelectDecoderMode: (VideoDecoderMode) -> Unit = {},
    onSpeedMenuExpandedChanged: (Boolean) -> Unit = {},
    onRotateScreen: () -> Unit,
    onEnterPiP: () -> Unit,
    onPlaylistClick: () -> Unit,
    onSubtitleClick: () -> Unit = {},
    onOpenWith: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    onDelete: () -> Unit,
    onLock: () -> Unit,
    onToggleRepeat: () -> Unit,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var decoderMenuExpanded by remember { mutableStateOf(false) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    val speedDisplay = if (playerState.playbackSpeed == playerState.playbackSpeed.toLong().toFloat()) {
        "${playerState.playbackSpeed.toInt()}\u00d7"
    } else {
        "${playerState.playbackSpeed}\u00d7"
    }

    val hasActiveSub = playerState.subtitlesEnabled && playerState.selectedSubtitleUri != null
    val repeatIcon = if (playerState.repeatMode == Player.REPEAT_MODE_ONE) {
        Icons.Rounded.RepeatOne
    } else {
        Icons.Rounded.Repeat
    }
    val repeatDesc = when (playerState.repeatMode) {
        Player.REPEAT_MODE_ONE -> stringResource(R.string.loop_one)
        Player.REPEAT_MODE_ALL -> stringResource(R.string.loop_all)
        else -> stringResource(R.string.loop_off)
    }
    val lockDesc = stringResource(R.string.lock_controls)
    val metadataDesc = stringResource(R.string.extended_metadata)

    val decoderBadge = when (playerState.decoderMode) {
        VideoDecoderMode.AUTO -> if (playerState.preferSoftwareDecoder) "AUTO•SW" else "AUTO"
        VideoDecoderMode.SOFTWARE -> "SW"
        VideoDecoderMode.HARDWARE -> "HW"
    }
    val decoderLabel = when (playerState.decoderMode) {
        VideoDecoderMode.AUTO -> "Decoder: Auto (${if (playerState.preferSoftwareDecoder) "SW Fallback" else "HW Priority"})"
        VideoDecoderMode.SOFTWARE -> "Decoder: Software (FFmpeg)"
        VideoDecoderMode.HARDWARE -> "Decoder: Hardware (MediaCodec)"
    }
    val decoderIcon = when (playerState.decoderMode) {
        VideoDecoderMode.AUTO -> Icons.Rounded.AutoMode
        VideoDecoderMode.SOFTWARE -> Icons.Rounded.Memory
        VideoDecoderMode.HARDWARE -> Icons.Rounded.DeveloperBoard
    }

    val allActions = buildList {
        add(
            VideoTopBarAction(
                id = "speed",
                label = "Playback Speed",
                badgeText = speedDisplay,
                icon = Icons.Default.Speed,
                tint = if (playerState.playbackSpeed != 1.0f) colorScheme.primary else Color.White,
                onClick = {
                    speedMenuExpanded = true
                    onSpeedMenuExpandedChanged(true)
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "subtitles",
                label = "Subtitles & Captions",
                badgeText = if (hasActiveSub) "ON" else if (playerState.availableSubtitles.isNotEmpty()) "${playerState.availableSubtitles.size}" else null,
                icon = if (playerState.subtitlesEnabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                tint = if (hasActiveSub) colorScheme.primary else Color.White,
                onClick = {
                    onSubtitleClick()
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "decoder",
                label = decoderLabel,
                badgeText = decoderBadge,
                icon = decoderIcon,
                tint = if (playerState.preferSoftwareDecoder || playerState.decoderMode == VideoDecoderMode.SOFTWARE) colorScheme.primary else Color.White,
                onClick = {
                    decoderMenuExpanded = true
                    onSpeedMenuExpandedChanged(true)
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "rotate",
                label = "Rotate Screen",
                icon = Icons.AutoMirrored.Filled.RotateRight,
                onClick = {
                    onRotateScreen()
                    onUserInteraction()
                }
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                VideoTopBarAction(
                    id = "pip",
                    label = "Picture in Picture",
                    icon = Icons.Default.PictureInPicture,
                    onClick = {
                        onEnterPiP()
                        onUserInteraction()
                    }
                )
            )
        }
        if (playerState.playlist.size > 1) {
            add(
                VideoTopBarAction(
                    id = "playlist",
                    label = "Playlist (${playerState.playlist.size})",
                    icon = Icons.Default.VideoLibrary,
                    onClick = {
                        onPlaylistClick()
                        onUserInteraction()
                    }
                )
            )
        }
        add(
            VideoTopBarAction(
                id = "repeat",
                label = repeatDesc,
                icon = repeatIcon,
                tint = if (playerState.repeatMode != Player.REPEAT_MODE_OFF) colorScheme.primary else Color.White,
                onClick = {
                    onToggleRepeat()
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "mute",
                label = if (playerState.isMuted) "Unmute Audio" else "Mute Audio",
                icon = if (playerState.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                tint = if (playerState.isMuted) colorScheme.primary else Color.White,
                onClick = {
                    onToggleMute()
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "lock",
                label = lockDesc,
                icon = Icons.Rounded.LockOpen,
                onClick = {
                    onLock()
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "info",
                label = metadataDesc,
                icon = Icons.Rounded.Info,
                onClick = {
                    onInfoClick()
                    onUserInteraction()
                }
            )
        )
        add(
            VideoTopBarAction(
                id = "share",
                label = "Share Video",
                icon = Icons.Rounded.Share,
                onClick = {
                    onShare()
                    onUserInteraction()
                }
            )
        )
        onOpenWith?.let { openClick ->
            add(
                VideoTopBarAction(
                    id = "open_with",
                    label = "Open with",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        openClick()
                        onUserInteraction()
                    }
                )
            )
        }
        add(
            VideoTopBarAction(
                id = "delete",
                label = "Delete Video",
                icon = Icons.Default.Delete,
                onClick = {
                    onDelete()
                    onUserInteraction()
                }
            )
        )
    }

    BoxWithConstraints(
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
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        // Reserve 48.dp for Back button + almeno 120.dp for Title + 16.dp spacing = 184.dp
        val availableActionsWidth = (maxWidth - 184.dp).coerceAtLeast(96.dp)
        val totalSlots = (availableActionsWidth / 46.dp).toInt().coerceAtLeast(2)

        val inlineActions: List<VideoTopBarAction>
        val overflowActions: List<VideoTopBarAction>

        if (allActions.size <= totalSlots) {
            inlineActions = allActions
            overflowActions = emptyList()
        } else {
            val inlineCount = (totalSlots - 1).coerceAtLeast(1)
            inlineActions = allActions.take(inlineCount)
            overflowActions = allActions.drop(inlineCount)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playerState.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playerState.activeDecoderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Render fitting inline actions
            inlineActions.forEach { action ->
                if (action.id == "speed") {
                    Box {
                        Surface(
                            onClick = action.onClick,
                            shape = CircleShape,
                            color = Color.Transparent
                        ) {
                            Text(
                                text = speedDisplay,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = action.tint,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                        SpeedSelectionDropdownMenu(
                            expanded = speedMenuExpanded,
                            currentSpeed = playerState.playbackSpeed,
                            onDismiss = {
                                speedMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                            },
                            onSpeedSelected = { speed ->
                                speedMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                                onSpeedChange(speed)
                            }
                        )
                    }
                } else if (action.id == "decoder") {
                    Box {
                        Surface(
                            onClick = action.onClick,
                            shape = RoundedCornerShape(8.dp),
                            color = if (playerState.preferSoftwareDecoder) {
                                colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                Color.White.copy(alpha = 0.14f)
                            },
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = action.badgeText ?: "AUTO",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = action.tint,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                        DecoderModeDropdownMenu(
                            expanded = decoderMenuExpanded,
                            currentMode = playerState.decoderMode,
                            onDismiss = {
                                decoderMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                            },
                            onModeSelected = { selectedMode ->
                                decoderMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                                onSelectDecoderMode(selectedMode)
                            }
                        )
                    }
                } else {
                    IconButton(onClick = action.onClick) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = action.label,
                            tint = action.tint
                        )
                    }
                }
            }

            // Dynamic Hamburger / Overflow Menu for remaining actions
            if (overflowActions.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = {
                            overflowMenuExpanded = true
                            onSpeedMenuExpandedChanged(true)
                            onUserInteraction()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "More controls",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = {
                            overflowMenuExpanded = false
                            onSpeedMenuExpandedChanged(false)
                        }
                    ) {
                        overflowActions.forEach { action ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = if (action.tint == Color.White) colorScheme.onSurface else action.tint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = action.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                trailingIcon = action.badgeText?.let { badge ->
                                    {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = badge,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    if (action.id != "speed" && action.id != "decoder") {
                                        onSpeedMenuExpandedChanged(false)
                                    }
                                    action.onClick()
                                }
                            )
                        }
                    }

                    if (inlineActions.none { it.id == "speed" }) {
                        SpeedSelectionDropdownMenu(
                            expanded = speedMenuExpanded,
                            currentSpeed = playerState.playbackSpeed,
                            onDismiss = {
                                speedMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                            },
                            onSpeedSelected = { speed ->
                                speedMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                                onSpeedChange(speed)
                            }
                        )
                    }
                    if (inlineActions.none { it.id == "decoder" }) {
                        DecoderModeDropdownMenu(
                            expanded = decoderMenuExpanded,
                            currentMode = playerState.decoderMode,
                            onDismiss = {
                                decoderMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                            },
                            onModeSelected = { selectedMode ->
                                decoderMenuExpanded = false
                                onSpeedMenuExpandedChanged(false)
                                onSelectDecoderMode(selectedMode)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DecoderModeDropdownMenu(
    expanded: Boolean,
    currentMode: VideoDecoderMode,
    onDismiss: () -> Unit,
    onModeSelected: (VideoDecoderMode) -> Unit
) {
    val options = listOf(
        Triple(VideoDecoderMode.AUTO, "Auto (HW first, SW fallback)", Icons.Rounded.AutoMode),
        Triple(VideoDecoderMode.SOFTWARE, "Software (FFmpeg)", Icons.Rounded.Memory),
        Triple(VideoDecoderMode.HARDWARE, "Hardware (MediaCodec)", Icons.Rounded.DeveloperBoard)
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        options.forEach { (mode, label, icon) ->
            val isSelected = currentMode == mode
            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                    )
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
                onClick = { onModeSelected(mode) }
            )
        }
    }
}

@Composable
private fun SpeedSelectionDropdownMenu(
    expanded: Boolean,
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        SPEED_OPTIONS.forEach { speed ->
            val isSelected = abs(currentSpeed - speed) < 0.01f
            val speedLabel = if (speed == speed.toLong().toFloat()) {
                if (speed == 1.0f) "1\u00d7 (Normal)" else "${speed.toInt()}\u00d7"
            } else {
                "${speed}\u00d7"
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = speedLabel,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                    )
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null,
                onClick = { onSpeedSelected(speed) }
            )
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
            .windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        var isDragging by remember { mutableStateOf(false) }
        var pendingSeekPosition by remember { mutableLongStateOf(0L) }
        var hasUncommittedSeek by remember { mutableStateOf(false) }

        val displayPosition = when {
            isDragging || hasUncommittedSeek -> pendingSeekPosition
            else -> currentPosition
        }

        val progress = if (duration > 0) displayPosition.toFloat() / duration.toFloat() else 0f

        LaunchedEffect(currentPosition) {
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

            HorizontalDivider(
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
                        HorizontalDivider(
                            color = colorScheme.onSurface.copy(alpha = 0.05f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoSubtitleSheet(
    state: VideoPlayerState,
    onToggleSubtitles: (Boolean) -> Unit,
    onSelectSubtitle: (SubtitleItem?) -> Unit,
    onBrowseExternalSubtitle: () -> Unit,
    onChangeTextSize: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val textSizes = listOf(
        "Small" to 16f,
        "Normal" to 20f,
        "Large" to 24f,
        "Extra Large" to 30f
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Subtitles & Captions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.availableSubtitles.isEmpty())
                                "No subtitle files found in current folder"
                            else
                                "${state.availableSubtitles.size} subtitle file(s) in current folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.subtitlesEnabled,
                        onCheckedChange = onToggleSubtitles
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
            }

            HorizontalDivider(
                color = colorScheme.onSurface.copy(alpha = 0.1f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Subtitle Text Size",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    textSizes.forEach { (label, sizeSp) ->
                        FilterChip(
                            selected = state.subtitleTextSizeSp == sizeSp,
                            onClick = { onChangeTextSize(sizeSp) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onBrowseExternalSubtitle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Subtitle File from Storage (.srt, .vtt, .ass)")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                item {
                    val isOff = !state.subtitlesEnabled || state.selectedSubtitleUri == null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSubtitle(null) }
                            .background(
                                if (isOff) colorScheme.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SubtitlesOff,
                            contentDescription = null,
                            tint = if (isOff) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Off (Disable Subtitles)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isOff) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isOff) colorScheme.primary else colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isOff) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                itemsIndexed(state.availableSubtitles) { _, subItem ->
                    val isSelected = state.subtitlesEnabled && state.selectedSubtitleUri == subItem.uri
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSubtitle(subItem) }
                            .background(
                                if (isSelected) colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Subtitles,
                            contentDescription = null,
                            tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = subItem.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (subItem.isAutoMatched) {
                                Text(
                                    text = "Auto-matched with video filename",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.primary
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
