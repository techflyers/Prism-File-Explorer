package com.raival.compose.file.explorer.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SevenZipProgressParserTest {

    @Test
    fun testParseProgressPercentages() {
        val cases = listOf(
            "  0%" to 0.0f,
            "  5%" to 0.05f,
            " 42%" to 0.42f,
            " 99%" to 0.99f,
            "100%" to 1.0f
        )

        for ((input, expected) in cases) {
            val (percent, _) = NativeBinaryExecutor.parseProgress(input)
            assertNotNull("Expected percent for '$input'", percent)
            assertEquals("Expected $expected for '$input'", expected, percent!!, 0.001f)
        }
    }

    @Test
    fun testParseNonProgressLines() {
        val nonProgress = listOf(
            "Scanning the drive:",
            "1 folder, 5 files, 12345678 bytes",
            "Creating archive: /path/to/archive.7z",
            "Everything is Ok",
            ""
        )

        for (input in nonProgress) {
            val (percent, fileName) = NativeBinaryExecutor.parseProgress(input)
            assertNull("Expected null percent for '$input'", percent)
            assertTrue("Expected empty filename for '$input'", fileName.isEmpty())
        }
    }

    @Test
    fun testExtractFileNameCompression() {
        val lines = listOf(
            "  5% 10 + folder/sub/pic.jpg" to "pic.jpg",
            " 15% + data.json" to "data.json",
            " 99% + path/to/bigfile.iso" to "bigfile.iso",
            "+ single_added_file.txt" to "single_added_file.txt",
            "Compressing  documents/report.docx" to "report.docx"
        )

        for ((input, expectedFile) in lines) {
            val fileName = NativeBinaryExecutor.extractFileName(input)
            assertEquals("Expected filename for '$input'", expectedFile, fileName)
        }
    }

    @Test
    fun testExtractFileNameExtraction() {
        val lines = listOf(
            "  5% 12 - folder/sub/pic.jpg" to "pic.jpg",
            " 12% - path/to/document.pdf" to "document.pdf",
            "- root_extracted.txt" to "root_extracted.txt",
            "Extracting  photos/vacation.png" to "vacation.png"
        )

        for ((input, expectedFile) in lines) {
            val fileName = NativeBinaryExecutor.extractFileName(input)
            assertEquals("Expected filename for '$input'", expectedFile, fileName)
        }
    }

    @Test
    fun testExtractFileNameUpdate() {
        val lines = listOf(
            " 45% 1042. U files/data.bin" to "data.bin",
            "  2% 1. U folder/file.txt" to "file.txt",
            "U updated_file.cfg" to "updated_file.cfg"
        )

        for ((input, expectedFile) in lines) {
            val fileName = NativeBinaryExecutor.extractFileName(input)
            assertEquals("Expected filename for '$input'", expectedFile, fileName)
        }
    }

    @Test
    fun testFileNameWithSpacesDashesAndUtf8() {
        val lines = listOf(
            "  5% 1 + music/My Favorite - Song (2024).flac" to "My Favorite - Song (2024).flac",
            " 10% 2 - documents/résumé_2026.pdf" to "résumé_2026.pdf",
            " 20% 3 + photos/東京_여행_2026.jpg" to "東京_여행_2026.jpg",
            " 30% 4 - windows\\nested\\path\\document.docx" to "document.docx"
        )

        for ((input, expectedFile) in lines) {
            val fileName = NativeBinaryExecutor.extractFileName(input)
            assertEquals("Expected filename for '$input'", expectedFile, fileName)
        }
    }

    @Test
    fun testLinuxBackspaceSimulation() {
        // Simulates 7-Zip on Linux/Android ClosePrint and PrintRatio sequence
        val lineBuffer = StringBuilder()

        fun processChar(c: Char) {
            if (c == '\b') {
                if (lineBuffer.isNotEmpty()) lineBuffer.deleteCharAt(lineBuffer.length - 1)
            } else {
                lineBuffer.append(c)
            }
        }

        // Print initial 0%
        "  0%".forEach { processChar(it) }
        var (pct, file) = NativeBinaryExecutor.parseProgress(lineBuffer.toString())
        assertEquals(0.0f, pct!!, 0.001f)

        // ClosePrint sends 4 backspaces, 4 spaces, 4 backspaces
        "\b\b\b\b    \b\b\b\b".forEach { processChar(it) }

        // Print next update: " 15% 3 + folder/image.png"
        " 15% 3 + folder/image.png".forEach { processChar(it) }
        val (pct2, file2) = NativeBinaryExecutor.parseProgress(lineBuffer.toString())
        assertEquals(0.15f, pct2!!, 0.001f)
        assertEquals("image.png", file2)
    }
}
