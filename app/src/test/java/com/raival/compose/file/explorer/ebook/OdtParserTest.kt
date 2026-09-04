package com.raival.compose.file.explorer.ebook

import com.raival.compose.file.explorer.ebook.odt.OdtParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OdtParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testOdtParsing() {
        val testFile = tempFolder.newFile("sample.odt")

        val contentXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0"
                xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                xmlns:xlink="http://www.w3.org/1999/xlink">
                <office:body>
                    <office:text>
                        <text:h text:outline-level="1">Welcome to ODT</text:h>
                        <text:p>This is the first paragraph of the document.</text:p>
                        <text:p>Second paragraph with <text:span>formatted span</text:span>.</text:p>
                    </office:text>
                </office:body>
            </office:document-content>
        """.trimIndent()

        val metaXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
                <office:meta>
                    <dc:title>Sample ODT Document</dc:title>
                    <dc:creator>Alice Author</dc:creator>
                </office:meta>
            </office:document-meta>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(testFile)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/vnd.oasis.opendocument.text".toByteArray())

            zos.putNextEntry(ZipEntry("meta.xml"))
            zos.write(metaXml.toByteArray())

            zos.putNextEntry(ZipEntry("content.xml"))
            zos.write(contentXml.toByteArray())

            zos.putNextEntry(ZipEntry("Thumbnails/thumbnail.png"))
            zos.write(ByteArray(16) { 0x42 })
        }

        val book = OdtParser.parse(testFile)
        assertNotNull(book)
        assertEquals("Sample ODT Document", book.title)
        assertEquals("Alice Author", book.author)
        assertNotNull(book.coverBytes)
        assertEquals(16, book.coverBytes!!.size)

        assertTrue(book.chapters.isNotEmpty())
        val html = book.getChapterHtml(book.chapters[0])
        assertTrue("Contains heading", html.contains("Welcome to ODT"))
        assertTrue("Contains paragraph", html.contains("This is the first paragraph"))

        book.close()
    }
}
