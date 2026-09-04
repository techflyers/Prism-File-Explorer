package com.raival.compose.file.explorer.ebook

import com.raival.compose.file.explorer.ebook.fb2.Fb2Parser
import com.raival.compose.file.explorer.ebook.mobi.MobiParser
import com.raival.compose.file.explorer.ebook.odt.OdtParser
import com.raival.compose.file.explorer.ebook.rtf.RtfParser
import com.raival.compose.file.explorer.epub.EpubParser
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

object EbookParserFactory {

    val SUPPORTED_EXTENSIONS = setOf(
        "epub",
        "mobi", "azw", "azw3", "prc",
        "rtf",
        "odt",
        "fb2"
    )

    fun isSupported(file: File): Boolean {
        val name = file.name.lowercase(Locale.ROOT)
        if (name.endsWith(".fb2.zip")) return true
        val ext = file.extension.lowercase(Locale.ROOT)
        return SUPPORTED_EXTENSIONS.contains(ext)
    }

    fun parse(file: File): EbookDocument {
        val name = file.name.lowercase(Locale.ROOT)
        val ext = file.extension.lowercase(Locale.ROOT)

        if (name.endsWith(".fb2.zip") || ext == "fb2") {
            return Fb2Parser.parse(file)
        }

        return when (ext) {
            "epub" -> EpubParser.parse(file)
            "mobi", "azw", "azw3", "prc" -> MobiParser.parse(file)
            "rtf" -> RtfParser.parse(file)
            "odt" -> OdtParser.parse(file)
            else -> sniffAndParse(file)
        }
    }

    private fun sniffAndParse(file: File): EbookDocument {
        if (!file.exists() || file.length() < 16) {
            throw IllegalArgumentException("Unsupported or empty e-book file: ${file.name}")
        }

        val header = ByteArray(minOf(file.length().toInt(), 4096))
        file.inputStream().use { it.read(header) }

        // Check RTF
        if (header.size >= 5 && header[0] == '{'.code.toByte() && header[1] == '\\'.code.toByte() &&
            header[2] == 'r'.code.toByte() && header[3] == 't'.code.toByte() && header[4] == 'f'.code.toByte()
        ) {
            return RtfParser.parse(file)
        }

        // Check ZIP archives (EPUB, ODT, FB2.ZIP)
        if (header.size >= 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
        ) {
            try {
                ZipFile(file).use { zip ->
                    if (zip.getEntry("META-INF/container.xml") != null) {
                        return EpubParser.parse(file)
                    }
                    if (zip.getEntry("content.xml") != null) {
                        return OdtParser.parse(file)
                    }
                    if (zip.entries().asSequence().any { it.name.lowercase(Locale.ROOT).endsWith(".fb2") }) {
                        return Fb2Parser.parse(file)
                    }
                }
            } catch (_: Exception) {}
        }

        // Check MOBI / PalmDOC (MOBI magic at record 0 or offset 60-68)
        val headerStr = String(header, Charsets.US_ASCII)
        if (headerStr.contains("BOOKMOBI") || headerStr.contains("MOBI")) {
            return MobiParser.parse(file)
        }

        // Check FB2 XML
        if (headerStr.contains("<FictionBook", ignoreCase = true)) {
            return Fb2Parser.parse(file)
        }

        // Default fallback to EPUB
        return EpubParser.parse(file)
    }
}
