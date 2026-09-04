package com.raival.compose.file.explorer.screen.viewer.comic

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.comic.ComicArchive
import com.raival.compose.file.explorer.comic.ComicArchiveFactory
import com.raival.compose.file.explorer.comic.ComicBookmark
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ComicFitMode(val label: String) {
    FIT_WHOLE("Fit Whole"),
    FIT_WIDTH("Fit Width"),
    FIT_HEIGHT("Fit Height")
}

enum class ComicReadingDirection(val label: String) {
    LTR("Left-to-Right"),
    RTL("Right-to-Left (Manga)")
}

class ComicViewerInstance(
    override val uri: Uri,
    override val id: String,
    initialPath: String? = null
) : ViewerInstance {

    var archive: ComicArchive? = null
        private set

    var title by mutableStateOf<String>("Comic Viewer")
        private set

    var pageCount by mutableIntStateOf(0)
        private set

    var currentPage by mutableIntStateOf(0)

    var fitMode by mutableStateOf(ComicFitMode.FIT_WHOLE)

    var readingDirection by mutableStateOf(ComicReadingDirection.LTR)

    var bookmarks by mutableStateOf<List<ComicBookmark>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var localFile: File? = null
    private var isTempFile = false

    init {
        if (!initialPath.isNullOrEmpty()) {
            val f = File(initialPath)
            if (f.exists() && f.isFile) {
                localFile = f
            }
        }
    }

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        isLoading = true
        errorMessage = null

        try {
            val targetFile = resolveLocalFile(context)
            if (targetFile == null || !targetFile.exists()) {
                errorMessage = "Could not open comic file from $uri"
                isLoading = false
                return@withContext
            }

            val openedArchive = ComicArchiveFactory.open(targetFile)
            if (openedArchive.pageCount == 0) {
                openedArchive.close()
                errorMessage = "No images found inside comic archive"
                isLoading = false
                return@withContext
            }

            archive = openedArchive
            title = openedArchive.title ?: targetFile.nameWithoutExtension
            pageCount = openedArchive.pageCount
            bookmarks = openedArchive.bookmarks
            currentPage = 0
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load comic archive"
            isLoading = false
        }
    }

    fun getPageBytes(index: Int): ByteArray? {
        val arch = archive ?: return null
        if (index !in 0 until arch.pageCount) return null
        return try {
            arch.imageBytes(index)
        } catch (_: Exception) {
            null
        }
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

            // Copy content stream to temp file
            try {
                val tempDir = File(context.cacheDir, "comic_cache").apply { mkdirs() }
                val tempName = "comic_${System.currentTimeMillis()}.${uri.lastPathSegment?.substringAfterLast('.', "cbz") ?: "cbz"}"
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
            archive?.close()
        } catch (_: Exception) {}
        archive = null

        if (isTempFile) {
            try {
                localFile?.delete()
            } catch (_: Exception) {}
        }
    }
}
