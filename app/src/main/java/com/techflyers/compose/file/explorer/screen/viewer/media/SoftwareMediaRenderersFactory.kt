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
import com.techflyers.compose.file.explorer.screen.preferences.constant.VideoDecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import java.util.ArrayList

/**
 * Provides a configurable Media3 RenderersFactory supporting Auto (Hardware-first with Software fallback),
 * Software-first (FFmpeg + c2.android.*), and Hardware-first (`MediaCodec`) modes:
 * 1. In `AUTO` or `HARDWARE` mode ([preferSoftwareDecoding] = `false`), hardware `MediaCodec` renderers run first
 *    for maximum performance and battery efficiency, while [ExtendedFfmpegVideoRenderer], `FfmpegVideoRenderer`,
 *    and `FfmpegAudioRenderer` are registered immediately after (`EXTENSION_RENDERER_MODE_ON`) to handle any codec
 *    not supported by hardware. If hardware decoding fails at runtime, the player retries with [preferSoftwareDecoding] = `true`.
 * 2. In `SOFTWARE` mode ([preferSoftwareDecoding] = `true`), [ExtendedFfmpegVideoRenderer], `FfmpegVideoRenderer`,
 *    `FfmpegAudioRenderer`, and Android software codecs (`c2.android.*`, `OMX.google.*`) take first priority (`EXTENSION_RENDERER_MODE_PREFER`).
 */
@OptIn(UnstableApi::class)
object SoftwareMediaRenderersFactory {

    fun create(
        context: Context,
        mode: VideoDecoderMode,
        forceSoftwareFallback: Boolean = false
    ): DefaultRenderersFactory {
        val preferSoftware = mode == VideoDecoderMode.SOFTWARE || forceSoftwareFallback
        return create(
            context = context,
            preferSoftwareDecoding = preferSoftware
        )
    }

    fun create(
        context: Context,
        preferSoftwareDecoding: Boolean = false
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
