@file:kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.techflyers.compose.file.explorer.screen.viewer.audio.ui

import android.graphics.Bitmap
import android.net.Uri
import com.techflyers.compose.file.explorer.common.copyToClipboard
import com.techflyers.compose.file.explorer.common.showMsg
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Share
import com.techflyers.compose.file.explorer.screen.main.tab.files.ui.dialog.ExtendedMetadataSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import android.content.Intent
import androidx.core.content.FileProvider
import com.techflyers.compose.file.explorer.screen.viewer.ViewerActivity
import com.techflyers.compose.file.explorer.screen.viewer.audio.AudioPlayerActivity
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.toFormattedTime
import com.techflyers.compose.file.explorer.common.ui.Space
import com.techflyers.compose.file.explorer.screen.viewer.audio.AudioPlayerInstance
import com.techflyers.compose.file.explorer.screen.viewer.audio.model.AudioMetadata
import com.techflyers.compose.file.explorer.screen.viewer.audio.model.AudioPlayerColorScheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.abs

@Composable
fun MusicPlayerScreen(
    audioPlayerInstance: AudioPlayerInstance,
    onClosed: () -> Unit,
    onDelete: ((Uri) -> Boolean)? = null
) {
    val context = LocalContext.current
    val playerState by audioPlayerInstance.playerState.collectAsState()
    val metadata by audioPlayerInstance.metadata.collectAsState()
    val isEqualizerVisible by audioPlayerInstance.isEqualizerVisible.collectAsState()
    val isVolumeVisible by audioPlayerInstance.isVolumeVisible.collectAsState()
    val isSleepTimerVisible by audioPlayerInstance.isSleepTimerVisible.collectAsState()
    val customColorScheme by audioPlayerInstance.audioPlayerColorScheme.collectAsState()
    val defaultScheme = AudioPlayerColorScheme(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        background = MaterialTheme.colorScheme.background,
        surface = MaterialTheme.colorScheme.surface
    )

    var showPlaylist by remember { mutableStateOf(false) }
    var showMetadataSheet by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentTrackUri = audioPlayerInstance.playlist.getOrNull(playerState.currentTrackIndex)
        ?: audioPlayerInstance.uri

    // Initialize player
    LaunchedEffect(audioPlayerInstance.uri) {
        audioPlayerInstance.setDefaultColorScheme(defaultScheme)
        audioPlayerInstance.initializePlayer(context, audioPlayerInstance.uri)
    }

    // Dispose player when leaving
    DisposableEffect(Unit) {
        onDispose { audioPlayerInstance.onClose() }
    }

    // Playlist bottom sheet
    if (showPlaylist) {
        ModalBottomSheet(
            onDismissRequest = { showPlaylist = false },
            sheetState = sheetState,
            containerColor = customColorScheme.surface
        ) {
            PlaylistSheet(
                playlist = audioPlayerInstance.playlist,
                currentIndex = playerState.currentTrackIndex,
                colorScheme = customColorScheme,
                onItemClick = { index ->
                    // Seek to the selected track
                    audioPlayerInstance.seekToTrack(index)
                    showPlaylist = false
                },
                onDismiss = { showPlaylist = false }
            )
        }
    }

    if (showMetadataSheet) {
        ExtendedMetadataSheet(
            uri = currentTrackUri,
            displayNameHint = metadata.title,
            onDismiss = { showMetadataSheet = false }
        )
    }

    if (showTrackInfoDialog) {
        AudioTrackInfoDialog(
            metadata = metadata,
            uri = currentTrackUri,
            colorScheme = customColorScheme,
            onDismissRequest = { showTrackInfoDialog = false },
            onOpenMetadata = {
                showTrackInfoDialog = false
                showMetadataSheet = true
            }
        )
    }

    if (showDeleteConfirmationDialog) {
        val currentTrackName = metadata.title.ifBlank {
            currentTrackUri.lastPathSegment ?: stringResource(R.string.file)
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(stringResource(R.string.delete_confirmation)) },
            text = {
                Text("Are you sure you want to delete \"$currentTrackName\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmationDialog = false
                        val deletedIndex = playerState.currentTrackIndex
                        val trackUriToDelete = audioPlayerInstance.playlist.getOrNull(deletedIndex)
                            ?: currentTrackUri
                        val isDeleted = onDelete?.invoke(trackUriToDelete) ?: run {
                            val path = AudioPlayerActivity.resolveFilePath(context, trackUriToDelete)
                            val ok = if (path != null && File(path).exists()) {
                                File(path).delete()
                            } else {
                                runCatching { context.contentResolver.delete(trackUriToDelete, null, null) }.getOrDefault(0) > 0
                            }
                            if (ok) (context as? ViewerActivity)?.onFileDeleted(path)
                            ok
                        }
                        if (isDeleted) {
                            val remaining = audioPlayerInstance.removePlaylistItem(deletedIndex)
                            if (remaining.isEmpty()) {
                                onClosed()
                            } else {
                                val nextIndex = deletedIndex.coerceAtMost(remaining.lastIndex)
                                audioPlayerInstance.seekToTrack(nextIndex)
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = customColorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            customColorScheme.background,
                            customColorScheme.surface,
                            customColorScheme.background.copy(alpha = 0.8f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Status bar spacing
                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))

                // Top controls
                TopControls(
                    onEqualizerClick = { audioPlayerInstance.toggleEqualizer() },
                    onVolumeClick = { audioPlayerInstance.toggleVolume() },
                    onCloseClick = onClosed,
                    onPlaylistClick = { showPlaylist = true },
                    hasPlaylist = audioPlayerInstance.playlist.size > 1,
                    audioPlayerColorScheme = customColorScheme,
                    isShuffleEnabled = playerState.isShuffleEnabled,
                    onShuffleClick = { audioPlayerInstance.toggleShuffle() },
                    sleepTimerRemainingMs = playerState.sleepTimerRemainingMs,
                    onSleepTimerClick = { audioPlayerInstance.toggleSleepTimerPanel() },
                    onShareClick = {
                        val shareUri = if (currentTrackUri.scheme == "file") {
                            val file = File(currentTrackUri.path ?: "")
                            if (file.exists()) {
                                try {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                } catch (e: Exception) {
                                    currentTrackUri
                                }
                            } else currentTrackUri
                        } else {
                            currentTrackUri
                        }
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(shareIntent, context.getString(R.string.share))
                        )
                    },
                    onDeleteClick = {
                        showDeleteConfirmationDialog = true
                    },
                    onMetadataClick = { showMetadataSheet = true },
                    onInfoClick = { showTrackInfoDialog = true },
                    onOpenWithClick = {
                        val openUri = if (currentTrackUri.scheme == "file") {
                            val file = File(currentTrackUri.path ?: "")
                            if (file.exists()) {
                                try {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.provider",
                                        file
                                    )
                                } catch (e: Exception) {
                                    currentTrackUri
                                }
                            } else currentTrackUri
                        } else {
                            currentTrackUri
                        }
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(openUri, "audio/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                openIntent,
                                context.getString(com.techflyers.compose.file.explorer.R.string.open_with)
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Album art with rotation animation
                AlbumArt(
                    isPlaying = playerState.isPlaying,
                    metadata = metadata,
                    audioPlayerColorScheme = customColorScheme
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Track counter (when playlist has multiple tracks)
                if (playerState.totalTracks > 1) {
                    Text(
                        text = "${playerState.currentTrackIndex + 1} / ${playerState.totalTracks}",
                        color = customColorScheme.tintColor.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Song info
                SongInfo(metadata = metadata, colorScheme = customColorScheme)

                playerState.playbackErrorTitle?.let { errTitle ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = errTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            playerState.playbackErrorDetails?.let { details ->
                                Text(
                                    text = details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            playerState.codecDiagnosticLog?.takeIf { it.isNotBlank() }?.let { diag ->
                                Text(
                                    text = diag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Progress bar
                ProgressBar(
                    currentPosition = playerState.currentPosition,
                    duration = playerState.duration,
                    onSeek = { audioPlayerInstance.seekTo(it) },
                    colorScheme = customColorScheme
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Main controls
                MainControls(
                    isPlaying = playerState.isPlaying,
                    isLoading = playerState.isLoading,
                    hasPrevious = playerState.currentTrackIndex > 0 || playerState.totalTracks > 1,
                    hasNext = playerState.currentTrackIndex < playerState.totalTracks - 1 || playerState.totalTracks > 1,
                    onPlayPause = { audioPlayerInstance.playPause() },
                    onSkipNext = { audioPlayerInstance.skipNext() },
                    onSkipPrevious = { audioPlayerInstance.skipPrevious() },
                    colorScheme = customColorScheme
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Additional controls
                AdditionalControls(
                    playbackSpeed = playerState.playbackSpeed,
                    repeatMode = playerState.repeatMode,
                    onSpeedChange = { audioPlayerInstance.setPlaybackSpeed(it) },
                    onRepeatToggle = { audioPlayerInstance.toggleRepeatMode() },
                    colorScheme = customColorScheme
                )
            }

            // Volume overlay
            AnimatedVisibility(
                visible = isVolumeVisible,
                enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.6f),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                VolumeView(
                    volume = playerState.volume,
                    onVolumeChange = { audioPlayerInstance.setVolume(it) },
                    onDismiss = { audioPlayerInstance.toggleVolume() },
                    colorScheme = customColorScheme
                )
            }

            // Equalizer overlay
            AnimatedVisibility(
                visible = isEqualizerVisible,
                enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.6f),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                EqualizerView(
                    onDismiss = { audioPlayerInstance.toggleEqualizer() },
                    onOpenSystemEqualizer = { audioPlayerInstance.openSystemEqualizer(context) },
                    colorScheme = customColorScheme
                )
            }

            // Sleep timer overlay
            AnimatedVisibility(
                visible = isSleepTimerVisible,
                enter = slideInVertically { -it } + fadeIn() + scaleIn(initialScale = 0.6f),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                SleepTimerView(
                    remainingMs = playerState.sleepTimerRemainingMs,
                    onSetTimer = { durationMs ->
                        audioPlayerInstance.startSleepTimer(durationMs)
                        audioPlayerInstance.toggleSleepTimerPanel()
                    },
                    onCancel = {
                        audioPlayerInstance.cancelSleepTimer()
                        audioPlayerInstance.toggleSleepTimerPanel()
                    },
                    onDismiss = { audioPlayerInstance.toggleSleepTimerPanel() },
                    colorScheme = customColorScheme
                )
            }
        }
    }
}

private data class AudioTopBarAction(
    val id: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeText: String? = null,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@Composable
fun TopControls(
    onEqualizerClick: () -> Unit,
    onVolumeClick: () -> Unit,
    onCloseClick: () -> Unit,
    onPlaylistClick: () -> Unit = {},
    hasPlaylist: Boolean = false,
    audioPlayerColorScheme: AudioPlayerColorScheme,
    isShuffleEnabled: Boolean = false,
    onShuffleClick: () -> Unit = {},
    sleepTimerRemainingMs: Long = 0L,
    onSleepTimerClick: () -> Unit = {},
    onShareClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onMetadataClick: (() -> Unit)? = null,
    onInfoClick: (() -> Unit)? = null,
    onOpenWithClick: (() -> Unit)? = null,
) {
    var overflowMenuExpanded by remember { mutableStateOf(false) }

    val isTimerActive = sleepTimerRemainingMs > 0
    val timerBadge = if (isTimerActive) {
        val minutes = (sleepTimerRemainingMs / 60000).toInt()
        val seconds = ((sleepTimerRemainingMs % 60000) / 1000).toInt()
        if (minutes > 0) "${minutes}m" else "${seconds}s"
    } else null

    val playbackActions = buildList {
        add(
            AudioTopBarAction(
                id = "shuffle",
                label = if (isShuffleEnabled) "Shuffle: On" else "Shuffle: Off",
                icon = Icons.Default.Shuffle,
                tint = if (isShuffleEnabled) audioPlayerColorScheme.primary else audioPlayerColorScheme.tintColor.copy(alpha = 0.6f),
                onClick = onShuffleClick
            )
        )
        add(
            AudioTopBarAction(
                id = "sleep_timer",
                label = if (isTimerActive) "Sleep Timer ($timerBadge)" else "Sleep Timer",
                badgeText = timerBadge,
                icon = Icons.Default.Timer,
                tint = if (isTimerActive) audioPlayerColorScheme.primary else audioPlayerColorScheme.tintColor.copy(alpha = 0.6f),
                onClick = onSleepTimerClick
            )
        )
        if (hasPlaylist) {
            add(
                AudioTopBarAction(
                    id = "playlist",
                    label = "Playlist",
                    icon = Icons.Default.PlaylistPlay,
                    tint = audioPlayerColorScheme.tintColor,
                    onClick = onPlaylistClick
                )
            )
        }
        add(
            AudioTopBarAction(
                id = "volume",
                label = "Volume Control",
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                tint = audioPlayerColorScheme.tintColor,
                onClick = onVolumeClick
            )
        )
        add(
            AudioTopBarAction(
                id = "equalizer",
                label = "Equalizer",
                icon = Icons.Default.Equalizer,
                tint = audioPlayerColorScheme.tintColor,
                onClick = onEqualizerClick
            )
        )
    }

    val menuActions = buildList {
        onShareClick?.let { onClick ->
            add(
                AudioTopBarAction(
                    id = "share",
                    label = stringResource(R.string.share),
                    icon = Icons.Rounded.Share,
                    tint = audioPlayerColorScheme.tintColor,
                    onClick = onClick
                )
            )
        }
        onDeleteClick?.let { onClick ->
            add(
                AudioTopBarAction(
                    id = "delete",
                    label = stringResource(R.string.delete),
                    icon = Icons.Rounded.Delete,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onClick
                )
            )
        }
        onMetadataClick?.let { onClick ->
            add(
                AudioTopBarAction(
                    id = "metadata",
                    label = stringResource(R.string.extended_metadata),
                    icon = Icons.Rounded.DataObject,
                    tint = audioPlayerColorScheme.tintColor,
                    onClick = onClick
                )
            )
        }
        onInfoClick?.let { onClick ->
            add(
                AudioTopBarAction(
                    id = "info",
                    label = stringResource(R.string.track_info),
                    icon = Icons.Rounded.Info,
                    tint = audioPlayerColorScheme.tintColor,
                    onClick = onClick
                )
            )
        }
        onOpenWithClick?.let { onClick ->
            add(
                AudioTopBarAction(
                    id = "open_with",
                    label = stringResource(R.string.open_with),
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    tint = audioPlayerColorScheme.tintColor,
                    onClick = onClick
                )
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val reservedWidth = 112.dp
        val availableInlineWidth = (maxWidth - reservedWidth).coerceAtLeast(0.dp)
        val maxInlineSlots = (availableInlineWidth / 44.dp).toInt().coerceAtLeast(0)

        val inlineActions = playbackActions.take(maxInlineSlots)
        val overflowPlaybackActions = playbackActions.drop(maxInlineSlots)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close player",
                    tint = audioPlayerColorScheme.tintColor
                )
            }

            Spacer(Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                inlineActions.forEach { action ->
                    if (action.id == "sleep_timer" && isTimerActive) {
                        IconButton(onClick = action.onClick) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = audioPlayerColorScheme.primary) {
                                        Text(
                                            text = timerBadge ?: "",
                                            fontSize = 9.sp,
                                            color = audioPlayerColorScheme.tintColor
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    action.icon,
                                    contentDescription = action.label,
                                    tint = action.tint ?: audioPlayerColorScheme.tintColor
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = action.onClick) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = action.label,
                                tint = action.tint ?: audioPlayerColorScheme.tintColor
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { overflowMenuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Menu,
                            contentDescription = "More actions",
                            tint = audioPlayerColorScheme.tintColor
                        )
                    }

                    DropdownMenu(
                        expanded = overflowMenuExpanded,
                        onDismissRequest = { overflowMenuExpanded = false }
                    ) {
                        if (overflowPlaybackActions.isNotEmpty()) {
                            overflowPlaybackActions.forEach { action ->
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = null,
                                            tint = action.tint ?: MaterialTheme.colorScheme.onSurface,
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
                                                color = audioPlayerColorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = badge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = audioPlayerColorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        action.onClick()
                                    }
                                )
                            }
                            HorizontalDivider()
                        }

                        menuActions.forEach { action ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = if (action.id == "delete") MaterialTheme.colorScheme.error else (action.tint ?: MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = action.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (action.id == "delete") MaterialTheme.colorScheme.error else Color.Unspecified
                                    )
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    action.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioTrackInfoDialog(
    metadata: AudioMetadata,
    uri: Uri,
    colorScheme: AudioPlayerColorScheme,
    onDismissRequest: () -> Unit,
    onOpenMetadata: () -> Unit
) {
    val context = LocalContext.current
    val filePath = remember(uri) { uri.path ?: uri.toString() }

    val durationText = remember(metadata.duration) {
        if (metadata.duration > 0) {
            val totalSeconds = metadata.duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format("%02d:%02d", minutes, seconds)
        } else "Unknown"
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.track_info),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title
                Column {
                    Text(
                        text = "Title",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = metadata.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Artist & Album
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Artist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = metadata.artist,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Album",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = metadata.album,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Duration
                Column {
                    Text(
                        text = "Duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // File Path / Location with Copy Button
                Column {
                    Text(
                        text = stringResource(R.string.file_location),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = filePath,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                filePath.copyToClipboard()
                                showMsg("Path copied to clipboard")
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy path",
                                modifier = Modifier.size(16.dp),
                                tint = colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenMetadata,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
            ) {
                Text(text = stringResource(R.string.extended_metadata))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismissRequest) {
                Text(text = "Close")
            }
        }
    )
}

@Composable
fun AlbumArt(
    isPlaying: Boolean,
    metadata: AudioMetadata,
    audioPlayerColorScheme: AudioPlayerColorScheme
) {
    RotatingContainer(isRotating = isPlaying) {
        Box(
            modifier = Modifier
                .size(280.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            audioPlayerColorScheme.primary.copy(alpha = 0.6f),
                            audioPlayerColorScheme.primary.copy(alpha = 0.2f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (metadata.albumArt != null) {
                Image(
                    bitmap = metadata.albumArt.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(260.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun RotatingContainer(
    modifier: Modifier = Modifier,
    isRotating: Boolean,
    rotationDuration: Int = 10000,
    content: @Composable () -> Unit
) {
    var currentRotation by remember { mutableFloatStateOf(0f) }
    val animatedRotation = remember { Animatable(0f) }

    LaunchedEffect(isRotating) {
        if (isRotating) {
            animatedRotation.snapTo(currentRotation)

            while (isRotating) {
                animatedRotation.animateTo(
                    targetValue = currentRotation + 360f,
                    animationSpec = tween(
                        durationMillis = rotationDuration,
                        easing = LinearEasing
                    )
                )
                currentRotation = animatedRotation.value % 360f
                animatedRotation.snapTo(currentRotation)
            }
        } else {
            currentRotation = animatedRotation.value % 360f
            animatedRotation.stop()
        }
    }

    LaunchedEffect(isRotating, animatedRotation.value) {
        if (isRotating) {
            currentRotation = animatedRotation.value % 360f
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = if (isRotating) animatedRotation.value else currentRotation
            }
    ) {
        content()
    }
}

@Composable
fun SongInfo(metadata: AudioMetadata, colorScheme: AudioPlayerColorScheme) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = metadata.title,
            color = colorScheme.tintColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = metadata.artist,
            color = colorScheme.tintColor.copy(alpha = 0.8f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = metadata.album,
            color = colorScheme.tintColor.copy(alpha = 0.6f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    colorScheme: AudioPlayerColorScheme
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val safeDuration = duration.coerceAtLeast(0L)
    val currentProgress = if (safeDuration > 0) (currentPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) else 0f
    val displayProgress = if (isDragging) dragProgress else currentProgress
    val displayPosition = if (isDragging) (dragProgress * safeDuration).toLong() else currentPosition.coerceIn(0L, safeDuration)

    Column {
        Slider(
            value = displayProgress,
            onValueChange = {
                isDragging = true
                dragProgress = it
            },
            onValueChangeFinished = {
                val newPosition = (dragProgress * safeDuration).toLong().coerceIn(0L, safeDuration)
                onSeek(newPosition)
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = colorScheme.tintColor,
                activeTrackColor = colorScheme.primary,
                inactiveTrackColor = colorScheme.tintColor.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayPosition.coerceAtLeast(0L).toFormattedTime(),
                color = colorScheme.tintColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = safeDuration.toFormattedTime(),
                color = colorScheme.tintColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun MainControls(
    isPlaying: Boolean,
    isLoading: Boolean,
    hasPrevious: Boolean = true,
    hasNext: Boolean = true,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    colorScheme: AudioPlayerColorScheme
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button
        IconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = null,
                tint = if (hasPrevious) colorScheme.tintColor else colorScheme.tintColor.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        }

        // Play/Pause button
        Card(
            modifier = Modifier
                .size(72.dp)
                .clickable { onPlayPause() },
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.primary
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = colorScheme.tintColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Next button
        IconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = null,
                tint = if (hasNext) colorScheme.tintColor else colorScheme.tintColor.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun AdditionalControls(
    playbackSpeed: Float,
    repeatMode: Int,
    onSpeedChange: (Float) -> Unit,
    onRepeatToggle: () -> Unit,
    colorScheme: AudioPlayerColorScheme
) {
    val speedOptions = remember { listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Speed control dropdown menu
        Box {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { speedMenuExpanded = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = { speedMenuExpanded = true }
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = "Playback speed",
                        tint = colorScheme.tintColor
                    )
                }
                Text(
                    text = if (playbackSpeed == playbackSpeed.toLong().toFloat())
                        "${playbackSpeed.toInt()}x"
                    else
                        "${playbackSpeed}x",
                    color = colorScheme.tintColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            androidx.compose.material3.DropdownMenu(
                expanded = speedMenuExpanded,
                onDismissRequest = { speedMenuExpanded = false }
            ) {
                speedOptions.forEach { speed ->
                    val isSelected = abs(playbackSpeed - speed) < 0.01f
                    val speedLabel = if (speed == speed.toLong().toFloat()) {
                        if (speed == 1.0f) "1x (Normal)" else "${speed.toInt()}x"
                    } else {
                        "${speed}x"
                    }
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(
                                text = speedLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            speedMenuExpanded = false
                            onSpeedChange(speed)
                        }
                    )
                }
            }
        }

        // Repeat control (off, all, one)
        IconButton(onClick = onRepeatToggle) {
            val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
            Icon(
                icon,
                contentDescription = null,
                tint = if (repeatMode == Player.REPEAT_MODE_OFF)
                    colorScheme.tintColor.copy(alpha = 0.5f)
                else
                    colorScheme.tintColor,
            )
        }
    }
}

@Composable
fun VolumeView(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    colorScheme: AudioPlayerColorScheme
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.volume),
                    color = colorScheme.tintColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = colorScheme.tintColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.VolumeDown,
                    contentDescription = null,
                    tint = colorScheme.tintColor.copy(alpha = 0.7f)
                )

                Space(8.dp)

                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = colorScheme.tintColor,
                        activeTrackColor = colorScheme.primary,
                        inactiveTrackColor = colorScheme.tintColor.copy(alpha = 0.3f)
                    )
                )

                Space(8.dp)

                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = colorScheme.tintColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EqualizerView(
    onDismiss: () -> Unit,
    onOpenSystemEqualizer: () -> Unit,
    colorScheme: AudioPlayerColorScheme
) {
    val frequencies = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
    val gains = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer),
                    color = colorScheme.tintColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenSystemEqualizer) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = stringResource(R.string.open_device_equalizer),
                            tint = colorScheme.tintColor
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = colorScheme.tintColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                frequencies.forEachIndexed { index, frequency ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+15",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall
                        )

                        Space(8.dp)

                        Slider(
                            value = gains[index],
                            onValueChange = { gains[index] = it },
                            valueRange = -15f..15f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = colorScheme.tintColor,
                                activeTrackColor = colorScheme.primary,
                                inactiveTrackColor = colorScheme.tintColor.copy(alpha = 0.3f)
                            )
                        )

                        Space(8.dp)

                        Text(
                            text = "-15",
                            color = colorScheme.tintColor.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            style = MaterialTheme.typography.labelSmall
                        )

                        Space(8.dp)

                        Text(
                            text = frequency,
                            color = colorScheme.tintColor,
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOpenSystemEqualizer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorScheme.tintColor
                ),
                border = BorderStroke(1.dp, colorScheme.tintColor.copy(alpha = 0.35f))
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.tintColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.open_device_equalizer),
                    color = colorScheme.tintColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colorScheme.tintColor.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        for (i in gains.indices) {
                            gains[i] = 0f
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.reset),
                        color = colorScheme.tintColor
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.done),
                        color = colorScheme.tintColor
                    )
                }
            }
        }
    }
}

@Composable
fun SleepTimerView(
    remainingMs: Long,
    onSetTimer: (Long) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    colorScheme: AudioPlayerColorScheme
) {
    val isActive = remainingMs > 0
    val presets = listOf(
        5L to "5 min",
        10L to "10 min",
        15L to "15 min",
        30L to "30 min",
        60L to "1 hour"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sleep Timer",
                    color = colorScheme.tintColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = colorScheme.tintColor
                    )
                }
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(12.dp))

                val minutes = (remainingMs / 60000).toInt()
                val seconds = ((remainingMs % 60000) / 1000).toInt()
                Text(
                    text = "Playback will pause in ${if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"}",
                    color = colorScheme.tintColor.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        Icons.Default.TimerOff,
                        contentDescription = null,
                        tint = colorScheme.tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Space(8.dp)
                    Text(
                        text = "Cancel Timer",
                        color = colorScheme.tintColor
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Stop playing after",
                    color = colorScheme.tintColor.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (minutes, label) ->
                        Button(
                            onClick = { onSetTimer(minutes * 60 * 1000) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = label,
                                color = colorScheme.tintColor,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extract colors from bitmap
fun extractColorsFromBitmap(
    bitmap: Bitmap,
    defaultScheme: AudioPlayerColorScheme
): AudioPlayerColorScheme {
    return try {
        val palette = Palette.from(bitmap).generate()
        val primaryColor = palette.getDominantColor(0xFF6750A4.toInt())
        val vibrantColor = palette.getVibrantColor(primaryColor)
        val mutedColor = palette.getMutedColor(0xFF625B71.toInt())

        // Create darker variants for background
        val primaryHsl = FloatArray(3)
        ColorUtils.colorToHSL(primaryColor, primaryHsl)
        primaryHsl[2] = 0.1f // Very dark
        val backgroundColor = ColorUtils.HSLToColor(primaryHsl)

        primaryHsl[2] = 0.2f // Slightly lighter
        val surfaceColor = ColorUtils.HSLToColor(primaryHsl)

        AudioPlayerColorScheme(
            primary = Color(vibrantColor),
            secondary = Color(mutedColor),
            background = Color(backgroundColor),
            surface = Color(surfaceColor)
        )
    } catch (_: Exception) {
        defaultScheme // Fallback to default colors
    }
}

/**
 * A bottom-sheet playlist panel that lists all songs in the queue.
 * The currently playing track is highlighted and scrolled into view.
 */
@Composable
fun PlaylistSheet(
    playlist: List<android.net.Uri>,
    currentIndex: Int,
    colorScheme: AudioPlayerColorScheme,
    onItemClick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    // Scroll to the current track when sheet opens
    LaunchedEffect(currentIndex) {
        if (playlist.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceIn(0, playlist.lastIndex))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlaylistPlay,
                    contentDescription = null,
                    tint = colorScheme.tintColor,
                    modifier = Modifier.size(24.dp)
                )
                Space(8.dp)
                Text(
                    text = "Queue (${playlist.size})",
                    color = colorScheme.tintColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = colorScheme.tintColor
                )
            }
        }

        HorizontalDivider(color = colorScheme.tintColor.copy(alpha = 0.1f))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            itemsIndexed(playlist) { index, trackUri ->
                val isCurrentTrack = index == currentIndex
                val rawName = if (trackUri.scheme == "file") {
                    trackUri.path?.let { java.io.File(it).name }
                } else {
                    trackUri.lastPathSegment?.substringAfterLast('/')
                }
                val decoded = rawName?.let { android.net.Uri.decode(it) } ?: "Track ${index + 1}"
                val trackName = decoded.substringBeforeLast('.').ifBlank { decoded }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onItemClick(index) }
                        .background(
                            if (isCurrentTrack) colorScheme.primary.copy(alpha = 0.15f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    // Track number or playing indicator
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCurrentTrack) {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                color = colorScheme.tintColor.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Space(12.dp)

                    Text(
                        text = trackName,
                        color = if (isCurrentTrack) colorScheme.primary else colorScheme.tintColor,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrentTrack) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (index < playlist.lastIndex) {
                    HorizontalDivider(
                        color = colorScheme.tintColor.copy(alpha = 0.05f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}