package com.techflyers.compose.file.explorer.screen.viewer.video

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.name
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.media.MediaNotificationHelper
import com.techflyers.compose.file.explorer.screen.viewer.video.model.SubtitleItem
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
    private var mediaNotificationHelper: MediaNotificationHelper? = null

    // Per-video URI custom or overridden subtitle selection
    private val customSubtitlesByVideoUri = mutableMapOf<Uri, MutableList<SubtitleItem>>()
    private val selectedSubtitleByVideoUri = mutableMapOf<Uri, SubtitleItem?>()

    companion object {
        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp", "xml")

        fun resolveSubtitleMimeType(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
        }
    }

    private fun discoverSubtitlesForVideo(context: Context, videoUri: Uri): Pair<List<SubtitleItem>, SubtitleItem?> {
        val resolvedPath = VideoPlayerActivity.resolveFilePath(context, videoUri)
            ?: videoUri.path
        val videoFile = resolvedPath?.let { File(it) }?.takeIf { it.exists() }
        val parentDir = videoFile?.parentFile

        val discovered = mutableListOf<SubtitleItem>()
        var autoMatched: SubtitleItem? = null

        if (videoFile != null && parentDir != null && parentDir.exists() && parentDir.isDirectory) {
            val videoBase = videoFile.nameWithoutExtension.lowercase()
            val subFiles = parentDir.listFiles { f ->
                f.isFile && f.extension.lowercase() in SUBTITLE_EXTENSIONS
            }?.sortedBy { it.name.lowercase() } ?: emptyList()

            for (subFile in subFiles) {
                val subBase = subFile.nameWithoutExtension.lowercase()
                val isMatch = subBase == videoBase || subBase.startsWith("$videoBase.")
                val item = SubtitleItem(
                    uri = Uri.fromFile(subFile),
                    name = subFile.name,
                    mimeType = resolveSubtitleMimeType(subFile.name),
                    language = extractLanguageTag(videoBase, subBase),
                    isAutoMatched = isMatch
                )
                discovered.add(item)
                if (isMatch && autoMatched == null) {
                    autoMatched = item
                }
            }
        }

        // Merge any user-picked external subtitles for this video
        customSubtitlesByVideoUri[videoUri]?.forEach { custom ->
            if (discovered.none { it.uri == custom.uri }) {
                discovered.add(custom)
            }
        }

        val effectiveSelected = if (selectedSubtitleByVideoUri.containsKey(videoUri)) {
            selectedSubtitleByVideoUri[videoUri]
        } else {
            autoMatched
        }

        return discovered to effectiveSelected
    }

    private fun extractLanguageTag(videoBase: String, subBase: String): String {
        if (subBase.startsWith("$videoBase.")) {
            val suffix = subBase.removePrefix("$videoBase.").trim()
            if (suffix.length in 2..8) return suffix
        }
        return "en"
    }

    private fun buildMediaItemWithSubtitles(
        context: Context,
        itemUri: Uri,
        overrideSelectedSub: SubtitleItem? = null,
        hasExplicitOverride: Boolean = false
    ): MediaItem {
        val (_, autoMatched) = discoverSubtitlesForVideo(context, itemUri)
        val activeSub = if (hasExplicitOverride) overrideSelectedSub else autoMatched

        val builder = MediaItem.Builder().setUri(itemUri)
        if (activeSub != null) {
            val subConfig = MediaItem.SubtitleConfiguration.Builder(activeSub.uri)
                .setMimeType(activeSub.mimeType)
                .setLanguage(activeSub.language)
                .setLabel(activeSub.name)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        return builder.build()
    }

    private fun syncSubtitleStateForUri(context: Context, currentUri: Uri) {
        val (available, selected) = discoverSubtitlesForVideo(context, currentUri)
        _playerState.update { state ->
            state.copy(
                availableSubtitles = available,
                selectedSubtitleUri = selected?.uri
            )
        }
    }

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
                    buildMediaItemWithSubtitles(context, itemUri)
                }
                setMediaItems(mediaItems)
                repeatMode = _playerState.value.repeatMode

                val startIndex = uris.indexOfFirst { it == uri }.coerceAtLeast(0)
                val initialUri = uris.getOrNull(startIndex) ?: uri
                val (initialSubs, initialSelectedSub) = discoverSubtitlesForVideo(context, initialUri)

                seekTo(startIndex, 0)
                prepare()
                playWhenReady = true

                volume = 1.0f

                val initialTitle = if (initialUri.scheme == "file") {
                    initialUri.path?.let { File(it).name } ?: initialUri.name
                } else {
                    initialUri.name
                } ?: globalClass.getString(R.string.unknown)

                _playerState.update { currentState ->
                    currentState.copy(
                        title = initialTitle,
                        currentPlaylistIndex = startIndex,
                        isMuted = false,
                        availableSubtitles = initialSubs,
                        selectedSubtitleUri = initialSelectedSub?.uri,
                        subtitlesEnabled = true
                    )
                }

                if (initialSelectedSub != null && initialSelectedSub.isAutoMatched) {
                    globalClass.showMsg("Auto-loaded subtitle: ${initialSelectedSub.name}")
                }

                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.update { currentState -> currentState.copy(isPlaying = isPlaying) }
                        updateNotification()
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
                        val (subs, selectedSub) = discoverSubtitlesForVideo(context, currentUri)
                        _playerState.update { currentState ->
                            currentState.copy(
                                currentPlaylistIndex = currentIndex,
                                title = trackTitle,
                                currentPosition = 0L,
                                duration = player.duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L,
                                availableSubtitles = subs,
                                selectedSubtitleUri = selectedSub?.uri
                            )
                        }
                        updateNotification()
                        archiveSession?.let { session ->
                            ArchiveMediaQueueManager.prefetchWindow(session, currentIndex, windowSize = 2)
                        }
                    }
                })
            }
        }

        mediaNotificationHelper = MediaNotificationHelper(
            context = context,
            sessionTag = "PrismVideo",
            notificationId = 1002,
            targetActivityClass = VideoPlayerActivity::class.java,
            initialUri = uri,
            instanceId = id,
            onPlayPause = { playPause() },
            onNext = { playNext() },
            onPrevious = { playPrevious() },
            onSeekTo = { pos -> seekTo(pos) },
            onStop = {
                exoPlayer?.stop()
                _playerState.update { it.copy(isPlaying = false) }
            }
        )

        startPositionTracking()
        updateNotification()
    }

    fun selectSubtitle(context: Context, subtitle: SubtitleItem?) {
        val player = exoPlayer ?: return
        val currentIndex = player.currentMediaItemIndex
        val uris = _playerState.value.playlist.ifEmpty { listOf(uri) }
        val currentUri = uris.getOrNull(currentIndex) ?: uri

        selectedSubtitleByVideoUri[currentUri] = subtitle

        if (subtitle == null) {
            toggleSubtitles(false)
            _playerState.update { it.copy(selectedSubtitleUri = null) }
            return
        }

        // Enable text track rendering and replace current MediaItem preserving playback position
        val savedPosition = player.currentPosition
        val wasPlaying = player.playWhenReady

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguage(subtitle.language)
            .build()

        val updatedMediaItem = buildMediaItemWithSubtitles(
            context = context,
            itemUri = currentUri,
            overrideSelectedSub = subtitle,
            hasExplicitOverride = true
        )

        player.replaceMediaItem(currentIndex, updatedMediaItem)
        player.seekTo(currentIndex, savedPosition)
        player.playWhenReady = wasPlaying

        syncSubtitleStateForUri(context, currentUri)
        _playerState.update {
            it.copy(
                subtitlesEnabled = true,
                selectedSubtitleUri = subtitle.uri
            )
        }
        globalClass.showMsg("Subtitle loaded: ${subtitle.name}")
    }

    fun addAndSelectExternalSubtitle(context: Context, file: File) {
        if (!file.exists() || !file.isFile) return
        val uris = _playerState.value.playlist.ifEmpty { listOf(uri) }
        val currentIndex = exoPlayer?.currentMediaItemIndex ?: 0
        val currentUri = uris.getOrNull(currentIndex) ?: uri

        val item = SubtitleItem(
            uri = Uri.fromFile(file),
            name = file.name,
            mimeType = resolveSubtitleMimeType(file.name),
            language = "en",
            isAutoMatched = false
        )
        val list = customSubtitlesByVideoUri.getOrPut(currentUri) { mutableListOf() }
        if (list.none { it.uri == item.uri }) {
            list.add(item)
        }
        selectSubtitle(context, item)
    }

    fun toggleSubtitles(enabled: Boolean) {
        exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                .build()
        }
        _playerState.update { it.copy(subtitlesEnabled = enabled) }
    }

    fun setSubtitleTextSize(sizeSp: Float) {
        _playerState.update { it.copy(subtitleTextSizeSp = sizeSp.coerceIn(12f, 36f)) }
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

    fun seekBy(offsetMs: Long) {
        exoPlayer?.let { player ->
            val targetPosition = (player.currentPosition + offsetMs).coerceAtLeast(0L)
            val duration = player.duration
            player.seekTo(
                if (duration != TIME_UNSET && duration >= 0L) {
                    targetPosition.coerceAtMost(duration)
                } else {
                    targetPosition
                }
            )
        }
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

    private fun updateNotification() {
        val player = exoPlayer ?: return
        val state = _playerState.value
        val title = state.title.ifEmpty {
            val curUri = playlist.getOrNull(state.currentPlaylistIndex) ?: uri
            curUri.lastPathSegment ?: globalClass.getString(R.string.unknown)
        }
        val hasPrev = playlist.size > 1 && (state.currentPlaylistIndex > 0 || state.repeatMode != Player.REPEAT_MODE_OFF)
        val hasNext = playlist.size > 1 && (state.currentPlaylistIndex < playlist.lastIndex || state.repeatMode != Player.REPEAT_MODE_OFF)

        mediaNotificationHelper?.update(
            title = title,
            artist = globalClass.getString(R.string.video_playback),
            album = null,
            albumArt = null,
            isPlaying = state.isPlaying,
            hasPrevious = hasPrev,
            hasNext = hasNext,
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it isNot TIME_UNSET } ?: 0L
        )
    }

    override fun onClose() {
        positionTrackingJob?.cancel()
        mediaNotificationHelper?.release()
        mediaNotificationHelper = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
