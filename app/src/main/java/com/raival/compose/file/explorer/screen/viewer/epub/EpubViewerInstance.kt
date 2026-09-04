package com.raival.compose.file.explorer.screen.viewer.epub

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.ebook.EbookBookmark
import com.raival.compose.file.explorer.ebook.EbookChapter
import com.raival.compose.file.explorer.ebook.EbookDocument
import com.raival.compose.file.explorer.ebook.EbookParserFactory
import com.raival.compose.file.explorer.ebook.EbookPreferences
import com.raival.compose.file.explorer.epub.EpubAssetStreamer
import com.raival.compose.file.explorer.epub.EpubTheme
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class EpubViewerInstance(
    override val uri: Uri,
    override val id: String,
    initialPath: String? = null
) : ViewerInstance {

    var book: EbookDocument? = null
        private set

    var currentChapterIndex by mutableIntStateOf(0)
    var initialScrollPercentage by mutableFloatStateOf(0f)

    // Reading Settings & Styles
    var theme by mutableStateOf(EpubTheme.LIGHT)
    var fontSizePercent by mutableIntStateOf(115)
    var fontFamily by mutableStateOf("sans-serif")
    var lineHeight by mutableFloatStateOf(1.65f)
    var isPaged by mutableStateOf(false)
    var marginHorizontal by mutableIntStateOf(20)
    var textAlign by mutableStateOf("justify")

    // In-Book Search
    var isSearchActive by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var searchMatchCount by mutableIntStateOf(0)
    var currentMatchIndex by mutableIntStateOf(0)

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var localFile: File? = null
    private var isTempFile = false
    private var bookKey: String = ""

    init {
        if (!initialPath.isNullOrEmpty()) {
            val f = File(initialPath)
            if (f.exists() && f.isFile) {
                localFile = f
                bookKey = f.absolutePath
            }
        }
        if (bookKey.isEmpty()) {
            bookKey = uri.toString()
        }
    }

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        isLoading = true
        errorMessage = null

        // Load saved reader preferences
        theme = EbookPreferences.getTheme(context)
        fontSizePercent = EbookPreferences.getFontSizePercent(context)
        fontFamily = EbookPreferences.getFontFamily(context)
        lineHeight = EbookPreferences.getLineHeight(context)
        isPaged = EbookPreferences.isPaged(context)
        marginHorizontal = EbookPreferences.getMarginHorizontal(context)
        textAlign = EbookPreferences.getTextAlign(context)

        try {
            val targetFile = resolveLocalFile(context)
            if (targetFile == null || !targetFile.exists()) {
                errorMessage = "Could not open document from $uri"
                isLoading = false
                return@withContext
            }

            bookKey = targetFile.absolutePath
            val parsedBook = EbookParserFactory.parse(targetFile)
            if (parsedBook.chapters.isEmpty()) {
                parsedBook.close()
                errorMessage = "No readable content or chapters found in book"
                isLoading = false
                return@withContext
            }

            book = parsedBook

            // Restore saved reading position
            val (savedChapter, savedScroll) = EbookPreferences.getReadingPosition(context, bookKey)
            currentChapterIndex = savedChapter.coerceIn(0, (parsedBook.chapters.size - 1).coerceAtLeast(0))
            initialScrollPercentage = savedScroll

            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load document"
            isLoading = false
        }
    }

    val currentChapter: EbookChapter?
        get() {
            val b = book ?: return null
            if (currentChapterIndex !in 0 until b.chapters.size) return null
            return b.chapters[currentChapterIndex]
        }

    fun getCurrentChapterStyledHtml(): String? {
        val b = book ?: return null
        val ch = currentChapter ?: return null
        return try {
            val rawHtml = b.getChapterHtml(ch)
            EpubAssetStreamer.injectStyles(
                html = rawHtml,
                theme = theme,
                fontSizePercent = fontSizePercent,
                fontFamily = fontFamily,
                lineHeight = lineHeight,
                textAlign = textAlign,
                marginHorizontal = marginHorizontal,
                isPaged = isPaged
            )
        } catch (_: Exception) {
            null
        }
    }

    fun savePosition(context: Context, scrollPercentage: Float) {
        if (bookKey.isNotEmpty()) {
            EbookPreferences.saveReadingPosition(context, bookKey, currentChapterIndex, scrollPercentage)
        }
    }

    fun getBookmarks(context: Context): List<EbookBookmark> {
        return EbookPreferences.getBookmarks(context, bookKey)
    }

    fun addBookmark(context: Context, scrollPercentage: Float, customTitle: String? = null) {
        val ch = currentChapter
        val title = customTitle ?: (ch?.title ?: "Chapter ${currentChapterIndex + 1}")
        val bookmark = EbookBookmark(
            chapterIndex = currentChapterIndex,
            scrollPercentage = scrollPercentage,
            title = title
        )
        EbookPreferences.addBookmark(context, bookKey, bookmark)
    }

    fun removeBookmark(context: Context, bookmark: EbookBookmark) {
        EbookPreferences.removeBookmark(context, bookKey, bookmark)
    }

    fun updateTheme(context: Context, newTheme: EpubTheme) {
        theme = newTheme
        EbookPreferences.saveTheme(context, newTheme)
    }

    fun updateFontSize(context: Context, percent: Int) {
        fontSizePercent = percent
        EbookPreferences.saveFontSizePercent(context, percent)
    }

    fun updateFontFamily(context: Context, family: String) {
        fontFamily = family
        EbookPreferences.saveFontFamily(context, family)
    }

    fun updateLineHeight(context: Context, height: Float) {
        lineHeight = height
        EbookPreferences.saveLineHeight(context, height)
    }

    fun updatePaged(context: Context, paged: Boolean) {
        isPaged = paged
        EbookPreferences.savePaged(context, paged)
    }

    fun updateMargin(context: Context, margin: Int) {
        marginHorizontal = margin
        EbookPreferences.saveMarginHorizontal(context, margin)
    }

    fun updateTextAlign(context: Context, align: String) {
        textAlign = align
        EbookPreferences.saveTextAlign(context, align)
    }

    private fun resolveLocalFile(context: Context): File? {
        localFile?.let { if (it.exists()) return it }

        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val f = File(path)
                if (f.exists()) {
                    localFile = f
                    return f
                }
            }
        }

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    val pathIndex = cursor.getColumnIndex("_data")
                    if (pathIndex >= 0 && cursor.moveToFirst()) {
                        val path = cursor.getString(pathIndex)
                        if (!path.isNullOrEmpty()) {
                            val f = File(path)
                            if (f.exists()) {
                                localFile = f
                                return f
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val tempDir = File(context.cacheDir, "ebook_cache").apply { mkdirs() }
                val ext = uri.lastPathSegment?.substringAfterLast('.', "epub") ?: "epub"
                val tempName = "book_${System.currentTimeMillis()}.$ext"
                val tempFile = File(tempDir, tempName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    localFile = tempFile
                    isTempFile = true
                    return tempFile
                }
            } catch (_: Exception) {}
        }

        return null
    }

    override fun onClose() {
        try {
            book?.close()
        } catch (_: Exception) {}
        book = null

        if (isTempFile) {
            try {
                localFile?.delete()
            } catch (_: Exception) {}
        }
    }
}
