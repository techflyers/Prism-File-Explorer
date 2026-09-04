package com.raival.compose.file.explorer.ebook

import com.raival.compose.file.explorer.ebook.rtf.RtfParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RtfParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testRtfParsingWithFormatting() {
        val testFile = tempFolder.newFile("sample.rtf")
        val rtfContent = """
            {\rtf1\ansi\deff0
            {\fonttbl{\f0\fnil\fcharset0 Arial;}}
            \viewkind4\uc1\pard\lang1033\f0\fs24
            \b Hello Bold World\b0\par
            \i Italic text\i0\par
            \ul Underlined text\ulnone\par
            Some plain paragraph.\par
            }
        """.trimIndent()
        testFile.writeText(rtfContent)

        val rtfBook = RtfParser.parse(testFile)
        assertNotNull(rtfBook)
        assertEquals("sample", rtfBook.title)
        assertTrue(rtfBook.chapters.isNotEmpty())

        val html = rtfBook.getChapterHtml(rtfBook.chapters[0])
        assertTrue("Should contain bold tags", html.contains("<b>Hello Bold World</b>"))
        assertTrue("Should contain italic tags", html.contains("<i>Italic text</i>"))
        assertTrue("Should contain underline tags", html.contains("<u>Underlined text</u>"))
        assertTrue("Should contain plain paragraph", html.contains("Some plain paragraph."))
    }
}
