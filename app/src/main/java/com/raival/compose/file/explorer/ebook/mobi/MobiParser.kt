package com.raival.compose.file.explorer.ebook.mobi

import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import com.raival.compose.file.explorer.ebook.EbookTocItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * Pure Kotlin/Java parser for MOBI, AZW, AZW3, and PRC reflowable e-books.
 * Handles PalmDOC record offsets, PalmDOC LZ77 decompression, EXTH metadata,
 * image records, chapter extraction, and table of contents.
 */
class MobiBook(
    override val file: File,
    override val title: String,
    override val author: String?,
    override val coverBytes: ByteArray?,
    override val chapters: List<EbookChapter>,
    override val toc: List<EbookTocItem>,
    private val imageRecords: Map<Int, ByteArray>
) : EbookDocument {

    override fun getChapterHtml(chapter: EbookChapter): String {
        return chapter.htmlContent ?: ""
    }

    override fun getEntryStream(relativePath: String): InputStream? {
        val clean = relativePath.substringAfterLast('/')
        val regex = Regex("""mobi_img_(\d+)\.\w+""")
        val match = regex.find(clean) ?: return null
        val imgIndex = match.groupValues[1].toIntOrNull() ?: return null
        val bytes = imageRecords[imgIndex] ?: return null
        return ByteArrayInputStream(bytes)
    }

    override fun close() {
        // In-memory structures, no active file handles held open
    }
}

object MobiParser {

    private const val COMPRESSION_NONE = 1
    private const val COMPRESSION_PALMDOC = 2
    private const val COMPRESSION_HUFF = 17480

    fun parse(file: File): MobiBook {
        val raw = file.readBytes()
        if (raw.size < 78) {
            throw IllegalArgumentException("File too small to be a valid MOBI/PalmDOC book")
        }

        // 1. Palm Database Header
        val numRecords = readUInt16(raw, 76)
        if (numRecords <= 0 || 78 + numRecords * 8 > raw.size) {
            throw IllegalArgumentException("Corrupted MOBI header: record table out of bounds")
        }

        val recordOffsets = ArrayList<Int>(numRecords)
        for (i in 0 until numRecords) {
            val offsetPos = 78 + i * 8
            val offset = readInt32(raw, offsetPos)
            recordOffsets.add(offset)
        }

        // Record 0 contains PalmDOC + MOBI headers
        val mobiOffset = recordOffsets[0]
        if (mobiOffset + 16 > raw.size) {
            throw IllegalArgumentException("Invalid MOBI record 0 offset")
        }

        val compression = readUInt16(raw, mobiOffset)
        val encryption = readUInt16(raw, mobiOffset + 12)

        // MOBI Header (starts at mobiOffset + 16)
        var bookTitle = file.nameWithoutExtension
        var author: String? = null
        var firstImageIndex = numRecords
        var firstContentIndex = 1
        var lastContentIndex = numRecords
        val exthHeaders = mutableMapOf<Int, ByteArray>()

        var encodingCharset = Charsets.UTF_8

        if (mobiOffset + 16 + 4 <= raw.size &&
            raw.copyOfRange(mobiOffset + 16, mobiOffset + 20).toString(Charsets.US_ASCII) == "MOBI"
        ) {
            val mobiHeaderBase = mobiOffset + 16
            val codepage = readInt32(raw, mobiHeaderBase + 12)
            encodingCharset = if (codepage == 1252) {
                try { Charset.forName("windows-1252") } catch (_: Exception) { Charsets.ISO_8859_1 }
            } else {
                Charsets.UTF_8
            }

            val fullNameOffset = readInt32(raw, mobiHeaderBase + 68)
            val fullNameLen = readInt32(raw, mobiHeaderBase + 72)
            if (fullNameOffset > 0 && fullNameLen > 0 && mobiOffset + fullNameOffset + fullNameLen <= raw.size) {
                val name = String(raw, mobiOffset + fullNameOffset, fullNameLen, encodingCharset).trim()
                if (name.isNotEmpty()) {
                    bookTitle = name
                }
            }

            firstImageIndex = readInt32(raw, mobiHeaderBase + 92).coerceAtMost(numRecords)
            val exthFlags = readInt32(raw, mobiHeaderBase + 112)
            firstContentIndex = readUInt16(raw, mobiHeaderBase + 176).coerceAtLeast(1)
            lastContentIndex = readUInt16(raw, mobiHeaderBase + 178).coerceAtMost(numRecords)

            if ((exthFlags and 0x40) != 0) {
                parseExth(raw, mobiOffset, exthHeaders)
            }
        }

        // Author & metadata from EXTH
        exthHeaders[100]?.let { author = String(it, encodingCharset).trim() }

        // Extract Images
        val imageRecords = mutableMapOf<Int, ByteArray>()
        if (firstImageIndex in 1 until numRecords) {
            for (idx in firstImageIndex until numRecords) {
                val start = recordOffsets[idx]
                val end = if (idx + 1 < numRecords) recordOffsets[idx + 1] else raw.size
                if (start in 0..end && end <= raw.size) {
                    val imgBytes = raw.copyOfRange(start, end)
                    if (isImage(imgBytes)) {
                        val relativeImgIndex = idx - firstImageIndex + 1
                        imageRecords[relativeImgIndex] = imgBytes
                    }
                }
            }
        }

        // Cover detection
        var coverBytes: ByteArray? = null
        val coverOffsetBytes = exthHeaders[201] ?: exthHeaders[202]
        if (coverOffsetBytes != null) {
            val relCoverIdx = byteArrayToInt(coverOffsetBytes) + 1
            coverBytes = imageRecords[relCoverIdx]
        }
        if (coverBytes == null && imageRecords.isNotEmpty()) {
            coverBytes = imageRecords.values.firstOrNull { isJpeg(it) } ?: imageRecords.values.first()
        }

        // 2. Decompress text content
        val textBytesStream = ByteArrayOutputStream()
        val endContent = minOf(lastContentIndex, firstImageIndex, numRecords)
        for (i in firstContentIndex until endContent) {
            val start = recordOffsets[i]
            val end = if (i + 1 < numRecords) recordOffsets[i + 1] else raw.size
            if (start >= end || end > raw.size) continue

            val coded = raw.copyOfRange(start, end)
            val decoded = when (compression) {
                COMPRESSION_NONE -> coded
                COMPRESSION_PALMDOC -> lz77Decompress(coded)
                COMPRESSION_HUFF -> "<div>Huffman compression not supported</div>".toByteArray(encodingCharset)
                else -> "<div>Compression $compression not supported</div>".toByteArray(encodingCharset)
            }

            // Skip index records (starting with "INDX")
            if (decoded.size >= 4 && decoded[0] == 'I'.code.toByte() && decoded[1] == 'N'.code.toByte() &&
                decoded[2] == 'D'.code.toByte() && decoded[3] == 'X'.code.toByte()
            ) {
                continue
            }

            for (b in decoded) {
                if (b != 0.toByte()) {
                    textBytesStream.write(b.toInt())
                }
            }
        }

        var fullHtml = textBytesStream.toString(encodingCharset.name())

        // 3. Post-process HTML and rewrite image tags
        fullHtml = rewriteMobiImages(fullHtml, imageRecords)

        // 4. Split into chapters
        val (chapters, toc) = splitIntoChapters(fullHtml, bookTitle)

        return MobiBook(
            file = file,
            title = bookTitle,
            author = author,
            coverBytes = coverBytes,
            chapters = chapters,
            toc = toc,
            imageRecords = imageRecords
        )
    }

