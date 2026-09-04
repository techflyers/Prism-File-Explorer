package com.raival.compose.file.explorer.screen.main.tab.files.misc

import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.archiveFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.comicFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.documentFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.epubFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.supportedArchiveFileType
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicAndEpubMimeTypeTest {

    @Test
    fun testComicAndEpubCategories() {
        // Verify comic file extensions
        assertTrue(comicFileType.contains("cbz"))
        assertTrue(comicFileType.contains("cbr"))
        assertTrue(comicFileType.contains("cb7"))
        assertTrue(comicFileType.contains("cbt"))

        // Verify epub extension
        assertEquals("epub", epubFileType)

        // Verify document categorization includes both
        assertTrue(documentFileType.contains("epub"))
        assertTrue(documentFileType.contains("cbz"))
        assertTrue(documentFileType.contains("cbr"))
        assertTrue(documentFileType.contains("cb7"))
        assertTrue(documentFileType.contains("cbt"))

        // Verify archive categorization includes comic formats and epub
        assertTrue(archiveFileType.contains("cbz"))
        assertTrue(archiveFileType.contains("cbr"))
        assertTrue(archiveFileType.contains("cb7"))
        assertTrue(archiveFileType.contains("cbt"))
        assertTrue(archiveFileType.contains("epub"))

        assertTrue(supportedArchiveFileType.contains("cbz"))
        assertTrue(supportedArchiveFileType.contains("cbr"))
        assertTrue(supportedArchiveFileType.contains("cb7"))
        assertTrue(supportedArchiveFileType.contains("cbt"))
        assertTrue(supportedArchiveFileType.contains("epub"))

        // Verify ArchiveManager native extensions via isNativeArchive
        assertTrue(ArchiveManager.isNativeArchive("cbz"))
        assertTrue(ArchiveManager.isNativeArchive("cbr"))
        assertTrue(ArchiveManager.isNativeArchive("cb7"))
        assertTrue(ArchiveManager.isNativeArchive("cbt"))
        assertTrue(ArchiveManager.isNativeArchive("epub"))
    }
}
