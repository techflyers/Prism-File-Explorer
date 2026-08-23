package com.raival.compose.file.explorer.screen.main.tab.files.shizuku

import android.content.Context
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.codeFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.editableFileType
import java.io.File

/**
 * ContentHolder backed by privileged shell access (Shizuku or root).
 * Wraps a ShizukuFileEntry and provides ContentHolder-compatible listing
 * via ShizukuManager.listFiles() shell command.
 */
class ShizukuFileHolder(
    private val entry: ShizukuFileEntry,
    private val parentHolder: ShizukuFileHolder? = null
) : ContentHolder() {

    companion object {
        /**
         * Creates a root ShizukuFileHolder for a given path.
         */
        fun fromPath(path: String): ShizukuFileHolder {
            return ShizukuFileHolder(
                ShizukuFileEntry(
                    name = if (path == "/") "/" else path.substringAfterLast("/"),
                    path = path,
                    isDirectory = true,
                    size = 0L,
                    lastModified = 0L
                )
            )
        }
    }

    override val uniquePath: String = entry.path
    override val displayName: String = entry.name
    override val isFolder: Boolean = entry.isDirectory
    override val lastModified: Long = entry.lastModified
    override val size: Long = entry.size
    override val extension: String = if (isFolder) emptyString else entry.name.substringAfterLast(".", "")
    override val canRead: Boolean = true
    override val canWrite: Boolean = ShizukuManager.isPrivileged
    override val canAddNewContent: Boolean = isFolder && ShizukuManager.isPrivileged

    private var details = emptyString
    private var filesCount = 0
    private var foldersCount = 0

    override suspend fun getDetails(): String {
        val separator = " | "

        if (details.isNotEmpty()) return details

        return buildString {
            append(getLastModifiedDate())
            if (isFolder) {
                if (globalClass.preferencesManager.showFolderContentCount) {
                    append(separator)
                    append(getFormattedFileCount())
                }
            } else {
                append(separator)
                append(size.toFormattedSize())
                append(separator)
                append(extension)
            }
        }.also {
            details = it
        }
    }

    private suspend fun getFormattedFileCount(): String {
        if (filesCount == 0 && foldersCount == 0) {
            val content = listContent()
            foldersCount = content.count { it.isFolder }
            filesCount = content.count { !it.isFolder }
        }
        return getFormattedFileCount(filesCount, foldersCount)
    }

    override suspend fun isValid(): Boolean = ShizukuManager.exists(uniquePath)

    override suspend fun getParent(): ContentHolder? {
        if (parentHolder != null) return parentHolder
        val parentPath = uniquePath.substringBeforeLast("/", "")
        if (parentPath.isEmpty() || parentPath == uniquePath) return null
        return fromPath(if (parentPath.isEmpty()) "/" else parentPath)
    }

    override suspend fun listContent(): ArrayList<out ContentHolder> {
        val entries = ShizukuManager.listFiles(uniquePath)
        return ArrayList(entries.map { fileEntry ->
            ShizukuFileHolder(fileEntry, parentHolder = this)
        })
    }

    override suspend fun getContentCount(): ContentCount {
        val entries = ShizukuManager.listFiles(uniquePath)
        val dirs = entries.count { it.isDirectory }
        val files = entries.count { !it.isDirectory }
        return ContentCount(folders = dirs, files = files)
    }

    override suspend fun findFile(name: String): ContentHolder? {
        return ShizukuManager.listFiles(uniquePath)
            .find { it.name == name }
            ?.let { ShizukuFileHolder(it, parentHolder = this) }
    }

    override suspend fun createSubFile(name: String, onCreated: (ContentHolder?) -> Unit) {
        val targetPath = if (uniquePath == "/") "/$name" else "$uniquePath/$name"
        val success = ShizukuManager.createFile(targetPath)
        if (success) {
            val newHolder = ShizukuFileHolder(
                ShizukuFileEntry(
                    name = name,
                    path = targetPath,
                    isDirectory = false,
                    size = 0L,
                    lastModified = System.currentTimeMillis()
                ),
                parentHolder = this
            )
            onCreated(newHolder)
        } else {
            onCreated(null)
        }
    }

    override suspend fun createSubFolder(name: String, onCreated: (ContentHolder?) -> Unit) {
        val targetPath = if (uniquePath == "/") "/$name" else "$uniquePath/$name"
        val success = ShizukuManager.createDirectory(targetPath)
        if (success) {
            val newHolder = ShizukuFileHolder(
                ShizukuFileEntry(
                    name = name,
                    path = targetPath,
                    isDirectory = true,
                    size = 0L,
                    lastModified = System.currentTimeMillis()
                ),
                parentHolder = this
            )
            onCreated(newHolder)
        } else {
            onCreated(null)
        }
    }

    fun readText(): String {
        return ShizukuManager.readText(uniquePath) ?: ""
    }

    fun writeText(text: String) {
        if (!ShizukuManager.writeText(uniquePath, text)) {
            throw java.io.IOException("Failed to write to $uniquePath via privileged shell")
        }
    }

    override fun open(
        context: Context,
        anonymous: Boolean,
        skipSupportedExtensions: Boolean,
        customMimeType: String?
    ) {
        val directFile = File(uniquePath)
        if (directFile.exists() && directFile.canRead()) {
            LocalFileHolder(directFile).open(context, anonymous, skipSupportedExtensions, customMimeType)
            return
        }

        // Handle text editing directly
        val ext = extension.lowercase()
        if (codeFileType.contains(ext) || editableFileType.contains(ext) || ext == "txt" || ext == "log" || ext == "json" || ext == "xml") {
            globalClass.textEditorManager.openTextEditor(LocalFileHolder(directFile), context)
            return
        }

        // For files that need local access (images, media, APKs, archives), copy to cleanOnExitDir
        try {
            val tempFile = File(globalClass.cleanOnExitDir.file, "${System.currentTimeMillis()}_$displayName")
            if (ShizukuManager.copyToLocal(uniquePath, tempFile)) {
                val localHolder = LocalFileHolder(tempFile)
                if (isApk() || isApkBundle()) {
                    val activeTab = globalClass.mainActivityManager.getActiveTab()
                    if (activeTab is FilesTab) {
                        activeTab.toggleApkDialog(localHolder)
                        return
                    }
                }
                localHolder.open(context, anonymous, skipSupportedExtensions, customMimeType)
            } else {
                LocalFileHolder(directFile).open(context, anonymous, skipSupportedExtensions, customMimeType)
            }
        } catch (e: Exception) {
            logger.logError(e)
            LocalFileHolder(directFile).open(context, anonymous, skipSupportedExtensions, customMimeType)
        }
    }
}