    private fun parseExth(raw: ByteArray, mobiOffset: Int, headers: MutableMap<Int, ByteArray>) {
        val exthIdx = indexOfBytes(raw, "EXTH".toByteArray(Charsets.US_ASCII), mobiOffset)
        if (exthIdx == -1 || exthIdx + 12 > raw.size) return

        val count = readInt32(raw, exthIdx + 8)
        var offset = exthIdx + 12
        for (i in 0 until count) {
            if (offset + 8 > raw.size) break
            val rType = readInt32(raw, offset)
            val rLen = readInt32(raw, offset + 4)
            if (rLen < 8 || offset + rLen > raw.size) break
            val data = raw.copyOfRange(offset + 8, offset + rLen)
            headers[rType] = data
            offset += rLen
        }
    }

    private fun rewriteMobiImages(html: String, imageRecords: Map<Int, ByteArray>): String {
        // Rewrite <img recindex="123" ...> to <img src="mobi_img_123.jpg" ...>
        val recindexPattern = Pattern.compile("""<img([^>]*?)\s+recindex=["']?(\d+)["']?([^>]*?)>""", Pattern.CASE_INSENSITIVE)
        var result = recindexPattern.matcher(html).replaceAll("<img$1 src=\"mobi_img_$2.jpg\"$3>")

        // Rewrite kindle:embed:0001
        val embedPattern = Pattern.compile("""src=["']kindle:embed:0*(\d+)["']""", Pattern.CASE_INSENSITIVE)
        result = embedPattern.matcher(result).replaceAll("src=\"mobi_img_$1.jpg\"")

        // Also ensure images have responsive styling
        result = result.replace("<img ", "<img style=\"max-width:100%;height:auto;\" ")
        return result
    }

