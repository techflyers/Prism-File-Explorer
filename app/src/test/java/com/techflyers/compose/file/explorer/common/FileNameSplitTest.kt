package com.techflyers.compose.file.explorer.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSplitTest {

    @Test
    fun testSplitFileNameNormal() {
        val (base, ext) = splitFileName("document.pdf", false)
        assertEquals("document", base)
        assertEquals("pdf", ext)
    }

    @Test
    fun testSplitFileNameEmptyBaseNameLeadingDot() {
        // When filename field is deleted completely, ".pdf" must retain "pdf" as extension
        // and "" as base name instead of jumping to base name
        val (base, ext) = splitFileName(".pdf", false)
        assertEquals("", base)
        assertEquals("pdf", ext)
    }

    @Test
    fun testRoundTripWithEmptyBaseName() {
        val joined = joinFileName("", "pdf")
        assertEquals(".pdf", joined)
        val (base, ext) = splitFileName(joined, false)
        assertEquals("", base)
        assertEquals("pdf", ext)
    }

    @Test
    fun testSplitFileNameMultipleDots() {
        val (base, ext) = splitFileName("archive.tar.gz", false)
        assertEquals("archive.tar", base)
        assertEquals("gz", ext)
    }

    @Test
    fun testSplitFileNameFolder() {
        val (base, ext) = splitFileName("my_folder", true)
        assertEquals("my_folder", base)
        assertEquals("", ext)
    }

    @Test
    fun testSplitFileNameNoExtension() {
        val (base, ext) = splitFileName("README", false)
        assertEquals("README", base)
        assertEquals("", ext)
    }
}
