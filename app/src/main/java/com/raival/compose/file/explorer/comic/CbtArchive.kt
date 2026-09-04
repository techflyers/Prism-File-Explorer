package com.raival.compose.file.explorer.comic

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Comic archive implementation for CBT (TAR-of-images).
 * Uses Apache Commons Compress [TarArchiveInputStream].
 */
class CbtArchive(val file: File) : ComicArchive {

    private val entries: List<Entry>
    override val title: String = file.nameWithoutExtension
    override val bookmarks: List<ComicBookmark> = emptyList()

    init {
        val entryList = mutableListOf<Entry>()
        TarArchiveInputStream(file.inputStream().buffered()).use { tar ->
            var entry: TarArchiveEntry? = tar.nextEntry
            while (entry != null) {
                if (!entry.isDirectory &&
                    !ComicArchive.isExcluded(entry.name) &&
                    ComicArchive.isImageFile(entry.name)
                ) {
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    val media = ComicArchive.IMAGE_EXTENSIONS[ext] ?: "image/jpeg"
                    entryList.add(Entry(entry.name, media))
                }
                entry = tar.nextEntry
            }
        }
        entries = entryList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
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
        return ByteArrayInputStream(imageBytes(pageIndex))
    }

    override fun imageBytes(pageIndex: Int): ByteArray {
        val targetName = entries[pageIndex].name
        TarArchiveInputStream(file.inputStream().buffered()).use { tar ->
            var entry: TarArchiveEntry? = tar.nextEntry
            while (entry != null) {
                if (entry.name == targetName) {
                    return tar.readBytes()
                }
                entry = tar.nextEntry
            }
        }
        throw IllegalStateException("Missing entry $targetName in ${file.name}")
    }

    override fun close() {
        // No persistent resources to hold
    }

    private data class Entry(val name: String, val mediaType: String)
}
