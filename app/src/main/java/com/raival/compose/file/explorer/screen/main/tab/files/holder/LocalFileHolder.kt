package com.raival.compose.file.explorer.screen.main.tab.files.holder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.anggrayudi.storage.file.getBasePath
import com.anggrayudi.storage.file.mimeType
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.MimeTypeDetector
import com.raival.compose.file.explorer.common.drawableToBitmap
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.fromJson
import com.raival.compose.file.explorer.common.hasParent
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.common.magika.MagikaFileTypeDetector
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.raival.compose.file.explorer.screen.main.tab.files.misc.DefaultOpeningMethods
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.anyFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.codeFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.editableFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.prismPrefsFileType
import com.raival.compose.file.explorer.screen.viewer.audio.AudioPlayerActivity
import com.raival.compose.file.explorer.screen.viewer.document.DocumentViewerActivity
import com.raival.compose.file.explorer.screen.viewer.html.HtmlViewerActivity
import com.raival.compose.file.explorer.screen.viewer.image.ImageViewerActivity
import com.raival.compose.file.explorer.screen.viewer.latex.LatexViewerActivity
import com.raival.compose.file.explorer.screen.viewer.markdown.MarkdownViewerActivity
import com.raival.compose.file.explorer.screen.viewer.pdf.PdfViewerActivity
import com.raival.compose.file.explorer.screen.viewer.video.VideoPlayerActivity
import kotlinx.coroutines.runBlocking
import java.io.File

class LocalFileHolder(file: File) : ContentHolder() {
    val file: File = if (file.absolutePath.contains("storage_root")) {
        var p = file.absolutePath
        if (p.startsWith("/storage_root")) {
            p = p.removePrefix("/storage_root")
        } else {
            p = p.substringAfter("storage_root")
        }
        File(p)
    } else {
        file
    }

    private var folderCount = 0
    private var fileCount = 0
    private var timestamp = -1L

    override val displayName: String by lazy { file.name }

    var details = emptyString

    override val isFolder: Boolean by lazy { file.isDirectory }

    override val lastModified: Long
        get() = file.lastModified().also {
            if (timestamp == -1L) timestamp = it
        }

    override val size: Long by lazy { file.length() }

    override val uniquePath: String by lazy { file.absolutePath }

    override val extension: String by lazy { file.extension.lowercase() }

    override val canAddNewContent: Boolean = true

    override val canRead: Boolean by lazy { file.canRead() }

    override val canWrite: Boolean by lazy { file.canWrite() }

    val mimeType by lazy { file.mimeType ?: anyFileType }

    val basePath by lazy { file.getBasePath(globalClass) }

    override suspend fun getDetails(): String {
        if (details.isNotEmpty()) return details

        // Right side: date formatted as DD/MM/YY • HH:MM
        val rightSide = lastModified.toFormattedDate(
            customFormat = "dd/MM/yy • HH:mm"
        )

        val prefs = globalClass.preferencesManager
        val leftSide = if (file.isDirectory) {
            if (prefs.showFolderContentCount && file.canRead()) {
                // Pass showHidden so hidden items are only counted when enabled
                val count = getContentCount(prefs.showHiddenFiles)
                buildString {
                    if (count.folders > 0) {
                        append("📁 ${count.folders}")
                        if (count.files > 0) append(" • ")
                    }
                    if (count.files > 0) {
                        append("📄 ${count.files}")
                    }
                    if (count.folders == 0 && count.files == 0) {
                        append(globalClass.getString(R.string.empty_folder))
                    }
                }
            } else ""
        } else {
            val sizeStr = file.length().toFormattedSize()
            val ext = file.extension.lowercase()
            // Only show extension in metadata when hide-extensions is ON
            val extLabel = if (prefs.hideFileExtensions && file.extension.isNotEmpty())
                file.extension.uppercase() else null

            when {
                // PDF: show page count
                ext == "pdf" -> {
                    val pages = getPdfPageCount()
                    if (pages > 0) "$sizeStr • $pages ${if (pages == 1) "page" else "pages"}"
                    else if (extLabel != null) "$sizeStr • $extLabel" else sizeStr
                }
                // Archives: show compression ratio
                ext in archiveExtensions -> {
                    val ratio = getArchiveCompressionRatio()
                    if (ratio != null) "$sizeStr • $ratio"
                    else if (extLabel != null) "$sizeStr • $extLabel" else sizeStr
                }
                // Default
                else -> if (extLabel != null) "$sizeStr • $extLabel" else sizeStr
            }
        }

        return "$leftSide\t$rightSide".also { details = it }
    }

