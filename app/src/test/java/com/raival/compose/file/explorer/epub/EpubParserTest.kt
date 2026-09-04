package com.raival.compose.file.explorer.epub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testEpubParsingWithMetadataSpineAndToc() {
        val epubFile = tempFolder.newFile("sample_book.epub")

        val containerXml = """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opfXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>Sample EPUB Book</dc:title>
                <dc:creator>Jane Author</dc:creator>
                <dc:language>en</dc:language>
                <dc:publisher>Sample Press</dc:publisher>
                <meta name="cover" content="cover-image"/>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="cover-image" href="images/cover.jpg" media-type="image/jpeg" properties="cover-image"/>
                <item id="chapter1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="chapter2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="chapter1"/>
                <itemref idref="chapter2"/>
              </spine>
            </package>
        """.trimIndent()

        val ncxXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="navPoint-1" playOrder="1">
                  <navLabel><text>Chapter 1: The Beginning</text></navLabel>
                  <content src="text/ch1.xhtml"/>
                </navPoint>
                <navPoint id="navPoint-2" playOrder="2">
                  <navLabel><text>Chapter 2: The Journey</text></navLabel>
                  <content src="text/ch2.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()

        val ch1Html = "<html><body><h1>Chapter 1</h1><p>Once upon a time...</p></body></html>"
        val ch2Html = "<html><body><h1>Chapter 2</h1><p>The journey continued...</p></body></html>"

        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(opfXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zos.write(ncxXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/images/cover.jpg"))
            zos.write(ByteArray(20))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/text/ch1.xhtml"))
            zos.write(ch1Html.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/text/ch2.xhtml"))
            zos.write(ch2Html.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val epubBook = EpubParser.parse(epubFile)
        assertNotNull(epubBook)

        // Verify metadata
        assertEquals("Sample EPUB Book", epubBook.title)
        assertEquals("Jane Author", epubBook.author)

        // Verify chapters
        assertEquals(2, epubBook.chapters.size)
        assertEquals("chapter1", epubBook.chapters[0].id)
        assertEquals("OEBPS/text/ch1.xhtml", epubBook.chapters[0].fullZipPath)
        assertEquals("chapter2", epubBook.chapters[1].id)
        assertEquals("OEBPS/text/ch2.xhtml", epubBook.chapters[1].fullZipPath)

        // Verify cover
        assertEquals("OEBPS/images/cover.jpg", epubBook.coverZipPath)

        // Verify TOC
        assertEquals(2, epubBook.toc.size)
        assertEquals("Chapter 1: The Beginning", epubBook.toc[0].title)
        assertEquals("text/ch1.xhtml", epubBook.toc[0].href)
        assertEquals(0, epubBook.toc[0].chapterIndex)
        assertEquals("Chapter 2: The Journey", epubBook.toc[1].title)
        assertEquals("text/ch2.xhtml", epubBook.toc[1].href)
        assertEquals(1, epubBook.toc[1].chapterIndex)

        // Test reading entry content
        val ch1HtmlRead = epubBook.getChapterHtml(epubBook.chapters[0])
        assertEquals(ch1Html, ch1HtmlRead)

        val coverBytes = epubBook.coverBytes
        assertNotNull(coverBytes)
        assertEquals(20, coverBytes!!.size)

        epubBook.close()
    }
}
