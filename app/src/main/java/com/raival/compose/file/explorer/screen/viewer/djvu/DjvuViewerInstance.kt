package com.raival.compose.file.explorer.screen.viewer.djvu

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DjvuViewerInstance(
    override val uri: Uri,
    override val id: String,
    initialPath: String? = null
) : ViewerInstance {

    var localFile: File? = null
        private set

    var fileName by mutableStateOf("")
        private set

    var fileSizeBytes by mutableStateOf(0L)
        private set

    var currentPage by mutableIntStateOf(1)
    var totalPages by mutableIntStateOf(1)
    var zoomScale by mutableFloatStateOf(1.0f)

    var isLoading by mutableStateOf(true)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var isTempFile = false

    init {
        if (!initialPath.isNullOrEmpty()) {
            val f = File(initialPath)
            if (f.exists() && f.isFile) {
                localFile = f
                fileName = f.name
                fileSizeBytes = f.length()
            }
        }
    }

    suspend fun load(context: Context) = withContext(Dispatchers.IO) {
        isLoading = true
        errorMessage = null

        try {
            val file = resolveLocalFile(context)
            if (file == null || !file.exists()) {
                errorMessage = "Could not open DJVU file from $uri"
                isLoading = false
                return@withContext
            }

            localFile = file
            fileName = file.name
            fileSizeBytes = file.length()
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to open DJVU document"
            isLoading = false
        }
    }

    private fun resolveLocalFile(context: Context): File? {
        localFile?.let { if (it.exists()) return it }

        if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val f = File(path)
                if (f.exists()) return f
            }
        }

        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, arrayOf("_data", "_display_name"), null, null, null)?.use { cursor ->
                    val pathIndex = cursor.getColumnIndex("_data")
                    if (pathIndex >= 0 && cursor.moveToFirst()) {
                        val path = cursor.getString(pathIndex)
                        if (!path.isNullOrEmpty()) {
                            val f = File(path)
                            if (f.exists()) return f
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val tempDir = File(context.cacheDir, "djvu_cache").apply { mkdirs() }
                val tempName = "doc_${System.currentTimeMillis()}.djvu"
                val tempFile = File(tempDir, tempName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    isTempFile = true
                    return tempFile
                }
            } catch (_: Exception) {}
        }

        return null
    }

    override fun onClose() {
        if (isTempFile) {
            try {
                localFile?.delete()
            } catch (_: Exception) {}
        }
        localFile = null
    }
}
