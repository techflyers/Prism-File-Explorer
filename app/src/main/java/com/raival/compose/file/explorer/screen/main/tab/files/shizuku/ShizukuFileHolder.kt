package com.raival.compose.file.explorer.screen.main.tab.files.shizuku

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount

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

    override suspend fun isValid(): Boolean = true

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
}
