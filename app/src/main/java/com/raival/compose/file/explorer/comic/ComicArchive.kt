package com.raival.compose.file.explorer.comic

import java.io.Closeable
import java.io.InputStream

/**
 * Bookmark inside a comic archive (typically from ComicInfo.xml).
 */
data class ComicBookmark(
    val pageIndex: Int,
    val title: String
)

/**
 * Unified random-access abstraction for comic book archives (CBZ, CBR, CB7, CBT).
 * Pages are the archive's image entries in case-insensitive filename-sorted order.
 */
interface ComicArchive : Closeable {
    val pageCount: Int
    val title: String?
    val bookmarks: List<ComicBookmark>

    /** Returns the file/entry name for the given page index. */
    fun pageName(pageIndex: Int): String

    /** Returns the MIME type for the given page index (e.g. "image/jpeg"). */
    fun mediaType(pageIndex: Int): String

    /** Opens a streaming InputStream for the page image. Caller must close the stream. */
    fun openStream(pageIndex: Int): InputStream

    /** Returns the raw decompressed bytes of the page image. */
    fun imageBytes(pageIndex: Int): ByteArray {
        return openStream(pageIndex).use { it.readBytes() }
    }

    companion object {
        val IMAGE_EXTENSIONS = mapOf(
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "png" to "image/png",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "bmp" to "image/bmp",
            "avif" to "image/avif"
        )

        fun isImageFile(name: String): Boolean {
            val lower = name.lowercase()
            val ext = lower.substringAfterLast('.', "")
            return IMAGE_EXTENSIONS.containsKey(ext)
        }

        fun isExcluded(name: String): Boolean {
            return name.contains("__MACOSX") ||
                    name.substringAfterLast('/').startsWith(".") ||
                    name.endsWith("thumbs.db", ignoreCase = true)
        }
    }
}
