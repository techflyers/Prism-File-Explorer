package com.techflyers.compose.file.explorer.screen.viewer.media

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.util.ArrayList

/**
 * Provides a Software-First Media3 RenderersFactory combining:
 * 1. [ExtendedFfmpegVideoRenderer] + bundled `NextRenderersFactory` (`FfmpegVideoRenderer`, `FfmpegAudioRenderer`)
 *    unlocking all 45+ FFmpeg decoders compiled into `libavcodec.so` (.mov QuickTime, Apple ProRes, MJPEG,
 *    Sorenson SVQ1/SVQ3, QuickTime Animation RLE, Apple Video RPZA, Apple Graphics SMC, Cinepak, DV,
 *    HEVC/H.265, H.264 10-bit, MPEG-4/DivX/Xvid, AV1, VP8, VP9, WMV/VC-1, H.263, MPEG-1/2, Theora,
 *    ALAC, PCM sowt/twos/in24/in32/fl32/fl64, AC-3, E-AC-3, DTS, TrueHD, Opus, Vorbis, FLAC, MP3, AMR).
 * 2. A Software-Preferring [MediaCodecSelector] that prioritizes Android's software codecs (`c2.android.*`, `OMX.google.*`,
 *    `softwareOnly`) ahead of SoC hardware codecs when [preferSoftwareDecoding] is enabled.
 */
@OptIn(UnstableApi::class)
object SoftwareMediaRenderersFactory {

    fun create(
        context: Context,
        preferSoftwareDecoding: Boolean = true
    ): DefaultRenderersFactory {
        val extensionMode = if (preferSoftwareDecoding) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        }

        val baseFactory: DefaultRenderersFactory = try {
            object : NextRenderersFactory(context.applicationContext) {
                override fun buildVideoRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    mediaCodecSelector: MediaCodecSelector,
                    enableDecoderFallback: Boolean,
                    eventHandler: Handler,
                    eventListener: VideoRendererEventListener,
                    allowedVideoJoiningTimeMs: Long,
                    out: ArrayList<Renderer>
                ) {
                    if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
                        // Place ExtendedFfmpegVideoRenderer first so all FFmpeg video codecs
                        // (including ProRes, MJPEG, MPEG-4, SVQ1/3, QTRLE, Cinepak, DV, AVC, HEVC, AV1, VP8/9)
                        // are decoded in software first.
                        out.add(
                            ExtendedFfmpegVideoRenderer(
                                allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
                                eventHandler = eventHandler,
                                eventListener = eventListener,
                                maxDroppedFramesToNotify = MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                            )
                        )
                    }

                    super.buildVideoRenderers(
                        context,
                        extensionRendererMode,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        allowedVideoJoiningTimeMs,
                        out
                    )

                    if (extensionRendererMode != EXTENSION_RENDERER_MODE_PREFER &&
                        extensionRendererMode != EXTENSION_RENDERER_MODE_OFF
                    ) {
                        out.add(
                            ExtendedFfmpegVideoRenderer(
                                allowedJoiningTimeMs = allowedVideoJoiningTimeMs,
                                eventHandler = eventHandler,
                                eventListener = eventListener,
                                maxDroppedFramesToNotify = MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            DefaultRenderersFactory(context.applicationContext)
        }

        return baseFactory
            .setExtensionRendererMode(extensionMode)
            .setMediaCodecSelector(SoftwarePreferringMediaCodecSelector(preferSoftwareDecoding))
            .setEnableDecoderFallback(true)
            .setEnableAudioFloatOutput(true)
            .setEnableAudioTrackPlaybackParams(true)
    }

    private class SoftwarePreferringMediaCodecSelector(
        private val preferSoftware: Boolean
    ) : MediaCodecSelector {
        override fun getDecoderInfos(
            mimeType: String,
            requiresSecureDecoder: Boolean,
            requiresTunnelingDecoder: Boolean
        ): MutableList<MediaCodecInfo> {
            val defaultList = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            if (defaultList.size <= 1) {
                return defaultList.toMutableList()
            }

            return if (preferSoftware && !requiresSecureDecoder && !requiresTunnelingDecoder) {
                val (softwareCodecs, hardwareCodecs) = defaultList.partition { info ->
                    isSoftwareCodec(info)
                }
                (softwareCodecs + hardwareCodecs).toMutableList()
            } else {
                defaultList.toMutableList()
            }
        }

        private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
            if (info.softwareOnly || !info.hardwareAccelerated) return true
            val lower = info.name.lowercase()
            return lower.startsWith("c2.android.") ||
                lower.startsWith("omx.google.") ||
                lower.startsWith("c2.google.") ||
                lower.contains(".sw.") ||
                lower.contains("ffmpeg")
        }
    }
}
