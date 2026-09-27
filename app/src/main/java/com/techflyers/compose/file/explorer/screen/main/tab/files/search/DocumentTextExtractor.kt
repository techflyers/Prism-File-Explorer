package com.techflyers.compose.file.explorer.screen.main.tab.files.search

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai.MlKitOcrEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.util.zip.ZipFile

object DocumentTextExtractor {
    suspend fun extract(file: File): String {
        val ext = file.extension.lowercase()
        return try {
            when {
                ext == "docx" || ext == "docm" -> extractDocx(file)
                ext == "xlsx" || ext == "xlsm" -> extractXlsx(file)
                ext == "pptx" || ext == "pptm" || ext == "ppsx" -> extractPptx(file)
                ext == "pdf" -> {
                    val native = extractPdf(file)
                    if (native.trim().length < 50) {
                        MlKitOcrEngine().extractTextFromPdf(file)
                    } else native
                }
                ext in FileMimeType.imageFileType -> MlKitOcrEngine().extractTextFromImage(file)
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun extractDocx(file: File): String {
        return try {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return@use ""
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("""<w:t[^>]*>(.*?)</w:t>""").findAll(xml)
                    .joinToString(" ") { it.groupValues[1] }
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun extractXlsx(file: File): String {
        return try {
            val sb = StringBuilder()
            java.util.zip.ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                    .forEach { entry ->
                        val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        Regex("""<t[^>]*>(.*?)</t>""").findAll(xml)
                            .forEach { sb.append(it.groupValues[1]).append(' ') }
                    }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun extractPptx(file: File): String {
        return try {
            val sb = StringBuilder()
            java.util.zip.ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                    .forEach { entry ->
                        val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                        Regex("""<a:t[^>]*>(.*?)</a:t>""").findAll(xml)
                            .forEach { sb.append(it.groupValues[1]).append(' ') }
                    }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun extractPdf(file: File): String {
        return try {
            PDFBoxResourceLoader.init(globalClass)
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            }
        } catch (_: Exception) {
            ""
        }
    }
}
