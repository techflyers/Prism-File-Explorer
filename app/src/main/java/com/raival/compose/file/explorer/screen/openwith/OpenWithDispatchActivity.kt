package com.raival.compose.file.explorer.screen.openwith

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.base.BaseActivity
import com.raival.compose.file.explorer.common.MimeTypeDetector
import com.raival.compose.file.explorer.common.magika.MagikaFileTypeDetector
import com.raival.compose.file.explorer.common.resolveUriToPath
import com.raival.compose.file.explorer.common.ui.SafeSurface
import com.raival.compose.file.explorer.screen.main.MainActivity
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.viewer.audio.AudioPlayerActivity
import com.raival.compose.file.explorer.screen.viewer.document.DocumentViewerActivity
import com.raival.compose.file.explorer.screen.viewer.html.HtmlViewerActivity
import com.raival.compose.file.explorer.screen.viewer.image.ImageViewerActivity
import com.raival.compose.file.explorer.screen.viewer.latex.LatexViewerActivity
import com.raival.compose.file.explorer.screen.viewer.markdown.MarkdownViewerActivity
import com.raival.compose.file.explorer.screen.viewer.pdf.PdfViewerActivity
import com.raival.compose.file.explorer.screen.viewer.text.TextViewerActivity
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerActivity
import com.raival.compose.file.explorer.theme.FileExplorerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OpenWithDispatchActivity : BaseActivity() {

    companion object {
        const val EXTRA_OPEN_ARCHIVE = "extra_open_archive"
        const val EXTRA_OPEN_APK = "extra_open_apk"
        const val EXTRA_OPEN_PREFS = "extra_open_prefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
    }

    override fun onPermissionGranted() {
        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        setContent {
            FileExplorerTheme {
                SafeSurface {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.opening_file),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            dispatchUri(uri)
        }
    }

    private suspend fun dispatchUri(uri: Uri) {
        val (fileName, localFile) = withContext(Dispatchers.IO) {
            resolveFileAndName(uri)
        }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = intent.type ?: getMimeType(uri, localFile, extension)

        withContext(Dispatchers.Main) {
            routeFile(uri, fileName, extension, mimeType, localFile)
        }
    }

    private fun resolveFileAndName(uri: Uri): Pair<String, File?> {
        var resolvedName: String? = null
        var localFile: File? = null

        if (uri.scheme == "file") {
            val path = uri.path
            if (path != null) {
                localFile = File(path)
                resolvedName = localFile.name
            }
        }

        if (localFile == null || !localFile.exists()) {
            val resolvedPath = resolveUriToPath(this, uri, null)
            if (resolvedPath.isNotEmpty()) {
                val candidate = File(resolvedPath)
                if (candidate.exists()) {
                    localFile = candidate
                    resolvedName = candidate.name
                }
            }
        }

        if (resolvedName.isNullOrEmpty() && uri.scheme == "content") {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            resolvedName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (resolvedName.isNullOrEmpty()) {
            resolvedName = uri.lastPathSegment ?: "unknown_file"
        }

        return Pair(resolvedName!!, localFile)
    }

    private fun getMimeType(uri: Uri, file: File?, extension: String): String {
        if (uri.scheme == "content") {
            val type = contentResolver.getType(uri)
            if (!type.isNullOrEmpty()) return type
        }

        if (extension.isNotEmpty()) {
            val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (!type.isNullOrEmpty()) return type
        }

        if (file != null && file.exists()) {
            val detected = MimeTypeDetector.detect(file)
            if (detected != null) return detected.mimeType
        }

        return "application/octet-stream"
    }

    private suspend fun routeFile(
        uri: Uri,
        fileName: String,
        extension: String,
        mimeType: String,
        localFile: File?
    ) {
        val lowerExt = extension.lowercase()

        // 1. PDF
        if (lowerExt == "pdf" || mimeType == "application/pdf") {
            startViewer(PdfViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 2. Images
        if (FileMimeType.imageFileType.contains(lowerExt) ||
            FileMimeType.vectorFileType.contains(lowerExt) ||
            mimeType.startsWith("image/")
        ) {
            startViewer(ImageViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 3. Audio
        if (FileMimeType.audioFileType.contains(lowerExt) || mimeType.startsWith("audio/")) {
            startViewer(AudioPlayerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 4. Video
        if (FileMimeType.videoFileType.contains(lowerExt) || mimeType.startsWith("video/")) {
            startViewer(VideoPlayerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 5. LaTeX
        if (FileMimeType.latexFileType.contains(lowerExt) ||
            mimeType == "text/x-tex" || mimeType == "application/x-latex"
        ) {
            startViewer(LatexViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 6. Markdown
        if (lowerExt == "md" || lowerExt == "markdown" ||
            mimeType == "text/markdown" || mimeType == "text/x-markdown"
        ) {
            startViewer(MarkdownViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 7. HTML
        if (FileMimeType.htmlFileType.contains(lowerExt) || mimeType == "text/html") {
            startViewer(HtmlViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 8. Office Documents
        if (FileMimeType.officeFileType.contains(lowerExt) ||
            lowerExt in setOf("odt", "ods", "odp") ||
            mimeType in setOf(
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )
        ) {
            startViewer(DocumentViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 9. APK & APK Bundles -> route to MainActivity APK preview/dialog
        if (FileMimeType.apkFileType == lowerExt ||
            FileMimeType.apkBundleFileType.contains(lowerExt) ||
            mimeType == "application/vnd.android.package-archive"
        ) {
            val targetFile = ensureLocalFile(uri, fileName, localFile)
            if (targetFile != null) {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_APK, targetFile.absolutePath)
                }
                startActivity(mainIntent)
            } else {
                Toast.makeText(this, R.string.invalid_apk_file, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        // 10. Prism Preferences -> route to MainActivity import dialog
        if (FileMimeType.prismPrefsFileType == lowerExt) {
            val targetFile = ensureLocalFile(uri, fileName, localFile)
            if (targetFile != null) {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_PREFS, targetFile.absolutePath)
                }
                startActivity(mainIntent)
            } else {
                Toast.makeText(this, R.string.invalid_preferences_file, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        // 11. Archive formats -> route to MainActivity archive browser (ZipManager)
        if (FileMimeType.supportedArchiveFileType.contains(lowerExt) ||
            FileMimeType.archiveFileType.contains(lowerExt) ||
            isTarCompressed(fileName) ||
            isArchiveMimeType(mimeType)
        ) {
            val targetFile = ensureLocalFile(uri, fileName, localFile)
            if (targetFile != null) {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_ARCHIVE, targetFile.absolutePath)
                }
                startActivity(mainIntent)
            } else {
                Toast.makeText(this, R.string.invalid_zip, Toast.LENGTH_SHORT).show()
            }
            finish()
            return
        }

        // 12. Text & Code files -> route to TextViewerActivity
        if (FileMimeType.codeFileType.contains(lowerExt) ||
            FileMimeType.editableFileType.contains(lowerExt) ||
            MimeTypeDetector.isTextMimeType(mimeType) ||
            mimeType.startsWith("text/")
        ) {
            startViewer(TextViewerActivity::class.java, uri, mimeType, localFile)
            return
        }

        // 13. Magika Content-Based Fallback
        val ensuredFile = ensureLocalFile(uri, fileName, localFile)
        if (ensuredFile != null) {
            val detected = MagikaFileTypeDetector.detect(ensuredFile)
            if (detected != null) {
                val label = detected.label
                val detectedMime = detected.mimeType
                when {
                    label in setOf("html") || detectedMime == "text/html" -> {
                        startViewer(HtmlViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    label in setOf("markdown") || detectedMime == "text/markdown" -> {
                        startViewer(MarkdownViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    label in setOf("latex") || detectedMime.contains("tex") -> {
                        startViewer(LatexViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    label in setOf("pdf") || detectedMime == "application/pdf" -> {
                        startViewer(PdfViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    detectedMime.startsWith("image/") || label in setOf("png", "jpeg", "gif", "webp", "bmp", "tiff", "svg", "ico") -> {
                        startViewer(ImageViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    detectedMime.startsWith("video/") || label in setOf("mp4", "mkv", "webm", "flv", "3gp") -> {
                        startViewer(VideoPlayerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    detectedMime.startsWith("audio/") || label in setOf("mp3", "wav", "ogg", "flac", "midi") -> {
                        startViewer(AudioPlayerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    label in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp") -> {
                        startViewer(DocumentViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                    detectedMime.contains("zip") || detectedMime.contains("archive") ||
                            label in setOf("zip", "jar", "apk", "gzip", "bzip", "sevenzip", "rar", "tar", "xz", "iso", "dmg", "cab", "zst", "zstd", "lz4") -> {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(EXTRA_OPEN_ARCHIVE, ensuredFile.absolutePath)
                        }
                        startActivity(mainIntent)
                        finish()
                        return
                    }
                    detected.isText || MimeTypeDetector.isTextMimeType(detectedMime) -> {
                        startViewer(TextViewerActivity::class.java, uri, detectedMime, ensuredFile)
                        return
                    }
                }
            }
        }

        // 14. Ultimate Fallback: Try Text Viewer
        startViewer(TextViewerActivity::class.java, uri, mimeType, localFile)
    }

    private fun startViewer(
        activityClass: Class<*>,
        uri: Uri,
        mimeType: String,
        localFile: File?
    ) {
        val viewerIntent = Intent(this, activityClass).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(uri, mimeType)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            localFile?.let { putExtra("extra_file_path", it.absolutePath) }
        }
        startActivity(viewerIntent)
        finish()
    }

    private suspend fun ensureLocalFile(uri: Uri, fileName: String, existingFile: File?): File? {
        if (existingFile != null && existingFile.exists()) return existingFile

        return withContext(Dispatchers.IO) {
            try {
                val tempDir = File(cacheDir, "open_with_cache").apply { mkdirs() }
                val targetFile = File(tempDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetFile.exists()) targetFile else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun isTarCompressed(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".tar.gz") || lower.endsWith(".tar.bz2") ||
                lower.endsWith(".tar.bzip2") || lower.endsWith(".tar.xz") ||
                lower.endsWith(".tar.zst") || lower.endsWith(".tar.zstd") ||
                lower.endsWith(".tar.lz4") || lower.endsWith(".tar.lz") ||
                lower.endsWith(".tgz") || lower.endsWith(".tbz2") ||
                lower.endsWith(".tbz") || lower.endsWith(".txz") ||
                lower.endsWith(".tzst") || lower.endsWith(".tpz")
    }

    private fun isArchiveMimeType(mime: String): Boolean {
        return mime.contains("zip") || mime.contains("archive") ||
                mime.contains("7z") || mime.contains("rar") ||
                mime.contains("tar") || mime.contains("gzip") ||
                mime.contains("bzip") || mime.contains("xz") ||
                mime.contains("iso") || mime.contains("zstd") ||
                mime.contains("lz4")
    }
}
