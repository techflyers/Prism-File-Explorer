package com.raival.compose.file.explorer.comic

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Comic archive implementation for CBR (RAR-of-images).
 * Uses bundled [ArchiveManager] (lib7za) for extraction with on-demand page caching.
 */
class CbrArchive(val file: File) : ComicArchive {

    private val entries: List<Entry>
    override val title: String = file.nameWithoutExtension
    override val bookmarks: List<ComicBookmark> = emptyList()

    private val cacheDir: File by lazy {
        val baseDir = try {
            globalClass?.cleanOnExitDir?.file
        } catch (_: Throwable) {
            null
        } ?: File(System.getProperty("java.io.tmpdir", "/tmp"), "prism_cbr_cache")
        val hash = (file.absolutePath + file.lastModified()).hashCode().toString()
        File(baseDir, "cbr_$hash").apply { mkdirs() }
    }

    init {

        val archiveEntries = runBlocking {
            ArchiveManager.listArchive(file.absolutePath)
        }

        entries = archiveEntries
            .filter { !it.isDirectory }
            .filter { !ComicArchive.isExcluded(it.path) }
            .filter { ComicArchive.isImageFile(it.path) }
            .map { entry ->
                val ext = entry.path.substringAfterLast('.', "").lowercase()
                val media = ComicArchive.IMAGE_EXTENSIONS[ext] ?: "image/jpeg"
                Entry(entry.path, media)
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
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
        val targetFile = ensureExtracted(pageIndex)
        return FileInputStream(targetFile)
    }

    override fun imageBytes(pageIndex: Int): ByteArray {
        val targetFile = ensureExtracted(pageIndex)
        return targetFile.readBytes()
    }

    private fun ensureExtracted(pageIndex: Int): File {
        val entry = entries[pageIndex]
        val cachedFile = File(cacheDir, entry.name)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            return cachedFile
        }

        cachedFile.parentFile?.mkdirs()
        runBlocking {
            ArchiveManager.extractSingleFile(
                archivePath = file.absolutePath,
                internalPath = entry.name,
                destinationDir = cacheDir.absolutePath
            )
        }

        if (!cachedFile.exists()) {
            // Some 7za versions extract flattened or with different separator
            val flatFile = File(cacheDir, entry.name.substringAfterLast('/'))
            if (flatFile.exists()) return flatFile
        }

        return cachedFile
    }

    override fun close() {
        // Cached pages in cleanOnExitDir will be cleaned up automatically on app exit
    }

    private data class Entry(val name: String, val mediaType: String)
}
