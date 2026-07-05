package com.raival.compose.file.explorer.common

import java.io.File
import java.io.FileInputStream

/**
 * Detected MIME type result with confidence level.
 */
data class DetectedMimeType(
    val mimeType: String,
    val isText: Boolean,
    val confidence: Float // 0.0 to 1.0
)

/**
 * Content-based MIME type detection using magic bytes and text heuristics.
 * Handles files with missing or rare extensions.
 */
object MimeTypeDetector {

    // Magic byte signatures: bytes -> mime type
    private val MAGIC_SIGNATURES = listOf(
        // Images
        MagicSignature(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), "image/png"),
        MagicSignature(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "image/jpeg"),
        MagicSignature(byteArrayOf(0x47, 0x49, 0x46, 0x38), "image/gif"),
        MagicSignature(byteArrayOf(0x52, 0x49, 0x46, 0x46), "image/webp", offset = 0, extraCheck = { bytes ->
            bytes.size >= 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
                    bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
        }),
        MagicSignature(byteArrayOf(0x42, 0x4D), "image/bmp"),

        // Documents
        MagicSignature(byteArrayOf(0x25, 0x50, 0x44, 0x46), "application/pdf"),

        // Archives — ZIP, RAR, 7Z, GZ, BZ2, XZ already present above
        MagicSignature(byteArrayOf(0x50, 0x4B, 0x03, 0x04), "application/zip"),
        MagicSignature(byteArrayOf(0x52, 0x61, 0x72, 0x21), "application/x-rar-compressed"),
        MagicSignature(byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C), "application/x-7z-compressed"),
        MagicSignature(byteArrayOf(0x1F, 0x8B.toByte()), "application/gzip"),
        MagicSignature(byteArrayOf(0x42, 0x5A, 0x68), "application/x-bzip2"),
        MagicSignature(byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00), "application/x-xz"),

        // TAR — detected by the "ustar" magic at offset 257
        MagicSignature(byteArrayOf(0x75, 0x73, 0x74, 0x61, 0x72), "application/x-tar", offset = 257),

        // WIM — Windows Imaging Format
        MagicSignature(byteArrayOf(0x4D, 0x53, 0x57, 0x49, 0x4D, 0x00), "application/x-ms-wim"),

        // CAB — Microsoft Cabinet
        MagicSignature(byteArrayOf(0x4D, 0x53, 0x43, 0x46), "application/vnd.ms-cab-compressed"),

        // ISO 9660 — disc image (primary volume descriptor at sector 16 = offset 32768)
        MagicSignature(byteArrayOf(0x01, 0x43, 0x44, 0x30, 0x30, 0x31), "application/x-iso9660-image", offset = 32769),

        // CPIO — old binary format
        MagicSignature(byteArrayOf(0xC7.toByte(), 0x71), "application/x-cpio"),

        // RPM — Red Hat Package Manager
        MagicSignature(byteArrayOf(0xED.toByte(), 0xAB.toByte(), 0xEE.toByte(), 0xDB.toByte()), "application/x-rpm"),

        // LZH/LHA — archive
        MagicSignature(byteArrayOf(0x2D, 0x6C, 0x68), "application/x-lzh-compressed", offset = 2),

        // Unix compress (.Z)
        MagicSignature(byteArrayOf(0x1F, 0x9D.toByte()), "application/x-compress"),

        // LZMA alone (7z uses its own magic; raw lzma files start with 0x5D followed by properties)
        MagicSignature(byteArrayOf(0x5D, 0x00, 0x00), "application/x-lzma"),

        // QCOW2 — QEMU disk image
        MagicSignature(byteArrayOf(0x51, 0x46, 0x49, 0xFB.toByte()), "application/x-qcow2"),

        // VDI — VirtualBox disk image
        MagicSignature(byteArrayOf(0x3C, 0x3C, 0x3C, 0x20), "application/x-virtualbox-vdi"),

        // VMDK — VMware disk image
        MagicSignature(byteArrayOf(0x4B, 0x44, 0x4D, 0x56), "application/x-vmdk"),

        // DMG — Apple disk image (starts with 0x78 0x01 zlib or has "koly" at end; check first 4 bytes)
        MagicSignature(byteArrayOf(0x78, 0x01, 0x73, 0x0D), "application/x-apple-diskimage"),

        // AR — Unix archive
        MagicSignature(byteArrayOf(0x21, 0x3C, 0x61, 0x72, 0x63, 0x68, 0x3E), "application/x-archive"),


        // Audio
        MagicSignature(byteArrayOf(0x49, 0x44, 0x33), "audio/mpeg"), // ID3 tag
        MagicSignature(byteArrayOf(0xFF.toByte(), 0xFB.toByte()), "audio/mpeg"), // MPEG sync
        MagicSignature(byteArrayOf(0x66, 0x4C, 0x61, 0x43), "audio/flac"),
        MagicSignature(byteArrayOf(0x4F, 0x67, 0x67, 0x53), "audio/ogg"),

        // Video
        MagicSignature(byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte()), "video/x-matroska"),

        // Executables
        MagicSignature(byteArrayOf(0x7F, 0x45, 0x4C, 0x46), "application/x-elf"),
        MagicSignature(byteArrayOf(0x64, 0x65, 0x78, 0x0A), "application/x-dex"), // DEX
    )

    private data class MagicSignature(
        val bytes: ByteArray,
        val mimeType: String,
        val offset: Int = 0,
        val extraCheck: ((ByteArray) -> Boolean)? = null
    )

    /**
     * Detect the MIME type of a file using magic bytes and text heuristics.
     *
     * @param file The file to detect
     * @return [DetectedMimeType] with the detected type, or null if detection fails
     */
    fun detect(file: File): DetectedMimeType? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        if (file.length() == 0L) return null

        try {
            val headerBytes = readHeader(file, 8192)
            if (headerBytes.isEmpty()) return null

            // Check magic bytes first
            for (sig in MAGIC_SIGNATURES) {
                if (matchesSignature(headerBytes, sig)) {
                    return DetectedMimeType(
                        mimeType = sig.mimeType,
                        isText = false,
                        confidence = 0.95f
                    )
                }
            }

            // Check for ftyp box (MP4/MOV/3GP)
            if (headerBytes.size >= 12) {
                val ftypOffset = 4
                if (headerBytes.size > ftypOffset + 4) {
                    val ftypStr = String(headerBytes, ftypOffset, 4, Charsets.US_ASCII)
                    if (ftypStr == "ftyp") {
                        return DetectedMimeType(
                            mimeType = "video/mp4",
                            isText = false,
                            confidence = 0.9f
                        )
                    }
                }
            }

            // Text heuristic: check if content appears to be text
            if (isLikelyText(headerBytes)) {
                val textMime = guessTextSubtype(headerBytes)
                return DetectedMimeType(
                    mimeType = textMime,
                    isText = true,
                    confidence = 0.8f
                )
            }

            // Binary file of unknown type
            return DetectedMimeType(
                mimeType = "application/octet-stream",
                isText = false,
                confidence = 0.3f
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Check if a MIME type is text-based and should open in the text editor.
     */
    fun isTextMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                mimeType == "application/xml" ||
                mimeType == "application/javascript" ||
                mimeType == "application/x-sh" ||
                mimeType == "application/x-yaml"
    }

    private fun readHeader(file: File, maxBytes: Int): ByteArray {
        val size = minOf(file.length(), maxBytes.toLong()).toInt()
        val buffer = ByteArray(size)
        FileInputStream(file).use { fis ->
            var read = 0
            while (read < size) {
                val r = fis.read(buffer, read, size - read)
                if (r <= 0) break
                read += r
            }
            return buffer.copyOf(read)
        }
    }

    private fun matchesSignature(data: ByteArray, sig: MagicSignature): Boolean {
        if (data.size < sig.offset + sig.bytes.size) return false
        for (i in sig.bytes.indices) {
            if (data[sig.offset + i] != sig.bytes[i]) return false
        }
        return sig.extraCheck?.invoke(data) ?: true
    }

    /**
     * Heuristic: check if content is likely UTF-8 text.
     * A file is considered text if >95% of bytes are printable ASCII, tab, CR, LF,
     * or valid UTF-8 continuation bytes.
     */
    private fun isLikelyText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false

        var printableCount = 0
        var nullCount = 0
        val checkLen = minOf(bytes.size, 4096)

        for (i in 0 until checkLen) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == 0 -> nullCount++
                b in 0x20..0x7E -> printableCount++ // printable ASCII
                b == 0x09 || b == 0x0A || b == 0x0D -> printableCount++ // tab, LF, CR
                b >= 0xC0 -> printableCount++ // UTF-8 multi-byte start
                b >= 0x80 -> printableCount++ // UTF-8 continuation
            }
        }

        // Too many null bytes = likely binary
        if (nullCount > checkLen * 0.01) return false

        return printableCount.toFloat() / checkLen >= 0.95f
    }

    /**
     * Try to guess the text subtype from content patterns.
     */
    private fun guessTextSubtype(bytes: ByteArray): String {
        val content = String(bytes, 0, minOf(bytes.size, 2048), Charsets.UTF_8)
        val trimmed = content.trimStart()

        return when {
            trimmed.startsWith("<?xml") || trimmed.startsWith("<") && trimmed.contains("xmlns") -> "text/xml"
            trimmed.startsWith("<!DOCTYPE html") || trimmed.startsWith("<html") -> "text/html"
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "application/json"
            trimmed.startsWith("#!/") -> "application/x-sh"
            trimmed.startsWith("# ") || trimmed.contains("\n## ") -> "text/markdown"
            trimmed.contains("\\documentclass") || trimmed.contains("\\begin{") -> "text/x-tex"
            else -> "text/plain"
        }
    }
}
