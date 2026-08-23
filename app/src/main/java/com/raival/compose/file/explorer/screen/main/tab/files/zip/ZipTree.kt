package com.raival.compose.file.explorer.screen.main.tab.files.zip

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.common.NativeBinaryExecutor
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toUuid
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ZipFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.zip.model.ZipNode
import kotlinx.coroutines.runBlocking
import java.io.File

class ZipTree(
    val source: LocalFileHolder,
) {
    var password: String? = null
    var timeStamp = source.lastModified
    val cleanOnExitDir = LocalFileHolder(
        file = File(globalClass.cleanOnExitDir.file, source.uniquePath.toUuid().toString()).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    )
    val extractedFiles = hashMapOf<String, LocalFileHolder>()

    /**
     * Path that 7za / zip4j should open. Prefer the original filesystem path;
     * [ArchiveManager.resolveAccessibleArchivePath] only creates a cache copy
     * when the native binary cannot open the original.
     *
     * Kept as a [File] for callers that previously used [tempArchiveFile].
     */
    val tempArchiveFile: File
        get() = File(archivePathForNative)

    /** Absolute path passed to lib7za / zip4j. Updated by [prepare]. */
    var archivePathForNative: String = source.file.absolutePath
        private set

    private val nodes = hashMapOf<String, ZipNode>()

    private val root = ZipNode(
        name = source.displayName,
        path = emptyString,
        isDirectory = true,
        lastModified = 0,
        lastAccessed = 0,
        size = 0
    )

    var isReady = false
        private set

    fun invalidate() {
        android.util.Log.d("PrismArchive", "ZipTree: invalidate() called. Setting isReady=false.")
        isReady = false
    }

    fun getRelatedNode(extractedFile: LocalFileHolder): ZipNode? {
        if (!extractedFile.uniquePath.startsWith(cleanOnExitDir.uniquePath)) return null
        return findNodeByPath(
            extractedFile.uniquePath.removePrefix(cleanOnExitDir.uniquePath)
                .removePrefix(File.separator)
        )
    }

    fun createExtractionDestinationDirFor(node: ZipNode) =
        if (node.parentPath.isEmpty()) cleanOnExitDir.file else File(
            cleanOnExitDir.file,
            node.parentPath
        )

    fun getExtractionDestinationFile(node: ZipNode): LocalFileHolder? {
        val file = File(createExtractionDestinationDirFor(node), node.name)

        if (!file.exists()) return null

        return LocalFileHolder(file)
    }

    fun getRootNode() = root

    fun createRootContentHolder() = ZipFileHolder(this, root)

    fun findNodeByPath(path: String) = nodes[path]

    fun reset() {
        isReady = false
        timeStamp = source.lastModified
    }

    /**
     * Resolve a path that lib7za can open, then build the in-memory tree.
     * No mandatory full-file copy: [ArchiveManager.resolveAccessibleArchivePath]
     * uses the original path when possible and only falls back to a cache copy
     * on access failure.
     */
    fun prepare() {
        archivePathForNative = runBlocking {
            ArchiveManager.resolveAccessibleArchivePath(source.file.absolutePath)
        }
        android.util.Log.d(
            "PrismArchive",
            "ZipTree: prepare() archivePathForNative=$archivePathForNative (original=${source.file.absolutePath})"
        )
        build()
    }

    private fun build() {
        android.util.Log.d(
            "PrismArchive",
            "ZipTree: build() started for archive=${source.displayName}, extension=${source.extension}, hasPassword=${!password.isNullOrEmpty()}"
        )
        isReady = false

        try {
            android.util.Log.d("PrismArchive", "ZipTree: Listing archive entries via lib7za...")
            val entries = runBlocking {
                ArchiveManager.listArchive(archivePathForNative, password)
            }
            android.util.Log.d("PrismArchive", "ZipTree: Native listing succeeded. Found ${entries.size} entries.")

            val hasEncryptedEntries = entries.any { it.encrypted }
            android.util.Log.d(
                "PrismArchive",
                "ZipTree: hasEncryptedEntries=$hasEncryptedEntries, hasPassword=${!password.isNullOrEmpty()}"
            )
            if (hasEncryptedEntries) {
                if (password.isNullOrEmpty()) {
                    throw ArchivePasswordRequiredException(source.displayName)
                }

                val firstEncryptedFile = entries.firstOrNull { it.encrypted && !it.isDirectory }
                if (firstEncryptedFile != null) {
                    val testArgs = listOf(
                        "t", archivePathForNative, "-p$password", "-y", firstEncryptedFile.path
                    )
                    val testResult = runBlocking {
                        NativeBinaryExecutor.run(
                            context = globalClass,
                            binaryName = "lib7za.so",
                            arguments = testArgs
                        )
                    }
                    if (!testResult.success) {
                        throw ArchivePasswordRequiredException(source.displayName)
                    }
                }
            }

            buildTreeFromNativeEntries(entries)
        } catch (e: Exception) {
            android.util.Log.e("PrismArchive", "ZipTree: Listing failed: ${e.message}", e)
            if (e is ArchivePasswordRequiredException) {
                throw e
            }
            val msg = e.message?.lowercase() ?: ""
            val needsPassword = msg.contains("wrong password") ||
                    msg.contains("encrypted") ||
                    msg.contains("password") ||
                    msg.contains("cannot open encrypted") ||
                    msg.contains("incorrect password")
            if (needsPassword) {
                throw ArchivePasswordRequiredException(source.displayName)
            }
            logger.logError(e)
            globalClass.showMsg("Failed to open archive: ${e.message}")
        }

        isReady = true
        android.util.Log.d("PrismArchive", "ZipTree: build() completed successfully.")
    }

    class ArchivePasswordRequiredException(val archiveName: String) :
        Exception("Archive '$archiveName' is encrypted and requires a password")

    private fun buildTreeFromNativeEntries(entries: List<ArchiveEntry>) {
        nodes.clear()
        nodes[emptyString] = root.apply { children.clear() }

        for (entry in entries) {
            val path = entry.path.replace('\\', '/')
            val parts = path.split("/")
            var currentNode = root
            var currentPath = root.path

            for ((i, part) in parts.withIndex()) {
                if (part.isNotEmpty()) {
                    val existingChild = currentNode.children.find { it.name == part }
                    currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                    if (existingChild == null) {
                        val isDir = if (i < parts.lastIndex) true else entry.isDirectory
                        val newNode = ZipNode(
                            name = part,
                            path = currentPath,
                            isDirectory = isDir,
                            lastModified = entry.lastModified,
                            lastAccessed = 0,
                            size = entry.size
                        )
                        currentNode.children.add(newNode)
                        nodes[currentPath] = newNode
                        currentNode = newNode
                    } else {
                        currentNode = existingChild
                    }
                }
            }
        }
    }

    fun checkExtractedFiles(): ArrayList<LocalFileHolder> {
        val result = arrayListOf<LocalFileHolder>()
        extractedFiles.forEach { item ->
            val file = item.value
            if (file.hasSourceChanged()) {
                result.add(file)
            }
        }
        return result
    }

    fun addExtractedFile(node: ZipNode, file: LocalFileHolder) {
        extractedFiles[node.path] = file
    }
}
