package com.raival.compose.file.explorer.screen.main.tab.files.zip

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.NativeBinaryExecutor
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toUuid
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ZipFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.zip.model.ZipNode
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipEntry

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
    private val zipEntries = hashMapOf<String, ZipEntry>()
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
        zipEntries.clear()

        val ext = source.extension.lowercase()

        if (ArchiveManager.isNativeArchive(ext)) {
            // Use 7za for native archive formats (7z, rar, iso, cab, xz, etc.)
            try {
                android.util.Log.d("PrismArchive", "ZipTree: Listing native archive entries via 7za...")
                val entries = runBlocking { ArchiveManager.listArchive(tempArchiveFile.absolutePath, password) }
                android.util.Log.d("PrismArchive", "ZipTree: Native listing succeeded. Found ${entries.size} entries.")

                // If any entry is encrypted, verify password
                val hasEncryptedEntries = entries.any { it.encrypted }
                android.util.Log.d("PrismArchive", "ZipTree: hasEncryptedEntries=$hasEncryptedEntries, hasPassword=${!password.isNullOrEmpty()}")
                if (hasEncryptedEntries) {
                    if (password.isNullOrEmpty()) {
                        android.util.Log.d("PrismArchive", "ZipTree: Native archive contains encrypted entries but no password was provided. Throwing ArchivePasswordRequiredException.")
                        throw ArchivePasswordRequiredException(source.displayName)
                    }

                    // Verify password by testing the first encrypted file entry
                    val firstEncryptedFile = entries.firstOrNull { it.encrypted && !it.isDirectory }
                    if (firstEncryptedFile != null) {
                        val testArgs = listOf("t", tempArchiveFile.absolutePath, "-p$password", "-y", firstEncryptedFile.path)
                        android.util.Log.d("PrismArchive", "ZipTree: Testing native password verification: 7z ${testArgs.joinToString(" ")}")
                        val testResult = runBlocking {
                            NativeBinaryExecutor.run(
                                context = globalClass,
                                binaryName = "lib7za.so",
                                arguments = testArgs
                            )
                        }
                        android.util.Log.d("PrismArchive", "ZipTree: Password test result exitCode=${testResult.exitCode}, success=${testResult.success}")
                        if (!testResult.success) {
                            android.util.Log.w("PrismArchive", "ZipTree: Native password verification failed. Throwing ArchivePasswordRequiredException.")
                            throw ArchivePasswordRequiredException(source.displayName)
                        }
                    }
                }

                buildTreeFromNativeEntries(entries)
            } catch (e: Exception) {
                android.util.Log.e("PrismArchive", "ZipTree: Native listing failed: ${e.message}", e)
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
        } else {
            // Use zip4j for zip/jar/apk/apks formats (supports password-protected zips)
            try {
                android.util.Log.d("PrismArchive", "ZipTree: Opening zip archive via zip4j...")
                val zip4jFile = if (!password.isNullOrEmpty()) {
                    net.lingala.zip4j.ZipFile(tempArchiveFile, password!!.toCharArray())
                } else {
                    net.lingala.zip4j.ZipFile(tempArchiveFile)
                }
                
                android.util.Log.d("PrismArchive", "ZipTree: zip4j file object created. IsEncrypted=${zip4jFile.isEncrypted}")
                
                if (zip4jFile.isEncrypted) {
                    if (password.isNullOrEmpty()) {
                        android.util.Log.d("PrismArchive", "ZipTree: zip4j archive is encrypted but no password provided. Throwing ArchivePasswordRequiredException.")
                        throw ArchivePasswordRequiredException(source.displayName)
                    }

                    // Verify the password by trying to read the first file entry
                    val firstFileHeader = zip4jFile.fileHeaders.firstOrNull { !it.isDirectory }
                    if (firstFileHeader != null) {
                        try {
                            zip4jFile.getInputStream(firstFileHeader).use { it.read() }
                            android.util.Log.d("PrismArchive", "ZipTree: Password successfully verified for zip4j.")
                        } catch (e: Exception) {
                            android.util.Log.w("PrismArchive", "ZipTree: zip4j password verification failed: ${e.message}. Throwing ArchivePasswordRequiredException.")
                            throw ArchivePasswordRequiredException(source.displayName)
                        }
                    }
                }

                // Iterate all entries via zip4j and convert to java ZipEntry-like map
                val headers = zip4jFile.fileHeaders
                android.util.Log.d("PrismArchive", "ZipTree: Retrieved ${headers.size} headers from zip4j.")
                headers.forEach { header ->
                    val entryName = header.fileName ?: return@forEach
                    // Re-use the java.util.zip.ZipEntry just for the tree builder (we only need the name and time)
                    val entry = java.util.zip.ZipEntry(entryName).apply {
                        if (!header.isDirectory) size = header.uncompressedSize
                        time = dosToEpochTime(header.lastModifiedTime)
                    }
                    zipEntries.put(entryName, entry)
                }
            } catch (e: net.lingala.zip4j.exception.ZipException) {
                android.util.Log.e("PrismArchive", "ZipTree: zip4j parsing failed: ${e.message}", e)
                val msg = e.message?.lowercase() ?: ""
                if (msg.contains("wrong password") || msg.contains("password") ||
                    msg.contains("encrypt") || msg.contains("incorrect") ||
                    msg.contains("invalid password")) {
                    android.util.Log.d("PrismArchive", "ZipTree: zip4j password required or incorrect. Throwing ArchivePasswordRequiredException.")
                    throw ArchivePasswordRequiredException(source.displayName)
                }
                logger.logError(e)
                globalClass.showMsg(R.string.invalid_zip)
            } catch (e: Exception) {
                android.util.Log.e("PrismArchive", "ZipTree: Unexpected exception parsing zip: ${e.message}", e)
                if (e is ArchivePasswordRequiredException) {
                    throw e
                }
                logger.logError(e)
                globalClass.showMsg(R.string.invalid_zip)
            }

            buildTree()
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

    private fun buildTree() {
        nodes.clear()
        nodes.put(emptyString, root.apply { children.clear() })

        zipEntries.forEach { entry ->
            val path = entry.key

            val parts = path.split(File.separator)
            var currentNode = root
            var currentPath = root.path

            for ((i, part) in parts.withIndex()) {
                if (part.isNotEmpty()) {
                    val childNode = currentNode.children.find { it.name == part }

                    currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                    if (childNode == null) {
                        val newNode = ZipNode(
                            name = part,
                            path = currentPath,
                            isDirectory = if (i < parts.lastIndex) true else entry.value.isDirectory,
                            lastModified = entry.value.lastModifiedTime?.toMillis() ?: entry.value.time.takeIf { it != -1L } ?: 0L,
                            lastAccessed = entry.value.lastAccessTime?.toMillis() ?: entry.value.time.takeIf { it != -1L } ?: 0L,
                            size = entry.value.size
                        )
                        currentNode.children.add(newNode)
                        currentNode = newNode

                        nodes.put(currentPath, newNode)
                    } else {
                        currentNode = childNode
                    }
                }
            }
        }

        zipEntries.clear()
    }

    private fun dosToEpochTime(dosTime: Long): Long {
        if (dosTime <= 0) return 0L
        val year = (((dosTime shr 25) and 0x7f) + 1980).toInt()
        val month = (((dosTime shr 21) and 0x0f) - 1).toInt() // Calendar months are 0-indexed
        val day = ((dosTime shr 16) and 0x1f).toInt()
        val hour = ((dosTime shr 11) and 0x1f).toInt()
        val minute = ((dosTime shr 5) and 0x3f).toInt()
        val second = ((dosTime and 0x1f) * 2).toInt()

        return try {
            val calendar = java.util.Calendar.getInstance()
            calendar.set(year, month, day, hour, minute, second)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        } catch (_: Exception) {
            0L
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