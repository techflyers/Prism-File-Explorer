package com.techflyers.compose.file.explorer.screen.viewer.media

import android.os.Handler
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.TraceUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import androidx.media3.decoder.VideoDecoderOutputBuffer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.video.DecoderVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegDecoderException
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.nio.ByteBuffer

/**
 * Extended FFmpeg software video renderer that unlocks all video codecs compiled into the bundled
 * `libavcodec.so` / `libmedia3ext.so` native library — including QuickTime (.mov) codecs that the
 * stock `FfmpegLibrary.getCodecName()` switch in `nextlib-media3ext` omits:
 * - Apple ProRes (`apch`, `apcn`, `apcs`, `apco`, `ap4h`, `ap4x` -> `prores`)
 * - Motion JPEG / Photo-JPEG (`jpeg`, `mjpa`, `mjpb`, `mjp2`, `video/mjpeg` -> `mjpeg`)
 * - MPEG-4 Part 2 / DivX / Xvid (`mp4v`, `divx`, `dx50`, `xvid`, `fmp4` -> `mpeg4`)
 * - Sorenson Video 1 & 3 (`SVQ1` -> `svq1`, `SVQ3` -> `svq3`)
 * - QuickTime Animation RLE (`rle ` -> `qtrle`)
 * - QuickTime Apple Video (`rpza` -> `rpza`)
 * - QuickTime Graphics (`smc ` -> `smc`)
 * - Cinepak (`cvid` -> `cinepak`)
 * - DV / DVCPro (`dvc `, `dvcp`, `dvpp`, `dv5p`, `dv5n` -> `dvvideo`)
 * - H.264 / AVC (`avc1`, `avc3` -> `h264`, including single-buffer initializationData)
 * - HEVC / H.265 / Dolby Vision (`hvc1`, `hev1`, `dvh1`, `dvhe` -> `hevc`)
 * - AV1 (`av01` -> `av1`)
 * - VP8 / VP9 (`vp08` -> `vp8`/`libvpx`, `vp09` -> `vp9`/`libvpx-vp9`)
 * - H.263 (`s263`, `H263`, `h263` -> `h263`)
 * - MPEG-1 / MPEG-2 (`mpegvideo`, `mpeg2video`)
 * - VC-1 / WMV (`vc1`, `wmv3`, `wmv2`, `wmv1`)
 * - Theora (`theora`)
 */
