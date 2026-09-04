package com.raival.compose.file.explorer.comic

import java.io.File
import java.io.FileInputStream

object ComicArchiveFactory {

    /**
     * Opens a comic archive file, automatically detecting its underlying archive format
     * by header magic bytes, with fallback to file extension.
     */
    fun open(file: File): ComicArchive {
        if (!file.exists()) {
            throw IllegalArgumentException("Comic file does not exist: ${file.absolutePath}")
        }

        val header = readHeader(file, 8)
        val ext = file.extension.lowercase()

        // 1. Magic bytes sniff
        if (isZipHeader(header)) {
            return CbzArchive(file)
        }
        if (is7zHeader(header)) {
            return Cb7Archive(file)
        }
        if (isRarHeader(header)) {
            return CbrArchive(file)
        }

        // 2. Extension fallback
        return when (ext) {
            "cbz", "zip" -> CbzArchive(file)
            "cbt", "tar" -> CbtArchive(file)
            "cb7", "7z" -> Cb7Archive(file)
            "cbr", "rar" -> CbrArchive(file)
            else -> {
                // Last resort attempt as CBZ
                try {
                    CbzArchive(file)
                } catch (_: Exception) {
                    CbrArchive(file)
                }
            }
        }
    }

    private fun readHeader(file: File, length: Int): ByteArray {
        val buffer = ByteArray(length)
        return try {
            FileInputStream(file).use { input ->
                val bytesRead = input.read(buffer)
                if (bytesRead < length) buffer.copyOf(bytesRead.coerceAtLeast(0)) else buffer
            }
        } catch (_: Exception) {
            ByteArray(0)
        }
    }

    internal fun isZipHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte()) &&
                (header[3] == 0x04.toByte() || header[3] == 0x06.toByte() || header[3] == 0x08.toByte())
    }

    internal fun isRarHeader(header: ByteArray): Boolean {
        if (header.size < 4) return false
        return header[0] == 0x52.toByte() &&
                header[1] == 0x61.toByte() &&
                header[2] == 0x72.toByte() &&
                header[3] == 0x21.toByte()
    }

    internal fun is7zHeader(header: ByteArray): Boolean {
        if (header.size < 6) return false
        return header[0] == 0x37.toByte() &&
                header[1] == 0x7A.toByte() &&
                header[2] == 0xBC.toByte() &&
                header[3] == 0xAF.toByte() &&
                header[4] == 0x27.toByte() &&
                header[5] == 0x1C.toByte()
    }
}
