package com.raival.compose.file.explorer.comic

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Comic archive implementation for CB7 (7z-of-images).
 * Uses Apache Commons Compress [SevenZFile] with fallback to native [ArchiveManager] (lib7za).
 */
class Cb7Archive(val file: File) : ComicArchive {

    private val entries: List<Entry>
    override val title: String = file.nameWithoutExtension
    override val bookmarks: List<ComicBookmark> = emptyList()

    private val cacheDir: File by lazy {
        val baseDir = try {
            globalClass?.cleanOnExitDir?.file
        } catch (_: Throwable) {
            null
        } ?: File(System.getProperty("java.io.tmpdir", "/tmp"), "prism_cb7_cache")
        val hash = (file.absolutePath + file.lastModified()).hashCode().toString()
        File(baseDir, "cb7_$hash").apply { mkdirs() }
    }

    init {
        var entryList: List<Entry>? = null

        // Attempt 1: Commons Compress SevenZFile
        try {
            val list = mutableListOf<Entry>()
            SevenZFile.builder().setFile(file).get().use { sevenZ ->
                for (entry in sevenZ.entries) {
                    if (!entry.isDirectory &&
                        !ComicArchive.isExcluded(entry.name) &&
                        ComicArchive.isImageFile(entry.name)
                    ) {
                        val ext = entry.name.substringAfterLast('.', "").lowercase()
                        val media = ComicArchive.IMAGE_EXTENSIONS[ext] ?: "image/jpeg"
                        list.add(Entry(entry.name, media))
                    }
                }
            }
            entryList = list
        } catch (_: Throwable) {
            // Fallback to ArchiveManager (lib7za)
        }

        // Attempt 2: ArchiveManager (lib7za)
        if (entryList == null || entryList.isEmpty()) {
            try {
                val archiveEntries = runBlocking {
                    ArchiveManager.listArchive(file.absolutePath)
                }
                entryList = archiveEntries
                    .filter { !it.isDirectory }
                    .filter { !ComicArchive.isExcluded(it.path) }
                    .filter { ComicArchive.isImageFile(it.path) }
                    .map { entry ->
                        val ext = entry.path.substringAfterLast('.', "").lowercase()
                        val media = ComicArchive.IMAGE_EXTENSIONS[ext] ?: "image/jpeg"
                        Entry(entry.path, media)
                    }
            } catch (_: Throwable) {
                entryList = emptyList()
            }
        }

        entries = (entryList ?: emptyList()).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
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
        val cached = File(cacheDir, entry.name)
        if (cached.exists() && cached.length() > 0) {
            return FileInputStream(cached)
        }
        val flatFile = File(cacheDir, entry.name.substringAfterLast('/'))
        if (flatFile.exists() && flatFile.length() > 0) {
            return FileInputStream(flatFile)
        }
        return ByteArrayInputStream(imageBytes(pageIndex))
    }

    override fun imageBytes(pageIndex: Int): ByteArray {
        val targetName = entries[pageIndex].name

        // Check cache first
        val cached = File(cacheDir, targetName)
        if (cached.exists() && cached.length() > 0) {
            return cached.readBytes()
        }
        val flatFile = File(cacheDir, targetName.substringAfterLast('/'))
        if (flatFile.exists() && flatFile.length() > 0) {
            return flatFile.readBytes()
        }

        // Attempt 1: SevenZFile
        try {
            SevenZFile.builder().setFile(file).get().use { sevenZ ->
                for (entry in sevenZ.entries) {
                    if (entry.name == targetName) {
                        val bytes = sevenZ.getInputStream(entry).use { it.readBytes() }
                        // Write to cache for subsequent accesses
                        try {
                            cached.parentFile?.mkdirs()
                            cached.writeBytes(bytes)
                        } catch (_: Throwable) {}
                        return bytes
                    }
                }
            }
        } catch (_: Throwable) {
            // Fallback to ArchiveManager
        }

        // Attempt 2: Extract via ArchiveManager (lib7za)
        try {
            cached.parentFile?.mkdirs()
            runBlocking {
                ArchiveManager.extractSingleFile(
                    archivePath = file.absolutePath,
                    internalPath = targetName,
                    destinationDir = cacheDir.absolutePath
                )
            }
            if (cached.exists() && cached.length() > 0) {
                return cached.readBytes()
            }
            if (flatFile.exists() && flatFile.length() > 0) {
                return flatFile.readBytes()
            }
        } catch (_: Throwable) {}

        throw IllegalStateException("Cannot extract entry $targetName from ${file.name}")
    }

    override fun close() {
        // Cached pages in cleanOnExitDir are cleaned up when the app exits
    }

    private data class Entry(val name: String, val mediaType: String)
}