@OptIn(UnstableApi::class)
class ExtendedFfmpegVideoRenderer(
    allowedJoiningTimeMs: Long,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int,
    private val threads: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8),
    private val numInputBuffers: Int = 4,
    private val numOutputBuffers: Int = 4
) : DecoderVideoRenderer(
    allowedJoiningTimeMs,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {
    private var activeDecoder: ExtendedFfmpegVideoDecoder? = null

    override fun getName(): String = "ExtendedFfmpegVideoRenderer"

    override fun supportsFormat(format: Format): Int {
        val sampleMime = format.sampleMimeType
        if (sampleMime.isNullOrBlank() || (!MimeTypes.isVideo(sampleMime) && sampleMime != MimeTypes.IMAGE_JPEG)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        if (!ExtendedFfmpegNativeBridge.isAvailable()) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
        if (format.cryptoType != C.CRYPTO_TYPE_NONE) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_DRM)
        }
        val codecName = ExtendedFfmpegNativeBridge.resolveFfmpegVideoCodecName(format)
            ?: return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)

        if (!ExtendedFfmpegNativeBridge.hasDecoder(codecName)) {
            return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
        }

        return RendererCapabilities.create(
            C.FORMAT_HANDLED,
            ADAPTIVE_SEAMLESS,
            TUNNELING_NOT_SUPPORTED
        )
    }

    override fun createDecoder(
        format: Format,
        cryptoConfig: CryptoConfig?
    ): Decoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException> {
        TraceUtil.beginSection("createExtendedFfmpegVideoDecoder")
        return try {
            val initialInputBufferSize = if (format.maxInputSize != Format.NO_VALUE) {
                format.maxInputSize
            } else {
                DEFAULT_INPUT_BUFFER_SIZE
            }
            val decoder = ExtendedFfmpegVideoDecoder(
                numInputBuffers = numInputBuffers,
                numOutputBuffers = numOutputBuffers,
                initialInputBufferSize = initialInputBufferSize,
                threads = threads,
                format = format
            )
            activeDecoder = decoder
            decoder
        } finally {
            TraceUtil.endSection()
        }
    }

    override fun renderOutputBufferToSurface(
        outputBuffer: VideoDecoderOutputBuffer,
        surface: Surface
    ) {
        val decoder = activeDecoder
            ?: throw ExtendedFfmpegNativeBridge.newDecoderException(
                "Failed to render output buffer to surface: decoder is not initialized."
            )
        decoder.renderToSurface(outputBuffer, surface)
        outputBuffer.release()
    }

    override fun setDecoderOutputMode(outputMode: Int) {
        activeDecoder?.setOutputMode(outputMode)
    }

    companion object {
        private val DEFAULT_INPUT_BUFFER_SIZE: Int =
            Util.ceilDivide(1920, 64) * Util.ceilDivide(1080, 64) * (64 * 64 * 3 / 2) / 2

        const val MIME_VIDEO_PRORES = "video/prores"
        const val MIME_VIDEO_SVQ1 = "video/x-svq1"
        const val MIME_VIDEO_SVQ3 = "video/x-svq3"
        const val MIME_VIDEO_QTRLE = "video/x-qtrle"
        const val MIME_VIDEO_RPZA = "video/x-rpza"
        const val MIME_VIDEO_SMC = "video/x-smc"
        const val MIME_VIDEO_CINEPAK = "video/x-cinepak"
        const val MIME_VIDEO_DV = "video/dv"
        const val MIME_VIDEO_THEORA = "video/x-theora"
        const val MIME_VIDEO_WMV = "video/x-ms-wmv"
    }
}

@OptIn(UnstableApi::class)
internal object ExtendedFfmpegNativeBridge {
    private var initialized = false
    private var bridgeAvailable = false

    private var ffmpegHasDecoderMethod: Method? = null
    private var ffmpegGetInputPaddingMethod: Method? = null

    private var nativeReceiverInstance: Any? = null
    private var ffmpegInitializeMethod: Method? = null
    private var ffmpegSendPacketMethod: Method? = null
    private var ffmpegReceiveFrameMethod: Method? = null
    private var ffmpegRenderFrameMethod: Method? = null
    private var ffmpegResetMethod: Method? = null
    private var ffmpegReleaseMethod: Method? = null

    private var exceptionMsgCtor: Constructor<FfmpegDecoderException>? = null
    private var exceptionMsgCauseCtor: Constructor<FfmpegDecoderException>? = null