    private val archiveExtensions by lazy { FileMimeType.archiveFileType.toSet() }

    private fun getPdfPageCount(): Int {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        } catch (_: Exception) { 0 }
    }

    private fun getArchiveCompressionRatio(): String? {
        if (!file.exists() || file.extension.lowercase() !in setOf("zip", "jar", "apk", "xapk")) return null
        return try {
            val zip = java.util.zip.ZipFile(file)
            var compressedTotal = 0L
            var uncompressedTotal = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                compressedTotal += entry.compressedSize.coerceAtLeast(0)
                uncompressedTotal += entry.size.coerceAtLeast(0)
            }
            zip.close()
            if (uncompressedTotal <= 0) return null
            val ratio = (compressedTotal.toDouble() / uncompressedTotal.toDouble()) * 100.0
            "${ratio.toInt()}% ratio"
        } catch (_: Exception) { null }
    }

    override suspend fun isValid(): Boolean {
        if (file.exists()) return true
        if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
            return com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.exists(file.absolutePath)
        }
        return false
    }

    override suspend fun listContent(): ArrayList<out ContentHolder> {
        folderCount = 0
        fileCount = 0

        // Handle Recycle Bin root listing
        if (file.absolutePath == globalClass.recycleBinDir.file.absolutePath) {
            val combinedList = ArrayList<LocalFileHolder>()
            file.listFiles()?.forEach { subDir ->
                if (subDir.isDirectory) {
                    val children = subDir.listFiles() ?: emptyArray()
                    val userFiles = children.filter { it.name != "metadata.json" }
                    if (userFiles.isEmpty()) {
                        subDir.deleteRecursively()
                    } else {
                        userFiles.forEach { child ->
                            combinedList.add(LocalFileHolder(child))
                        }
                    }
                }
            }
            combinedList.forEach {
                if (it.isFolder) folderCount++ else fileCount++
            }
            return combinedList
        }

        val list = file.listFiles()
        if (list != null) {
            // Also hide metadata.json if browsing a timestamp folder directly
            val filtered = list.filter { it.name != "metadata.json" }
            filtered.forEach {
                if (it.isDirectory) folderCount++ else fileCount++
            }
            return ArrayList(filtered.map { LocalFileHolder(it) })
        }

        if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
            val shizukuEntry = com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder.fromPath(file.absolutePath)
            val shizukuList = shizukuEntry.listContent()
            return ArrayList(shizukuList.filter { it.displayName != "metadata.json" })
        }

        return arrayListOf()
    }

    override suspend fun getParent(): ContentHolder? =
        file.parentFile?.let { LocalFileHolder(it) }

    override fun open(
        context: Context,
        anonymous: Boolean,
        skipSupportedExtensions: Boolean,
        customMimeType: String?
    ) {
        val defaultOpeningMethods =
            fromJson<DefaultOpeningMethods>(globalClass.preferencesManager.defaultOpeningMethods)
                ?: DefaultOpeningMethods()
        defaultOpeningMethods.openingMethods.forEach {
            if (it.extension == extension) {
                openFileWithPackage(context, it.packageName, it.className)
                return
            }
        }

        // Only run the built-in handler routing when no explicit customMimeType is given.
        // When customMimeType is provided (e.g. "Install" or "Browse content" from ApkPreviewDialog),
        // we must skip handleSupportedFiles so the caller-specified mime type is honoured.
        if (customMimeType == null && handleSupportedFiles(skipSupportedExtensions, context)) {
            return
        }

        Intent(Intent.ACTION_VIEW).let { newIntent ->
            newIntent.setDataAndType(
                createUri(),
                customMimeType ?: if (anonymous) anyFileType else file.mimeType
            )

            newIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            try {
                context.startActivity(newIntent)
            } catch (_: ActivityNotFoundException) {
                if (!anonymous) {
                    open(context, anonymous = true, skipSupportedExtensions = true, null)
                } else {
                    globalClass.showMsg(R.string.no_app_can_open_file)
                }
            } catch (e: Exception) {
                with(globalClass) {
                    logger.logError(e)
                    showMsg(getString(R.string.failed_to_open_this_file))
                }
            }
        }
    }

    override suspend fun getContentCount(): ContentCount = getContentCount(globalClass.preferencesManager.showHiddenFiles)

    suspend fun getContentCount(showHidden: Boolean): ContentCount {
        if (file.absolutePath == globalClass.recycleBinDir.file.absolutePath) {
            var files = 0
            var folders = 0
            file.listFiles()?.forEach { subDir ->
                if (subDir.isDirectory) {
                    subDir.listFiles()?.forEach { child ->
                        if (child.name != "metadata.json") {
                            val hidden = child.name.startsWith(".")
                            if (showHidden || !hidden) {
                                if (child.isDirectory) folders++ else files++
                            }
                        }
                    }
                }
            }
            fileCount = files
            folderCount = folders
            return ContentCount(fileCount, folderCount)
        }

        // Always recount with the current showHidden value
        fileCount = 0
        folderCount = 0
        file.listFiles()?.let { list ->
            list.forEach {
                if (it.name != "metadata.json") {
                    val hidden = it.name.startsWith(".")
                    if (showHidden || !hidden) {
                        if (it.isFile) fileCount++ else folderCount++
                    }
                }
            }
        }

        return ContentCount(fileCount, folderCount)
    }

    override suspend fun createSubFile(name: String, onCreated: (ContentHolder?) -> Unit) {
        File(file, name).let { newFile ->
            if (newFile.createNewFile()) {
                onCreated(LocalFileHolder(newFile))
                return
            }
            if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
                if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.createFile(newFile.absolutePath)) {
                    onCreated(LocalFileHolder(newFile))
                    return
                }
            }
        }
        onCreated(null)
    }

    override suspend fun createSubFolder(name: String, onCreated: (ContentHolder?) -> Unit) {
        File(file, name).let { newFolder ->
            if (newFolder.exists() || newFolder.mkdir()) {
                onCreated(LocalFileHolder(newFolder))
                return
            }
            if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
                if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.createDirectory(newFolder.absolutePath)) {
                    onCreated(LocalFileHolder(newFolder))
                    return
                }
            }
        }
        onCreated(null)
    }

    override suspend fun findFile(name: String): LocalFileHolder? {
        File(file, name).let {
            if (it.exists()) {
                return LocalFileHolder(it)
            }
        }
        return null
    }

    fun exists() = runBlocking { isValid() }

    fun hasSourceChanged() = timestamp isNot -1L && lastModified isNot timestamp

    fun resetCachedTimestamp() {
        timestamp = lastModified
    }

    fun getAppsHandlingFile(mimeType: String = emptyString): List<OpenWithActivityHolder> {
        val packageManager: PackageManager = globalClass.packageManager

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(createUri(), mimeType.ifEmpty { this@LocalFileHolder.mimeType })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }

        val appsList = ArrayList<OpenWithActivityHolder>()

        packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        ).onEach {
            globalClass.grantUriPermission(
                it.activityInfo.packageName,
                createUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            appsList.add(
                OpenWithActivityHolder(
                    label = it.activityInfo.loadLabel(packageManager).toString(),
                    name = it.activityInfo.name,
                    packageName = it.activityInfo.packageName,
                    icon = it.activityInfo.loadIcon(packageManager).drawableToBitmap(),
                )
            )
        }

        return appsList
    }

    fun openFileWithPackage(context: Context, packageName: String, className: String) {
        val uri = createUri()

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            setPackage(packageName)
            setClassName(packageName, className)
            // Pass the real file path so viewers can resolve the parent folder (e.g. audio queue)
            putExtra("extra_file_path", file.absolutePath)
        }

        if (intent.resolveActivity(globalClass.packageManager) != null) {
            context.startActivity(intent)
        } else {
            globalClass.showMsg("No app found to open this file.")
        }
    }

    fun writeText(text: String) {
        try {
            file.writeText(text)
        } catch (e: Exception) {
            if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
                if (!com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.writeText(file.absolutePath, text)) {
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    fun readText(): String {
        return try {
            file.readText()
        } catch (e: Exception) {
            if (com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.isPrivileged) {
                com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager.readText(file.absolutePath) ?: throw e
            } else {
                throw e
            }
        }
    }

    private fun handleSupportedFiles(skipSupportedExtensions: Boolean, context: Context): Boolean {
        if (prismPrefsFileType == extension) {
            val activeTab = globalClass.mainActivityManager.getActiveTab()
            if (activeTab is FilesTab) {
                activeTab.toggleImportPrefsDialog(this)
                return true
            }
        }

        if (isApk() || isApkBundle()) {
            val activeTab = globalClass.mainActivityManager.getActiveTab()
            if (activeTab is FilesTab) {
                activeTab.toggleApkDialog(this)
                return true
            }
        }

        if (FileMimeType.supportedArchiveFileType.contains(extension) || isTarCompressed()) {
            if (isApk() && skipSupportedExtensions) return false
            if (isApkBundle()) {
                // Skip opening as zip
            } else {
                globalClass.zipManager.openArchive(this)
                return true
            }
        }

        if (skipSupportedExtensions) return false

        val magika = MagikaFileTypeDetector.detect(file)
        val skipMagikaOverride = extension.isNotEmpty() &&
            globalClass.preferencesManager.disableMagikaExtOverride
        if (!skipMagikaOverride && magika != null && magika.probability >= 0.55f) {
            val detectedExt = magika.suggestedExtension.lowercase()
            val extensionMissingOrWrong = extension.isEmpty() ||
                (detectedExt.isNotEmpty() && !extensionsEquivalent(extension, detectedExt))
            if (extensionMissingOrWrong && openByDetectedType(magika, context)) {
                return true
            }
        }

        // LaTeX files → dedicated LaTeX viewer with tectonic compilation
        if (FileMimeType.latexFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                LatexViewerActivity::class.java.name
            )
            return true
        }

        // Markdown → dedicated Markdown viewer
        if (extension == "md" || extension == "markdown") {
            openFileWithPackage(
                context,
                context.packageName,
                MarkdownViewerActivity::class.java.name
            )
            return true
        }

        // HTML → dedicated HTML viewer
        if (FileMimeType.htmlFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                HtmlViewerActivity::class.java.name
            )
            return true
        }

        // Office documents → dedicated document viewer
        if (FileMimeType.officeFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                DocumentViewerActivity::class.java.name
            )
            return true
        }

        if (codeFileType.contains(extension) || editableFileType.contains(extension)) {
            globalClass.textEditorManager.openTextEditor(
                this,
                context
            )
            return true
        }

        if (FileMimeType.videoFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                VideoPlayerActivity::class.java.name
            )
            return true
        }

        if (FileMimeType.audioFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                AudioPlayerActivity::class.java.name
            )
            return true
        }

        if (FileMimeType.imageFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                ImageViewerActivity::class.java.name
            )
            return true
        }

        if (FileMimeType.pdfFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                PdfViewerActivity::class.java.name
            )
            return true
        }

        // MIME / Magika fallback for missing/rare/wrong extensions
        if (extension.isEmpty() || !hasKnownExtension()) {
            val detected = MagikaFileTypeDetector.detect(file)
            if (detected != null && openByDetectedType(detected, context)) {
                return true
            }
        }

        return false
    }

    private fun openByDetectedType(
        detected: MagikaFileTypeDetector.Result,
        context: Context
    ): Boolean {
        val mime = detected.mimeType
        val label = detected.label
        when {
            label == "html" || mime == "text/html" -> {
                openFileWithPackage(context, context.packageName, HtmlViewerActivity::class.java.name)
                return true
            }
            label == "markdown" || mime == "text/markdown" -> {
                openFileWithPackage(context, context.packageName, MarkdownViewerActivity::class.java.name)
                return true
            }
            label == "latex" || mime.contains("tex") -> {
                openFileWithPackage(context, context.packageName, LatexViewerActivity::class.java.name)
                return true
            }
            label in setOf("pdf") || mime == "application/pdf" -> {
                openFileWithPackage(context, context.packageName, PdfViewerActivity::class.java.name)
                return true
            }
            mime.startsWith("image/") || label in setOf("png", "jpeg", "gif", "webp", "bmp", "tiff", "svg", "ico") -> {
                openFileWithPackage(context, context.packageName, ImageViewerActivity::class.java.name)
                return true
            }
            mime.startsWith("video/") || label in setOf("mp4", "mkv", "webm", "flv", "3gp") -> {
                openFileWithPackage(context, context.packageName, VideoPlayerActivity::class.java.name)
                return true
            }
            mime.startsWith("audio/") || label in setOf("mp3", "wav", "ogg", "flac", "midi") -> {
                openFileWithPackage(context, context.packageName, AudioPlayerActivity::class.java.name)
                return true
            }
            label in setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp") -> {
                openFileWithPackage(context, context.packageName, DocumentViewerActivity::class.java.name)
                return true
            }
            mime.contains("zip") || mime.contains("archive") || mime.contains("7z") ||
                mime.contains("rar") || mime.contains("tar") || mime.contains("gzip") ||
                mime.contains("bzip") || mime.contains("xz") || mime.contains("iso") ||
                mime.contains("zstd") || mime.contains("lz4") ||
                label in setOf("zip", "jar", "apk", "gzip", "bzip", "sevenzip", "rar", "tar", "xz", "iso", "dmg", "cab", "zst", "zstd", "lz4") -> {
                globalClass.zipManager.openArchive(this)
                return true
            }
            detected.isText || MimeTypeDetector.isTextMimeType(mime) -> {
                globalClass.textEditorManager.openTextEditor(this, context)
                return true
            }
        }
        return false
    }

    private fun extensionsEquivalent(actual: String, detected: String): Boolean {
        if (actual == detected) return true
        val aliases = mapOf(
            "jpg" to "jpeg", "jpeg" to "jpg",
            "htm" to "html", "html" to "htm",
            "yml" to "yaml", "yaml" to "yml",
            "py" to "python"
        )
        return aliases[actual] == detected
    }

    /**
     * Returns true if this file is a TAR wrapped in a compression format (.tar.gz, .tar.bz2, .tar.xz).
     * These are detected by checking the second-to-last extension.
     */
    fun isTarCompressed(): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".tar.gz") || name.endsWith(".tar.bz2") ||
               name.endsWith(".tar.bzip2") || name.endsWith(".tar.xz") ||
               name.endsWith(".tar.zst") || name.endsWith(".tar.zstd") ||
               name.endsWith(".tar.lz4") || name.endsWith(".tar.lz") ||
               name.endsWith(".tgz") || name.endsWith(".tbz2") ||
               name.endsWith(".tbz") || name.endsWith(".txz") ||
               name.endsWith(".tzst") || name.endsWith(".tpz")
    }

    /**
     * Check if the file has a well-known extension that Prism recognizes.
     */
    private fun hasKnownExtension(): Boolean {
        if (extension.isEmpty()) return false
        return codeFileType.contains(extension) ||
                editableFileType.contains(extension) ||
                FileMimeType.videoFileType.contains(extension) ||
                FileMimeType.audioFileType.contains(extension) ||
                FileMimeType.imageFileType.contains(extension) ||
                FileMimeType.archiveFileType.contains(extension) ||
                FileMimeType.supportedArchiveFileType.contains(extension) ||
                FileMimeType.latexFileType.contains(extension) ||
                FileMimeType.officeFileType.contains(extension) ||
                FileMimeType.htmlFileType.contains(extension) ||
                extension == "pdf" || extension == "apk" ||
                extension == "md" || extension == "markdown" ||
                isTarCompressed()
    }

    private fun createUri() = FileProvider.getUriForFile(
        globalClass,
        "com.raival.compose.file.explorer.provider",
        file
    )

    fun hasParent(parent: LocalFileHolder): Boolean =
        file.absolutePath.hasParent(parent.file.absolutePath)

    suspend fun getFormattedFileCount(): String {
        val contentCount = getContentCount()

        return getFormattedFileCount(
            contentCount.files,
            contentCount.folders
        )
    }
}