    private fun splitIntoChapters(html: String, bookTitle: String): Pair<List<EbookChapter>, List<EbookTocItem>> {
        // Detect page breaks or chapter splits
        val splitRegex = Regex("""(?i)<(?:mbp:pagebreak|div\s+[^>]*class=["'][^"']*pagebreak[^"']*["']|h1\b|h2\b)""")
        val rawParts = html.split(Regex("""(?i)<mbp:pagebreak\s*/?>"""))

        val finalHtmlParts = mutableListOf<String>()
        if (rawParts.size > 1) {
            for (p in rawParts) {
                if (p.isNotBlank()) finalHtmlParts.add(p)
            }
        }

        // If no <mbp:pagebreak>, check if file is large (>128KB) and split by <h2> or <h1> or chunks
        if (finalHtmlParts.isEmpty()) {
            if (html.length > 120_000 && html.contains("<h1", ignoreCase = true)) {
                val chunks = html.split(Regex("""(?i)(?=<h1\b)"""))
                for (chunk in chunks) {
                    if (chunk.isNotBlank()) finalHtmlParts.add(chunk)
                }
            } else if (html.length > 120_000 && html.contains("<h2", ignoreCase = true)) {
                val chunks = html.split(Regex("""(?i)(?=<h2\b)"""))
                for (chunk in chunks) {
                    if (chunk.isNotBlank()) finalHtmlParts.add(chunk)
                }
            } else {
                finalHtmlParts.add(html)
            }
        }

        val chapters = mutableListOf<EbookChapter>()
        val toc = mutableListOf<EbookTocItem>()

        val titleExtractor = Pattern.compile("""<h[1-3][^>]*>(.*?)</h[1-3]>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

        for ((idx, content) in finalHtmlParts.withIndex()) {
            var chapterTitle = "Section ${idx + 1}"
            val matcher = titleExtractor.matcher(content)
            if (matcher.find()) {
                val rawTitle = matcher.group(1).replace(Regex("<[^>]*>"), "").trim()
                if (rawTitle.isNotBlank() && rawTitle.length < 80) {
                    chapterTitle = rawTitle
                }
            }

            val wrappedHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { margin: 0; padding: 16px; word-wrap: break-word; }
                        img { max-width: 100%; height: auto; }
                    </style>
                </head>
                <body>
                    $content
                </body>
                </html>
            """.trimIndent()

            val chapter = EbookChapter(
                id = "mobi_ch_$idx",
                title = chapterTitle,
                htmlContent = wrappedHtml
            )
            chapters.add(chapter)
            toc.add(EbookTocItem(title = chapterTitle, href = "mobi_ch_$idx", chapterIndex = idx))
        }

        return Pair(chapters, toc)
    }

    /**
     * PalmDOC LZ77 Decompressor.
     */
    fun lz77Decompress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size * 2)
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i++].toInt() and 0xFF
            when {
                b == 0x00 -> {
                    out.write(b)
                }
                b <= 0x08 -> {
                    // Copy b uncompressed bytes
                    for (j in 0 until b) {
                        if (i + j < bytes.size) {
                            out.write(bytes[i + j].toInt() and 0xFF)
                        }
                    }
                    i += b
                }
                b <= 0x7F -> {
                    // Single literal byte
                    out.write(b)
                }
                b <= 0xBF -> {
                    // 2-byte distance and length sequence
                    if (i < bytes.size) {
                        val b2 = bytes[i++].toInt() and 0xFF
                        val combined = (b shl 8) or b2
                        val length = (combined and 0x0007) + 3
                        val distance = (combined shr 3) and 0x7FF

                        val rawOut = out.toByteArray()
                        for (j in 0 until length) {
                            val pos = rawOut.size - distance + (j % distance)
                            if (pos in rawOut.indices) {
                                out.write(rawOut[pos].toInt() and 0xFF)
                            }
                        }
                    }
                }
                else -> {
                    // b >= 0xC0: space + character with high bit cleared
                    out.write(' '.code)
                    out.write(b xor 0x80)
                }
            }
        }
        return out.toByteArray()
    }

    private fun readUInt16(bytes: ByteArray, offset: Int): Int {
        if (offset + 1 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun byteArrayToInt(buf: ByteArray): Int {
        var total = 0
        for (b in buf) {
            total = (total shl 8) or (b.toInt() and 0xFF)
        }
        return total
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray, startOffset: Int = 0): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        val max = source.size - target.size
        for (i in startOffset..max) {
            var match = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    private fun isJpeg(bytes: ByteArray): Boolean {
        return bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8
    }

    private fun isImage(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        // JPEG
        if (b0 == 0xFF && b1 == 0xD8) return true
        // PNG
        if (b0 == 0x89 && b1 == 0x50 && bytes[2].toInt() and 0xFF == 0x4E && bytes[3].toInt() and 0xFF == 0x47) return true
        // GIF
        if (b0 == 0x47 && b1 == 0x49 && bytes[2].toInt() and 0xFF == 0x46) return true
        return false
    }
}
