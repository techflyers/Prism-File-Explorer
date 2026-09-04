package com.raival.compose.file.explorer.ebook

import com.raival.compose.file.explorer.ebook.mobi.MobiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

class MobiParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testLz77Decompress() {
        // Test LZ77 decompressor
        // 1. Literal bytes <= 0x7F: 'H', 'e', 'l', 'l', 'o'
        // 2. Space + byte xor 0x80: 0xC0 + ('W'.code)
        val input = byteArrayOf(
            'H'.code.toByte(), 'e'.code.toByte(), 'l'.code.toByte(), 'l'.code.toByte(), 'o'.code.toByte(),
            (0x80 or 'W'.code).toByte(), 'o'.code.toByte(), 'r'.code.toByte(), 'l'.code.toByte(), 'd'.code.toByte()
        )
        val decompressed = MobiParser.lz77Decompress(input)
        val text = String(decompressed, Charsets.US_ASCII)
        assertEquals("Hello World", text)
    }

    @Test
    fun testLz77RunAndLookback() {
        // Run of bytes: byte 0x03 followed by 3 bytes
        val input = byteArrayOf(
            0x03, 'A'.code.toByte(), 'B'.code.toByte(), 'C'.code.toByte()
        )
        val decompressed = MobiParser.lz77Decompress(input)
        val text = String(decompressed, Charsets.US_ASCII)
        assertEquals("ABC", text)
    }

    @Test
    fun testMobiParserWithConstructedFile() {
        val testFile = tempFolder.newFile("test_book.mobi")

        val out = ByteArrayOutputStream()

        // 1. Palm Database Header (78 bytes)
        val header = ByteArray(78)
        "Test PalmDOC Book".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
        // Record count at offset 76 (2 bytes) = 3 records
        header[76] = 0x00
        header[77] = 0x03
        out.write(header)

        // Record offset entries (3 records * 8 bytes = 24 bytes)
        // Record 0 offset = 78 + 24 = 102
        // Record 1 offset = 102 + 250 = 352
        // Record 2 offset = 352 + 100 = 452
        val r0Offset = 102
        val r1Offset = 352
        val r2Offset = 452

        fun writeInt32(v: Int) {
            out.write((v shr 24) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write(v and 0xFF)
        }

        // Record 0 entry
        writeInt32(r0Offset)
        writeInt32(0) // attributes + id
        // Record 1 entry
        writeInt32(r1Offset)
        writeInt32(0)
        // Record 2 entry
        writeInt32(r2Offset)
        writeInt32(0)

        // Record 0 payload (MOBI header)
        val r0 = ByteArray(250)
        // PalmDOC header:
        // Compression at offset 0 (2 bytes) = 2 (PalmDOC LZ77)
        r0[0] = 0x00
        r0[1] = 0x02
        // Text length at offset 4 = 100
        r0[4] = 0x00
        r0[5] = 0x00
        r0[6] = 0x00
        r0[7] = 0x64
        // Encryption at offset 12 = 0
        r0[12] = 0x00
        r0[13] = 0x00

        // MOBI header starts at offset 16
        val mobiMagic = "MOBI".toByteArray(Charsets.US_ASCII)
        mobiMagic.copyInto(r0, 16)
        // Header length at 16 + 4 = 232
        r0[20] = 0x00
        r0[21] = 0x00
        r0[22] = 0x00
        r0[23] = 0xE8.toByte()
        // Codepage at 16 + 12 = 65001 (UTF-8)
        r0[28] = 0x00
        r0[29] = 0x00
        r0[30] = 0xFD.toByte()
        r0[31] = 0xE9.toByte()

        // Full name offset at 16 + 68 = 84 relative to record 0 = 200
        r0[84] = 0x00
        r0[85] = 0x00
        r0[86] = 0x00
        r0[87] = 200.toByte()
        // Full name length at 16 + 72 = 88 = 12 ("My Mobi Book".length)
        r0[88] = 0x00
        r0[89] = 0x00
        r0[90] = 0x00
        r0[91] = 12.toByte()

        // firstImageIndex at 16 + 92 = 108 = 2 (record 2 is image)
        r0[108] = 0x00
        r0[109] = 0x00
        r0[110] = 0x00
        r0[111] = 0x02

        // EXTH flags at 16 + 112 = 128 = 0x40 (has EXTH)
        r0[128] = 0x00
        r0[129] = 0x00
        r0[130] = 0x00
        r0[131] = 0x40

        // firstContentIndex at 16 + 176 = 192 = 1
        r0[192] = 0x00
        r0[193] = 0x01
        // lastContentIndex at 16 + 178 = 194 = 2
        r0[194] = 0x00
        r0[195] = 0x02

        // Book title string at offset 200: "My Mobi Book"
        "My Mobi Book".toByteArray(Charsets.UTF_8).copyInto(r0, 200)

        out.write(r0)

        // Record 1 payload (Text content)
        val chapterHtml = "<h1>Chapter 1</h1><p>This is a test mobi book.</p><img recindex=\"1\">"
        val lz77Text = chapterHtml.toByteArray(Charsets.UTF_8)
        out.write(lz77Text)
        // Pad to record 2 offset
        val pad = r2Offset - out.size()
        if (pad > 0) out.write(ByteArray(pad))

        // Record 2 payload (Image JPEG cover)
        val jpegHeader = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte()
        )
        out.write(jpegHeader)

        testFile.writeBytes(out.toByteArray())

        val book = MobiParser.parse(testFile)
        assertNotNull(book)
        assertEquals("My Mobi Book", book.title)
        assertEquals(1, book.chapters.size)
        assertTrue(book.chapters[0].htmlContent?.contains("Chapter 1") == true)
        assertTrue(book.chapters[0].htmlContent?.contains("mobi_img_1.jpg") == true)
        assertNotNull(book.coverBytes)
    }
}
