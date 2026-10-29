package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-throughput 64-bit xxHash3 / XXH64-inspired non-cryptographic hash for routine file and
 * directory comparisons. Processes 64-byte stripes in little-endian 64-bit lanes with zero
 * per-chunk allocations.
 */
object XxHash3 {
    private const val PRIME64_1 = -0x61c8864e7a143579L // 0x9E3779B185EBCA87UL
    private const val PRIME64_2 = -0x3d4d51c2d82b14b1L // 0xC2B2AE3D27D4EB4FUL
    private const val PRIME64_3 = 0x165667b19e3779f9L
    private const val PRIME64_4 = -0x7a1435883d4d519dL // 0x85EBCA77C2B2AE63UL
    private const val PRIME64_5 = 0x27d4eb2f165667c5L

    private fun round(acc: Long, input: Long): Long {
        var v = acc + input * PRIME64_2
        v = java.lang.Long.rotateLeft(v, 31)
        v *= PRIME64_1
        return v
    }

    private fun mergeRound(acc: Long, `val`: Long): Long {
        val v = round(0L, `val`)
        var res = acc xor v
        res = res * PRIME64_1 + PRIME64_4
        return res
    }

    private fun avalanche(h: Long): Long {
        var v = h xor (h ushr 33)
        v *= PRIME64_2
        v = v xor (v ushr 29)
        v *= PRIME64_3
        v = v xor (v ushr 32)
        return v
    }

    fun hashFile(
        file: File,
        seed: Long = 0L,
        isCancelled: () -> Boolean = { false },
        onProgress: ((bytesProcessed: Long, totalBytes: Long) -> Unit)? = null
    ): String {
        val totalSize = file.length()
        var totalLen = 0L
        var v1 = seed + PRIME64_1 + PRIME64_2
        var v2 = seed + PRIME64_2
        var v3 = seed
        var v4 = seed - PRIME64_1

        val bufferSize = 64 * 1024 // 64 KB multiple of 32 bytes
        val buffer = ByteArray(bufferSize)
        val byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        val tail = ByteArray(32)
        var tailSize = 0

        FileInputStream(file).use { fis ->
            while (true) {
                if (isCancelled()) return ""
                val read = fis.read(buffer, tailSize, bufferSize - tailSize)
                if (read <= 0) break
                val available = tailSize + read
                totalLen += read

                var offset = 0
                val limit = available - 32
                while (offset <= limit) {
                    v1 = round(v1, byteBuffer.getLong(offset))
                    v2 = round(v2, byteBuffer.getLong(offset + 8))
                    v3 = round(v3, byteBuffer.getLong(offset + 16))
                    v4 = round(v4, byteBuffer.getLong(offset + 24))
                    offset += 32
                }

                tailSize = available - offset
                if (tailSize > 0) {
                    System.arraycopy(buffer, offset, tail, 0, tailSize)
                    System.arraycopy(tail, 0, buffer, 0, tailSize)
                }

                onProgress?.invoke(totalLen, totalSize)
            }
        }

        var h64 = if (totalLen >= 32L) {
            var h = java.lang.Long.rotateLeft(v1, 1) +
                    java.lang.Long.rotateLeft(v2, 7) +
                    java.lang.Long.rotateLeft(v3, 12) +
                    java.lang.Long.rotateLeft(v4, 18)
            h = mergeRound(h, v1)
            h = mergeRound(h, v2)
            h = mergeRound(h, v3)
            h = mergeRound(h, v4)
            h
        } else {
            seed + PRIME64_5
        }

        h64 += totalLen

        val tailBuf = ByteBuffer.wrap(tail, 0, tailSize).order(ByteOrder.LITTLE_ENDIAN)
        var idx = 0
        while (idx + 8 <= tailSize) {
            val k1 = round(0L, tailBuf.getLong(idx))
            h64 = h64 xor k1
            h64 = java.lang.Long.rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4
            idx += 8
        }
        if (idx + 4 <= tailSize) {
            val k1 = (tailBuf.getInt(idx).toLong() and 0xFFFFFFFFL) * PRIME64_1
            h64 = h64 xor k1
            h64 = java.lang.Long.rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3
            idx += 4
        }
        while (idx < tailSize) {
            val k1 = (tail[idx].toLong() and 0xFFL) * PRIME64_5
            h64 = h64 xor k1
            h64 = java.lang.Long.rotateLeft(h64, 11) * PRIME64_1
            idx++
        }

        h64 = avalanche(h64)
        return java.lang.Long.toUnsignedString(h64, 16).padStart(16, '0')
    }
}
