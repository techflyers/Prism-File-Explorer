package com.raival.compose.file.explorer.screen.main.tab.files.holder

import android.content.Context
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.common.toUuid
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ZipTree
import com.raival.compose.file.explorer.screen.main.tab.files.zip.model.ZipNode
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.ByteArrayInputStream
import java.io.File

class ZipFileHolder(
    val zipTree: ZipTree,
    val node: ZipNode,
) : ContentHolder() {
    override val uniquePath = node.path
    override val displayName = node.name
    var details = emptyString

    override val isFolder = node.isDirectory
    override val lastModified = node.lastModified
    override val size = node.size
    override val extension = node.extension
    override val canRead = true
    override val canWrite = true
    override val canAddNewContent = true

    private var filesCount = 0
    private var foldersCount = 0

    private var contentListCount = ContentCount()

    override suspend fun getDetails(): String {
        if (details.isNotEmpty()) return details

        return createDetails().also {
            details = it
        }
    }

    override suspend fun listContent(): ArrayList<ZipFileHolder> {
        android.util.Log.d("PrismArchive", "ZipFileHolder: listContent() started for path='$uniquePath', isReady=${zipTree.isReady}")
        filesCount = 0
        foldersCount = 0

        if (!zipTree.isReady) {
            try {
                android.util.Log.d("PrismArchive", "ZipFileHolder: zipTree is not ready. Preparing tree...")
                zipTree.prepare()
                android.util.Log.d("PrismArchive", "ZipFileHolder: zipTree preparation completed.")
            } catch (e: ZipTree.ArchivePasswordRequiredException) {
                android.util.Log.w("PrismArchive", "ZipFileHolder: Caught ArchivePasswordRequiredException for ${e.archiveName}. Showing password dialog...")
                val tab = globalClass.mainActivityManager.getActiveTab()
                        as? com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
                tab?.toggleArchivePasswordDialog(zipTree.source)
                return arrayListOf()
            } catch (e: Exception) {
                android.util.Log.e("PrismArchive", "ZipFileHolder: Error preparing zipTree: ${e.message}", e)
                return arrayListOf()
            }
        }

        // In case the tab reloaded with new content, the `node` linked to this holder will have the
        // old data, so we need to make sure that the content is up-to-date.
        // This also somewhat apply to the LocalFileHolder, but that get the information of a 'File'
        // which will show latest changes (except the cached stuff).
        val newNode = zipTree.findNodeByPath(uniquePath)

        if (newNode == null) {
            android.util.Log.w("PrismArchive", "ZipFileHolder: Node not found in tree for path='$uniquePath'")
            return arrayListOf()
        }

        val childrenList = newNode.children
            .map {
                ZipFileHolder(zipTree, it).also {
                    if (it.node.isDirectory) foldersCount++ else filesCount++
                }
            }
        
        android.util.Log.d("PrismArchive", "ZipFileHolder: listContent() finished. Found ${childrenList.size} children.")
        return ArrayList(childrenList).also {
            contentListCount = ContentCount(filesCount, foldersCount)
        }
    }

    override suspend fun getParent(): ContentHolder? {
        if (!node.path.contains(File.separator) && node.path.isNotEmpty()) {
            return ZipFileHolder(zipTree, zipTree.getRootNode())
        }

        var parentPath = node.parentPath

        while (parentPath.isNotEmpty()) {
            val parentNode = zipTree.findNodeByPath(parentPath)

            if (parentNode != null) {
                return ZipFileHolder(zipTree, parentNode)
            }

            parentPath = ZipNode(emptyString, parentPath).parentPath
        }

        // We are at the root of this ZipTree.
        // Check if this ZipTree is a nested archive.
        val sourcePath = zipTree.source.uniquePath
        val cleanOnExitPath = globalClass.cleanOnExitDir.uniquePath
        if (sourcePath.startsWith(cleanOnExitPath)) {
            val relativePath = sourcePath.removePrefix(cleanOnExitPath).trimStart('/')
            val uuid = relativePath.substringBefore('/')
            val internalPath = relativePath.substringAfter('/')

            val parentTree = globalClass.zipManager.archiveList.values.find {
                it.source.uniquePath.toUuid().toString() == uuid
            }

            if (parentTree != null) {
                val parentNode = parentTree.findNodeByPath(internalPath)
                if (parentNode != null) {
                    return ZipFileHolder(parentTree, parentNode)
                }
            }
        }

        return zipTree.source.getParent()
    }

    override suspend fun createSubFile(name: String, onCreated: (ContentHolder?) -> Unit) {
        val path = "${if (uniquePath.isEmpty()) emptyString else "$uniquePath/"}$name"
        val params = ZipParameters().apply {
            fileNameInZip = path
            isOverrideExistingFilesInZip = false
        }

        ZipFile(zipTree.source.file).use { zipFile ->
            zipFile.addStream(ByteArrayInputStream(ByteArray(0)), params)
        }

        zipTree.prepare()

        zipTree.findNodeByPath(path)?.let {
            onCreated(ZipFileHolder(zipTree, it))
        } ?: onCreated(null)
    }

    override suspend fun createSubFolder(name: String, onCreated: (ContentHolder?) -> Unit) {
        val path = "${if (uniquePath.isEmpty()) emptyString else "$uniquePath/"}$name/"
        val params = ZipParameters().apply {
            fileNameInZip = path
            isOverrideExistingFilesInZip = false
        }

        ZipFile(zipTree.source.file).use { zipFile ->
            zipFile.addStream(ByteArrayInputStream(ByteArray(0)), params)
        }

        zipTree.prepare()

        zipTree.findNodeByPath(path.trimEnd('/'))?.let {
            onCreated(ZipFileHolder(zipTree, it))
        } ?: onCreated(null)
    }

    override suspend fun getContentCount() = contentListCount

    override suspend fun findFile(name: String) = node.children.find { it.name == name }?.let {
        ZipFileHolder(zipTree, it)
    }

    override suspend fun isValid() = if (zipTree.isReady) {
        (zipTree.source.isValid() && zipTree.findNodeByPath(uniquePath) != null).also { isValid ->
            if (!isValid) globalClass.zipManager.validateArchiveTrees()
        }
    } else {
        zipTree.source.isValid().also { isValid ->
            if (!isValid) globalClass.zipManager.validateArchiveTrees()
        }
    }

    override fun open(
        context: Context,
        anonymous: Boolean,
        skipSupportedExtensions: Boolean,
        customMimeType: String?
    ) {
        val file = zipTree.getExtractionDestinationFile(node)

        if (file != null) {
            file.open(context, anonymous, skipSupportedExtensions, customMimeType)
        } else {
            (globalClass.mainActivityManager.getActiveTab() as? FilesTab)?.extractZipHolderForPreview(
                this
            ) {
                val file = zipTree.getExtractionDestinationFile(node)
                if (file != null) {
                    zipTree.addExtractedFile(node, file)
                    file.open(context, anonymous, skipSupportedExtensions, customMimeType)
                } else {
                    showMsg(R.string.failed_to_extract_file)
                }
            }
        }
    }

    suspend fun extractForPreview(): String {
        val destDir = zipTree.createExtractionDestinationDirFor(node).absolutePath
        android.util.Log.d("PrismArchive", "ZipFileHolder: extractForPreview() started for node='${node.path}', destDir='$destDir'")
        try {
            val ext = zipTree.source.extension.lowercase()
            if (ArchiveManager.isNativeArchive(ext)) {
                android.util.Log.d("PrismArchive", "ZipFileHolder: Native archive type detected. Extracting via 7za...")
                // Use 7za to extract just this single file
                ArchiveManager.extractSingleFile(
                    archivePath = zipTree.tempArchiveFile.absolutePath,
                    internalPath = node.path,
                    destinationDir = zipTree.cleanOnExitDir.uniquePath,
                    password = zipTree.password
                )
            } else {
                android.util.Log.d("PrismArchive", "ZipFileHolder: ZIP archive type detected. Extracting via zip4j...")
                val zipFile = if (!zipTree.password.isNullOrEmpty()) {
                    net.lingala.zip4j.ZipFile(zipTree.tempArchiveFile, zipTree.password!!.toCharArray())
                } else {
                    net.lingala.zip4j.ZipFile(zipTree.tempArchiveFile)
                }
                zipFile.use { zf ->
                    zf.extractFile(
                        node.path,
                        zipTree.cleanOnExitDir.uniquePath
                    )
                }
            }
            android.util.Log.d("PrismArchive", "ZipFileHolder: extractForPreview() completed successfully.")
        } catch (e: Exception) {
            android.util.Log.e("PrismArchive", "ZipFileHolder: extractForPreview() failed: ${e.message}", e)
            logger.logError(e)
        }
        return destDir
    }

    private suspend fun createDetails(): String {
        val separator = " | "
        return buildString {
            append(getLastModifiedDate())
            if (node.isDirectory) {
                if (globalClass.preferencesManager.showFolderContentCount) {
                    append(separator)
                    append(getFormattedFileCount())
                }
            } else {
                append(separator)
                append(node.size.toFormattedSize())
                append(separator)
                append(node.extension)
            }
        }
    }

    private suspend fun getFormattedFileCount(): String {
        if (filesCount == 0 && foldersCount == 0) {
            runBlocking {
                listContent().forEach {
                    if (it.node.isDirectory) foldersCount++
                    else filesCount++
                }
            }
        }

        return getFormattedFileCount(
            filesCount,
            foldersCount
        )
    }
}