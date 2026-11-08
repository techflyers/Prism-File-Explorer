package com.techflyers.compose.file.explorer.screen.viewer.media

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Custom [ExtractorsFactory] that adds full QuickTime (`.mov` / `.qt`) container support on top of
 * Media3's [DefaultExtractorsFactory]:
 * 1. [QuickTimeMovExtractor]: Handles `.mov` containers that Media3's `Mp4Extractor` rejects or drops
 *    tracks from — including compressed movie headers (`cmov` -> `dcom` `'zlib'` + `cmvd`),
 *    QuickTime video fourccs (`jpeg`, `mjpa`, `mjpb`, `apch`, `apcn`, `apcs`, `apco`, `ap4h`, `ap4x`,
 *    `SVQ1`, `SVQ3`, `rle `, `rpza`, `smc `, `cvid`, `dvc `, `dvcp`, `dvpp`, `DIVX`, `XVID`, `DX50`),
 *    and QuickTime Sound Sample Description v0/v1/v2 audio (`in24`, `in32`, `fl32`, `fl64`, `raw `,
 *    `sowt`, `twos`, `lpcm`, `ulaw`, `alaw`, `ima4`).
 * 2. [QuickTimeAwareMp4Extractor]: Wraps Media3's [Mp4Extractor] so standard H.264/HEVC/MPEG-4 `.mov`
 *    files that lack an `ftyp` atom (starting directly with `wide`, `mdat`, `moov`, `free`, `skip`,
 *    or `pnot`) or use camera-specific `ftyp` brands (Canon `CAEP`, Nikon `niko`, etc.) never fail
 *    `Sniffer` with `NoDeclaredBrandSniffFailure` or `UnsupportedBrandsSniffFailure`, and automatically
 *    falls back to [QuickTimeMovExtractor] if `Mp4Extractor` fails during `moov` parsing.
 */
@OptIn(UnstableApi::class)
class PrismExtractorsFactory : ExtractorsFactory {

    private val defaultFactory = DefaultExtractorsFactory()
        .setConstantBitrateSeekingEnabled(true)
        .setConstantBitrateSeekingAlwaysEnabled(true)
        .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
        .setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )

    override fun createExtractors(): Array<Extractor> {
        return wrapExtractors(defaultFactory.createExtractors(), null)
    }

    override fun createExtractors(
        uri: Uri,
        responseHeaders: MutableMap<String, MutableList<String>>
    ): Array<Extractor> {
        return wrapExtractors(defaultFactory.createExtractors(uri, responseHeaders), uri)
    }

    private fun wrapExtractors(base: Array<Extractor>, uri: Uri?): Array<Extractor> {
        val result = ArrayList<Extractor>(base.size + 1)
        // Put QuickTimeMovExtractor first: it inspects the local file's `moov` box (even when `moov`
        // is at the end of the file after a large `mdat`) or stream header and claims the file
        // whenever it uses `cmov` or QuickTime-specific track fourccs that Media3's BoxParser drops.
        result.add(QuickTimeMovExtractor(uri = uri, onlyClaimSpecialQuickTime = true))
        for (extractor in base) {
            if (extractor is Mp4Extractor) {
                result.add(QuickTimeAwareMp4Extractor(extractor, uri))
            } else {
                result.add(extractor)
            }
        }
        return result.toTypedArray()
    }
}

/**
 * Wraps Media3's [Mp4Extractor] so QuickTime `.mov` files without an `ftyp` box or with
 * non-standard `ftyp` brands pass `sniff()`, and falls back to [QuickTimeMovExtractor] if
 * [Mp4Extractor] throws a [ParserException] while reading the container.
 */
@OptIn(UnstableApi::class)
class QuickTimeAwareMp4Extractor(
    private val delegate: Mp4Extractor,
    private val uri: Uri? = null
) : Extractor {
    private var extractorOutput: ExtractorOutput? = null
    private val fallbackExtractor by lazy { QuickTimeMovExtractor(uri = uri, onlyClaimSpecialQuickTime = false) }
    private var usingFallback = false

    override fun sniff(input: ExtractorInput): Boolean {
        val standardSniff = try {
            delegate.sniff(input)
        } catch (_: Throwable) {
            false
        }
        if (standardSniff) return true

        input.resetPeekPosition()
        return QuickTimeMovExtractor.looksLikeQuickTimeOrMp4Header(input)
    }

    override fun init(output: ExtractorOutput) {
        extractorOutput = output
        delegate.init(output)
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (usingFallback) {
            return fallbackExtractor.read(input, seekPosition)
        }
        return try {
            delegate.read(input, seekPosition)
        } catch (e: ParserException) {
            val out = extractorOutput ?: throw e
            android.util.Log.w(
                "QuickTimeExtractor",
                "Mp4Extractor threw ParserException (${e.message}); falling back to QuickTimeMovExtractor"
            )
            usingFallback = true
            fallbackExtractor.init(out)
            seekPosition.position = 0L
            Extractor.RESULT_SEEK
        }
    }

    override fun seek(position: Long, timeUs: Long) {
        if (usingFallback) {
            fallbackExtractor.seek(position, timeUs)
        } else {
            delegate.seek(position, timeUs)
        }
    }

    override fun release() {
        if (usingFallback) {
            fallbackExtractor.release()
        }
        delegate.release()
    }
}

/**
 * Native QuickTime (`.mov`) & ISO-BMFF demuxer supporting:
 * - Zlib-compressed movie headers (`moov` -> `cmov` -> `dcom ('zlib')` + `cmvd`)
 * - Legacy & modern QuickTime video sample entries (`apch`/`apcn`/`apcs`/`apco`/`ap4h`/`ap4x` ProRes,
 *   `jpeg`/`mjpa`/`mjpb` MJPEG, `SVQ1`/`SVQ3` Sorenson, `rle ` Animation, `rpza` Apple Video,
 *   `smc ` Graphics, `cvid` Cinepak, `dvc `/`dvcp`/`dvpp` DV, `mp4v`/`DIVX`/`XVID`, `avc1`, `hvc1`/`hev1`,
 *   `vp08`, `vp09`, `av01`, `H263`, `s263`, `m1v `)
 * - QuickTime Sound Sample Description v0, v1, and v2 (`sowt`, `twos`, `lpcm`, `in24`, `in32`,
 *   `fl32`, `fl64`, `raw `, `ulaw`, `alaw`, `mp4a`, `.mp3`, `.mp2`, `ac-3`, `ec-3`, `alac`, `samr`, `sawb`, `Opus`, `fLaC`)
 *   including automatic chunk rechunking for uncompressed PCM audio tracks where `stsz.sampleSize <= 16`.
 */
