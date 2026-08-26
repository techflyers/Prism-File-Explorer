package com.raival.compose.file.explorer.saf

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.MimeTypeDetector
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionsService
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteConnectionPool
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemotePaths
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class PrismDocumentsProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.raival.compose.file.explorer.documents"
        private const val REMOTE_PREFIX = "remote:"

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES,
            Root.COLUMN_CAPACITY_BYTES,
            Root.COLUMN_MIME_TYPES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE
        )
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val context = context ?: return result

        try {
            val rootsUri = DocumentsContract.buildRootsUri(AUTHORITY)
            result.setNotificationUri(context.contentResolver, rootsUri)
        } catch (_: Exception) {
        }

        // 1. Primary Internal Storage Root
        val internalDir = Environment.getExternalStorageDirectory()
        if (internalDir.exists()) {
            includeRoot(
                result = result,
                rootId = "primary",
                title = context.getString(R.string.internal_storage),
                summary = formatStorageSummary(internalDir),
                docId = internalDir.absolutePath,
                file = internalDir
            )
        }

        // 2. Secondary External Storage Roots (SD Cards, USB OTG)
        val externalDirs = context.getExternalFilesDirs(null)
        val addedPaths = mutableSetOf<String>()
        if (internalDir.exists()) {
            addedPaths.add(internalDir.absolutePath)
        }

        externalDirs.forEachIndexed { index, dir ->
            val volume = dir?.parentFile?.parentFile?.parentFile?.parentFile
            if (volume != null && volume.exists() && addedPaths.add(volume.absolutePath)) {
                includeRoot(
                    result = result,
                    rootId = "secondary_$index",
                    title = volume.name.ifEmpty { "SD Card $index" },
                    summary = formatStorageSummary(volume),
                    docId = volume.absolutePath,
                    file = volume
                )
            }
        }

        // 3. Saved Remote Connections (FTP, FTPS, SFTP, WebDAV, SMB/LAN)
        try {
            val connections = NetworkConnectionsService.getConnections(context)
            for (conn in connections) {
                includeRemoteRoot(result, conn)
            }
        } catch (_: Exception) {
        }

        return result
    }

    private fun includeRoot(
        result: MatrixCursor,
        rootId: String,
        title: String,
        summary: String,
        docId: String,
        file: File
    ) {
        val row = result.newRow()
        row.set(Root.COLUMN_ROOT_ID, rootId)
        row.set(
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or
                    Root.FLAG_SUPPORTS_IS_CHILD or
                    Root.FLAG_SUPPORTS_RECENTS or
                    Root.FLAG_SUPPORTS_SEARCH
        )
        row.set(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        row.set(Root.COLUMN_TITLE, title)
        row.set(Root.COLUMN_SUMMARY, summary)
        row.set(Root.COLUMN_DOCUMENT_ID, docId)

        try {
            val statFs = StatFs(file.absolutePath)
            row.set(Root.COLUMN_AVAILABLE_BYTES, statFs.availableBytes)
            row.set(Root.COLUMN_CAPACITY_BYTES, statFs.totalBytes)
        } catch (_: Exception) {
        }

        row.set(Root.COLUMN_MIME_TYPES, "*/*")
    }

    private fun includeRemoteRoot(result: MatrixCursor, conn: NetworkConnectionModel) {
        val row = result.newRow()
        row.set(Root.COLUMN_ROOT_ID, "remote_${conn.id}")
        row.set(
            Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or
                    Root.FLAG_SUPPORTS_IS_CHILD or
                    Root.FLAG_SUPPORTS_SEARCH
        )
        row.set(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        row.set(Root.COLUMN_TITLE, "${conn.type}: ${conn.name}")
        row.set(Root.COLUMN_SUMMARY, "${conn.type} • ${conn.host}")
        row.set(Root.COLUMN_DOCUMENT_ID, buildRemoteDocId(conn.id, "/"))
        row.set(Root.COLUMN_MIME_TYPES, "*/*")
    }

    private fun formatStorageSummary(dir: File): String {
        return try {
            val statFs = StatFs(dir.absolutePath)
            val free = statFs.availableBytes / (1024 * 1024 * 1024.0)
            val total = statFs.totalBytes / (1024 * 1024 * 1024.0)
            String.format("%.1f GB free of %.1f GB", free, total)
        } catch (_: Exception) {
            dir.absolutePath
        }
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        if (isRemoteDoc(documentId)) {
            val (connId, remotePath) = parseRemoteDocId(documentId)
            val conn = getConnection(connId) ?: throw FileNotFoundException("Remote connection not found: $connId")

            if (remotePath == "/" || remotePath.isEmpty()) {
                val row = result.newRow()
                row.set(Document.COLUMN_DOCUMENT_ID, documentId)
                row.set(Document.COLUMN_DISPLAY_NAME, "${conn.type}: ${conn.name}")
                row.set(Document.COLUMN_SIZE, 0L)
                row.set(Document.COLUMN_LAST_MODIFIED, 0L)
                row.set(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                row.set(
                    Document.COLUMN_FLAGS,
                    Document.FLAG_DIR_SUPPORTS_CREATE or
                            Document.FLAG_SUPPORTS_DELETE or
                            Document.FLAG_SUPPORTS_RENAME
                )
            } else {
                val client = RemoteConnectionPool.clientFor(conn)
                val parent = RemotePaths.parent(remotePath) ?: "/"
                val name = RemotePaths.name(remotePath)
                val items = try {
                    client.listDirectory(parent)
                } catch (_: Exception) {
                    emptyList()
                }
                val item = items.find { it.name == name }

                val isDir = item?.isDirectory ?: false
                val mime = if (isDir) Document.MIME_TYPE_DIR else getMimeFromExtension(name)

                val row = result.newRow()
                row.set(Document.COLUMN_DOCUMENT_ID, documentId)
                row.set(Document.COLUMN_DISPLAY_NAME, name)
                row.set(Document.COLUMN_SIZE, item?.size ?: 0L)
                row.set(Document.COLUMN_LAST_MODIFIED, item?.modified?.time ?: 0L)
                row.set(Document.COLUMN_MIME_TYPE, mime)

                var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
                if (isDir) {
                    flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
                } else {
                    flags = flags or Document.FLAG_SUPPORTS_WRITE
                }
                row.set(Document.COLUMN_FLAGS, flags)
            }
            return result
        }

        val file = getFileForDocId(documentId)
        includeDocument(result, documentId, file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        if (isRemoteDoc(parentDocumentId)) {
            val (connId, remotePath) = parseRemoteDocId(parentDocumentId)
            val conn = getConnection(connId) ?: throw FileNotFoundException("Remote connection not found: $connId")

            val client = RemoteConnectionPool.clientFor(conn)
            val items = try {
                client.listDirectory(remotePath).sortedWith(
                    compareBy<com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteFileItem> { !it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
            } catch (_: Exception) {
                emptyList()
            }

            for (item in items) {
                val childDocId = buildRemoteDocId(connId, item.path)
                val mime = if (item.isDirectory) Document.MIME_TYPE_DIR else getMimeFromExtension(item.name)

                val row = result.newRow()
                row.set(Document.COLUMN_DOCUMENT_ID, childDocId)
                row.set(Document.COLUMN_DISPLAY_NAME, item.name)
                row.set(Document.COLUMN_SIZE, item.size)
                row.set(Document.COLUMN_LAST_MODIFIED, item.modified.time)
                row.set(Document.COLUMN_MIME_TYPE, mime)

                var flags = Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
                if (item.isDirectory) {
                    flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
                } else {
                    flags = flags or Document.FLAG_SUPPORTS_WRITE
                }
                row.set(Document.COLUMN_FLAGS, flags)
            }
            return result
        }

        val parent = getFileForDocId(parentDocumentId)
        val children = parent.listFiles() ?: emptyArray()

        val sorted = children.sortedWith(
            compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )

        for (child in sorted) {
            includeDocument(result, child.absolutePath, child)
        }
        return result
    }

    override fun queryRecentDocuments(rootId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (rootId.startsWith("remote_")) return result

        val rootDir = if (rootId == "primary") {
            Environment.getExternalStorageDirectory()
        } else {
            File(rootId)
        }
        if (!rootDir.exists()) return result

        val recentFiles = mutableListOf<File>()
        findRecentFiles(rootDir, recentFiles, maxCount = 64, maxDepth = 4)

        recentFiles.sortByDescending { it.lastModified() }
        for (file in recentFiles) {
            includeDocument(result, file.absolutePath, file)
        }
        return result
    }

    private fun findRecentFiles(dir: File, list: MutableList<File>, maxCount: Int, maxDepth: Int) {
        if (maxDepth <= 0 || list.size >= maxCount) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.isDirectory) {
                findRecentFiles(file, list, maxCount, maxDepth - 1)
            } else if (file.isFile) {
                list.add(file)
                if (list.size >= maxCount) return
            }
        }
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?
    ): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        if (rootId.startsWith("remote_")) {
            val connId = rootId.removePrefix("remote_")
            val conn = getConnection(connId) ?: return result
            val client = RemoteConnectionPool.clientFor(conn)
            val matches = mutableListOf<com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteFileItem>()
            searchRemoteFiles(client, "/", query.lowercase(), matches, maxCount = 64, maxDepth = 3)

            for (item in matches) {
                val childDocId = buildRemoteDocId(connId, item.path)
                val mime = if (item.isDirectory) Document.MIME_TYPE_DIR else getMimeFromExtension(item.name)

                val row = result.newRow()
                row.set(Document.COLUMN_DOCUMENT_ID, childDocId)
                row.set(Document.COLUMN_DISPLAY_NAME, item.name)
                row.set(Document.COLUMN_SIZE, item.size)
                row.set(Document.COLUMN_LAST_MODIFIED, item.modified.time)
                row.set(Document.COLUMN_MIME_TYPE, mime)
                row.set(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME)
            }
            return result
        }

        val rootDir = if (rootId == "primary") {
            Environment.getExternalStorageDirectory()
        } else {
            File(rootId)
        }
        if (!rootDir.exists()) return result

        val matches = mutableListOf<File>()
        searchFiles(rootDir, query.lowercase(), matches, maxCount = 100, maxDepth = 6)

        for (file in matches) {
            includeDocument(result, file.absolutePath, file)
        }
        return result
    }

    private fun searchRemoteFiles(
        client: com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteClient,
        path: String,
        query: String,
        matches: MutableList<com.raival.compose.file.explorer.screen.main.tab.files.service.remote.RemoteFileItem>,
        maxCount: Int,
        maxDepth: Int
    ) {
        if (maxDepth <= 0 || matches.size >= maxCount) return
        val items = try {
            client.listDirectory(path)
        } catch (_: Exception) {
            return
        }

        for (item in items) {
            if (item.name.lowercase().contains(query)) {
                matches.add(item)
                if (matches.size >= maxCount) return
            }
            if (item.isDirectory) {
                searchRemoteFiles(client, item.path, query, matches, maxCount, maxDepth - 1)
            }
        }
    }

    private fun searchFiles(dir: File, query: String, list: MutableList<File>, maxCount: Int, maxDepth: Int) {
        if (maxDepth <= 0 || list.size >= maxCount) return
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.name.startsWith(".")) continue
            if (file.name.lowercase().contains(query)) {
                list.add(file)
                if (list.size >= maxCount) return
            }
            if (file.isDirectory) {
                searchFiles(file, query, list, maxCount, maxDepth - 1)
            }
        }
    }

    private fun includeDocument(result: MatrixCursor, docId: String, file: File) {
        val row = result.newRow()
        row.set(Document.COLUMN_DOCUMENT_ID, docId)
        row.set(Document.COLUMN_DISPLAY_NAME, file.name.ifEmpty { docId })
        row.set(Document.COLUMN_SIZE, if (file.isDirectory) 0L else file.length())
        row.set(Document.COLUMN_LAST_MODIFIED, file.lastModified())

        val mimeType = getDocumentType(docId)
        row.set(Document.COLUMN_MIME_TYPE, mimeType)

        var flags = 0
        if (file.isDirectory) {
            if (file.canWrite()) {
                flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
                flags = flags or Document.FLAG_SUPPORTS_DELETE
                flags = flags or Document.FLAG_SUPPORTS_RENAME
                flags = flags or Document.FLAG_SUPPORTS_COPY
                flags = flags or Document.FLAG_SUPPORTS_MOVE
            }
        } else {
            if (file.canWrite()) {
                flags = flags or Document.FLAG_SUPPORTS_WRITE
                flags = flags or Document.FLAG_SUPPORTS_DELETE
                flags = flags or Document.FLAG_SUPPORTS_RENAME
                flags = flags or Document.FLAG_SUPPORTS_COPY
                flags = flags or Document.FLAG_SUPPORTS_MOVE
            }
            if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType == "application/pdf") {
                flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
            }
        }
        row.set(Document.COLUMN_FLAGS, flags)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (isRemoteDoc(documentId)) {
            val (connId, remotePath) = parseRemoteDocId(documentId)
            val conn = getConnection(connId) ?: throw FileNotFoundException("Remote connection not found: $connId")
            val client = RemoteConnectionPool.clientFor(conn)

            val cacheDir = File(context?.cacheDir, "saf_remote_cache/$connId").apply { mkdirs() }
            val fileName = RemotePaths.name(remotePath)
            val localFile = File(cacheDir, fileName)

            client.downloadFile(remotePath, localFile.absolutePath) {}
            val accessMode = ParcelFileDescriptor.parseMode(mode)
            return ParcelFileDescriptor.open(localFile, accessMode)
        }

        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        if (isRemoteDoc(documentId)) return null

        val file = getFileForDocId(documentId)
        val mimeType = getDocumentType(documentId)

        return try {
            val bitmap: Bitmap? = when {
                mimeType.startsWith("image/") -> {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    val sampleSize = maxOf(
                        options.outWidth / maxOf(sizeHint.x, 1),
                        options.outHeight / maxOf(sizeHint.y, 1)
                    )
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = maxOf(1, sampleSize)
                    }
                    BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                }
                mimeType.startsWith("video/") -> {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(
                        file.absolutePath,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                }
                else -> null
            }

            if (bitmap != null) {
                val tempFile = File.createTempFile("thumb_", ".jpg", context?.cacheDir)
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                AssetFileDescriptor(pfd, 0, tempFile.length())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        if (isRemoteDoc(parentDocumentId)) {
            val (connId, remotePath) = parseRemoteDocId(parentDocumentId)
            val conn = getConnection(connId) ?: throw FileNotFoundException("Remote connection not found: $connId")
            val client = RemoteConnectionPool.clientFor(conn)
            val targetPath = RemotePaths.join(remotePath, displayName)

            if (Document.MIME_TYPE_DIR == mimeType) {
                client.createDirectory(targetPath)
            } else {
                client.createFile(targetPath)
            }
            return buildRemoteDocId(connId, targetPath)
        }

        val parent = getFileForDocId(parentDocumentId)
        val newFile = File(parent, displayName)

        if (Document.MIME_TYPE_DIR == mimeType) {
            if (!newFile.mkdirs() && !newFile.isDirectory) {
                throw FileNotFoundException("Failed to create directory: ${newFile.absolutePath}")
            }
        } else {
            try {
                if (!newFile.createNewFile()) {
                    throw FileNotFoundException("Failed to create file: ${newFile.absolutePath}")
                }
            } catch (e: IOException) {
                throw FileNotFoundException("Failed to create file: ${e.message}")
            }
        }
        return newFile.absolutePath
    }

    override fun deleteDocument(documentId: String) {
        if (isRemoteDoc(documentId)) {
            val (connId, remotePath) = parseRemoteDocId(documentId)
            val conn = getConnection(connId) ?: throw FileNotFoundException("Remote connection not found: $connId")
            val client = RemoteConnectionPool.clientFor(conn)
            client.deleteRecursive(remotePath, isDir = true)
            return
        }

        val file = getFileForDocId(documentId)
        if (!file.deleteRecursively()) {
            throw FileNotFoundException("Failed to delete document: $documentId")
        }
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (isRemoteDoc(documentId)) {
            val (connId, remotePath) = parseRemoteDocId(documentId)
            val conn = getConnection(connId) ?: throw FileNotFoundException("Remote connection not found: $connId")
            val client = RemoteConnectionPool.clientFor(conn)
            val parent = RemotePaths.parent(remotePath) ?: "/"
            val newPath = RemotePaths.join(parent, displayName)
            client.rename(remotePath, newPath)
            return buildRemoteDocId(connId, newPath)
        }

        val file = getFileForDocId(documentId)
        val parent = file.parentFile ?: throw FileNotFoundException("Cannot rename root document")
        val target = File(parent, displayName)
        if (!file.renameTo(target)) {
            throw FileNotFoundException("Failed to rename document to: $displayName")
        }
        return target.absolutePath
    }

    override fun copyDocument(sourceDocumentId: String, targetParentDocumentId: String): String {
        val src = getFileForDocId(sourceDocumentId)
        val targetParent = getFileForDocId(targetParentDocumentId)
        val dest = File(targetParent, src.name)
        src.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String
    ): String {
        val src = getFileForDocId(sourceDocumentId)
        val targetParent = getFileForDocId(targetParentDocumentId)
        val dest = File(targetParent, src.name)
        if (!src.renameTo(dest)) {
            src.copyTo(dest, overwrite = true)
            src.deleteRecursively()
        }
        return dest.absolutePath
    }

    override fun removeDocument(documentId: String, parentDocumentId: String) {
        deleteDocument(documentId)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        if (isRemoteDoc(parentDocumentId) && isRemoteDoc(documentId)) {
            val (parentConnId, parentPath) = parseRemoteDocId(parentDocumentId)
            val (docConnId, docPath) = parseRemoteDocId(documentId)
            return parentConnId == docConnId && docPath.startsWith(parentPath)
        }
        return documentId.startsWith(parentDocumentId)
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String
    ): DocumentsContract.Path {
        val pathSegments = mutableListOf<String>()

        if (isRemoteDoc(childDocumentId)) {
            val (connId, remotePath) = parseRemoteDocId(childDocumentId)
            var cur: String? = remotePath
            while (cur != null) {
                pathSegments.add(0, buildRemoteDocId(connId, cur))
                cur = RemotePaths.parent(cur)
            }
            return DocumentsContract.Path(null, pathSegments)
        }

        var cur: File? = File(childDocumentId)
        val parentFile = parentDocumentId?.let { File(it) }

        while (cur != null) {
            pathSegments.add(0, cur.absolutePath)
            if (parentFile != null && cur.absolutePath == parentFile.absolutePath) {
                break
            }
            cur = cur.parentFile
        }

        return DocumentsContract.Path(null, pathSegments)
    }

    override fun getDocumentType(documentId: String): String {
        if (isRemoteDoc(documentId)) {
            val (_, remotePath) = parseRemoteDocId(documentId)
            val name = RemotePaths.name(remotePath)
            return getMimeFromExtension(name)
        }

        val file = getFileForDocId(documentId)
        if (file.isDirectory) {
            return Document.MIME_TYPE_DIR
        }

        val extension = file.extension.lowercase()
        if (extension.isNotEmpty()) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (!mime.isNullOrEmpty()) return mime
        }

        val detected = MimeTypeDetector.detect(file)
        return detected?.mimeType ?: "application/octet-stream"
    }

    private fun getFileForDocId(docId: String): File {
        val file = File(docId)
        if (!file.exists()) {
            throw FileNotFoundException("File not found: $docId")
        }
        return file
    }

    private fun isRemoteDoc(docId: String): Boolean = docId.startsWith(REMOTE_PREFIX)

    private fun buildRemoteDocId(connId: String, path: String): String = "$REMOTE_PREFIX$connId:$path"

    private fun parseRemoteDocId(docId: String): Pair<String, String> {
        val withoutPrefix = docId.removePrefix(REMOTE_PREFIX)
        val colonIdx = withoutPrefix.indexOf(':')
        return if (colonIdx >= 0) {
            val connId = withoutPrefix.substring(0, colonIdx)
            val path = withoutPrefix.substring(colonIdx + 1)
            Pair(connId, if (path.isEmpty()) "/" else path)
        } else {
            Pair(withoutPrefix, "/")
        }
    }

    private fun getConnection(connId: String): NetworkConnectionModel? {
        val ctx = context ?: return null
        return NetworkConnectionsService.getConnections(ctx).find { it.id == connId }
    }

    private fun getMimeFromExtension(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            if (!mime.isNullOrEmpty()) return mime
        }
        return "application/octet-stream"
    }

    private fun MatrixCursor.RowBuilder.set(columnName: String, value: Any?) {
        try {
            if (value != null) {
                add(columnName, value)
            }
        } catch (_: IllegalArgumentException) {
            // Ignored when caller does not request this column in their projection
        }
    }
}
