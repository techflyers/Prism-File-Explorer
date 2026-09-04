package com.raival.compose.file.explorer.comic

import org.w3c.dom.Element
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Comic archive implementation for CBZ (ZIP-of-images).
 * Uses [java.util.zip.ZipFile] for fast, zero-extraction random access.
 */
class CbzArchive(val file: File) : ComicArchive {

    private val zip = ZipFile(file)
    private val entries: List<Entry>
    override var title: String? = null
        private set
    override val bookmarks: List<ComicBookmark>

    init {
        entries = zip.entries().asSequence()
            .filter { !it.isDirectory }
            .filter { !ComicArchive.isExcluded(it.name) }
            .filter { ComicArchive.isImageFile(it.name) }
            .map { entry ->
                val ext = entry.name.substringAfterLast('.', "").lowercase()
                val media = ComicArchive.IMAGE_EXTENSIONS[ext] ?: "image/jpeg"
                Entry(entry.name, media)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .toList()

        val comicInfo = parseComicInfo()
        if (comicInfo.title != null) {
            title = comicInfo.title
        } else {
            title = file.nameWithoutExtension
        }
        bookmarks = comicInfo.bookmarks
    }

    override val pageCount: Int
        get() = entries.size

    override fun pageName(pageIndex: Int): String {
        return entries[pageIndex].name
    }

    override fun mediaType(pageIndex: Int): String {
        return entries[pageIndex].mediaType
    }

    override fun openStream(pageIndex: Int): InputStream {
        val entry = entries[pageIndex]
        val zipEntry = zip.getEntry(entry.name)
            ?: throw IllegalStateException("Missing entry ${entry.name}")
        return zip.getInputStream(zipEntry)
    }

    override fun imageBytes(pageIndex: Int): ByteArray {
        val entry = entries[pageIndex]
        val zipEntry = zip.getEntry(entry.name)
            ?: throw IllegalStateException("Missing entry ${entry.name}")
        return zip.getInputStream(zipEntry).use { it.readBytes() }
    }

    override fun close() {
        try {
            zip.close()
        } catch (_: Exception) {}
    }

    private data class ComicInfoResult(val title: String?, val bookmarks: List<ComicBookmark>)

    private fun parseComicInfo(): ComicInfoResult {
        val comicInfoEntry = zip.entries().asSequence()
            .firstOrNull { e ->
                !e.isDirectory && (
                    e.name.equals("ComicInfo.xml", ignoreCase = true) ||
                    e.name.endsWith("/ComicInfo.xml", ignoreCase = true)
                )
            } ?: return ComicInfoResult(null, emptyList())

        return try {
            val doc = zip.getInputStream(comicInfoEntry).use { stream ->
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream)
            }

            var parsedTitle: String? = null
            val titleNodes = doc.getElementsByTagName("Title")
            if (titleNodes.length > 0 && titleNodes.item(0).textContent.isNotBlank()) {
                parsedTitle = titleNodes.item(0).textContent.trim()
            }

            val seriesNodes = doc.getElementsByTagName("Series")
            val numberNodes = doc.getElementsByTagName("Number")
            if (seriesNodes.length > 0 && seriesNodes.item(0).textContent.isNotBlank()) {
                val series = seriesNodes.item(0).textContent.trim()
                val num = if (numberNodes.length > 0 && numberNodes.item(0).textContent.isNotBlank()) {
                    " #${numberNodes.item(0).textContent.trim()}"
                } else ""
                parsedTitle = if (parsedTitle != null) "$series$num - $parsedTitle" else "$series$num"
            }

            val pageNodes = doc.getElementsByTagName("Page")
            val bookmarkList = mutableListOf<ComicBookmark>()
            for (i in 0 until pageNodes.length) {
                val page = pageNodes.item(i) as? Element ?: continue
                val bookmark = page.getAttribute("Bookmark").takeIf { it.isNotBlank() } ?: continue
                val imageIndex = page.getAttribute("Image").toIntOrNull() ?: continue
                if (imageIndex in 0 until entries.size) {
                    bookmarkList.add(ComicBookmark(pageIndex = imageIndex, title = bookmark))
                }
            }

            ComicInfoResult(parsedTitle, bookmarkList)
        } catch (_: Exception) {
            ComicInfoResult(null, emptyList())
        }
    }

    private data class Entry(val name: String, val mediaType: String)
}
