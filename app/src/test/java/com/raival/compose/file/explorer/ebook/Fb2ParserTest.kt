package com.raival.compose.file.explorer.ebook

import com.raival.compose.file.explorer.ebook.fb2.Fb2Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

class Fb2ParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testFb2Parsing() {
        val testFile = tempFolder.newFile("sample.fb2")

        val dummyImageBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)
        val base64Image = Base64.getEncoder().encodeToString(dummyImageBytes)

        val fb2Xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
                <description>
                    <title-info>
                        <genre>science_fiction</genre>
                        <author>
                            <first-name>Isaac</first-name>
                            <last-name>Asimov</last-name>
                        </author>
                        <book-title>Foundation</book-title>
                        <coverpage>
                            <image l:href="#cover.jpg"/>
                        </coverpage>
                    </title-info>
                </description>
                <body>
                    <section>
                        <title><p>Chapter I: The Psychohistorians</p></title>
                        <p>Hari Seldon was born in the 11,988th year of the Galactic Era.</p>
                        <image l:href="#img1.jpg"/>
                    </section>
                </body>
                <binary id="cover.jpg" content-type="image/jpeg">$base64Image</binary>
                <binary id="img1.jpg" content-type="image/jpeg">$base64Image</binary>
            </FictionBook>
        """.trimIndent()

        testFile.writeText(fb2Xml)

        val book = Fb2Parser.parse(testFile)
        assertNotNull(book)
        assertEquals("Foundation", book.title)
        assertEquals("Isaac Asimov", book.author)
        assertNotNull(book.coverBytes)
        assertEquals(5, book.coverBytes!!.size)

        assertEquals(1, book.chapters.size)
        val ch = book.chapters[0]
        assertEquals("Chapter I: The Psychohistorians", ch.title)

        val html = book.getChapterHtml(ch)
        assertTrue("Contains chapter title", html.contains("Chapter I: The Psychohistorians"))
        assertTrue("Contains paragraph", html.contains("Hari Seldon was born"))
        assertTrue("Contains image data url", html.contains("data:image/jpeg;base64,"))
    }
}
