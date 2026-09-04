package com.raival.compose.file.explorer.ebook

import java.io.Closeable
import java.io.File
import java.io.InputStream

/**
 * Represents a single chapter or section in a reflowable e-book/document.
 */
data class EbookChapter(
    val id: String,
    val title: String,
    val href: String? = null,
    val htmlContent: String? = null,
    val fullZipPath: String? = null
)

/**
 * An item in the e-book's Table of Contents.
 */
data class EbookTocItem(
    val title: String,
    val href: String,
    val chapterIndex: Int,
    val children: List<EbookTocItem> = emptyList()
)

/**
 * User bookmark saved at a specific chapter and position.
 */
data class EbookBookmark(
    val chapterIndex: Int,
    val scrollPercentage: Float = 0f,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Unified abstraction representing any reflowable e-book or document
 * (EPUB, MOBI, AZW, AZW3, PRC, RTF, ODT, FB2).
 */
interface EbookDocument : Closeable {
    val file: File
    val title: String
    val author: String?
    val coverBytes: ByteArray?
    val chapters: List<EbookChapter>
    val toc: List<EbookTocItem>

    /**
     * Retrieves the HTML content for the given chapter.
     */
    fun getChapterHtml(chapter: EbookChapter): String

    /**
     * Retrieves a stream for an internal asset (image, stylesheet, font) if applicable.
     */
    fun getEntryStream(relativePath: String): InputStream? {
        return null
    }

    override fun close() {
        // Default no-op
    }
}
