package com.techflyers.compose.file.explorer.screen.viewer.video

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.isNot
import com.techflyers.compose.file.explorer.common.name
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.screen.preferences.constant.VideoDecoderMode
import com.techflyers.compose.file.explorer.screen.viewer.ViewerInstance
import com.techflyers.compose.file.explorer.screen.viewer.archive.ArchiveMediaQueueManager
import com.techflyers.compose.file.explorer.screen.viewer.archive.ArchiveMediaSession
import com.techflyers.compose.file.explorer.screen.viewer.media.MediaNotificationHelper
import com.techflyers.compose.file.explorer.screen.viewer.media.SoftwareMediaRenderersFactory
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
import java.io.File

class VideoPlayerInstance(
    override val uri: Uri,
    override val id: String,
    val playlist: List<Uri> = listOf(),
    val archiveSession: ArchiveMediaSession? = null
) : ViewerInstance {
    private val initialMode = runCatching {
        VideoDecoderMode.fromOrdinal(globalClass.preferencesManager.videoDecoderMode)
    }.getOrDefault(VideoDecoderMode.AUTO)

    private val _playerState = MutableStateFlow(
        VideoPlayerState(
            decoderMode = initialMode,
            preferSoftwareDecoder = initialMode == VideoDecoderMode.SOFTWARE,
            activeDecoderLabel = formatModeDecoderLabel(
                mode = initialMode,
                preferSoftware = initialMode == VideoDecoderMode.SOFTWARE,
                decoderName = null
            )
        )
    )
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()
    private var exoPlayer: ExoPlayer? = null
    private var appContext: Context? = null
    private var positionTrackingJob: Job? = null
    private var mediaNotificationHelper: MediaNotificationHelper? = null
    private var audioFallbackRetriedForIndex: Int = -1
    private var softwareFallbackRetriedForIndex: Int = -1
    private var hardwareFallbackRetriedForIndex: Int = -1
    private var hasSyncedInitialPreference: Boolean = false

    // Per-video URI custom or overridden subtitle selection
    private val customSubtitlesByVideoUri = mutableMapOf<Uri, MutableList<SubtitleItem>>()
    private val selectedSubtitleByVideoUri = mutableMapOf<Uri, SubtitleItem?>()

    companion object {
        val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml", "dfxp", "xml")

        fun formatModeDecoderLabel(
            mode: VideoDecoderMode,
            preferSoftware: Boolean,
            decoderName: String?
        ): String {
            val modePrefix = when (mode) {
                VideoDecoderMode.AUTO -> "Auto • "
                VideoDecoderMode.SOFTWARE -> ""
                VideoDecoderMode.HARDWARE -> ""
            }
            if (decoderName.isNullOrBlank()) {
                return if (preferSoftware) "${modePrefix}SW (FFmpeg)" else "${modePrefix}HW (MediaCodec)"
            }
            val lower = decoderName.lowercase()
            val isSw = lower.contains("ffmpeg") ||
                lower.contains("dav1d") ||
                lower.startsWith("c2.android.") ||
                lower.startsWith("omx.google.") ||
                lower.contains(".sw.")
            return if (isSw) "${modePrefix}SW ($decoderName)" else "${modePrefix}HW ($decoderName)"
        }

        fun resolveSubtitleMimeType(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "vtt" -> MimeTypes.TEXT_VTT
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
                else -> MimeTypes.APPLICATION_SUBRIP
            }
        }

        fun resolveVideoMimeTypeHint(uri: Uri): String? {
            val pathOrName = (uri.path ?: uri.lastPathSegment ?: "").lowercase()
            return when (pathOrName.substringAfterLast('.', "")) {
                "mov", "qt" -> "video/quicktime"
                "mp4", "m4v" -> MimeTypes.VIDEO_MP4
                "mkv" -> MimeTypes.VIDEO_MATROSKA
                "webm" -> MimeTypes.VIDEO_WEBM
                "3gp", "3gpp" -> MimeTypes.VIDEO_H263
                "avi", "divx" -> MimeTypes.VIDEO_AVI
                "flv", "f4v" -> MimeTypes.VIDEO_FLV
                "mpeg", "mpg", "m2v", "vob", "mp2v", "mpv" -> MimeTypes.VIDEO_MPEG
                "ts", "m2ts", "mts" -> MimeTypes.VIDEO_MP2T
                "ogv" -> MimeTypes.VIDEO_OGG
                else -> null
            }
        }

        fun describeCodecName(mimeType: String?, codecs: String?): String {
            val c = codecs?.lowercase().orEmpty()
            val m = mimeType?.lowercase().orEmpty()
            return when {
                c.startsWith("apch") || c.startsWith("apcn") || c.startsWith("apcs") ||
                    c.startsWith("apco") || c.startsWith("ap4h") || c.startsWith("ap4x") ||
                    m.contains("prores") || m.contains("apcn") -> "Apple ProRes ($codecs)"
                c.startsWith("hvc1") || c.startsWith("hev1") || m == MimeTypes.VIDEO_H265 ->
                    if (!codecs.isNullOrBlank()) "HEVC / H.265 ($codecs)" else "HEVC / H.265"
                c.startsWith("avc1") || c.startsWith("avc3") || m == MimeTypes.VIDEO_H264 ->
                    if (!codecs.isNullOrBlank()) "AVC / H.264 ($codecs)" else "AVC / H.264"
                c.startsWith("av01") || m == MimeTypes.VIDEO_AV1 ->
                    if (!codecs.isNullOrBlank()) "AV1 ($codecs)" else "AV1"
                c.startsWith("vp09") || c.startsWith("vp9") || m == MimeTypes.VIDEO_VP9 -> "VP9"
                c.startsWith("vp08") || c.startsWith("vp8") || m == MimeTypes.VIDEO_VP8 -> "VP8"
                m == MimeTypes.VIDEO_MP4V -> "MPEG-4 Part 2"
                m == MimeTypes.VIDEO_MPEG2 -> "MPEG-2 Video"
                m == MimeTypes.VIDEO_MPEG -> "MPEG-1/2 Video"
                m == MimeTypes.VIDEO_MJPEG || c == "jpeg" || c == "mjpa" || c == "mjpb" -> "Motion JPEG (MJPEG)"
                m == MimeTypes.VIDEO_DIVX -> "DivX"
                m == MimeTypes.VIDEO_VC1 || m.contains("wmv") -> "VC-1 / WMV"
                m == MimeTypes.AUDIO_AC3 -> "Dolby AC-3"
                m == MimeTypes.AUDIO_E_AC3 || m == MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus (E-AC-3)"
                m == MimeTypes.AUDIO_DTS || m == MimeTypes.AUDIO_DTS_HD -> "DTS Audio"
                m == MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
                m == MimeTypes.AUDIO_ALAC || c == "alac" -> "Apple Lossless (ALAC)"
                m == MimeTypes.AUDIO_RAW || c in setOf("twos", "sowt", "in24", "in32", "fl32", "fl64", "lpcm") ->
                    if (!codecs.isNullOrBlank()) "Linear PCM ($codecs)" else "Raw PCM Audio"
                !mimeType.isNullOrBlank() && !codecs.isNullOrBlank() -> "$mimeType ($codecs)"
                !mimeType.isNullOrBlank() -> mimeType
                !codecs.isNullOrBlank() -> codecs
                else -> "Unknown Codec"
            }
        }

        fun formatTrackFormatDiagnostic(format: Format, isSupported: Boolean): String {
            return buildString {
                append(describeCodecName(format.sampleMimeType ?: format.containerMimeType, format.codecs))
                if (format.width != Format.NO_VALUE && format.height != Format.NO_VALUE) {
                    append(" • ${format.width}×${format.height}")
                }
                if (format.frameRate != Format.NO_VALUE.toFloat() && format.frameRate > 0f) {
                    append(" @ ${String.format("%.2f", format.frameRate)}fps")
                }
                if (format.channelCount != Format.NO_VALUE && format.channelCount > 0) {
                    append(" • ${format.channelCount}ch")
                }
                if (format.sampleRate != Format.NO_VALUE && format.sampleRate > 0) {
                    append(" ${format.sampleRate}Hz")
                }
                if (format.bitrate != Format.NO_VALUE && format.bitrate > 0) {
                    append(" • ${format.bitrate / 1000} kbps")
                }
                append(if (isSupported) " [Supported]" else " [Unsupported by active decoder]")
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

    @OptIn(UnstableApi::class)
    suspend fun initializePlayer(
        context: Context,
        uri: Uri,
        preservePlaybackPositionMs: Long? = null,
        preservePlaylistIndex: Int? = null
    ) {
        appContext = context.applicationContext
        if (!hasSyncedInitialPreference) {
            hasSyncedInitialPreference = true
            val prefMode = runCatching {
                VideoDecoderMode.fromOrdinal(globalClass.preferencesManager.videoDecoderMode)
            }.getOrDefault(VideoDecoderMode.AUTO)
            val initialPreferSw = prefMode == VideoDecoderMode.SOFTWARE
            _playerState.update {
                it.copy(
                    decoderMode = prefMode,
                    preferSoftwareDecoder = initialPreferSw
                )
            }
        }

        val currentMode = _playerState.value.decoderMode
        val preferSw = _playerState.value.preferSoftwareDecoder
        _playerState.update {
            it.copy(
                isLoading = true,
                isReady = false,
                usePlatformPlayerFallback = false,
                activeDecoderLabel = formatModeDecoderLabel(
                    mode = currentMode,
                    preferSoftware = preferSw,
                    decoderName = null
                ),
                playbackErrorTitle = null,
                playbackErrorDetails = null,
                codecDiagnosticLog = null,
                trackWarningMessage = if (preservePlaybackPositionMs != null) it.trackWarningMessage else null,
                playlist = playlist
            )
        }

        withContext(Dispatchers.Main) {
            exoPlayer?.release()
            exoPlayer = null

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            val renderersFactory = SoftwareMediaRenderersFactory.create(
                context = context,
                preferSoftwareDecoding = preferSw
            )

            val extractorsFactory = com.techflyers.compose.file.explorer.screen.viewer.media.PrismExtractorsFactory()

            val trackSelector = DefaultTrackSelector(context).apply {
                setParameters(
                    buildUponParameters()
                        .setExceedRendererCapabilitiesIfNecessary(true)
                        .setExceedVideoConstraintsIfNecessary(true)
                        .setExceedAudioConstraintsIfNecessary(true)
                )
            }

            val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .build().apply {
                val uris = playlist.ifEmpty { listOf(uri) }
                val mediaItems = uris.map { itemUri ->
                    buildMediaItemWithSubtitles(context, itemUri)
                }
                setMediaItems(mediaItems)
                repeatMode = _playerState.value.repeatMode
                setPlaybackSpeed(_playerState.value.playbackSpeed)

                val startIndex = preservePlaylistIndex
                    ?: uris.indexOfFirst { it == uri }.coerceAtLeast(0)
                val initialUri = uris.getOrNull(startIndex) ?: uri
                val (initialSubs, initialSelectedSub) = discoverSubtitlesForVideo(context, initialUri)

                seekTo(startIndex, preservePlaybackPositionMs ?: 0L)
                prepare()
                playWhenReady = true

                volume = if (_playerState.value.isMuted) 0f else 1.0f

                val initialTitle = if (initialUri.scheme == "file") {
                    initialUri.path?.let { File(it).name } ?: initialUri.name
                } else {
                    initialUri.name
                } ?: globalClass.getString(R.string.unknown)

                _playerState.update { currentState ->
                    currentState.copy(
                        playerInstanceId = currentState.playerInstanceId + 1,
                        title = initialTitle,
                        currentPlaylistIndex = startIndex,
                        availableSubtitles = initialSubs,
                        selectedSubtitleUri = initialSelectedSub?.uri,
                        subtitlesEnabled = true
                    )
                }

                if (preservePlaybackPositionMs == null && initialSelectedSub != null && initialSelectedSub.isAutoMatched) {
                    globalClass.showMsg("Auto-loaded subtitle: ${initialSelectedSub.name}")
                }

                addAnalyticsListener(object : AnalyticsListener {
                    override fun onVideoDecoderInitialized(
                        eventTime: AnalyticsListener.EventTime,
                        decoderName: String,
                        initializedTimestampMs: Long,
                        initializationDurationMs: Long
                    ) {
                        val friendly = formatModeDecoderLabel(
                            mode = _playerState.value.decoderMode,
                            preferSoftware = _playerState.value.preferSoftwareDecoder,
                            decoderName = decoderName
                        )
                        _playerState.update { it.copy(activeDecoderLabel = friendly) }
                    }
                })

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
                                    isReady = currentState.playbackErrorTitle == null
                                )
                            }
                        } else if (playbackState == Player.STATE_IDLE) {
                            _playerState.update { currentState ->
                                currentState.copy(isLoading = false)
                            }
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        inspectTracksSupport(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        handlePlaybackException(error)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val player = exoPlayer ?: return
                        val currentIndex = player.currentMediaItemIndex
                        audioFallbackRetriedForIndex = -1
                        softwareFallbackRetriedForIndex = -1
                        hardwareFallbackRetriedForIndex = -1
                        val currentUri = mediaItem?.localConfiguration?.uri ?: uris.getOrNull(currentIndex) ?: uri
                        val trackTitle = if (currentUri.scheme == "file") {
                            currentUri.path?.let { File(it).name } ?: currentUri.name
                        } else {
                            currentUri.name
                        } ?: globalClass.getString(R.string.unknown)
                        val (subs, selectedSub) = discoverSubtitlesForVideo(context, currentUri)

                        // If in AUTO mode and a previous item fell back to SW, reset to HW-first for the new track
                        val mode = _playerState.value.decoderMode
                        val shouldResetToHwInAuto = mode == VideoDecoderMode.AUTO && _playerState.value.preferSoftwareDecoder

                        _playerState.update { currentState ->
                            currentState.copy(
                                currentPlaylistIndex = currentIndex,
                                title = trackTitle,
                                currentPosition = 0L,
                                duration = player.duration.takeIf { d -> d isNot TIME_UNSET } ?: 0L,
                                availableSubtitles = subs,
                                selectedSubtitleUri = selectedSub?.uri,
                                usePlatformPlayerFallback = false,
                                playbackErrorTitle = null,
                                playbackErrorDetails = null,
                                codecDiagnosticLog = null,
                                trackWarningMessage = null
                            )
                        }
                        updateNotification()
                        archiveSession?.let { session ->
                            ArchiveMediaQueueManager.prefetchWindow(session, currentIndex, windowSize = 2)
                        }
                        if (shouldResetToHwInAuto) {
                            _playerState.update {
                                it.copy(
                                    preferSoftwareDecoder = false,
                                    activeDecoderLabel = formatModeDecoderLabel(mode, false, null)
                                )
                            }
                            CoroutineScope(Dispatchers.Main).launch {
                                initializePlayer(
                                    context = context,
                                    uri = uri,
                                    preservePlaybackPositionMs = 0L,
                                    preservePlaylistIndex = currentIndex
                                )
                            }
                        }
                    }
                })
            }
        }

        if (mediaNotificationHelper == null) {
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
        }

        startPositionTracking()
        updateNotification()
    }

    /**
     * Sets a specific [VideoDecoderMode] (`AUTO`, `SOFTWARE`, `HARDWARE`), saves it to user preferences,
     * and reinitializes the player at the current position.
     */
    fun setDecoderMode(context: Context, mode: VideoDecoderMode) {
        val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val currentIdx = exoPlayer?.currentMediaItemIndex ?: _playerState.value.currentPlaylistIndex
        val preferSw = mode == VideoDecoderMode.SOFTWARE
        softwareFallbackRetriedForIndex = -1
        hardwareFallbackRetriedForIndex = -1

        runCatching {
            globalClass.preferencesManager.videoDecoderMode = mode.ordinal
        }

        _playerState.update {
            it.copy(
                decoderMode = mode,
                preferSoftwareDecoder = preferSw,
                activeDecoderLabel = formatModeDecoderLabel(mode, preferSw, null),
                trackWarningMessage = null
            )
        }
        val modeLabel = when (mode) {
            VideoDecoderMode.AUTO -> "Auto (Hardware first, Software fallback)"
            VideoDecoderMode.SOFTWARE -> "Software Decoder (FFmpeg)"
            VideoDecoderMode.HARDWARE -> "Hardware Decoder (MediaCodec)"
        }
        globalClass.showMsg("Decoder mode: $modeLabel")
        CoroutineScope(Dispatchers.Main).launch {
            initializePlayer(
                context = context,
                uri = uri,
                preservePlaybackPositionMs = currentPos,
                preservePlaylistIndex = currentIdx
            )
        }
    }

    /**
     * Toggles between Software-first (FFmpeg + c2.android.*) and Hardware-first decoding
     * (used by the error recovery dialog and quick toggle) while preserving the current playback timestamp.
     */
    fun toggleDecoderMode(context: Context) {
        val nextPreferSw = !_playerState.value.preferSoftwareDecoder
        val targetMode = if (nextPreferSw) VideoDecoderMode.SOFTWARE else VideoDecoderMode.HARDWARE
        setDecoderMode(context, targetMode)
    }

    private fun inspectTracksSupport(tracks: Tracks) {
        val groups = tracks.groups
        if (groups.isEmpty()) return

        val videoGroups = groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        val hasSupportedVideo = videoGroups.isEmpty() || videoGroups.any { it.isSupported }
        val hasSupportedAudio = audioGroups.isEmpty() || audioGroups.any { it.isSupported }

        val diagnosticReport = buildTrackDiagnostics(tracks)

        if (videoGroups.isNotEmpty() && !hasSupportedVideo) {
            // If we were in Hardware / Auto mode (preferSoftwareDecoder == false), automatically retry in Software (FFmpeg) mode!
            val currentIdx = exoPlayer?.currentMediaItemIndex ?: 0
            val ctx = appContext
            if (!_playerState.value.preferSoftwareDecoder && ctx != null && softwareFallbackRetriedForIndex != currentIdx) {
                softwareFallbackRetriedForIndex = currentIdx
                _playerState.update {
                    it.copy(
                        preferSoftwareDecoder = true,
                        activeDecoderLabel = formatModeDecoderLabel(it.decoderMode, true, null)
                    )
                }
                CoroutineScope(Dispatchers.Main).launch {
                    initializePlayer(
                        context = ctx,
                        uri = uri,
                        preservePlaybackPositionMs = 0L,
                        preservePlaylistIndex = currentIdx
                    )
                }
                return
            }

            val firstFormat = videoGroups.first().getTrackFormat(0)
            val codecLabel = describeCodecName(firstFormat.sampleMimeType, firstFormat.codecs)
            val dims = if (firstFormat.width != Format.NO_VALUE && firstFormat.height != Format.NO_VALUE) {
                " (${firstFormat.width}×${firstFormat.height})"
            } else ""
            _playerState.update { state ->
                state.copy(
                    isLoading = false,
                    isReady = false,
                    playbackErrorTitle = "Unsupported Video Codec",
                    playbackErrorDetails = "This video track uses $codecLabel$dims, which could not be decoded by the bundled FFmpeg software decoder or device MediaCodec.",
                    codecDiagnosticLog = diagnosticReport
                )
            }
            return
        }

        if (audioGroups.isNotEmpty() && !hasSupportedAudio) {
            val currentIdx = exoPlayer?.currentMediaItemIndex ?: 0
            val ctx = appContext
            if (!_playerState.value.preferSoftwareDecoder && ctx != null && softwareFallbackRetriedForIndex != currentIdx) {
                softwareFallbackRetriedForIndex = currentIdx
                _playerState.update {
                    it.copy(
                        preferSoftwareDecoder = true,
                        activeDecoderLabel = formatModeDecoderLabel(it.decoderMode, true, null)
                    )
                }
                CoroutineScope(Dispatchers.Main).launch {
                    initializePlayer(
                        context = ctx,
                        uri = uri,
                        preservePlaybackPositionMs = 0L,
                        preservePlaylistIndex = currentIdx
                    )
                }
                return
            }

            val firstAudio = audioGroups.first().getTrackFormat(0)
            val audioCodec = describeCodecName(firstAudio.sampleMimeType, firstAudio.codecs)
            if (videoGroups.isEmpty()) {
                _playerState.update { state ->
                    state.copy(
                        isLoading = false,
                        isReady = false,
                        playbackErrorTitle = "Unsupported Audio Codec",
                        playbackErrorDetails = "This media file uses $audioCodec, which is not supported by the audio decoders.",
                        codecDiagnosticLog = diagnosticReport
                    )
                }
            } else {
                _playerState.update { state ->
                    state.copy(
                        trackWarningMessage = "Audio track ($audioCodec) is not supported; playing video without sound.",
                        codecDiagnosticLog = diagnosticReport
                    )
                }
            }
        }
    }

    private fun handlePlaybackException(error: PlaybackException) {
        val player = exoPlayer
        val currentIdx = player?.currentMediaItemIndex ?: 0
        val currentPos = player?.currentPosition?.coerceAtLeast(0L) ?: 0L
        val ctx = appContext

        val isDecoderFailure = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
        )

        // 1. If Hardware decoder failed (in AUTO or HARDWARE mode), automatically fallback to Software (FFmpeg) decoder!
        if (isDecoderFailure && !_playerState.value.preferSoftwareDecoder && ctx != null && softwareFallbackRetriedForIndex != currentIdx) {
            softwareFallbackRetriedForIndex = currentIdx
            _playerState.update {
                it.copy(
                    preferSoftwareDecoder = true,
                    activeDecoderLabel = formatModeDecoderLabel(it.decoderMode, true, null),
                    trackWarningMessage = "Hardware decoder failed; switched to FFmpeg software decoder."
                )
            }
            CoroutineScope(Dispatchers.Main).launch {
                initializePlayer(
                    context = ctx,
                    uri = uri,
                    preservePlaybackPositionMs = currentPos,
                    preservePlaylistIndex = currentIdx
                )
            }
            return
        }

        // 2. If Software decoder failed (in SOFTWARE mode), automatically try Hardware decoder as fallback!
        if (isDecoderFailure && _playerState.value.preferSoftwareDecoder && ctx != null && hardwareFallbackRetriedForIndex != currentIdx && softwareFallbackRetriedForIndex != currentIdx) {
            hardwareFallbackRetriedForIndex = currentIdx
            _playerState.update {
                it.copy(
                    preferSoftwareDecoder = false,
                    activeDecoderLabel = formatModeDecoderLabel(it.decoderMode, false, null),
                    trackWarningMessage = "Software decoder failed; switched to Hardware decoder."
                )
            }
            CoroutineScope(Dispatchers.Main).launch {
                initializePlayer(
                    context = ctx,
                    uri = uri,
                    preservePlaybackPositionMs = currentPos,
                    preservePlaylistIndex = currentIdx
                )
            }
            return
        }

        // If the failure is solely in the audio renderer/decoder while video tracks exist,
        // disable the audio track once and retry so the video can still play.
        val causeMsg = (error.cause?.message ?: error.message ?: "").lowercase()
        val isAudioError = error.errorCode in setOf(
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        ) || causeMsg.contains("audio") || causeMsg.contains("mediacodecaudiorenderer") || causeMsg.contains("ffmpegaudiorenderer")

        if (isAudioError && player != null && audioFallbackRetriedForIndex != currentIdx) {
            val hasVideo = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
            if (hasVideo) {
                audioFallbackRetriedForIndex = currentIdx
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                player.prepare()
                player.play()
                _playerState.update { state ->
                    state.copy(
                        trackWarningMessage = "Audio track decoder failed (${error.errorCodeName}); playing video without audio."
                    )
                }
                return
            }
        }

        val errorTitle = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "Unsupported Media Codec"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Unsupported or Corrupted Container"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "File Access Error"
            else -> "Media Playback Error"
        }

        val trackDiagnostics = player?.currentTracks?.let { buildTrackDiagnostics(it) }.orEmpty()
        val causeChain = buildString {
            appendLine("Decoder Mode: ${_playerState.value.activeDecoderLabel}")
            appendLine("Error Code: ${error.errorCodeName} (${error.errorCode})")
            error.message?.let { appendLine("Message: $it") }
            var c = error.cause
            var depth = 1
            while (c != null && depth <= 5) {
                appendLine("Cause [$depth]: ${c.javaClass.simpleName}: ${c.message}")
                c = c.cause
                depth++
            }
            if (trackDiagnostics.isNotBlank()) {
                appendLine()
                append(trackDiagnostics)
            }
        }.trim()

        val humanDetails = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "The video or audio stream uses a codec or profile that could not be decoded (${error.cause?.message ?: error.errorCodeName})."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                "The media container format is unsupported or has a non-standard header structure (${error.cause?.message ?: error.errorCodeName})."
            else ->
                error.cause?.message ?: error.message ?: "Unable to play this media file (${error.errorCodeName})."
        }

        _playerState.update { state ->
            state.copy(
                isLoading = false,
                isReady = false,
                isPlaying = false,
                playbackErrorTitle = errorTitle,
                playbackErrorDetails = humanDetails,
                codecDiagnosticLog = causeChain
            )
        }
    }

    private fun buildTrackDiagnostics(tracks: Tracks): String {
        val currentUri = _playerState.value.playlist.getOrNull(_playerState.value.currentPlaylistIndex) ?: uri
        return buildString {
            appendLine("File: ${_playerState.value.title.ifEmpty { currentUri.lastPathSegment ?: currentUri.toString() }}")
            appendLine("URI: $currentUri")
            val groups = tracks.groups
            if (groups.isEmpty()) {
                appendLine("Tracks: None detected in container")
            } else {
                groups.forEachIndexed { groupIdx, group ->
                    val typeLabel = when (group.type) {
                        C.TRACK_TYPE_VIDEO -> "Video"
                        C.TRACK_TYPE_AUDIO -> "Audio"
                        C.TRACK_TYPE_TEXT -> "Subtitle"
                        else -> "Track (${group.type})"
                    }
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val supported = group.isTrackSupported(i)
                        appendLine("$typeLabel #$groupIdx.$i: ${formatTrackFormatDiagnostic(format, supported)}")
                    }
                }
            }
        }.trim()
    }

    fun switchToPlatformPlayerFallback() {
        exoPlayer?.stop()
        _playerState.update { state ->
            state.copy(
                usePlatformPlayerFallback = true,
                playbackErrorTitle = null,
                playbackErrorDetails = null,
                isLoading = false,
                isReady = true
            )
        }
    }

    fun onPlatformPlayerError(what: Int, extra: Int) {
        val prevLog = _playerState.value.codecDiagnosticLog.orEmpty()
        val updatedLog = buildString {
            if (prevLog.isNotBlank()) {
                appendLine(prevLog)
                appendLine()
            }
            appendLine("Android Platform MediaPlayer Error: what=$what, extra=$extra")
        }.trim()
        _playerState.update { state ->
            state.copy(
                usePlatformPlayerFallback = false,
                isLoading = false,
                isReady = false,
                isPlaying = false,
                playbackErrorTitle = "Unsupported Video Codec / Format",
                playbackErrorDetails = "Both FFmpeg/ExoPlayer and Android's native MediaPlayer failed to decode this file (what=$what, extra=$extra).",
                codecDiagnosticLog = updatedLog
            )
        }
    }

    fun dismissTrackWarning() {
        _playerState.update { it.copy(trackWarningMessage = null) }
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
                    if (!_playerState.value.usePlatformPlayerFallback) {
                        _playerState.update { currentState ->
                            currentState.copy(
                                currentPosition = player.currentPosition,
                                duration = player.duration.takeIf { it isNot TIME_UNSET } ?: 0L
                            )
                        }
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
        appContext = null
        positionTrackingJob?.cancel()
        mediaNotificationHelper?.release()
        mediaNotificationHelper = null
        exoPlayer?.release()
        exoPlayer = null
    }
}
