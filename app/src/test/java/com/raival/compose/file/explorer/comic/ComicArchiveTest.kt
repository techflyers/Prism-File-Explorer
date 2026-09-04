package com.raival.compose.file.explorer.comic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ComicArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testCbzArchivePageListingAndMetadata() {
        val cbzFile = tempFolder.newFile("test_comic.cbz")

        val comicInfoXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
              <Title>The Secret Origin</Title>
              <Series>Cosmic Heroes</Series>
              <Number>42</Number>
              <Summary>An epic tale of galactic proportions.</Summary>
              <Writer>John Doe</Writer>
              <Penciller>Jane Smith</Penciller>
              <PageCount>3</PageCount>
              <Pages>
                <Page Image="0" Bookmark="Cover"/>
                <Page Image="1" Bookmark="Chapter 1"/>
              </Pages>
            </ComicInfo>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(cbzFile)).use { zos ->
            // Add ComicInfo.xml
            zos.putNextEntry(ZipEntry("ComicInfo.xml"))
            zos.write(comicInfoXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Add non-image / ignored files
            zos.putNextEntry(ZipEntry("thumbs.db"))
            zos.write(ByteArray(10))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("__MACOSX/._page1.jpg"))
            zos.write(ByteArray(10))
            zos.closeEntry()

            // Add image pages out of order
            zos.putNextEntry(ZipEntry("page_10.jpg"))
            zos.write("page 10 content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("page_02.PNG"))
            zos.write("page 2 content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("page_01.jpg"))
            zos.write("page 1 content".toByteArray())
            zos.closeEntry()
        }

        val archive = CbzArchive(cbzFile)
        archive.use {
            // Should only contain the 3 image pages, sorted
            assertEquals(3, archive.pageCount)
            assertEquals("page_01.jpg", archive.pageName(0))
            assertEquals("page_02.PNG", archive.pageName(1))
            assertEquals("page_10.jpg", archive.pageName(2))

            // Test media types
            assertEquals("image/jpeg", archive.mediaType(0))
            assertEquals("image/png", archive.mediaType(1))

            // Test page reading
            val stream = archive.openStream(0)
            assertNotNull(stream)
            val content = stream.bufferedReader().readText()
            assertEquals("page 1 content", content)

            val bytes = archive.imageBytes(0)
            assertEquals("page 1 content", String(bytes))

            // Test title formatting from ComicInfo
            assertEquals("Cosmic Heroes #42 - The Secret Origin", archive.title)

            // Test bookmarks
            assertEquals(2, archive.bookmarks.size)
            assertEquals(0, archive.bookmarks[0].pageIndex)
            assertEquals("Cover", archive.bookmarks[0].title)
            assertEquals(1, archive.bookmarks[1].pageIndex)
            assertEquals("Chapter 1", archive.bookmarks[1].title)
        }
    }

    @Test
    fun testComicArchiveFactorySniffing() {
        // Test magic bytes detection
        val zipHeader = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 0x03, 0x04)
        assertTrue(ComicArchiveFactory.isZipHeader(zipHeader))

        val sevenZHeader = byteArrayOf('7'.code.toByte(), 'z'.code.toByte(), 0xBC.toByte(), 0xAF.toByte(), 0x27.toByte(), 0x1C.toByte())
        assertTrue(ComicArchiveFactory.is7zHeader(sevenZHeader))

        val rarHeader = byteArrayOf('R'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(), '!'.code.toByte(), 0x1A.toByte(), 0x07.toByte())
        assertTrue(ComicArchiveFactory.isRarHeader(rarHeader))

        // Test opening a valid ZIP archive via ComicArchiveFactory
        val zipFile = tempFolder.newFile("sample_archive.comic")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("001.jpg"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
        }

        val comicArchive = ComicArchiveFactory.open(zipFile)
        assertTrue("Factory should identify ZIP magic bytes as CbzArchive", comicArchive is CbzArchive)
        assertEquals(1, comicArchive.pageCount)
        comicArchive.close()
    }

    @Test
    fun testCb7ArchiveOpeningAndReading() {
        val cb7File = tempFolder.newFile("sample_comic.cb7")
        val dummyFile = tempFolder.newFile("dummy.jpg")
        dummyFile.writeText("7z page 1 content")

        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(cb7File).use { out ->
            out.setContentCompression(org.apache.commons.compress.archivers.sevenz.SevenZMethod.LZMA2)
            val entry = out.createArchiveEntry(dummyFile, "page_01.jpg")
            out.putArchiveEntry(entry)
            out.write("7z page 1 content".toByteArray(Charsets.UTF_8))
            out.closeArchiveEntry()
        }

        val cb7Archive = Cb7Archive(cb7File)
        cb7Archive.use { archive ->
            assertEquals(1, archive.pageCount)
            assertEquals("page_01.jpg", archive.pageName(0))
            assertEquals("image/jpeg", archive.mediaType(0))
            val stream = archive.openStream(0)
            assertEquals("7z page 1 content", stream.bufferedReader().readText())
            val bytes = archive.imageBytes(0)
            assertEquals("7z page 1 content", String(bytes))
        }

        // Test opening via factory with magic bytes
        val factoryArchive = ComicArchiveFactory.open(cb7File)
        assertTrue("Factory should open cb7 as Cb7Archive", factoryArchive is Cb7Archive)
        assertEquals(1, factoryArchive.pageCount)
        factoryArchive.close()
    }
}
