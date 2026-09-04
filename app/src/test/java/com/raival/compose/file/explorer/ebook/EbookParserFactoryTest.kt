package com.raival.compose.file.explorer.ebook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EbookParserFactoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIsSupportedExtensions() {
        val epubFile = File("book.epub")
        val mobiFile = File("book.mobi")
        val azwFile = File("book.azw")
        val azw3File = File("book.azw3")
        val prcFile = File("book.prc")
        val rtfFile = File("book.rtf")
        val odtFile = File("book.odt")
        val fb2File = File("book.fb2")
        val fb2ZipFile = File("book.fb2.zip")
        val pdfFile = File("book.pdf")
        val zipFile = File("archive.zip")

        assertTrue(EbookParserFactory.isSupported(epubFile))
        assertTrue(EbookParserFactory.isSupported(mobiFile))
        assertTrue(EbookParserFactory.isSupported(azwFile))
        assertTrue(EbookParserFactory.isSupported(azw3File))
        assertTrue(EbookParserFactory.isSupported(prcFile))
        assertTrue(EbookParserFactory.isSupported(rtfFile))
        assertTrue(EbookParserFactory.isSupported(odtFile))
        assertTrue(EbookParserFactory.isSupported(fb2File))
        assertTrue(EbookParserFactory.isSupported(fb2ZipFile))

        assertFalse(EbookParserFactory.isSupported(pdfFile))
        assertFalse(EbookParserFactory.isSupported(zipFile))
    }

    @Test
    fun testRtfAutoDetection() {
        val file = tempFolder.newFile("document.rtf")
        file.writeText("{\\rtf1\\ansi Hello World\\par}")

        val book = EbookParserFactory.parse(file)
        assertTrue(book.title.contains("document"))
        assertTrue(book.chapters.isNotEmpty())
    }
}
