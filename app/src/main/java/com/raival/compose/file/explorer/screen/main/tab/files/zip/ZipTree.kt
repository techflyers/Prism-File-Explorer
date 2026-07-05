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
    val tempArchiveFile = File(cleanOnExitDir.file, "source_archive.${source.extension}")
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

    fun prepare() {
        if (!tempArchiveFile.exists() || tempArchiveFile.length() != source.file.length()) {
            var copied = false
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    globalClass,
                    "com.raival.compose.file.explorer.provider",
                    source.file
                )
                globalClass.contentResolver.openInputStream(uri)?.use { input ->
                    tempArchiveFile.outputStream().use { output ->
                        input.copyTo(output)
                        copied = true
                    }
                }
            } catch (_: Exception) {}

            if (!copied) {
                try {
                    source.file.inputStream().use { input ->
                        tempArchiveFile.outputStream().use { output ->
                            input.copyTo(output)
                            copied = true
                        }
                    }
                } catch (e: Exception) {
                    logger.logError(e)
                }
            }
        }
        build()
    }

    private fun build() {
        android.util.Log.d("PrismArchive", "ZipTree: build() started for archive=${source.displayName}, extension=${source.extension}, hasPassword=${!password.isNullOrEmpty()}")
        isReady = false

        // All formats are routed through lib7za (ArchiveManager.listArchive).
        // This covers zip, jar, apk, 7z, rar, iso, tar, gz, bz2, xz, wim, cab, dmg, and all other
        // lib7za-supported formats uniformly.
        try {
            android.util.Log.d("PrismArchive", "ZipTree: Listing archive entries via lib7za...")
            val entries = runBlocking { ArchiveManager.listArchive(tempArchiveFile.absolutePath, password) }
            android.util.Log.d("PrismArchive", "ZipTree: Native listing succeeded. Found ${entries.size} entries.")

            // If any entry is encrypted, verify password
            val hasEncryptedEntries = entries.any { it.encrypted }
            android.util.Log.d("PrismArchive", "ZipTree: hasEncryptedEntries=$hasEncryptedEntries, hasPassword=${!password.isNullOrEmpty()}")
            if (hasEncryptedEntries) {
                if (password.isNullOrEmpty()) {
                    android.util.Log.d("PrismArchive", "ZipTree: Archive contains encrypted entries but no password was provided. Throwing ArchivePasswordRequiredException.")
                    throw ArchivePasswordRequiredException(source.displayName)
                }

                // Verify password by testing the first encrypted file entry
                val firstEncryptedFile = entries.firstOrNull { it.encrypted && !it.isDirectory }
                if (firstEncryptedFile != null) {
                    val testArgs = listOf("t", tempArchiveFile.absolutePath, "-p$password", "-y", firstEncryptedFile.path)
                    android.util.Log.d("PrismArchive", "ZipTree: Testing password verification: 7z ${testArgs.joinToString(" ")}")
                    val testResult = runBlocking {
                        NativeBinaryExecutor.run(
                            context = globalClass,
                            binaryName = "lib7za.so",
                            arguments = testArgs
                        )
                    }
                    android.util.Log.d("PrismArchive", "ZipTree: Password test result exitCode=${testResult.exitCode}, success=${testResult.success}")
                    if (!testResult.success) {
                        android.util.Log.w("PrismArchive", "ZipTree: Password verification failed. Throwing ArchivePasswordRequiredException.")
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
                android.util.Log.d("PrismArchive", "ZipTree: Archive password required or incorrect. Throwing ArchivePasswordRequiredException.")
                throw ArchivePasswordRequiredException(source.displayName)
            }
            logger.logError(e)
            globalClass.showMsg("Failed to open archive: ${e.message}")
        }

        isReady = true
        android.util.Log.d("PrismArchive", "ZipTree: build() completed successfully.")
    }

    /** Thrown by [build] when the archive requires a password but none was provided (or was wrong). */
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