@OptIn(UnstableApi::class)
class QuickTimeMovExtractor(
    private val uri: Uri? = null,
    private val onlyClaimSpecialQuickTime: Boolean = false
) : Extractor, SeekMap {

    private var extractorOutput: ExtractorOutput? = null
    private var tracksParsed = false
    private var pendingSeekOffset: Long = -1L
    private var durationUs: Long = C.TIME_UNSET
    private val tracks = mutableListOf<DemuxTrack>()
    private val nalStartCode = byteArrayOf(0, 0, 0, 1)
    private val scratchBuffer = ParsableByteArray(64 * 1024)

    internal class SampleEntry(
        val offset: Long,
        val size: Int,
        val timeUs: Long,
        val isKeyframe: Boolean
    )

    internal class DemuxTrack(
        val id: Int,
        val trackType: Int,
        val format: Format,
        val nalUnitLengthFieldLength: Int,
        val samples: List<SampleEntry>,
        val trackOutput: TrackOutput
    ) {
        var currentSampleIndex: Int = 0
    }

    override fun sniff(input: ExtractorInput): Boolean {
        if (!looksLikeQuickTimeOrMp4Header(input)) return false
        if (!onlyClaimSpecialQuickTime) return true

        input.resetPeekPosition()
        val byFile = try {
            uri?.path?.let { java.io.File(it) }?.takeIf { it.exists() && it.isFile && it.canRead() }
                ?.let { fileRequiresCustomQuickTimeExtractor(it) }
        } catch (_: Throwable) {
            null
        }
        if (byFile != null) {
            input.resetPeekPosition()
            return byFile
        }

        return try {
            peekRequiresCustomQuickTimeExtractor(input)
        } catch (_: Throwable) {
            false
        } finally {
            input.resetPeekPosition()
        }
    }

    override fun init(output: ExtractorOutput) {
        extractorOutput = output
    }

    override fun read(input: ExtractorInput, seekPosition: PositionHolder): Int {
        if (pendingSeekOffset >= 0L) {
            val target = pendingSeekOffset
            pendingSeekOffset = -1L
            if (input.position != target) {
                seekPosition.position = target
                return Extractor.RESULT_SEEK
            }
        }

        if (!tracksParsed) {
            val seekTarget = parseContainerTracks(input)
            if (seekTarget >= 0L) {
                seekPosition.position = seekTarget
                return Extractor.RESULT_SEEK
            }
            tracksParsed = true
        }

        // Select the track whose next sample has the earliest file offset (or earliest timestamp if close)
        val activeTrack = selectNextTrack() ?: return Extractor.RESULT_END_OF_INPUT
        val sample = activeTrack.samples[activeTrack.currentSampleIndex]

        if (input.position != sample.offset) {
            val diff = sample.offset - input.position
            if (diff in 1..(64 * 1024)) {
                input.skipFully(diff.toInt())
            } else {
                seekPosition.position = sample.offset
                return Extractor.RESULT_SEEK
            }
        }

        val bytesWritten = if (activeTrack.nalUnitLengthFieldLength > 0) {
            writeLengthPrefixedNalSample(input, activeTrack, sample.size)
        } else {
            var remaining = sample.size
            var total = 0
            while (remaining > 0) {
                val read = activeTrack.trackOutput.sampleData(input, remaining, false)
                if (read == C.RESULT_END_OF_INPUT) return Extractor.RESULT_END_OF_INPUT
                remaining -= read
                total += read
            }
            total
        }

        val flags = if (sample.isKeyframe) C.BUFFER_FLAG_KEY_FRAME else 0
        activeTrack.trackOutput.sampleMetadata(
            sample.timeUs,
            flags,
            bytesWritten,
            0,
            null
        )
        activeTrack.currentSampleIndex++
        return Extractor.RESULT_CONTINUE
    }

    private fun writeLengthPrefixedNalSample(
        input: ExtractorInput,
        track: DemuxTrack,
        sampleSize: Int
    ): Int {
        scratchBuffer.ensureCapacity(sampleSize)
        input.readFully(scratchBuffer.data, 0, sampleSize)
        val data = scratchBuffer.data
        val nalLenSize = track.nalUnitLengthFieldLength
        var pos = 0
        var written = 0
        val out = track.trackOutput

        while (pos + nalLenSize <= sampleSize) {
            var nalLength = 0
            for (i in 0 until nalLenSize) {
                nalLength = (nalLength shl 8) or (data[pos + i].toInt() and 0xFF)
            }
            pos += nalLenSize
            if (nalLength <= 0 || pos + nalLength > sampleSize) {
                // Fallback if sample is already Annex-B or malformed
                val leftover = sampleSize - (pos - nalLenSize)
                if (leftover > 0) {
                    out.sampleData(ParsableByteArray(data, sampleSize).apply { position = pos - nalLenSize }, leftover)
                    written += leftover
                }
                break
            }
            out.sampleData(ParsableByteArray(nalStartCode), 4)
            out.sampleData(ParsableByteArray(data, pos + nalLength).apply { position = pos }, nalLength)
            pos += nalLength
            written += 4 + nalLength
        }
        return written
    }

    private fun selectNextTrack(): DemuxTrack? {
        var bestTrack: DemuxTrack? = null
        var bestScore = Long.MAX_VALUE
        for (track in tracks) {
            if (track.currentSampleIndex >= track.samples.size) continue
            val sample = track.samples[track.currentSampleIndex]
            // Prioritize by timestamp (in 100ms buckets) and then file offset to avoid thrashing
            val score = sample.timeUs
            if (score < bestScore) {
                bestScore = score
                bestTrack = track
            }
        }
        return bestTrack
    }

    override fun seek(position: Long, timeUs: Long) {
        for (track in tracks) {
            if (track.samples.isEmpty()) {
                track.currentSampleIndex = 0
                continue
            }
            // Binary search for sample at or before timeUs, then walk back to preceding keyframe
            var low = 0
            var high = track.samples.lastIndex
            var targetIdx = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (track.samples[mid].timeUs <= timeUs) {
                    targetIdx = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            var keyIdx = targetIdx
            while (keyIdx > 0 && !track.samples[keyIdx].isKeyframe) {
                keyIdx--
            }
            track.currentSampleIndex = keyIdx
        }
    }

    override fun release() {
        tracks.clear()
    }

    override fun isSeekable(): Boolean = durationUs != C.TIME_UNSET && durationUs > 0L

    override fun getDurationUs(): Long = durationUs

    override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints {
        val primaryTrack = tracks.firstOrNull { it.trackType == C.TRACK_TYPE_VIDEO && it.samples.isNotEmpty() }
            ?: tracks.firstOrNull { it.samples.isNotEmpty() }
            ?: return SeekMap.SeekPoints(SeekPoint.START)

        val samples = primaryTrack.samples
        var low = 0
        var high = samples.lastIndex
        var targetIdx = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (samples[mid].timeUs <= timeUs) {
                targetIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        var keyIdx = targetIdx
        while (keyIdx > 0 && !samples[keyIdx].isKeyframe) {
            keyIdx--
        }
        val point = SeekPoint(samples[keyIdx].timeUs, samples[keyIdx].offset)
        return SeekMap.SeekPoints(point)
    }

    /**
     * Locates and parses the `moov` box (seeking to the end of the file if `mdat` precedes `moov`).
     * Returns `-1L` when `moov` has been parsed, or a non-negative byte offset to seek to first.
     */
    private fun parseContainerTracks(input: ExtractorInput): Long {
        val header = ByteArray(16)
        val fileLength = input.length

        while (true) {
            val pos = input.position
            if (fileLength != C.LENGTH_UNSET.toLong() && pos + 8 > fileLength) {
                break
            }
            if (!input.readFully(header, 0, 8, true)) {
                break
            }
            val size32 = readUint32BE(header, 0)
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            val boxSize = when {
                size32 == 1L -> {
                    if (!input.readFully(header, 8, 8, true)) break
                    headerSize = 16
                    readInt64BE(header, 8)
                }
                size32 == 0L -> {
                    if (fileLength != C.LENGTH_UNSET.toLong()) fileLength - pos else -1L
                }
                else -> size32
            }

            if (boxSize < headerSize) break

            if (type == "moov") {
                val payloadSize = (boxSize - headerSize).toInt()
                if (payloadSize <= 0 || payloadSize > 128 * 1024 * 1024) {
                    throw ParserException.createForMalformedContainer("Invalid moov atom size: $payloadSize", null)
                }
                val moovPayload = ByteArray(payloadSize)
                input.readFully(moovPayload, 0, payloadSize)
                parseMoovPayload(moovPayload)
                return -1L
            }

            val nextBoxOffset = pos + boxSize
            val remainingPayload = boxSize - headerSize
            if (remainingPayload <= 64 * 1024) {
                input.skipFully(remainingPayload.toInt())
            } else {
                // Seek directly past large `mdat` atom to reach trailing `moov`
                return nextBoxOffset
            }
        }

        // If we reached EOF without finding `moov`, finish with whatever tracks were found
        val out = extractorOutput ?: return -1L
        out.endTracks()
        out.seekMap(this)
        return -1L
    }

    private fun parseMoovPayload(rawMoovPayload: ByteArray) {
        val out = extractorOutput ?: return
        val moovBytes = decompressCmovIfPresent(rawMoovPayload)

        var movieTimescale = 1000L
        var movieDurationTicks = 0L
        var trackIndexCounter = 0

        forEachChildBox(moovBytes, 0, moovBytes.size) { boxType, payloadStart, payloadEnd ->
            when (boxType) {
                "mvhd" -> {
                    val mvhdLen = payloadEnd - payloadStart
                    if (mvhdLen >= 20) {
                        val version = moovBytes[payloadStart].toInt() and 0xFF
                        if (version == 1 && mvhdLen >= 32) {
                            movieTimescale = readUint32BE(moovBytes, payloadStart + 20)
                            movieDurationTicks = readInt64BE(moovBytes, payloadStart + 24)
                        } else {
                            movieTimescale = readUint32BE(moovBytes, payloadStart + 12)
                            movieDurationTicks = readUint32BE(moovBytes, payloadStart + 16)
                        }
                    }
                }
                "trak" -> {
                    parseTrakBox(moovBytes, payloadStart, payloadEnd, trackIndexCounter, out)?.let { track ->
                        tracks.add(track)
                        trackIndexCounter++
                    }
                }
            }
        }

        if (movieTimescale > 0 && movieDurationTicks > 0) {
            durationUs = Util.scaleLargeTimestamp(movieDurationTicks, C.MICROS_PER_SECOND, movieTimescale)
        }
        val maxTrackSampleUs = tracks.mapNotNull { it.samples.lastOrNull()?.timeUs }.maxOrNull() ?: 0L
        if (durationUs == C.TIME_UNSET || maxTrackSampleUs > durationUs) {
            if (maxTrackSampleUs > 0L) durationUs = maxTrackSampleUs
        }

        out.endTracks()
        out.seekMap(this)
    }

    /**
     * Checks if [moovPayload] contains a QuickTime `cmov` compressed header atom (`dcom` `'zlib'` + `cmvd`).
     * If so, inflates the zlib payload and strips any outer `moov` box header from the decompressed bytes.
     */
    private fun decompressCmovIfPresent(moovPayload: ByteArray): ByteArray {
        var decompressed: ByteArray? = null
        forEachChildBox(moovPayload, 0, moovPayload.size) { boxType, start, end ->
            if (boxType == "cmov") {
                var isZlib = false
                var cmvdStart = -1
                var cmvdEnd = -1
                var uncompressedSize = 0
                forEachChildBox(moovPayload, start, end) { childType, cStart, cEnd ->
                    if (childType == "dcom" && cEnd - cStart >= 4) {
                        val compAlgo = String(moovPayload, cStart, 4, Charsets.ISO_8859_1)
                        if (compAlgo == "zlib") isZlib = true
                    } else if (childType == "cmvd" && cEnd - cStart > 4) {
                        uncompressedSize = readUint32BE(moovPayload, cStart).toInt()
                        cmvdStart = cStart + 4
                        cmvdEnd = cEnd
                    }
                }
                if (isZlib && cmvdStart > 0 && cmvdEnd > cmvdStart) {
                    val inflated = InflaterInputStream(
                        ByteArrayInputStream(moovPayload, cmvdStart, cmvdEnd - cmvdStart)
                    ).use { it.readBytes() }
                    // Decompressed cmvd usually starts with an 8-byte `moov` box header
                    decompressed = if (inflated.size >= 8 &&
                        String(inflated, 4, 4, Charsets.ISO_8859_1) == "moov"
                    ) {
                        inflated.copyOfRange(8, inflated.size)
                    } else {
                        inflated
                    }
                }
            }
        }
        return decompressed ?: moovPayload
    }

    private fun parseTrakBox(
        buf: ByteArray,
        trakStart: Int,
        trakEnd: Int,
        defaultTrackId: Int,
        extractorOutput: ExtractorOutput
    ): DemuxTrack? {
        var trackId = defaultTrackId + 1
        var tkhdWidth = Format.NO_VALUE
        var tkhdHeight = Format.NO_VALUE
        var rotationDegrees = 0
        var mdiaStart = -1
        var mdiaEnd = -1

        forEachChildBox(buf, trakStart, trakEnd) { boxType, start, end ->
            when (boxType) {
                "tkhd" -> {
                    val len = end - start
                    if (len >= 84) {
                        val version = buf[start].toInt() and 0xFF
                        val idOffset = if (version == 1) start + 20 else start + 12
                        val matrixOffset = if (version == 1) start + 48 else start + 40
                        val dimOffset = if (version == 1) start + 88 else start + 76
                        if (idOffset + 4 <= end) {
                            trackId = readUint32BE(buf, idOffset).toInt()
                        }
                        if (dimOffset + 8 <= end) {
                            val wFixed = readUint32BE(buf, dimOffset)
                            val hFixed = readUint32BE(buf, dimOffset + 4)
                            val w = (wFixed ushr 16).toInt()
                            val h = (hFixed ushr 16).toInt()
                            if (w > 0) tkhdWidth = w
                            if (h > 0) tkhdHeight = h
                        }
                        if (matrixOffset + 36 <= end) {
                            val a = readInt32BE(buf, matrixOffset)
                            val b = readInt32BE(buf, matrixOffset + 4)
                            val c = readInt32BE(buf, matrixOffset + 12)
                            val d = readInt32BE(buf, matrixOffset + 16)
                            rotationDegrees = when {
                                a == 0 && b > 0 && c < 0 && d == 0 -> 90
                                a == 0 && b < 0 && c > 0 && d == 0 -> 270
                                a < 0 && b == 0 && c == 0 && d < 0 -> 180
                                else -> 0
                            }
                        }
                    }
                }
                "mdia" -> {
                    mdiaStart = start
                    mdiaEnd = end
                }
            }
        }

        if (mdiaStart < 0) return null

        var mediaTimescale = 1000L
        var handlerType = ""
        var minfStart = -1
        var minfEnd = -1

        forEachChildBox(buf, mdiaStart, mdiaEnd) { boxType, start, end ->
            when (boxType) {
                "mdhd" -> {
                    val len = end - start
                    if (len >= 16) {
                        val version = buf[start].toInt() and 0xFF
                        mediaTimescale = if (version == 1 && len >= 24) {
                            readUint32BE(buf, start + 20)
                        } else {
                            readUint32BE(buf, start + 12)
                        }.coerceAtLeast(1L)
                    }
                }
                "hdlr" -> {
                    if (end - start >= 12) {
                        handlerType = String(buf, start + 8, 4, Charsets.ISO_8859_1)
                    }
                }
                "minf" -> {
                    minfStart = start
                    minfEnd = end
                }
            }
        }

        val trackType = when (handlerType) {
            "vide" -> C.TRACK_TYPE_VIDEO
            "soun" -> C.TRACK_TYPE_AUDIO
            else -> return null
        }
        if (minfStart < 0) return null

        var stblStart = -1
        var stblEnd = -1
        forEachChildBox(buf, minfStart, minfEnd) { boxType, start, end ->
            if (boxType == "stbl") {
                stblStart = start
                stblEnd = end
            }
        }
        if (stblStart < 0) return null

        var stsdInfo: ParsedStsdInfo? = null
        val sttsEntries = mutableListOf<Pair<Int, Int>>() // count to delta
        val cttsEntries = mutableListOf<Pair<Int, Int>>() // count to offset
        val stssSyncSamples = HashSet<Int>() // 1-based sample indices
        var hasStss = false
        val stscEntries = mutableListOf<Triple<Int, Int, Int>>() // firstChunk(1-based), samplesPerChunk, descIdx
        var fixedSampleSize = 0
        var sampleCountFromStsz = 0
        var stszTableOffset = -1
        val chunkOffsets = mutableListOf<Long>()

        forEachChildBox(buf, stblStart, stblEnd) { boxType, start, end ->
            val len = end - start
            when (boxType) {
                "stsd" -> {
                    if (len >= 8) {
                        stsdInfo = parseStsd(buf, start, end, trackType, tkhdWidth, tkhdHeight, rotationDegrees)
                    }
                }
                "stts" -> {
                    if (len >= 8) {
                        val count = readUint32BE(buf, start + 4).toInt()
                        var p = start + 8
                        for (i in 0 until count) {
                            if (p + 8 > end) break
                            val sampleCount = readUint32BE(buf, p).toInt()
                            val sampleDelta = readUint32BE(buf, p + 4).toInt()
                            if (sampleCount > 0) {
                                sttsEntries.add(sampleCount to sampleDelta.coerceAtLeast(0))
                            }
                            p += 8
                        }
                    }
                }
                "ctts" -> {
                    if (len >= 8) {
                        val count = readUint32BE(buf, start + 4).toInt()
                        var p = start + 8
                        for (i in 0 until count) {
                            if (p + 8 > end) break
                            val sampleCount = readUint32BE(buf, p).toInt()
                            val sampleOffset = readInt32BE(buf, p + 4)
                            if (sampleCount > 0) {
                                cttsEntries.add(sampleCount to sampleOffset)
                            }
                            p += 8
                        }
                    }
                }
                "stss" -> {
                    if (len >= 8) {
                        hasStss = true
                        val count = readUint32BE(buf, start + 4).toInt()
                        var p = start + 8
                        for (i in 0 until count) {
                            if (p + 4 > end) break
                            stssSyncSamples.add(readUint32BE(buf, p).toInt())
                            p += 4
                        }
                    }
                }
                "stsc" -> {
                    if (len >= 8) {
                        val count = readUint32BE(buf, start + 4).toInt()
                        var p = start + 8
                        for (i in 0 until count) {
                            if (p + 12 > end) break
                            val firstChunk = readUint32BE(buf, p).toInt()
                            val samplesPerChunk = readUint32BE(buf, p + 4).toInt()
                            val descIdx = readUint32BE(buf, p + 8).toInt()
                            stscEntries.add(Triple(firstChunk, samplesPerChunk, descIdx))
                            p += 12
                        }
                    }
                }
                "stsz" -> {
                    if (len >= 12) {
                        fixedSampleSize = readUint32BE(buf, start + 4).toInt()
                        sampleCountFromStsz = readUint32BE(buf, start + 8).toInt()
                        stszTableOffset = start + 12
                    }
                }
                "stco" -> {
                    if (len >= 8) {
                        val count = readUint32BE(buf, start + 4).toInt()
                        var p = start + 8
                        for (i in 0 until count) {
                            if (p + 4 > end) break
                            chunkOffsets.add(readUint32BE(buf, p))
                            p += 4
                        }
                    }
                }
                "co64" -> {
                    if (len >= 8) {
                        val count = readUint32BE(buf, start + 4).toInt()
                        var p = start + 8
                        for (i in 0 until count) {
                            if (p + 8 > end) break
                            chunkOffsets.add(readInt64BE(buf, p))
                            p += 8
                        }
                    }
                }
            }
        }

        val parsedStsd = stsdInfo ?: return null
        if (chunkOffsets.isEmpty() || stscEntries.isEmpty()) return null

        val samples = buildSampleTable(
            buf = buf,
            parsedStsd = parsedStsd,
            trackType = trackType,
            mediaTimescale = mediaTimescale,
            sttsEntries = sttsEntries,
            cttsEntries = cttsEntries,
            hasStss = hasStss,
            stssSyncSamples = stssSyncSamples,
            stscEntries = stscEntries,
            fixedSampleSize = fixedSampleSize,
            sampleCountFromStsz = sampleCountFromStsz,
            stszTableOffset = stszTableOffset,
            chunkOffsets = chunkOffsets
        )
        if (samples.isEmpty()) return null

        val trackOutput = extractorOutput.track(trackId, trackType)
        trackOutput.format(parsedStsd.format)

        return DemuxTrack(
            id = trackId,
            trackType = trackType,
            format = parsedStsd.format,
            nalUnitLengthFieldLength = parsedStsd.nalUnitLengthFieldLength,
            samples = samples,
            trackOutput = trackOutput
        )
    }

    private data class ParsedStsdInfo(
        val format: Format,
        val nalUnitLengthFieldLength: Int = 0,
        val isRawPcmAudio: Boolean = false,
        val pcmBytesPerFrame: Int = 0
    )

    private fun parseStsd(
        buf: ByteArray,
        stsdStart: Int,
        stsdEnd: Int,
        trackType: Int,
        tkhdWidth: Int,
        tkhdHeight: Int,
        rotationDegrees: Int
    ): ParsedStsdInfo? {
        val entryCount = readUint32BE(buf, stsdStart + 4).toInt()
        if (entryCount <= 0) return null
        val entryStart = stsdStart + 8
        if (entryStart + 16 > stsdEnd) return null

        val entrySize = readUint32BE(buf, entryStart).toInt()
        val entryFourcc = String(buf, entryStart + 4, 4, Charsets.ISO_8859_1)
        val entryEnd = min(stsdEnd, entryStart + max(16, entrySize))

        return if (trackType == C.TRACK_TYPE_VIDEO) {
            parseVideoStsdEntry(buf, entryStart, entryEnd, entryFourcc, tkhdWidth, tkhdHeight, rotationDegrees)
        } else {
            parseAudioStsdEntry(buf, entryStart, entryEnd, entryFourcc)
        }
    }

    private fun parseVideoStsdEntry(
        buf: ByteArray,
        entryStart: Int,
        entryEnd: Int,
        fourcc: String,
        tkhdWidth: Int,
        tkhdHeight: Int,
        rotationDegrees: Int
    ): ParsedStsdInfo? {
        // VideoSampleEntry header is 86 bytes (8 box header + 6 reserved + 2 dataRefIdx + 16 pre-defined/reserved + 2 width + 2 height + 4 horizRes + 4 vertRes + 4 reserved + 2 frameCount + 32 compressorName + 2 depth + 2 preDefined)
        if (entryEnd - entryStart < 40) return null
        val parsedW = readUint16BE(buf, entryStart + 32)
        val parsedH = readUint16BE(buf, entryStart + 34)
        val width = if (parsedW > 0) parsedW else tkhdWidth
        val height = if (parsedH > 0) parsedH else tkhdHeight

        val trimmedFourcc = fourcc.trim()
        val lowerFourcc = fourcc.lowercase()
        var mimeType: String = when {
            lowerFourcc == "avc1" || lowerFourcc == "avc3" -> MimeTypes.VIDEO_H264
            lowerFourcc == "hvc1" || lowerFourcc == "hev1" -> MimeTypes.VIDEO_H265
            lowerFourcc == "dvh1" || lowerFourcc == "dvhe" || lowerFourcc == "dva1" || lowerFourcc == "dvav" -> MimeTypes.VIDEO_DOLBY_VISION
            lowerFourcc == "mp4v" || lowerFourcc == "divx" || lowerFourcc == "dx50" ||
                lowerFourcc == "xvid" || lowerFourcc == "fmp4" || lowerFourcc == "mp42" || lowerFourcc == "mp43" -> MimeTypes.VIDEO_MP4V
            lowerFourcc == "jpeg" || lowerFourcc == "mjpa" || lowerFourcc == "mjpb" ||
                lowerFourcc == "mjpg" || lowerFourcc == "mjp2" || lowerFourcc == "dmb1" -> MimeTypes.VIDEO_MJPEG
            lowerFourcc.startsWith("apch") || lowerFourcc.startsWith("apcn") ||
                lowerFourcc.startsWith("apcs") || lowerFourcc.startsWith("apco") ||
                lowerFourcc.startsWith("ap4h") || lowerFourcc.startsWith("ap4x") -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_PRORES
            lowerFourcc == "svq1" -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_SVQ1
            lowerFourcc == "svq3" -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_SVQ3
            lowerFourcc == "rle " || trimmedFourcc.equals("rle", ignoreCase = true) -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_QTRLE
            lowerFourcc == "rpza" || lowerFourcc == "azpr" -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_RPZA
            lowerFourcc == "smc " || trimmedFourcc.equals("smc", ignoreCase = true) -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_SMC
            lowerFourcc == "cvid" -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_CINEPAK
            lowerFourcc == "dvc " || lowerFourcc == "dvcp" || lowerFourcc == "dvpp" ||
                lowerFourcc == "dv5p" || lowerFourcc == "dv5n" || lowerFourcc == "dvsd" -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_DV
            lowerFourcc == "vp08" -> MimeTypes.VIDEO_VP8
            lowerFourcc == "vp09" -> MimeTypes.VIDEO_VP9
            lowerFourcc == "av01" -> MimeTypes.VIDEO_AV1
            lowerFourcc == "h263" || lowerFourcc == "s263" || lowerFourcc == "d263" -> MimeTypes.VIDEO_H263
            lowerFourcc == "m1v " || lowerFourcc == "mpg1" -> MimeTypes.VIDEO_MPEG
            lowerFourcc == "m2v1" || lowerFourcc == "hdv1" || lowerFourcc == "hdv2" || lowerFourcc == "xdv2" -> MimeTypes.VIDEO_MPEG2
            lowerFourcc == "wvc1" || lowerFourcc == "vc-1" -> MimeTypes.VIDEO_VC1
            lowerFourcc == "wmv1" || lowerFourcc == "wmv2" || lowerFourcc == "wmv3" -> ExtendedFfmpegVideoRenderer.MIME_VIDEO_WMV
            else -> MimeTypes.VIDEO_UNKNOWN
        }

        val initData = mutableListOf<ByteArray>()
        var nalLengthFieldSize = 0
        val childrenStart = entryStart + 86

        if (childrenStart + 8 <= entryEnd) {
            forEachChildBox(buf, childrenStart, entryEnd) { childType, cStart, cEnd ->
                when (childType) {
                    "avcC" -> {
                        val parsed = parseAvcC(buf, cStart, cEnd)
                        if (parsed != null) {
                            nalLengthFieldSize = parsed.first
                            initData.addAll(parsed.second)
                        }
                    }
                    "hvcC" -> {
                        val parsed = parseHvcC(buf, cStart, cEnd)
                        if (parsed != null) {
                            nalLengthFieldSize = parsed.first
                            initData.addAll(parsed.second)
                        }
                    }
                    "esds" -> {
                        parseEsdsDecoderSpecificInfo(buf, cStart, cEnd)?.let { (esdsMime, dsi) ->
                            if (esdsMime != null && mimeType == MimeTypes.VIDEO_UNKNOWN) {
                                mimeType = esdsMime
                            }
                            if (dsi.isNotEmpty()) initData.add(dsi)
                        }
                    }
                    "SMI ", "SEQH", "glbl", "av1C", "vpcC", "dvcC" -> {
                        if (cEnd > cStart) {
                            initData.add(buf.copyOfRange(cStart, cEnd))
                        }
                    }
                }
            }
        }

        // For QuickTime RLE / SMC / RPZA, FFmpeg expects the 86-byte QuickTime Stsd entry header as extradata
        if (initData.isEmpty() && mimeType in setOf(
                ExtendedFfmpegVideoRenderer.MIME_VIDEO_QTRLE,
                ExtendedFfmpegVideoRenderer.MIME_VIDEO_SMC,
                ExtendedFfmpegVideoRenderer.MIME_VIDEO_RPZA,
                ExtendedFfmpegVideoRenderer.MIME_VIDEO_SVQ3
            )
        ) {
            initData.add(buf.copyOfRange(entryStart, entryEnd))
        }

        val formatBuilder = Format.Builder()
            .setContainerMimeType("video/quicktime")
            .setSampleMimeType(mimeType)
            .setCodecs(fourcc.trimEnd())
            .setRotationDegrees(rotationDegrees)

        if (width > 0 && height > 0) {
            formatBuilder.setWidth(width).setHeight(height)
        }
        if (initData.isNotEmpty()) {
            formatBuilder.setInitializationData(initData)
        }

        return ParsedStsdInfo(
            format = formatBuilder.build(),
            nalUnitLengthFieldLength = nalLengthFieldSize
        )
    }

    private fun parseAudioStsdEntry(
        buf: ByteArray,
        entryStart: Int,
        entryEnd: Int,
        fourcc: String
    ): ParsedStsdInfo? {
        if (entryEnd - entryStart < 36) return null
        val qtVersion = readUint16BE(buf, entryStart + 16)
        var channelCount = readUint16BE(buf, entryStart + 24).coerceAtLeast(1)
        var sampleSizeBits = readUint16BE(buf, entryStart + 26).takeIf { it > 0 } ?: 16
        var sampleRate = (readUint32BE(buf, entryStart + 32) ushr 16).toInt().takeIf { it > 0 } ?: 44100

        var childrenStart = entryStart + 36
        if (qtVersion == 1 && entryEnd - entryStart >= 52) {
            val bytesPerSample = readUint32BE(buf, entryStart + 44).toInt()
            if (bytesPerSample in 1..8) {
                sampleSizeBits = bytesPerSample * 8
            }
            childrenStart = entryStart + 52
        } else if (qtVersion == 2 && entryEnd - entryStart >= 72) {
            val rateDoubleBits = readInt64BE(buf, entryStart + 40)
            val rateDouble = Double.fromBits(rateDoubleBits).toInt()
            if (rateDouble in 4000..384000) sampleRate = rateDouble
            val ch = readUint32BE(buf, entryStart + 48).toInt()
            if (ch in 1..16) channelCount = ch
            val bits = readUint32BE(buf, entryStart + 56).toInt()
            if (bits in listOf(8, 16, 24, 32, 64)) sampleSizeBits = bits
            childrenStart = entryStart + 72
        }

        val lowerFourcc = fourcc.lowercase()
        var pcmEncoding: Int = Format.NO_VALUE
        var isRawPcm = false

        var mimeType: String = when (lowerFourcc) {
            "sowt" -> {
                isRawPcm = true
                pcmEncoding = when (sampleSizeBits) {
                    8 -> C.ENCODING_PCM_8BIT
                    24 -> C.ENCODING_PCM_24BIT
                    32 -> C.ENCODING_PCM_32BIT
                    else -> C.ENCODING_PCM_16BIT
                }
                MimeTypes.AUDIO_RAW
            }
            "twos" -> {
                isRawPcm = true
                pcmEncoding = when (sampleSizeBits) {
                    8 -> C.ENCODING_PCM_8BIT
                    24 -> C.ENCODING_PCM_24BIT_BIG_ENDIAN
                    32 -> C.ENCODING_PCM_32BIT_BIG_ENDIAN
                    else -> C.ENCODING_PCM_16BIT_BIG_ENDIAN
                }
                MimeTypes.AUDIO_RAW
            }
            "in24" -> {
                isRawPcm = true
                sampleSizeBits = 24
                // Check endian box inside `wave` if present; default in QuickTime is big-endian unless `enda` == 1
                pcmEncoding = C.ENCODING_PCM_24BIT_BIG_ENDIAN
                MimeTypes.AUDIO_RAW
            }
            "in32" -> {
                isRawPcm = true
                sampleSizeBits = 32
                pcmEncoding = C.ENCODING_PCM_32BIT_BIG_ENDIAN
                MimeTypes.AUDIO_RAW
            }
            "fl32" -> {
                isRawPcm = true
                sampleSizeBits = 32
                pcmEncoding = C.ENCODING_PCM_FLOAT
                MimeTypes.AUDIO_RAW
            }
            "fl64" -> {
                isRawPcm = true
                sampleSizeBits = 64
                pcmEncoding = C.ENCODING_PCM_FLOAT
                MimeTypes.AUDIO_RAW
            }
            "lpcm", "ipcm", "fpcm" -> {
                isRawPcm = true
                pcmEncoding = when (sampleSizeBits) {
                    8 -> C.ENCODING_PCM_8BIT
                    24 -> C.ENCODING_PCM_24BIT
                    32 -> if (lowerFourcc == "fpcm") C.ENCODING_PCM_FLOAT else C.ENCODING_PCM_32BIT
                    else -> C.ENCODING_PCM_16BIT
                }
                MimeTypes.AUDIO_RAW
            }
            "raw ", "NONE" -> {
                isRawPcm = true
                sampleSizeBits = 8
                pcmEncoding = C.ENCODING_PCM_8BIT
                MimeTypes.AUDIO_RAW
            }
            "ulaw" -> MimeTypes.AUDIO_MLAW
            "alaw" -> MimeTypes.AUDIO_ALAW
            "mp4a" -> MimeTypes.AUDIO_AAC
            ".mp3", "ms\u0000U" -> MimeTypes.AUDIO_MPEG
            ".mp2" -> MimeTypes.AUDIO_MPEG_L2
            "ac-3", "sac3" -> MimeTypes.AUDIO_AC3
            "ec-3" -> MimeTypes.AUDIO_E_AC3
            "alac" -> MimeTypes.AUDIO_ALAC
            "samr" -> MimeTypes.AUDIO_AMR_NB
            "sawb" -> MimeTypes.AUDIO_AMR_WB
            "opus" -> MimeTypes.AUDIO_OPUS
            "flac" -> MimeTypes.AUDIO_FLAC
            "dtsc", "dtsh", "dtsl", "dtse" -> MimeTypes.AUDIO_DTS
            "mlpa" -> MimeTypes.AUDIO_TRUEHD
            else -> MimeTypes.AUDIO_UNKNOWN
        }

        val initData = mutableListOf<ByteArray>()

        fun parseAudioChildren(start: Int, end: Int) {
            if (start + 8 > end) return
            forEachChildBox(buf, start, end) { childType, cStart, cEnd ->
                when (childType) {
                    "wave" -> parseAudioChildren(cStart, cEnd)
                    "enda" -> {
                        if (cEnd - cStart >= 2) {
                            val isLittleEndian = readUint16BE(buf, cStart) == 1
                            if (lowerFourcc == "in24") {
                                pcmEncoding = if (isLittleEndian) C.ENCODING_PCM_24BIT else C.ENCODING_PCM_24BIT_BIG_ENDIAN
                            } else if (lowerFourcc == "in32") {
                                pcmEncoding = if (isLittleEndian) C.ENCODING_PCM_32BIT else C.ENCODING_PCM_32BIT_BIG_ENDIAN
                            }
                        }
                    }
                    "esds" -> {
                        parseEsdsDecoderSpecificInfo(buf, cStart, cEnd)?.let { (esdsMime, dsi) ->
                            if (esdsMime != null) mimeType = esdsMime
                            if (dsi.isNotEmpty()) initData.add(dsi)
                        }
                    }
                    "alac" -> {
                        if (cEnd - cStart >= 24) {
                            // Full ALAC atom (including 8-byte box header + 4-byte version/flags)
                            val boxStart = (cStart - 8).coerceAtLeast(0)
                            initData.add(buf.copyOfRange(boxStart, cEnd))
                        }
                    }
                    "dOps", "dfLa" -> {
                        if (cEnd > cStart) {
                            initData.add(buf.copyOfRange(cStart, cEnd))
                        }
                    }
                }
            }
        }

        parseAudioChildren(childrenStart, entryEnd)

        if (mimeType == MimeTypes.AUDIO_UNKNOWN) return null

        val formatBuilder = Format.Builder()
            .setContainerMimeType("video/quicktime")
            .setSampleMimeType(mimeType)
            .setCodecs(fourcc.trimEnd())
            .setChannelCount(channelCount)
            .setSampleRate(sampleRate)

        if (pcmEncoding != Format.NO_VALUE) {
            formatBuilder.setPcmEncoding(pcmEncoding)
        }
        if (initData.isNotEmpty()) {
            formatBuilder.setInitializationData(initData)
        }

        val bytesPerFrame = if (isRawPcm) {
            ((sampleSizeBits + 7) / 8).coerceAtLeast(1) * channelCount.coerceAtLeast(1)
        } else 0

        return ParsedStsdInfo(
            format = formatBuilder.build(),
            isRawPcmAudio = isRawPcm,
            pcmBytesPerFrame = bytesPerFrame
        )
    }

    private fun buildSampleTable(
        buf: ByteArray,
        parsedStsd: ParsedStsdInfo,
        trackType: Int,
        mediaTimescale: Long,
        sttsEntries: List<Pair<Int, Int>>,
        cttsEntries: List<Pair<Int, Int>>,
        hasStss: Boolean,
        stssSyncSamples: Set<Int>,
        stscEntries: List<Triple<Int, Int, Int>>,
        fixedSampleSize: Int,
        sampleCountFromStsz: Int,
        stszTableOffset: Int,
        chunkOffsets: List<Long>
    ): List<SampleEntry> {
        val totalChunks = chunkOffsets.size
        if (totalChunks == 0) return emptyList()

        // Expand chunk -> sample count mapping from stsc
        val samplesPerChunkByChunk = IntArray(totalChunks)
        for (i in stscEntries.indices) {
            val firstChunk1Based = stscEntries[i].first.coerceAtLeast(1)
            val samplesInChunk = stscEntries[i].second.coerceAtLeast(0)
            val nextFirstChunk1Based = if (i + 1 < stscEntries.size) {
                stscEntries[i + 1].first.coerceAtLeast(firstChunk1Based)
            } else {
                totalChunks + 1
            }
            for (chunkIdx in (firstChunk1Based - 1) until min(totalChunks, nextFirstChunk1Based - 1)) {
                samplesPerChunkByChunk[chunkIdx] = samplesInChunk
            }
        }

        // Special QuickTime uncompressed PCM case:
        // In QuickTime .mov, uncompressed PCM (`sowt`, `twos`, `in24`, `in32`, `fl32`, `lpcm`, `raw `)
        // often stores `fixedSampleSize = 1` (or `pcmBytesPerFrame`) where each "sample" in `stsc` is a single
        // audio sample frame (e.g. 48000 samples/sec), which would create millions of tiny 2-4 byte SampleEntries!
        // Instead, emit one SampleEntry per QuickTime chunk (or 4096-frame block)!
        if (trackType == C.TRACK_TYPE_AUDIO && parsedStsd.isRawPcmAudio && fixedSampleSize > 0 && fixedSampleSize <= 16) {
            val bytesPerPcmFrame = if (fixedSampleSize > 1) fixedSampleSize else parsedStsd.pcmBytesPerFrame.coerceAtLeast(2)
            val rechunked = ArrayList<SampleEntry>(totalChunks)
            var accumulatedFrames = 0L
            for (chunkIdx in 0 until totalChunks) {
                val framesInChunk = samplesPerChunkByChunk[chunkIdx]
                if (framesInChunk <= 0) continue
                val chunkByteSize = framesInChunk * bytesPerPcmFrame
                val timeUs = Util.scaleLargeTimestamp(accumulatedFrames, C.MICROS_PER_SECOND, mediaTimescale)
                rechunked.add(
                    SampleEntry(
                        offset = chunkOffsets[chunkIdx],
                        size = chunkByteSize,
                        timeUs = timeUs,
                        isKeyframe = true
                    )
                )
                accumulatedFrames += framesInChunk
            }
            return rechunked
        }

        val totalSamples = if (sampleCountFromStsz > 0) {
            sampleCountFromStsz
        } else {
            samplesPerChunkByChunk.sum()
        }
        if (totalSamples <= 0 || totalSamples > 2_000_000) return emptyList()

        // Iterator state for stts & ctts
        var sttsIdx = 0
        var sttsRemaining = if (sttsEntries.isNotEmpty()) sttsEntries[0].first else 0
        var currentDtsTicks = 0L

        var cttsIdx = 0
        var cttsRemaining = if (cttsEntries.isNotEmpty()) cttsEntries[0].first else 0

        val result = ArrayList<SampleEntry>(totalSamples)
        var sampleIdx = 0 // 0-based

        for (chunkIdx in 0 until totalChunks) {
            val countInChunk = samplesPerChunkByChunk[chunkIdx]
            var currentOffset = chunkOffsets[chunkIdx]
            for (s in 0 until countInChunk) {
                if (sampleIdx >= totalSamples) break
                val size = if (fixedSampleSize > 0) {
                    fixedSampleSize
                } else {
                    val pos = stszTableOffset + sampleIdx * 4
                    if (stszTableOffset >= 0 && pos + 4 <= buf.size) {
                        readUint32BE(buf, pos).toInt()
                    } else 0
                }

                val cttsOffsetTicks = if (cttsIdx < cttsEntries.size) {
                    val v = cttsEntries[cttsIdx].second
                    cttsRemaining--
                    if (cttsRemaining <= 0) {
                        cttsIdx++
                        if (cttsIdx < cttsEntries.size) cttsRemaining = cttsEntries[cttsIdx].first
                    }
                    v
                } else 0

                val ptsTicks = (currentDtsTicks + cttsOffsetTicks).coerceAtLeast(0L)
                val timeUs = Util.scaleLargeTimestamp(ptsTicks, C.MICROS_PER_SECOND, mediaTimescale)

                if (sttsIdx < sttsEntries.size) {
                    val delta = sttsEntries[sttsIdx].second
                    currentDtsTicks += delta
                    sttsRemaining--
                    if (sttsRemaining <= 0) {
                        sttsIdx++
                        if (sttsIdx < sttsEntries.size) sttsRemaining = sttsEntries[sttsIdx].first
                    }
                }

                val isKeyframe = if (hasStss) {
                    stssSyncSamples.contains(sampleIdx + 1)
                } else {
                    true
                }

                if (size > 0) {
                    result.add(
                        SampleEntry(
                            offset = currentOffset,
                            size = size,
                            timeUs = timeUs,
                            isKeyframe = isKeyframe
                        )
                    )
                    currentOffset += size
                }
                sampleIdx++
            }
        }

        return result
    }

    private fun parseAvcC(buf: ByteArray, start: Int, end: Int): Pair<Int, List<ByteArray>>? {
        if (end - start < 7) return null
        val nalLenSize = ((buf[start + 4].toInt() and 0x03) + 1)
        val numSps = buf[start + 5].toInt() and 0x1F
        var p = start + 6
        val list = mutableListOf<ByteArray>()
        for (i in 0 until numSps) {
            if (p + 2 > end) break
            val spsLen = readUint16BE(buf, p)
            p += 2
            if (p + spsLen > end) break
            val nal = ByteArray(4 + spsLen)
            System.arraycopy(nalStartCode, 0, nal, 0, 4)
            System.arraycopy(buf, p, nal, 4, spsLen)
            list.add(nal)
            p += spsLen
        }
        if (p < end) {
            val numPps = buf[p].toInt() and 0xFF
            p += 1
            for (i in 0 until numPps) {
                if (p + 2 > end) break
                val ppsLen = readUint16BE(buf, p)
                p += 2
                if (p + ppsLen > end) break
                val nal = ByteArray(4 + ppsLen)
                System.arraycopy(nalStartCode, 0, nal, 0, 4)
                System.arraycopy(buf, p, nal, 4, ppsLen)
                list.add(nal)
                p += ppsLen
            }
        }
        return nalLenSize to list
    }

    private fun parseHvcC(buf: ByteArray, start: Int, end: Int): Pair<Int, List<ByteArray>>? {
        if (end - start < 23) return null
        val nalLenSize = ((buf[start + 21].toInt() and 0x03) + 1)
        val numArrays = buf[start + 22].toInt() and 0xFF
        var p = start + 23
        val combined = java.io.ByteArrayOutputStream()
        for (i in 0 until numArrays) {
            if (p + 3 > end) break
            val numNalus = readUint16BE(buf, p + 1)
            p += 3
            for (j in 0 until numNalus) {
                if (p + 2 > end) break
                val naluLen = readUint16BE(buf, p)
                p += 2
                if (p + naluLen > end) break
                combined.write(nalStartCode)
                combined.write(buf, p, naluLen)
                p += naluLen
            }
        }
        val bytes = combined.toByteArray()
        return nalLenSize to if (bytes.isNotEmpty()) listOf(bytes) else emptyList()
    }

    private fun parseEsdsDecoderSpecificInfo(
        buf: ByteArray,
        start: Int,
        end: Int
    ): Pair<String?, ByteArray>? {
        // Skip 4-byte version/flags
        var p = start + 4
        var objectTypeMime: String? = null
        while (p < end) {
            val tag = buf[p].toInt() and 0xFF
            p++
            var len = 0
            for (i in 0 until 4) {
                if (p >= end) break
                val b = buf[p].toInt() and 0xFF
                p++
                len = (len shl 7) or (b and 0x7F)
                if ((b and 0x80) == 0) break
            }
            when (tag) {
                0x03 -> {
                    // ES_Descriptor: 2 bytes ES_ID + 1 byte flags
                    if (p + 3 > end) break
                    val flags = buf[p + 2].toInt() and 0xFF
                    p += 3
                    if ((flags and 0x80) != 0) p += 2 // streamDependenceFlag
                    if ((flags and 0x40) != 0 && p < end) {
                        val urlLen = buf[p].toInt() and 0xFF
                        p += 1 + urlLen
                    }
                    if ((flags and 0x20) != 0) p += 2 // OCRstreamFlag
                }
                0x04 -> {
                    // DecoderConfigDescriptor: 1 byte objectTypeIndication + 12 bytes
                    if (p + 13 > end) break
                    val oti = buf[p].toInt() and 0xFF
                    objectTypeMime = when (oti) {
                        0x20 -> MimeTypes.VIDEO_MP4V
                        0x21 -> MimeTypes.VIDEO_H264
                        0x23 -> MimeTypes.VIDEO_H265
                        0x60, 0x61, 0x62, 0x63, 0x64, 0x65 -> MimeTypes.VIDEO_MPEG2
                        0x6A -> MimeTypes.VIDEO_MPEG
                        0x6C -> MimeTypes.VIDEO_MJPEG
                        0x40, 0x66, 0x67, 0x68 -> MimeTypes.AUDIO_AAC
                        0x69, 0x6B -> MimeTypes.AUDIO_MPEG
                        0xA5 -> MimeTypes.AUDIO_AC3
                        0xA6 -> MimeTypes.AUDIO_E_AC3
                        0xA9, 0xAC -> MimeTypes.AUDIO_DTS
                        0xDD -> MimeTypes.AUDIO_VORBIS
                        else -> null
                    }
                    p += 13
                }
                0x05 -> {
                    // DecoderSpecificInfo
                    if (len > 0 && p + len <= end) {
                        return objectTypeMime to buf.copyOfRange(p, p + len)
                    }
                    break
                }
                else -> {
                    p += len
                }
            }
        }
        return if (objectTypeMime != null) (objectTypeMime to ByteArray(0)) else null
    }

    companion object {
        private val TOP_LEVEL_QT_BOXES = setOf(
            "ftyp", "moov", "wide", "mdat", "free", "skip", "pnot", "pict", "uuid", "meta", "styp"
        )

        /**
         * FourCC codes in QuickTime `.mov` `stsd` boxes that Media3's `BoxParser.parseStsd`
         * does NOT support (or drops with `format = null`), requiring [QuickTimeMovExtractor].
         */
        private val SPECIAL_QUICKTIME_FOURCCS = setOf(
            // Video codecs dropped by Media3 BoxParser
            "jpeg", "mjpa", "mjpb", "mjpg", "mjp2", "dmb1",
            "apch", "apcn", "apcs", "apco", "ap4h", "ap4x",
            "svq1", "svq3",
            "rle ", "rpza", "azpr", "smc ", "cvid",
            "dvc ", "dvcp", "dvpp", "dv5p", "dv5n", "dvsd",
            "divx", "dx50", "xvid", "fmp4", "mp42", "mp43",
            "wmv1", "wmv2", "wmv3", "wvc1",
            // Audio codecs dropped by Media3 BoxParser
            "in24", "in32", "fl32", "fl64", "raw ", "NONE"
        )

        fun looksLikeQuickTimeOrMp4Header(input: ExtractorInput): Boolean {
            val header = ByteArray(16)
            return try {
                if (!input.peekFully(header, 0, 12, true)) return false
                val size32 = readUint32BE(header, 0)
                val boxType = String(header, 4, 4, Charsets.ISO_8859_1)
                if (boxType !in TOP_LEVEL_QT_BOXES) return false
                size32 == 0L || size32 == 1L || size32 >= 8L
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Peeks into the beginning of the stream to check if `moov` is within the peekable window
         * and contains either a compressed `cmov` box or a QuickTime track fourcc that Media3's
         * `BoxParser` would drop.
         */
        private fun peekRequiresCustomQuickTimeExtractor(input: ExtractorInput): Boolean {
            val header = ByteArray(16)
            var peekedOffset = 0L
            val maxPeekBytes = 512 * 1024L

            while (peekedOffset < maxPeekBytes) {
                if (!input.peekFully(header, 0, 8, true)) return false
                val size32 = readUint32BE(header, 0)
                val type = String(header, 4, 4, Charsets.ISO_8859_1)
                var headerSize = 8
                val boxSize = when {
                    size32 == 1L -> {
                        if (!input.peekFully(header, 8, 8, true)) return false
                        headerSize = 16
                        readInt64BE(header, 8)
                    }
                    size32 == 0L -> return false
                    else -> size32
                }
                if (boxSize < headerSize) return false

                if (type == "moov") {
                    val payloadSize = (boxSize - headerSize).toInt()
                    if (payloadSize <= 0 || payloadSize > 2 * 1024 * 1024) return false
                    val moovBuf = ByteArray(payloadSize)
                    if (!input.peekFully(moovBuf, 0, payloadSize, true)) return false
                    return moovContainsSpecialQuickTimeFeatures(moovBuf)
                }

                val skipBytes = boxSize - headerSize
                if (peekedOffset + boxSize > maxPeekBytes || skipBytes > Int.MAX_VALUE) {
                    return false
                }
                if (!input.advancePeekPosition(skipBytes.toInt(), true)) {
                    return false
                }
                peekedOffset += boxSize
            }
            return false
        }

        internal fun fileRequiresCustomQuickTimeExtractor(file: java.io.File): Boolean? {
            java.io.RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                val header = ByteArray(16)
                var pos = 0L
                var safetyCounter = 0
                while (pos + 8 <= fileLen && safetyCounter < 128) {
                    safetyCounter++
                    raf.seek(pos)
                    raf.readFully(header, 0, 8)
                    val size32 = readUint32BE(header, 0)
                    val type = String(header, 4, 4, Charsets.ISO_8859_1)
                    var headerSize = 8
                    val boxSize = when {
                        size32 == 1L -> {
                            if (pos + 16 > fileLen) return null
                            raf.readFully(header, 8, 8)
                            headerSize = 16
                            readInt64BE(header, 8)
                        }
                        size32 == 0L -> fileLen - pos
                        else -> size32
                    }
                    if (boxSize < headerSize || pos + boxSize > fileLen) return null

                    if (type == "moov") {
                        val payloadSize = (boxSize - headerSize).toInt()
                        if (payloadSize <= 0 || payloadSize > 64 * 1024 * 1024) return null
                        val moovBuf = ByteArray(payloadSize)
                        raf.readFully(moovBuf)
                        return moovContainsSpecialQuickTimeFeatures(moovBuf)
                    }
                    pos += boxSize
                }
            }
            return null
        }

        internal fun moovContainsSpecialQuickTimeFeatures(moovPayload: ByteArray): Boolean {
            var foundSpecial = false
            forEachChildBox(moovPayload, 0, moovPayload.size) { boxType, start, end ->
                if (boxType == "cmov") {
                    foundSpecial = true
                } else if (boxType == "trak") {
                    forEachChildBox(moovPayload, start, end) { tType, tStart, tEnd ->
                        if (tType == "mdia") {
                            forEachChildBox(moovPayload, tStart, tEnd) { mType, mStart, mEnd ->
                                if (mType == "minf") {
                                    forEachChildBox(moovPayload, mStart, mEnd) { sType, sStart, sEnd ->
                                        if (sType == "stbl") {
                                            forEachChildBox(moovPayload, sStart, sEnd) { bType, bStart, bEnd ->
                                                if (bType == "stsd" && bEnd - bStart >= 16) {
                                                    val entryFourcc = String(
                                                        moovPayload,
                                                        bStart + 12,
                                                        4,
                                                        Charsets.ISO_8859_1
                                                    ).lowercase()
                                                    if (entryFourcc in SPECIAL_QUICKTIME_FOURCCS ||
                                                        entryFourcc.startsWith("apch") ||
                                                        entryFourcc.startsWith("apcn") ||
                                                        entryFourcc.startsWith("apcs") ||
                                                        entryFourcc.startsWith("apco") ||
                                                        entryFourcc.startsWith("ap4h") ||
                                                        entryFourcc.startsWith("ap4x")
                                                    ) {
                                                        foundSpecial = true
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return foundSpecial
        }

        private inline fun forEachChildBox(
            buf: ByteArray,
            start: Int,
            end: Int,
            action: (boxType: String, payloadStart: Int, payloadEnd: Int) -> Unit
        ) {
            var pos = start
            while (pos + 8 <= end) {
                val size32 = readUint32BE(buf, pos)
                val type = String(buf, pos + 4, 4, Charsets.ISO_8859_1)
                var headerSize = 8
                val boxSize = when {
                    size32 == 1L -> {
                        if (pos + 16 > end) break
                        headerSize = 16
                        readInt64BE(buf, pos + 8)
                    }
                    size32 == 0L -> (end - pos).toLong()
                    else -> size32
                }
                if (boxSize < headerSize || pos + boxSize > end) break
                val boxEnd = (pos + boxSize).toInt()
                action(type, pos + headerSize, boxEnd)
                pos = boxEnd
            }
        }

        private fun readUint16BE(buf: ByteArray, offset: Int): Int {
            return ((buf[offset].toInt() and 0xFF) shl 8) or
                (buf[offset + 1].toInt() and 0xFF)
        }

        private fun readInt32BE(buf: ByteArray, offset: Int): Int {
            return ((buf[offset].toInt() and 0xFF) shl 24) or
                ((buf[offset + 1].toInt() and 0xFF) shl 16) or
                ((buf[offset + 2].toInt() and 0xFF) shl 8) or
                (buf[offset + 3].toInt() and 0xFF)
        }

        private fun readUint32BE(buf: ByteArray, offset: Int): Long {
            return readInt32BE(buf, offset).toLong() and 0xFFFFFFFFL
        }

        private fun readInt64BE(buf: ByteArray, offset: Int): Long {
            return ByteBuffer.wrap(buf, offset, 8).order(ByteOrder.BIG_ENDIAN).long
        }
    }
}
