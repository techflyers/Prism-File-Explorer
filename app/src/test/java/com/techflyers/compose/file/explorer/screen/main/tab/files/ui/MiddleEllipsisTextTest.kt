package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiddleEllipsisTextTest {

    @Test
    fun testFileWithExtensionDefaultEndChars() {
        val parts = computeMiddleEllipsisParts("my_document.pdf", isFolder = false, endCharsCount = 0)
        assertEquals(Pair("my_document", ".pdf"), parts)
    }

    @Test
    fun testFileWithExtensionWithEndChars() {
        val parts = computeMiddleEllipsisParts("my_very_long_document_final.pdf", isFolder = false, endCharsCount = 5)
        assertEquals(Pair("my_very_long_document_", "final.pdf"), parts)
    }

    @Test
    fun testFileWithExtensionShorterThanEndChars() {
        val parts = computeMiddleEllipsisParts("doc.pdf", isFolder = false, endCharsCount = 5)
        assertEquals(Pair("doc", ".pdf"), parts)
    }

    @Test
    fun testFolderWithEndChars() {
        val parts = computeMiddleEllipsisParts("My Long Folder Name", isFolder = true, endCharsCount = 4)
        assertEquals(Pair("My Long Folder ", "Name"), parts)
    }

    @Test
    fun testFolderDefaultEndChars() {
        val parts = computeMiddleEllipsisParts("My Long Folder Name", isFolder = true, endCharsCount = 0)
        assertNull(parts)
    }

    @Test
    fun testFileWithoutExtension() {
        val parts = computeMiddleEllipsisParts("README", isFolder = false, endCharsCount = 3)
        assertEquals(Pair("REA", "DME"), parts)
    }
}
