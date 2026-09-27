package com.techflyers.compose.file.explorer.screen.main.tab.files.misc

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.util.zip.ZipFile

object DocumentPageCount {
    private val cache = android.util.LruCache<String, Pair<Int, String>>(500)

    data class Count(val value: Int, val unit: String)

    fun get(file: File): Count? {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        cache.get(key)?.let { return Count(it.first, it.second) }
        val ext = file.extension.lowercase()
        val result = when (ext) {
            "pdf" -> pdfPages(file).let { n -> Count(n, if (n == 1) "page" else "pages") }
            "pptx", "pptm", "ppsx" -> {
                val n = zipCount(file) { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
                Count(n, if (n == 1) "slide" else "slides")
            }
            "xlsx", "xlsm" -> {
                val n = zipCount(file) { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                Count(n, if (n == 1) "sheet" else "sheets")
            }
            "docx", "docm" -> {
                val pages = docxPages(file)
                if (pages > 0) Count(pages, if (pages == 1) "page" else "pages") else null
            }
            else -> null
        }
        if (result != null && result.value > 0) {
            cache.put(key, result.value to result.unit)
            return result
        }
        return null
    }

    private fun pdfPages(file: File): Int {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { it.pageCount }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun zipCount(file: File, predicate: (String) -> Boolean): Int {
        return try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().count { !it.isDirectory && predicate(it.name) }
            }
        } catch (_: Exception) {
            0
        }
    }

    private fun docxPages(file: File): Int {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("docProps/app.xml") ?: return 0
                zip.getInputStream(entry).bufferedReader().use { reader ->
                    val xml = reader.readText()
                    Regex("<Pages>(\\d+)</Pages>").find(xml)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }
            }
        } catch (_: Exception) {
            0
        }
    }
}
