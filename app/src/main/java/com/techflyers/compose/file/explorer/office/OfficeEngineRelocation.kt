package com.techflyers.compose.file.explorer.office

import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.RandomAccessFile

internal object OfficeEngineRelocation {

    private const val ALIAS_FD = 1023

    fun aliasFor(needle: String): String {
        val base = "/proc/self/fd/$ALIAS_FD"
        val pad = needle.length - base.length
        require(pad >= 0 && pad % 2 == 0) { "Cannot build an alias of length ${needle.length}" }
        return base + "/.".repeat(pad / 2)
    }

    fun patchLibraries(libDir: File, needle: String): Int {
        val from = needle.toByteArray(Charsets.US_ASCII)
        val to = aliasFor(needle).toByteArray(Charsets.US_ASCII)
        check(from.size == to.size)
        var total = 0
        libDir.listFiles { f -> f.isFile && f.name.endsWith(".so") }?.forEach { lib ->
            total += patchFile(lib, from, to)
        }
        return total
    }

    private fun patchFile(file: File, from: ByteArray, to: ByteArray): Int {
        val offsets = ArrayList<Long>()
        val chunk = 1 shl 20
        val overlap = from.size - 1
        RandomAccessFile(file, "rw").use { raf ->
            val buf = ByteArray(chunk + overlap)
            var filePos = 0L
            var carried = 0
            while (true) {
                val n = raf.read(buf, carried, chunk)
                if (n <= 0) break
                val valid = carried + n
                var i = 0
                val limit = valid - from.size
                while (i <= limit) {
                    if (buf[i] == from[0] && matches(buf, i, from)) {
                        offsets += filePos - carried + i
                        i += from.size
                    } else {
                        i++
                    }
                }
                val keep = minOf(overlap, valid)
                System.arraycopy(buf, valid - keep, buf, 0, keep)
                filePos += n
                carried = keep
            }
            val unique = offsets.distinct()
            for (offset in unique) {
                raf.seek(offset)
                raf.write(to)
            }
            return unique.size
        }
    }

    private fun matches(buf: ByteArray, at: Int, needle: ByteArray): Boolean {
        for (k in needle.indices) if (buf[at + k] != needle[k]) return false
        return true
    }

    fun pinAlias(engineDir: File) {
        val link = File("/proc/self/fd/$ALIAS_FD")
        val existing = runCatching { Os.readlink(link.path) }.getOrNull()
        if (existing != null) {
            check(File(existing).canonicalPath == engineDir.canonicalPath) {
                "Descriptor $ALIAS_FD is already in use"
            }
            return
        }
        val fd = Os.open(engineDir.path, OsConstants.O_RDONLY, 0)
        try {
            Os.dup2(fd, ALIAS_FD)
            Os.close(fd)
        } catch (t: Throwable) {
            runCatching { Os.close(fd) }
            throw t
        }
    }
}