    @Synchronized
    private fun ensureInitialized() {
        if (initialized) return
        initialized = true
        try {
            if (!FfmpegLibrary.isAvailable()) {
                bridgeAvailable = false
                return
            }

            val libClass = FfmpegLibrary::class.java
            ffmpegHasDecoderMethod = libClass.getDeclaredMethod("ffmpegHasDecoder", String::class.java).apply {
                isAccessible = true
            }
            ffmpegGetInputPaddingMethod = libClass.getDeclaredMethod("ffmpegGetInputBufferPaddingSize").apply {
                isAccessible = true
            }

            val videoDecoderClass = Class.forName(
                "io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoDecoder"
            )
            nativeReceiverInstance = try {
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val unsafeField = unsafeClass.getDeclaredField("theUnsafe").apply {
                    isAccessible = true
                }
                val unsafe = unsafeField.get(null)
                val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
                allocateMethod.invoke(unsafe, videoDecoderClass)
            } catch (_: Throwable) {
                try {
                    val newInstanceMethod = java.io.ObjectInputStream::class.java.getDeclaredMethod(
                        "newInstance",
                        Class::class.java,
                        Class::class.java
                    ).apply { isAccessible = true }
                    newInstanceMethod.invoke(null, videoDecoderClass, Any::class.java)
                } catch (_: Throwable) {
                    null
                }
            }

            ffmpegInitializeMethod = videoDecoderClass.getDeclaredMethod(
                "ffmpegInitialize",
                String::class.java,
                ByteArray::class.java,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            ffmpegSendPacketMethod = videoDecoderClass.getDeclaredMethod(
                "ffmpegSendPacket",
                Long::class.javaPrimitiveType,
                ByteBuffer::class.java,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType
            ).apply { isAccessible = true }

            ffmpegReceiveFrameMethod = videoDecoderClass.getDeclaredMethod(
                "ffmpegReceiveFrame",
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                VideoDecoderOutputBuffer::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }

            ffmpegRenderFrameMethod = videoDecoderClass.getDeclaredMethod(
                "ffmpegRenderFrame",
                Long::class.javaPrimitiveType,
                Surface::class.java,
                VideoDecoderOutputBuffer::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            ffmpegResetMethod = videoDecoderClass.getDeclaredMethod(
                "ffmpegReset",
                Long::class.javaPrimitiveType
            ).apply { isAccessible = true }

            ffmpegReleaseMethod = videoDecoderClass.getDeclaredMethod(
                "ffmpegRelease",
                Long::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val exClass = FfmpegDecoderException::class.java
            exceptionMsgCtor = exClass.getDeclaredConstructor(String::class.java).apply {
                isAccessible = true
            }
            exceptionMsgCauseCtor = exClass.getDeclaredConstructor(
                String::class.java,
                Throwable::class.java
            ).apply {
                isAccessible = true
            }

            bridgeAvailable = true
        } catch (t: Throwable) {
            android.util.Log.w("ExtendedFfmpegBridge", "Failed to initialize FFmpeg reflection bridge", t)
            bridgeAvailable = false
        }
    }

    fun isAvailable(): Boolean {
        ensureInitialized()
        return bridgeAvailable && FfmpegLibrary.isAvailable()
    }

    fun getInputBufferPaddingSize(): Int {
        ensureInitialized()
        return try {
            (ffmpegGetInputPaddingMethod?.invoke(null) as? Int)?.coerceAtLeast(0) ?: 64
        } catch (_: Throwable) {
            64
        }
    }

    fun hasDecoder(codecName: String): Boolean {
        ensureInitialized()
        if (!bridgeAvailable) return false
        return try {
            ffmpegHasDecoderMethod?.invoke(null, codecName) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    fun ffmpegInitialize(codecName: String, extraData: ByteArray?, threads: Int): Long {
        ensureInitialized()
        val receiver = nativeReceiverInstance ?: return 0L
        return (ffmpegInitializeMethod?.invoke(receiver, codecName, extraData, threads) as? Long) ?: 0L
    }

    fun ffmpegSendPacket(context: Long, encodedData: ByteBuffer, length: Int, inputTimeUs: Long): Int {
        val receiver = nativeReceiverInstance ?: return -2
        return (ffmpegSendPacketMethod?.invoke(receiver, context, encodedData, length, inputTimeUs) as? Int) ?: -2
    }

    fun ffmpegReceiveFrame(
        context: Long,
        outputMode: Int,
        outputBuffer: VideoDecoderOutputBuffer,
        decodeOnly: Boolean
    ): Int {
        val receiver = nativeReceiverInstance ?: return -2
        return (ffmpegReceiveFrameMethod?.invoke(receiver, context, outputMode, outputBuffer, decodeOnly) as? Int) ?: -2
    }

    fun ffmpegRenderFrame(
        context: Long,
        surface: Surface,
        outputBuffer: VideoDecoderOutputBuffer,
        displayedWidth: Int,
        displayedHeight: Int
    ): Int {
        val receiver = nativeReceiverInstance ?: return -2
        return (ffmpegRenderFrameMethod?.invoke(
            receiver,
            context,
            surface,
            outputBuffer,
            displayedWidth,
            displayedHeight
        ) as? Int) ?: -2
    }

    fun ffmpegReset(context: Long): Long {
        val receiver = nativeReceiverInstance ?: return 0L
        return (ffmpegResetMethod?.invoke(receiver, context) as? Long) ?: 0L
    }

    fun ffmpegRelease(context: Long) {
        val receiver = nativeReceiverInstance ?: return
        try {
            ffmpegReleaseMethod?.invoke(receiver, context)
        } catch (_: Throwable) {
        }
    }

    fun newDecoderException(message: String, cause: Throwable? = null): FfmpegDecoderException {
        ensureInitialized()
        return try {
            if (cause != null && exceptionMsgCauseCtor != null) {
                exceptionMsgCauseCtor!!.newInstance(message, cause)
            } else {
                exceptionMsgCtor!!.newInstance(message)
            }
        } catch (_: Throwable) {
            throw RuntimeException(message, cause)
        }
    }

    /**
     * Maps a Media3 [Format] (via `sampleMimeType` and `codecs` fourcc) to a decoder name supported
     * by the bundled `libavcodec.so`.
     */
    fun resolveFfmpegVideoCodecName(format: Format): String? {
        val mime = format.sampleMimeType?.lowercase().orEmpty()
        val codecs = format.codecs?.lowercase()?.trim().orEmpty()

        // 1. Check specific fourcc / codec tag overrides first (especially for QuickTime .mov)
        val byCodecTag = when {
            codecs.startsWith("apch") || codecs.startsWith("apcn") || codecs.startsWith("apcs") ||
                codecs.startsWith("apco") || codecs.startsWith("ap4h") || codecs.startsWith("ap4x") ||
                codecs == "prores" -> "prores"
            codecs == "jpeg" || codecs == "mjpa" || codecs == "mjpb" || codecs == "mjpg" || codecs == "mjp2" || codecs == "dmb1" -> "mjpeg"
            codecs == "svq1" -> "svq1"
            codecs == "svq3" -> "svq3"
            codecs == "rle " || codecs == "rle" || codecs == "qtrle" -> "qtrle"
            codecs == "rpza" || codecs == "azpr" -> "rpza"
            codecs == "smc " || codecs == "smc" -> "smc"
            codecs == "cvid" || codecs == "cinepak" -> "cinepak"
            codecs == "dvc " || codecs == "dvcp" || codecs == "dvpp" || codecs == "dv5p" || codecs == "dv5n" || codecs == "dvsd" -> "dvvideo"
            codecs == "wmv1" -> "wmv1"
            codecs == "wmv2" -> "wmv2"
            codecs == "wmv3" -> "wmv3"
            codecs == "wvc1" || codecs == "vc-1" || codecs == "vc1" -> "vc1"
            codecs.startsWith("avc1") || codecs.startsWith("avc3") || codecs == "h264" -> "h264"
            codecs.startsWith("hvc1") || codecs.startsWith("hev1") || codecs.startsWith("dvh1") || codecs.startsWith("dvhe") || codecs == "hevc" -> "hevc"
            codecs.startsWith("mp4v") || codecs == "divx" || codecs == "dx50" || codecs == "xvid" || codecs == "fmp4" -> "mpeg4"
            codecs.startsWith("av01") || codecs == "av1" -> "av1"
            codecs.startsWith("vp09") || codecs == "vp9" -> firstAvailableDecoder("vp9", "libvpx-vp9")
            codecs.startsWith("vp08") || codecs == "vp8" -> firstAvailableDecoder("vp8", "libvpx")
            codecs == "s263" || codecs == "h263" || codecs == "d263" -> "h263"
            else -> null
        }
        if (byCodecTag != null && hasDecoder(byCodecTag)) {
            return byCodecTag
        }

        // 2. Map by MIME type
        val candidate = when (mime) {
            MimeTypes.VIDEO_H264 -> "h264"
            MimeTypes.VIDEO_H265, MimeTypes.VIDEO_DOLBY_VISION, "video/mv-hevc" -> "hevc"
            MimeTypes.VIDEO_MP4V, MimeTypes.VIDEO_DIVX, "video/xvid", "video/mp4v" -> "mpeg4"
            MimeTypes.VIDEO_MJPEG, MimeTypes.IMAGE_JPEG, "video/x-mjpeg" -> "mjpeg"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_PRORES, "video/x-apple-prores" -> "prores"
            MimeTypes.VIDEO_AV1 -> firstAvailableDecoder("av1", "libdav1d", "dav1d")
            MimeTypes.VIDEO_VP8 -> firstAvailableDecoder("vp8", "libvpx")
            MimeTypes.VIDEO_VP9 -> firstAvailableDecoder("vp9", "libvpx-vp9")
            MimeTypes.VIDEO_MPEG2 -> "mpeg2video"
            MimeTypes.VIDEO_MPEG -> firstAvailableDecoder("mpegvideo", "mpeg2video", "mpeg1video")
            MimeTypes.VIDEO_H263, "video/s263" -> "h263"
            MimeTypes.VIDEO_VC1, "video/vc1" -> "vc1"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_WMV, "video/x-ms-asf" -> firstAvailableDecoder("wmv3", "vc1", "wmv2", "wmv1")
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_SVQ1 -> "svq1"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_SVQ3 -> "svq3"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_QTRLE -> "qtrle"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_RPZA -> "rpza"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_SMC -> "smc"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_CINEPAK -> "cinepak"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_DV -> "dvvideo"
            ExtendedFfmpegVideoRenderer.MIME_VIDEO_THEORA, MimeTypes.VIDEO_OGG -> "theora"
            else -> null
        }

        return candidate?.takeIf { hasDecoder(it) }
    }

    private fun firstAvailableDecoder(vararg names: String): String? {
        for (name in names) {
            if (hasDecoder(name)) return name
        }
        return names.firstOrNull()
    }

    /**
     * Safely builds codec extradata from [Format.initializationData] without throwing
     * [IndexOutOfBoundsException] when `video/avc` has a single initialization buffer,
     * and passes extradata for MPEG-4 (`esds`), Sorenson (`SVQ3`), QuickTime RLE, ProRes, VC-1, etc.
     */
    fun buildExtraData(format: Format): ByteArray? {
        val initData = format.initializationData
        if (initData.isEmpty()) return null

        val mime = format.sampleMimeType?.lowercase().orEmpty()
        return when (mime) {
            MimeTypes.VIDEO_H264 -> {
                if (initData.size == 1) {
                    initData[0]
                } else {
                    val totalSize = initData.sumOf { it.size }
                    val combined = ByteArray(totalSize)
                    var offset = 0
                    for (chunk in initData) {
                        System.arraycopy(chunk, 0, combined, offset, chunk.size)
                        offset += chunk.size
                    }
                    combined
                }
            }
            MimeTypes.VIDEO_H265, MimeTypes.VIDEO_DOLBY_VISION -> initData[0]
            else -> {
                if (initData.size == 1) {
                    initData[0]
                } else {
                    val totalSize = initData.sumOf { it.size }
                    val combined = ByteArray(totalSize)
                    var offset = 0
                    for (chunk in initData) {
                        System.arraycopy(chunk, 0, combined, offset, chunk.size)
                        offset += chunk.size
                    }
                    combined
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
internal class ExtendedFfmpegVideoDecoder(
    numInputBuffers: Int,
    numOutputBuffers: Int,
    initialInputBufferSize: Int,
    threads: Int,
    private var format: Format
) : SimpleDecoder<DecoderInputBuffer, VideoDecoderOutputBuffer, FfmpegDecoderException>(
    arrayOfNulls(numInputBuffers),
    arrayOfNulls(numOutputBuffers)
) {
    private val codecName: String
    private val extraData: ByteArray?
    private var nativeContext: Long = 0L

    @Volatile
    private var outputMode: Int = C.VIDEO_OUTPUT_MODE_SURFACE_YUV

    init {
        if (!ExtendedFfmpegNativeBridge.isAvailable()) {
            throw ExtendedFfmpegNativeBridge.newDecoderException("Failed to load FFmpeg decoder native library.")
        }
        codecName = ExtendedFfmpegNativeBridge.resolveFfmpegVideoCodecName(format)
            ?: throw ExtendedFfmpegNativeBridge.newDecoderException(
                "Unsupported video format: ${format.sampleMimeType} (codecs=${format.codecs})"
            )
        extraData = ExtendedFfmpegNativeBridge.buildExtraData(format)
        nativeContext = ExtendedFfmpegNativeBridge.ffmpegInitialize(codecName, extraData, threads)
        if (nativeContext == 0L) {
            throw ExtendedFfmpegNativeBridge.newDecoderException("Failed to initialize FFmpeg video decoder ($codecName).")
        }
        setInitialInputBufferSize(initialInputBufferSize)
    }

    override fun getName(): String = "ffmpeg-$codecName"

    fun setOutputMode(outputMode: Int) {
        this.outputMode = outputMode
    }

    override fun createInputBuffer(): DecoderInputBuffer {
        return DecoderInputBuffer(
            DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_DIRECT,
            ExtendedFfmpegNativeBridge.getInputBufferPaddingSize()
        )
    }

    override fun createOutputBuffer(): VideoDecoderOutputBuffer {
        return VideoDecoderOutputBuffer { buffer -> releaseOutputBuffer(buffer) }
    }

    override fun createUnexpectedDecodeException(error: Throwable): FfmpegDecoderException {
        return ExtendedFfmpegNativeBridge.newDecoderException("Unexpected FFmpeg video decode error", error)
    }

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: VideoDecoderOutputBuffer,
        reset: Boolean
    ): FfmpegDecoderException? {
        if (reset) {
            nativeContext = ExtendedFfmpegNativeBridge.ffmpegReset(nativeContext)
            if (nativeContext == 0L) {
                return ExtendedFfmpegNativeBridge.newDecoderException("Error resetting FFmpeg decoder ($codecName).")
            }
        }

        val inputData = Util.castNonNull(inputBuffer.data)
        val inputSize = inputData.limit()

        val sendPacketResult = ExtendedFfmpegNativeBridge.ffmpegSendPacket(
            nativeContext,
            inputData,
            inputSize,
            inputBuffer.timeUs
        )
        if (sendPacketResult == VIDEO_DECODER_ERROR_INVALID_DATA) {
            outputBuffer.shouldBeSkipped = true
            return null
        } else if (sendPacketResult == VIDEO_DECODER_ERROR_OTHER) {
            return ExtendedFfmpegNativeBridge.newDecoderException("ffmpegDecode sendPacket error ($codecName)")
        }

        val decodeOnly = !isAtLeastOutputStartTimeUs(inputBuffer.timeUs)
        val receiveFrameResult = ExtendedFfmpegNativeBridge.ffmpegReceiveFrame(
            nativeContext,
            outputMode,
            outputBuffer,
            decodeOnly
        )
        if (receiveFrameResult == VIDEO_DECODER_ERROR_OTHER) {
            return ExtendedFfmpegNativeBridge.newDecoderException("ffmpegDecode receiveFrame error ($codecName)")
        }
        if (receiveFrameResult == VIDEO_DECODER_ERROR_INVALID_DATA) {
            outputBuffer.shouldBeSkipped = true
        }
        if (! outputBuffer.shouldBeSkipped) {
            outputBuffer.format = inputBuffer.format ?: format
        }
        return null
    }

    fun renderToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        if (outputBuffer.mode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV) {
            throw ExtendedFfmpegNativeBridge.newDecoderException("Invalid output mode (${outputBuffer.mode}).")
        }
        val rc = ExtendedFfmpegNativeBridge.ffmpegRenderFrame(
            nativeContext,
            surface,
            outputBuffer,
            outputBuffer.width,
            outputBuffer.height
        )
        if (rc == VIDEO_DECODER_ERROR_OTHER) {
            throw ExtendedFfmpegNativeBridge.newDecoderException("Buffer render error ($rc)")
        }
    }

    override fun release() {
        super.release()
        if (nativeContext != 0L) {
            ExtendedFfmpegNativeBridge.ffmpegRelease(nativeContext)
            nativeContext = 0L
        }
    }

    companion object {
        private const val VIDEO_DECODER_SUCCESS = 0
        private const val VIDEO_DECODER_ERROR_INVALID_DATA = -1
        private const val VIDEO_DECODER_ERROR_OTHER = -2
        private const val VIDEO_DECODER_ERROR_READ_FRAME = -3
    }
}
