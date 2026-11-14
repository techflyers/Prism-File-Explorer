package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.media.MediaScannerConnection
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import java.util.concurrent.ConcurrentHashMap
import androidx.core.content.ContextCompat
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.RemoteFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.RootFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.StorageDevice
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileMimeType
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.FileSortingPrefs
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_DATE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_NAME
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_SIZE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.SortingMethod.SORT_BY_TYPE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.EXTERNAL_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.INTERNAL_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.REMOTE_STORAGE
import com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.ROOT
import com.techflyers.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionsService
import com.techflyers.compose.file.explorer.screen.main.tab.home.holder.RecentFile
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object StorageProvider {
    suspend fun getStorageDevices(context: Context): List<StorageDevice> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val storageDevices = mutableListOf<StorageDevice>()

        storageDevices.add(getPrimaryInternalStorage(context))

        val externalStoragePairs = getExternalStorageDirectories(context)

        for ((label, externalDir) in externalStoragePairs) {
            if (externalDir.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
                continue
            }

            try {
                val statFs = StatFs(externalDir.absolutePath)
                val totalSize = statFs.totalBytes
                val availableSize = statFs.availableBytes
                val usedSize = totalSize - availableSize
                val storageLabel = label.ifEmpty { "${externalDir.name}" }

                storageDevices.add(
                    StorageDevice(
                        LocalFileHolder(externalDir),
                        storageLabel,
                        totalSize,
                        usedSize,
                        EXTERNAL_STORAGE
                    )
                )
            } catch (_: Exception) {
            }
        }

        if (!globalClass.preferencesManager.hideRootStorage) {
            storageDevices.add(getRoot(context))
        }

        NetworkConnectionsService.getConnections(context).forEach { connection ->
            storageDevices.add(
                StorageDevice(
                    RemoteFileHolder.rootHolder(connection),
                    connection.name,
                    0L,
                    0L,
                    REMOTE_STORAGE
                )
            )
        }

        storageDevices
    }

    fun getRoot(context: Context): StorageDevice {
        val externalStorageDir = Environment.getRootDirectory()

        val externalStatFs = StatFs(externalStorageDir.absolutePath)

        val externalTotalSize = externalStatFs.totalBytes
        val externalAvailableSize = externalStatFs.availableBytes
        val externalUsedSize = externalTotalSize - externalAvailableSize

        return StorageDevice(
            RootFileHolder(),
            context.getString(R.string.root_dir),
            externalTotalSize,
            externalUsedSize,
            ROOT
        )
    }

    fun getPrimaryInternalStorage(context: Context): StorageDevice {
        val externalStorageDir = Environment.getExternalStorageDirectory()

        val externalStatFs = StatFs(externalStorageDir.absolutePath)

        val externalTotalSize = externalStatFs.totalBytes
        val externalAvailableSize = externalStatFs.availableBytes
        val externalUsedSize = externalTotalSize - externalAvailableSize

        return StorageDevice(
            LocalFileHolder(externalStorageDir),
            context.getString(R.string.internal_storage),
            externalTotalSize,
            externalUsedSize,
            INTERNAL_STORAGE
        )
    }

    fun getAvailableStorageRoots(context: Context): List<Pair<String, File>> {
        val list = mutableListOf<Pair<String, File>>()
        list.add(Pair(context.getString(R.string.internal_storage), Environment.getExternalStorageDirectory()))
        val externalPairs = getExternalStorageDirectories(context)
        for (pair in externalPairs) {
            if (pair.second.absolutePath != Environment.getExternalStorageDirectory().absolutePath &&
                list.none { it.second.absolutePath == pair.second.absolutePath }
            ) {
                list.add(pair)
            }
        }
        val rootDir = File("/")
        if (rootDir.exists()) {
            list.add(Pair(context.getString(R.string.root_dir), rootDir))
        }
        return list
    }

    fun getExternalStorageDirectories(context: Context): List<Pair<String, File>> {
        val storageList = mutableListOf<Pair<String, File>>()
        val addedPaths = mutableSetOf<String>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        // Method 1: Using ContextCompat.getExternalFilesDirs ()
        ContextCompat.getExternalFilesDirs(context, null).forEach { directory ->
            val volume = directory?.parentFile?.parentFile?.parentFile?.parentFile
            if (volume != null && volume.exists() && addedPaths.add(volume.absolutePath)) {
                var label = ""
                try {
                    val storageVolume = storageManager.getStorageVolume(volume)
                    if (storageVolume != null) {
                        label =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                storageVolume.getDescription(context) ?: volume.name
                            } else {
                                @Suppress("DEPRECATION")
                                storageVolume.getDescription(context) ?: volume.name
                            }
                    }
                } catch (_: Exception) { /* Ignore */
                }
                storageList.add(Pair(label.ifEmpty { volume.name }, volume))
            }
        }

        // Method 2: Parse /proc/mounts for additional storage devices
        try {
            val mountsFile = File("/proc/mounts")
            if (mountsFile.exists()) {
                mountsFile.readLines().forEach { line ->
                    val parts = line.split(" ")
                    if (parts.size >= 2) {
                        val mountPoint = parts[1]
                        val fsType = if (parts.size >= 3) parts[2] else ""

                        // Look for common removable storage mount points and file systems
                        if ((mountPoint.contains("/storage/") || mountPoint.contains("/mnt/")) &&
                            !mountPoint.contains("emulated") &&
                            (fsType in listOf("vfat", "exfat", "ntfs", "ext4", "ext3", "ext2")) &&
                            mountPoint != "/storage/self"
                        ) {

                            val storageDir = File(mountPoint)
                            if (storageDir.exists() && storageDir.canRead() &&
                                addedPaths.add(storageDir.absolutePath)
                            ) {
                                storageList.add(Pair(storageDir.name, storageDir))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore errors reading /proc/mounts
        }

        // Method 3: Check common OTG/USB mount points
        val commonOtgPaths = listOf(
            "/storage/usb",
            "/storage/usbotg",
            "/storage/usb1",
            "/storage/usb2",
            "/mnt/usb",
            "/mnt/usbdisk",
            "/mnt/usb_storage"
        )

        commonOtgPaths.forEach { path ->
            val dir = File(path)
            if (dir.exists() && dir.canRead() && addedPaths.add(dir.absolutePath)) {
                storageList.add(Pair(dir.name, dir))
            }

            // Also check subdirectories
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { subDir ->
                    if (subDir.isDirectory && subDir.canRead() &&
                        addedPaths.add(subDir.absolutePath)
                    ) {
                        storageList.add(Pair(subDir.name, subDir))
                    }
                }
            }
        }

        return storageList
    }

    /**
     * Unified, safe paged MediaStore query helper.
     * Avoids passing both QUERY_ARG_SQL_SORT_ORDER and QUERY_ARG_SORT_COLUMNS (which throws
     * IllegalArgumentException in Android's MediaProvider) and strictly enforces [offset] and [limit]
     * even on devices/ROMs where ContentResolver ignores Bundle pagination arguments.
     */
    private fun queryMediaStorePaged(
        selection: String,
        selectionArgs: Array<String>?,
        sortingPrefs: FileSortingPrefs?,
        limit: Int,
        offset: Int
    ): ArrayList<LocalFileHolder> {
        if (limit <= 0) return ArrayList()
        val files = ArrayList<LocalFileHolder>(minOf(limit, 256))
        val contentResolver: ContentResolver = globalClass.contentResolver
        val uri: Uri = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )

        val sortMethod = sortingPrefs?.sortMethod
        val reverse = sortingPrefs?.reverseSorting == true

        val sortOrderSql = when (sortMethod) {
            SORT_BY_NAME -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE ${if (reverse) "DESC" else "ASC"}"
            SORT_BY_DATE -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ${if (reverse) "ASC" else "DESC"}"
            SORT_BY_SIZE -> "${MediaStore.Files.FileColumns.SIZE} ${if (reverse) "ASC" else "DESC"}"
            SORT_BY_TYPE -> {
                val direction = if (reverse) "DESC" else "ASC"
                "CASE WHEN INSTR(${MediaStore.Files.FileColumns.DISPLAY_NAME}, '.') = 0 THEN 1 ELSE 0 END, " +
                    "SUBSTR(${MediaStore.Files.FileColumns.DISPLAY_NAME}, INSTR(${MediaStore.Files.FileColumns.DISPLAY_NAME}, '.') + 1) $direction"
            }
            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }

        var cursor: Cursor? = null
        var usedBundlePagination = false

        if (sortMethod != SORT_BY_TYPE) {
            val sortCol = when (sortMethod) {
                SORT_BY_NAME -> MediaStore.Files.FileColumns.DISPLAY_NAME
                SORT_BY_SIZE -> MediaStore.Files.FileColumns.SIZE
                else -> MediaStore.Files.FileColumns.DATE_MODIFIED
            }
            val sortDir = when (sortMethod) {
                SORT_BY_NAME -> if (reverse) ContentResolver.QUERY_SORT_DIRECTION_DESCENDING else ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                SORT_BY_SIZE, SORT_BY_DATE -> if (reverse) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                else -> ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            }
            // IMPORTANT: Do NOT set QUERY_ARG_SQL_SORT_ORDER together with QUERY_ARG_SORT_COLUMNS;
            // Android's MediaProvider throws IllegalArgumentException if both are present.
            val queryArgs = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                if (selectionArgs != null) {
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                }
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortCol))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, sortDir)
            }
            try {
                cursor = contentResolver.query(uri, projection, queryArgs, null)
                if (cursor != null) {
                    usedBundlePagination = true
                }
            } catch (_: Exception) {
                cursor = null
            }
        }

        if (cursor == null) {
            try {
                cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrderSql)
                usedBundlePagination = false
            } catch (_: Exception) {
                cursor = null
            }
        }

        cursor?.use { c ->
            val pathColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            // When fallback non-Bundle query was used, manually advance the cursor to [offset].
            if (!usedBundlePagination && offset > 0) {
                if (!c.moveToPosition(offset - 1)) {
                    return files
                }
            }

            val seenInBatch = HashSet<String>(minOf(limit, 256))
            while (c.moveToNext() && files.size < limit) {
                val path = c.getString(pathColumn)
                if (!path.isNullOrEmpty() && seenInBatch.add(path)) {
                    val name = c.getString(nameColumn) ?: File(path).name
                    val dateModified = c.getLong(dateColumn) * 1000L
                    val size = c.getLong(sizeColumn)
                    files.add(
                        LocalFileHolder(
                            file = File(path),
                            cachedName = name,
                            cachedIsDir = false,
                            cachedSize = size,
                            cachedLastModified = dateModified
                        )
                    )
                }
            }
        }

        return files
    }

    /**
     * Generic helper function to fetch files based on a list of MIME types.
     */
    private fun getFilesByMimeTypes(
        mimeTypes: Array<String>,
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        if (mimeTypes.isEmpty()) {
            return ArrayList()
        }
        val selection =
            mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
        return queryMediaStorePaged(
            selection = selection,
            selectionArgs = mimeTypes,
            sortingPrefs = sortingPrefs,
            limit = limit,
            offset = offset
        )
    }

    /**
     * Gets all document files.
     */
    fun getDocumentFiles(
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        val documentMimeTypes = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // XLSX
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" // PPTX
        )
        return getFilesByMimeTypes(documentMimeTypes, sortingPrefs, limit, offset)
    }

    /**
     * Gets all archive files.
     */
    fun getArchiveFiles(
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        val archiveMimeTypes = arrayOf(
            "application/zip",
            "application/x-rar-compressed",
            "application/x-tar",
            "application/gzip",
            "application/x-7z-compressed"
        )
        return getFilesByMimeTypes(archiveMimeTypes, sortingPrefs, limit, offset)
    }

    fun getApkFiles(
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apk' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apks' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xapk' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apkm'"
        val selectionArgs = arrayOf("application/vnd.android.package-archive")
        return queryMediaStorePaged(
            selection = selection,
            selectionArgs = selectionArgs,
            sortingPrefs = sortingPrefs,
            limit = limit,
            offset = offset
        )
    }

    /**
     * Generic helper function to fetch media files of a specific type.
     */
    private fun getMediaFiles(
        mediaType: Int,
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(mediaType.toString())
        return queryMediaStorePaged(
            selection = selection,
            selectionArgs = selectionArgs,
            sortingPrefs = sortingPrefs,
            limit = limit,
            offset = offset
        )
    }

    /**
     * Gets all image files.
     */
    fun getImageFiles(
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        return getMediaFiles(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, sortingPrefs, limit, offset)
    }

    /**
     * Gets all video files.
     */
    fun getVideoFiles(
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        return getMediaFiles(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, sortingPrefs, limit, offset)
    }

    /**
     * Gets all audio files.
     */
    fun getAudioFiles(
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        return getMediaFiles(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, sortingPrefs, limit, offset)
    }

    fun getBookmarks(): ArrayList<LocalFileHolder> {
        val titles = globalClass.preferencesManager.bookmarkTitles
        val list = ArrayList<LocalFileHolder>()
        for (path in globalClass.preferencesManager.bookmarks) {
            val file = File(path)
            val title = titles[path]
            list.add(LocalFileHolder(file, cachedName = title))
        }
        return list
    }

    @Volatile
    private var cachedAllDeviceFiles: ArrayList<RecentFile>? = null
    @Volatile
    private var cachedAllDeviceKey: String = ""
    @Volatile
    private var cachedAllDeviceTimestamp: Long = 0L
    @Volatile
    private var lastMutationCounter: Long = 0L
    private const val RECENT_CACHE_TTL_MS = 20_000L
    private const val INDEX_REFRESH_INTERVAL_MS = 45_000L
    private const val INDEX_FILE_NAME = "extensive_recent_files_index_v1.bin"
    private const val INDEX_MAGIC = 0x50524649 // "PRFI"

    private val recentlyMutatedFiles = ConcurrentHashMap<String, Long>()

    private data class IndexedFileRecord(
        val path: String,
        val name: String,
        val size: Long,
        val lastModifiedMs: Long
    )

    private data class IndexedDirRecord(
        val dirPath: String,
        val dirMtime: Long,
        val files: List<IndexedFileRecord>,
        val subDirs: List<String>
    )

    private val dirIndexMap = ConcurrentHashMap<String, IndexedDirRecord>()
    private val indexedFilesByPath = ConcurrentHashMap<String, IndexedFileRecord>()
    @Volatile
    private var sortedIndexedRecords: List<IndexedFileRecord> = emptyList()
    @Volatile
    private var persistentIndexLoaded = false
    @Volatile
    private var lastIndexBuildTimestamp = 0L
    private val indexLoadLock = Any()
    private val isIndexJobRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    private val indexScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    var isIndexingRecentFiles by androidx.compose.runtime.mutableStateOf(false)
        private set
    var indexingProgress by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set
    var indexedRecentFilesCount by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var lastIndexCompletedTimeMs by androidx.compose.runtime.mutableLongStateOf(0L)
        private set

    private val tempDbSuffixes = listOf(
        ".tmp", ".temp", ".bak", ".log",
        ".crdownload", ".part",
        "-shm", "-wal", "-journal",
        ".db-shm", ".db-wal", ".db-journal"
    )

    private val skippedSystemDirNames = setOf(
        "lost+found", ".thumbnails", ".Trash", ".trashed", ".recycle", "recyclebin"
    )

    private fun rebuildSortedIndexedRecords() {
        val snapshot = ArrayList(indexedFilesByPath.values)
        snapshot.sortWith { a, b -> b.lastModifiedMs.compareTo(a.lastModifiedMs) }
        sortedIndexedRecords = snapshot
    }

    fun invalidateRecentFilesCache() {
        cachedAllDeviceTimestamp = 0L
        lastMutationCounter++
    }

    fun notifyFileMutated(file: File) {
        try {
            val now = System.currentTimeMillis()
            if (file.exists() && file.isFile) {
                val path = file.absolutePath
                val mtime = maxOf(file.lastModified(), now)
                recentlyMutatedFiles[path] = mtime
                indexedFilesByPath[path] = IndexedFileRecord(path, file.name, file.length(), mtime)
                file.parentFile?.absolutePath?.let { dirIndexMap.remove(it) }
            } else if (file.exists() && file.isDirectory) {
                dirIndexMap.remove(file.absolutePath)
                file.parentFile?.absolutePath?.let { dirIndexMap.remove(it) }
                file.walkTopDown().maxDepth(4).forEach { child ->
                    if (child.isFile) {
                        val path = child.absolutePath
                        val mtime = maxOf(child.lastModified(), now)
                        recentlyMutatedFiles[path] = mtime
                        indexedFilesByPath[path] = IndexedFileRecord(path, child.name, child.length(), mtime)
                    } else if (child.isDirectory) {
                        dirIndexMap.remove(child.absolutePath)
                    }
                }
            } else if (!file.exists()) {
                val path = file.absolutePath
                recentlyMutatedFiles.remove(path)
                indexedFilesByPath.remove(path)
                file.parentFile?.absolutePath?.let { dirIndexMap.remove(it) }
            }
            indexedRecentFilesCount = indexedFilesByPath.size
            invalidateRecentFilesCache()
            MediaScannerConnection.scanFile(
                globalClass,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (_: Exception) {}
    }

    private fun ensurePersistentIndexLoaded() {
        if (persistentIndexLoaded) return
        synchronized(indexLoadLock) {
            if (persistentIndexLoaded) return
            persistentIndexLoaded = true
            val indexFile = File(globalClass.cacheDir, INDEX_FILE_NAME)
            if (!indexFile.exists()) return
            try {
                java.io.DataInputStream(java.io.BufferedInputStream(java.io.FileInputStream(indexFile))).use { inp ->
                    val magic = inp.readInt()
                    if (magic != INDEX_MAGIC) return
                    val savedTimestamp = inp.readLong()
                    val dirCount = inp.readInt()
                    val loadedDirs = HashMap<String, IndexedDirRecord>(dirCount.coerceAtLeast(16))
                    val loadedFiles = HashMap<String, IndexedFileRecord>(4096)
                    for (i in 0 until dirCount) {
                        val dirPath = inp.readUTF()
                        val dirMtime = inp.readLong()
                        val fileCount = inp.readInt()
                        val files = ArrayList<IndexedFileRecord>(fileCount)
                        for (j in 0 until fileCount) {
                            val fPath = inp.readUTF()
                            val fName = inp.readUTF()
                            val fSize = inp.readLong()
                            val fMtime = inp.readLong()
                            val rec = IndexedFileRecord(fPath, fName, fSize, fMtime)
                            files.add(rec)
                            loadedFiles[fPath] = rec
                        }
                        val subDirCount = inp.readInt()
                        val subDirs = ArrayList<String>(subDirCount)
                        for (k in 0 until subDirCount) {
                            subDirs.add(inp.readUTF())
                        }
                        loadedDirs[dirPath] = IndexedDirRecord(dirPath, dirMtime, files, subDirs)
                    }
                    dirIndexMap.putAll(loadedDirs)
                    indexedFilesByPath.putAll(loadedFiles)
                    rebuildSortedIndexedRecords()
                    lastIndexBuildTimestamp = savedTimestamp
                    lastIndexCompletedTimeMs = savedTimestamp
                    indexedRecentFilesCount = indexedFilesByPath.size
                }
            } catch (_: Exception) {
                dirIndexMap.clear()
                indexedFilesByPath.clear()
                sortedIndexedRecords = emptyList()
            }
        }
    }

    private fun savePersistentIndexToDisk() {
        val indexFile = File(globalClass.cacheDir, INDEX_FILE_NAME)
        val tempFile = File(globalClass.cacheDir, "$INDEX_FILE_NAME.tmp")
        try {
            val dirsSnapshot = ArrayList(dirIndexMap.values)
            java.io.DataOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(tempFile))).use { out ->
                out.writeInt(INDEX_MAGIC)
                out.writeLong(lastIndexBuildTimestamp)
                out.writeInt(dirsSnapshot.size)
                for (dir in dirsSnapshot) {
                    out.writeUTF(dir.dirPath)
                    out.writeLong(dir.dirMtime)
                    out.writeInt(dir.files.size)
                    for (f in dir.files) {
                        out.writeUTF(f.path)
                        out.writeUTF(f.name)
                        out.writeLong(f.size)
                        out.writeLong(f.lastModifiedMs)
                    }
                    out.writeInt(dir.subDirs.size)
                    for (sub in dir.subDirs) {
                        out.writeUTF(sub)
                    }
                }
                out.flush()
            }
            if (tempFile.exists()) {
                tempFile.renameTo(indexFile)
            }
        } catch (_: Exception) {
            runCatching { tempFile.delete() }
        }
    }

    fun triggerRecentFilesIndexingIfNeeded(forceRebuild: Boolean = false, onFinished: (() -> Unit)? = null) {
        val isExtensiveScan = !globalClass.preferencesManager.removeHiddenPathsFromRecentFiles
        if (!isExtensiveScan) {
            onFinished?.invoke()
            return
        }
        if (!isIndexJobRunning.compareAndSet(false, true)) {
            return
        }
        indexScope.launch {
            try {
                isIndexingRecentFiles = true
                indexingProgress = 0.08f
                ensurePersistentIndexLoaded()
                val changed = buildOrUpdateExtensiveIndex(forceFullRebuild = forceRebuild)
                indexingProgress = 1f
                if (changed || forceRebuild) {
                    invalidateRecentFilesCache()
                }
                if (onFinished != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onFinished()
                    }
                } else if (changed || forceRebuild) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        globalClass.mainActivityManager.state.value.tabs
                            .filterIsInstance<com.techflyers.compose.file.explorer.screen.main.tab.home.HomeTab>()
                            .forEach { it.refreshRecentFiles(forceInvalidate = false) }
                    }
                }
            } finally {
                isIndexingRecentFiles = false
                isIndexJobRunning.set(false)
            }
        }
    }

    private fun buildOrUpdateExtensiveIndex(forceFullRebuild: Boolean = false): Boolean {
        ensurePersistentIndexLoaded()
        val recycleBinPath = runCatching { globalClass.recycleBinDir.file.absolutePath }.getOrDefault("")

        val roots = mutableListOf<File>()
        val primaryRoot = Environment.getExternalStorageDirectory()
        if (primaryRoot.exists()) roots.add(primaryRoot)
        for ((_, extDir) in getExternalStorageDirectories(globalClass)) {
            if (extDir.exists() && roots.none { it.absolutePath == extDir.absolutePath }) {
                roots.add(extDir)
            }
        }

        val updatedDirMap = HashMap<String, IndexedDirRecord>(dirIndexMap.size.coerceAtLeast(256))
        val updatedFileMap = HashMap<String, IndexedFileRecord>(indexedFilesByPath.size.coerceAtLeast(4096))
        val expectedDirBaseline = dirIndexMap.size.coerceAtLeast(120)
        var anyDirChanged = forceFullRebuild || dirIndexMap.isEmpty()
        var lastCountUpdateMs = System.currentTimeMillis()

        fun indexDir(dir: File, depth: Int) {
            if (depth > 14) return
            val dirPath = dir.absolutePath
            val dirName = dir.name
            if (dirName in skippedSystemDirNames) return
            if (recycleBinPath.isNotEmpty() && dirPath.startsWith(recycleBinPath)) return

            val currentDirMtime = dir.lastModified()
            val cachedDir = if (!forceFullRebuild) dirIndexMap[dirPath] else null

            if (cachedDir != null && currentDirMtime > 0L && cachedDir.dirMtime == currentDirMtime) {
                updatedDirMap[dirPath] = cachedDir
                for (f in cachedDir.files) {
                    updatedFileMap[f.path] = f
                }
                val nowCachedMs = System.currentTimeMillis()
                if (nowCachedMs - lastCountUpdateMs >= 220L) {
                    lastCountUpdateMs = nowCachedMs
                    indexedRecentFilesCount = updatedFileMap.size
                    val ratio = (updatedDirMap.size.toFloat() / expectedDirBaseline).coerceIn(0f, 1f)
                    indexingProgress = (0.10f + ratio * 0.72f).coerceIn(indexingProgress, 0.84f)
                }
                for (subPath in cachedDir.subDirs) {
                    val subDir = File(subPath)
                    if (subDir.exists() && subDir.isDirectory) {
                        indexDir(subDir, depth + 1)
                    }
                }
                return
            }

            anyDirChanged = true
            val children = dir.listFiles() ?: return
            val directFiles = ArrayList<IndexedFileRecord>()
            val subDirs = ArrayList<String>()

            for (child in children) {
                val childName = child.name
                if (child.isDirectory) {
                    if (childName !in skippedSystemDirNames) {
                        val childPath = child.absolutePath
                        subDirs.add(childPath)
                        indexDir(child, depth + 1)
                    }
                } else if (child.isFile) {
                    val childPath = child.absolutePath
                    val rec = IndexedFileRecord(
                        path = childPath,
                        name = childName,
                        size = child.length(),
                        lastModifiedMs = child.lastModified()
                    )
                    directFiles.add(rec)
                    updatedFileMap[childPath] = rec
                }
            }

            updatedDirMap[dirPath] = IndexedDirRecord(
                dirPath = dirPath,
                dirMtime = currentDirMtime,
                files = directFiles,
                subDirs = subDirs
            )
            val nowMs = System.currentTimeMillis()
            if (nowMs - lastCountUpdateMs >= 220L) {
                lastCountUpdateMs = nowMs
                indexedRecentFilesCount = updatedFileMap.size
                val ratio = (updatedDirMap.size.toFloat() / expectedDirBaseline).coerceIn(0f, 1f)
                indexingProgress = (0.10f + ratio * 0.72f).coerceIn(indexingProgress, 0.84f)
            }
        }

        for (root in roots) {
            try {
                indexDir(root, 0)
            } catch (_: Exception) {}
        }

        indexingProgress = 0.88f

        // Merge MediaStore.Files for creation/modification timestamp accuracy
        try {
            val uri: Uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE
            )
            globalClass.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val colPath = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val colMod = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val colAdd = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val colName = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val colSize = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
                var unindexedStatChecks = 0

                while (cursor.moveToNext()) {
                    val filePath = cursor.getString(colPath) ?: continue
                    val existing = updatedFileMap[filePath]
                    val modSec = cursor.getLong(colMod)
                    val addSec = if (colAdd >= 0) cursor.getLong(colAdd) else 0L
                    val bestMs = maxOf(modSec, addSec) * 1000L
                    if (existing != null) {
                        if (bestMs > existing.lastModifiedMs) {
                            updatedFileMap[filePath] = existing.copy(lastModifiedMs = bestMs)
                            anyDirChanged = true
                        }
                    } else if (unindexedStatChecks < 150) {
                        unindexedStatChecks++
                        val f = File(filePath)
                        if (f.exists() && f.isFile) {
                            val name = cursor.getString(colName) ?: f.name
                            val size = if (colSize >= 0) cursor.getLong(colSize).takeIf { it > 0 } ?: f.length() else f.length()
                            val timestampMs = maxOf(bestMs, f.lastModified())
                            updatedFileMap[filePath] = IndexedFileRecord(filePath, name, size, timestampMs)
                            anyDirChanged = true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (updatedFileMap.size != indexedFilesByPath.size) {
            anyDirChanged = true
        }
        dirIndexMap.clear()
        dirIndexMap.putAll(updatedDirMap)
        indexedFilesByPath.clear()
        indexedFilesByPath.putAll(updatedFileMap)
        if (anyDirChanged || sortedIndexedRecords.isEmpty()) {
            rebuildSortedIndexedRecords()
        }
        lastIndexBuildTimestamp = System.currentTimeMillis()
        lastIndexCompletedTimeMs = lastIndexBuildTimestamp
        indexedRecentFilesCount = indexedFilesByPath.size
        if (anyDirChanged) {
            savePersistentIndexToDisk()
        }
        return anyDirChanged
    }

    private fun shouldIncludePath(
        path: String,
        name: String,
        showHiddenFiles: Boolean,
        excludeHiddenPaths: Boolean,
        hideTempAndDb: Boolean,
        excludedPaths: Set<String>,
        recycleBinPath: String
    ): Boolean {
        if (path.isEmpty() || name.isEmpty()) return false
        if (recycleBinPath.isNotEmpty() && path.startsWith(recycleBinPath)) return false
        if (!showHiddenFiles && name.startsWith(".")) return false
        if (excludeHiddenPaths && (name.startsWith(".") || path.contains("/.", ignoreCase = false))) return false
        for (excluded in excludedPaths) {
            if (excluded.isNotEmpty() && path.startsWith(excluded)) return false
        }
        if (hideTempAndDb) {
            if (path.contains("/Android/data/") || path.contains("/Android/obb/")) return false
            val lowerName = name.lowercase()
            if (tempDbSuffixes.any { lowerName.endsWith(it) }) return false
        }
        return true
    }

    /**
     * Fast bounded scan that extracts only the top [maxCount] newest matching files from the
     * pre-sorted index + in-app mutated files without allocating or sorting all 50,000+ device files.
     */
    private fun collectTopRecentFiles(maxCount: Int): ArrayList<RecentFile> {
        if (maxCount <= 0) return arrayListOf()
        val now = System.currentTimeMillis()
        ensurePersistentIndexLoaded()

        val records = sortedIndexedRecords
        if (records.isEmpty()) {
            // Never block UI/IO waiting for full filesystem walk; serve MediaStore immediately and index in background
            triggerRecentFilesIndexingIfNeeded(forceRebuild = false)
            return getLegacyMediaStoreRecentFiles(recentHours = 24 * 14, limit = maxCount)
        } else if (now - lastIndexBuildTimestamp > INDEX_REFRESH_INTERVAL_MS) {
            triggerRecentFilesIndexingIfNeeded(forceRebuild = false)
        }

        val prefs = globalClass.preferencesManager
        val showHiddenFiles = prefs.showHiddenFiles
        val excludeHiddenPaths = prefs.removeHiddenPathsFromRecentFiles
        val hideTempAndDb = prefs.hideTempAndDbFilesFromRecentFiles
        val excludedPaths = prefs.excludedPathsFromRecentFiles
        val recycleBinPath = runCatching { globalClass.recycleBinDir.file.absolutePath }.getOrDefault("")

        val resultMap = LinkedHashMap<String, RecentFile>(maxCount * 2)

        // 1. Include recently mutated files first
        val staleMutated = mutableListOf<String>()
        for ((path, recordedTime) in recentlyMutatedFiles) {
            val f = File(path)
            if (f.exists() && f.isFile) {
                val name = f.name
                if (shouldIncludePath(path, name, showHiddenFiles, excludeHiddenPaths, hideTempAndDb, excludedPaths, recycleBinPath)) {
                    val mtime = maxOf(f.lastModified(), recordedTime)
                    val holder = LocalFileHolder(
                        file = f,
                        cachedName = name,
                        cachedIsDir = false,
                        cachedSize = f.length(),
                        cachedLastModified = mtime
                    )
                    resultMap[path] = RecentFile(
                        name = name,
                        path = path,
                        lastModified = mtime / 1000L,
                        file = holder
                    )
                }
            } else if (!f.exists()) {
                staleMutated.add(path)
                indexedFilesByPath.remove(path)
            }
        }
        staleMutated.forEach { recentlyMutatedFiles.remove(it) }

        // 2. Scan pre-sorted records until we have maxCount items from the index
        var addedFromIndex = 0
        for (rec in records) {
            if (addedFromIndex >= maxCount) break
            if (resultMap.containsKey(rec.path)) continue
            if (!shouldIncludePath(rec.path, rec.name, showHiddenFiles, excludeHiddenPaths, hideTempAndDb, excludedPaths, recycleBinPath)) {
                continue
            }
            val f = File(rec.path)
            val holder = LocalFileHolder(
                file = f,
                cachedName = rec.name,
                cachedIsDir = false,
                cachedSize = rec.size,
                cachedLastModified = rec.lastModifiedMs
            )
            resultMap[rec.path] = RecentFile(
                name = rec.name,
                path = rec.path,
                lastModified = rec.lastModifiedMs / 1000L,
                file = holder
            )
            addedFromIndex++
        }

        val list = ArrayList(resultMap.values)
        list.sortWith { a, b -> b.lastModified.compareTo(a.lastModified) }
        return if (list.size > maxCount) ArrayList(list.subList(0, maxCount)) else list
    }

    private fun collectAllDeviceFilesSorted(): ArrayList<RecentFile> {
        val now = System.currentTimeMillis()
        val prefs = globalClass.preferencesManager
        val showHiddenFiles = prefs.showHiddenFiles
        val excludeHiddenPaths = prefs.removeHiddenPathsFromRecentFiles
        val hideTempAndDb = prefs.hideTempAndDbFilesFromRecentFiles
        val excludedPaths = prefs.excludedPathsFromRecentFiles
        val recycleBinPath = runCatching { globalClass.recycleBinDir.file.absolutePath }.getOrDefault("")

        ensurePersistentIndexLoaded()

        val cacheKey = "$showHiddenFiles:$excludeHiddenPaths:$hideTempAndDb:${excludedPaths.joinToString()}:$lastMutationCounter:$lastIndexCompletedTimeMs"
        val existingCache = cachedAllDeviceFiles
        if (existingCache != null && cachedAllDeviceKey == cacheKey) {
            if (now - lastIndexBuildTimestamp > INDEX_REFRESH_INTERVAL_MS) {
                triggerRecentFilesIndexingIfNeeded(forceRebuild = false)
            }
            return existingCache
        }

        val records = sortedIndexedRecords
        if (records.isEmpty()) {
            // Serve MediaStore immediately without blocking while full index builds in the background
            triggerRecentFilesIndexingIfNeeded(forceRebuild = false)
            return getLegacyMediaStoreRecentFiles(recentHours = 24 * 30, limit = 500)
        } else if (now - lastIndexBuildTimestamp > INDEX_REFRESH_INTERVAL_MS) {
            triggerRecentFilesIndexingIfNeeded(forceRebuild = false)
        }

        val sorted = ArrayList<RecentFile>(records.size.coerceAtLeast(256))
        val mutatedOverrides = HashMap<String, RecentFile>()

        val staleMutated = mutableListOf<String>()
        for ((path, recordedTime) in recentlyMutatedFiles) {
            val f = File(path)
            if (f.exists() && f.isFile) {
                val name = f.name
                if (shouldIncludePath(path, name, showHiddenFiles, excludeHiddenPaths, hideTempAndDb, excludedPaths, recycleBinPath)) {
                    val mtime = maxOf(f.lastModified(), recordedTime)
                    val holder = LocalFileHolder(
                        file = f,
                        cachedName = name,
                        cachedIsDir = false,
                        cachedSize = f.length(),
                        cachedLastModified = mtime
                    )
                    mutatedOverrides[path] = RecentFile(
                        name = name,
                        path = path,
                        lastModified = mtime / 1000L,
                        file = holder
                    )
                }
            } else if (!f.exists()) {
                staleMutated.add(path)
                indexedFilesByPath.remove(path)
            }
        }
        staleMutated.forEach { recentlyMutatedFiles.remove(it) }

        if (mutatedOverrides.isNotEmpty()) {
            sorted.addAll(mutatedOverrides.values)
        }

        // `records` is already sorted from newest to oldest
        for (rec in records) {
            if (mutatedOverrides.containsKey(rec.path)) continue
            if (!shouldIncludePath(rec.path, rec.name, showHiddenFiles, excludeHiddenPaths, hideTempAndDb, excludedPaths, recycleBinPath)) {
                continue
            }
            val f = File(rec.path)
            val holder = LocalFileHolder(
                file = f,
                cachedName = rec.name,
                cachedIsDir = false,
                cachedSize = rec.size,
                cachedLastModified = rec.lastModifiedMs
            )
            sorted.add(
                RecentFile(
                    name = rec.name,
                    path = rec.path,
                    lastModified = rec.lastModifiedMs / 1000L,
                    file = holder
                )
            )
        }

        if (mutatedOverrides.isNotEmpty()) {
            sorted.sortWith { a, b -> b.lastModified.compareTo(a.lastModified) }
        }

        cachedAllDeviceFiles = sorted
        cachedAllDeviceKey = cacheKey
        cachedAllDeviceTimestamp = System.currentTimeMillis()
        return sorted
    }

    private var cachedLegacyRecentFiles: ArrayList<RecentFile>? = null
    private var cachedLegacyRecentKey: String = ""
    private var cachedLegacyRecentTimestamp: Long = 0L

    private fun getLegacyMediaStoreRecentFiles(
        recentHours: Int = 24 * 5,
        limit: Int = 100
    ): ArrayList<RecentFile> {
        val effectiveHours = if (recentHours <= 0) 48 else recentHours
        val effectiveLimit = if (limit <= 0) 200 else limit
        val now = System.currentTimeMillis()
        val prefs = globalClass.preferencesManager
        val cacheKey = "$effectiveHours:$effectiveLimit:${prefs.showHiddenFiles}:${prefs.removeHiddenPathsFromRecentFiles}:${prefs.hideTempAndDbFilesFromRecentFiles}:${prefs.excludedPathsFromRecentFiles.joinToString()}:$lastMutationCounter"
        if (cachedLegacyRecentFiles != null && cachedLegacyRecentKey == cacheKey && (now - cachedLegacyRecentTimestamp < RECENT_CACHE_TTL_MS)) {
            return ArrayList(cachedLegacyRecentFiles!!)
        }

        val recentFiles = ArrayList<RecentFile>(effectiveLimit)
        val contentResolver: ContentResolver = globalClass.contentResolver
        val showHiddenFiles = prefs.showHiddenFiles
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )

        val selectionClauses = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        val time = (System.currentTimeMillis() / 1000) - (effectiveHours * 60 * 60)
        selectionClauses.add("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?")
        selectionArgsList.add(time.toString())

        selectionClauses.add("${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL")
        selectionClauses.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}")

        if (!showHiddenFiles) {
            selectionClauses.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%'")
        }

        val excludedPaths = prefs.excludedPathsFromRecentFiles
        excludedPaths.forEach { excludedPath ->
            selectionClauses.add("${MediaStore.Files.FileColumns.DATA} NOT LIKE ?")
            selectionArgsList.add("$excludedPath%")
        }

        if (prefs.removeHiddenPathsFromRecentFiles) {
            selectionClauses.add("${MediaStore.Files.FileColumns.DATA} NOT LIKE '%/.%'")
        }

        if (prefs.hideTempAndDbFilesFromRecentFiles) {
            listOf(
                "%.tmp", "%.temp", "%.bak", "%.log",
                "%.crdownload", "%.part",
                "%-shm", "%-wal", "%-journal",
                "%.db-shm", "%.db-wal", "%.db-journal",
                "%/Android/data/%", "%/Android/obb/%"
            ).forEach { pattern ->
                selectionClauses.add("${MediaStore.Files.FileColumns.DATA} NOT LIKE ?")
                selectionArgsList.add(pattern)
            }
        }

        val selection = selectionClauses.joinToString(" AND ")
        val selectionArgs = selectionArgsList.toTypedArray()
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, effectiveLimit)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, queryArgs, null)
        } catch (_: Exception) {
            val queryUri = uri.buildUpon().apply {
                appendQueryParameter("limit", effectiveLimit.toString())
            }.build()
            try {
                cursor = contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)
            } catch (_: Exception) {}
        }

        cursor?.use {
            val columnIndexPath = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val columnLastModified = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val columnName = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val columnSize = it.getColumnIndex(MediaStore.Files.FileColumns.SIZE)

            while (it.moveToNext() && recentFiles.size < effectiveLimit) {
                val filePath = it.getString(columnIndexPath)
                val name = it.getString(columnName)
                val lastModified = it.getLong(columnLastModified)
                val size = if (columnSize >= 0) it.getLong(columnSize) else 0L

                if (!filePath.isNullOrEmpty() && !name.isNullOrEmpty()) {
                    val mtimeMs = lastModified * 1000L
                    val holder = LocalFileHolder(
                        file = File(filePath),
                        cachedName = name,
                        cachedIsDir = false,
                        cachedSize = size,
                        cachedLastModified = mtimeMs
                    )
                    recentFiles.add(
                        RecentFile(
                            name = name,
                            path = filePath,
                            lastModified = lastModified,
                            file = holder
                        )
                    )
                }
            }
        }

        cachedLegacyRecentFiles = ArrayList(recentFiles)
        cachedLegacyRecentKey = cacheKey
        cachedLegacyRecentTimestamp = now
        return recentFiles
    }

    fun getRawRecentFiles(
        recentHours: Int = -1,
        limit: Int = 100
    ): ArrayList<RecentFile> {
        val configuredMax = globalClass.preferencesManager.recentFilesLimit
        if (configuredMax == 0 || limit == 0) return arrayListOf()
        val isExtensiveScan = !globalClass.preferencesManager.removeHiddenPathsFromRecentFiles
        if (!isExtensiveScan) {
            return getLegacyMediaStoreRecentFiles(
                recentHours = if (recentHours <= 0) 24 else recentHours,
                limit = if (limit < 0) 25 else limit
            )
        }
        if (limit > 0) {
            return collectTopRecentFiles(limit)
        }
        val allSorted = collectAllDeviceFilesSorted()
        return ArrayList(allSorted)
    }

    private fun applySorting(
        holders: List<LocalFileHolder>,
        sortingPrefs: FileSortingPrefs?
    ): List<LocalFileHolder> {
        if (sortingPrefs == null) return holders
        return when (sortingPrefs.sortMethod) {
            SORT_BY_NAME -> if (sortingPrefs.reverseSorting) {
                holders.sortedWith { a, b -> b.displayName.lowercase().compareTo(a.displayName.lowercase()) }
            } else {
                holders.sortedWith { a, b -> a.displayName.lowercase().compareTo(b.displayName.lowercase()) }
            }
            SORT_BY_SIZE -> if (sortingPrefs.reverseSorting) {
                holders.sortedByDescending { it.size }
            } else {
                holders.sortedBy { it.size }
            }
            SORT_BY_DATE -> if (sortingPrefs.reverseSorting) {
                holders.sortedBy { it.lastModified }
            } else {
                // Already sorted descending (newer to older) by default
                holders
            }
            else -> holders
        }
    }

    fun getRecentFiles(
        recentHours: Int = -1,
        limit: Int = 250,
        offset: Int = 0,
        sortingPrefs: FileSortingPrefs? = null
    ): ArrayList<LocalFileHolder> {
        val configuredMax = globalClass.preferencesManager.recentFilesLimit
        if (configuredMax == 0 || limit <= 0) return arrayListOf()
        val isExtensiveScan = !globalClass.preferencesManager.removeHiddenPathsFromRecentFiles
        if (!isExtensiveScan) {
            val effectiveCap = if (configuredMax > 0) minOf(limit + offset, configuredMax) else (limit + offset)
            val legacy = getLegacyMediaStoreRecentFiles(
                recentHours = if (recentHours <= 0) 48 else recentHours,
                limit = effectiveCap
            ).map { it.file }
            val sortedLegacy = applySorting(legacy, sortingPrefs)
            if (offset >= sortedLegacy.size) return arrayListOf()
            val end = minOf(offset + limit, sortedLegacy.size)
            return ArrayList(sortedLegacy.subList(offset, end))
        }
        val isDefaultNewestSort = sortingPrefs == null ||
                (sortingPrefs.sortMethod == SORT_BY_DATE && !sortingPrefs.reverseSorting)
        if (configuredMax > 0) {
            val capped = collectTopRecentFiles(configuredMax)
            val holders = applySorting(capped.map { it.file }, sortingPrefs)
            if (offset >= holders.size) return arrayListOf()
            val end = minOf(offset + limit, holders.size)
            return ArrayList(holders.subList(offset, end))
        }
        if (isDefaultNewestSort && offset + limit <= 1000) {
            val topSlice = collectTopRecentFiles(offset + limit)
            if (offset >= topSlice.size) return arrayListOf()
            val end = minOf(offset + limit, topSlice.size)
            return ArrayList(topSlice.subList(offset, end).map { it.file })
        }
        val allSorted = collectAllDeviceFilesSorted()
        val holders = applySorting(allSorted.map { it.file }, sortingPrefs)
        if (offset >= holders.size) return arrayListOf()
        val end = minOf(offset + limit, holders.size)
        return ArrayList(holders.subList(offset, end))
    }

    fun getAllFiles(
        sortingPrefs: FileSortingPrefs? = null,
        limit: Int = 250,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        val allSorted = collectAllDeviceFilesSorted()
        val holders = applySorting(allSorted.map { it.file }, sortingPrefs)
        if (offset >= holders.size) return arrayListOf()
        val end = minOf(offset + limit, holders.size)
        return ArrayList(holders.subList(offset, end))
    }

    fun getCategoryQuickCheck(type: Int): Pair<Int, Long> {
        if (type == VirtualFileHolder.RECENT || type == VirtualFileHolder.ALL_FILES) {
            return Pair(lastMutationCounter.toInt(), cachedAllDeviceTimestamp)
        }
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val selection = when (type) {
            VirtualFileHolder.IMAGE -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}"
            VirtualFileHolder.VIDEO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO}"
            VirtualFileHolder.AUDIO -> "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}"
            VirtualFileHolder.DOCUMENT -> {
                val documentMimeTypes = arrayOf(
                    "application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
                documentMimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = '$it'" }
            }
            VirtualFileHolder.ARCHIVE -> {
                val archiveMimeTypes = arrayOf(
                    "application/zip", "application/x-rar-compressed",
                    "application/x-tar", "application/gzip", "application/x-7z-compressed"
                )
                archiveMimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = '$it'" }
            }
            VirtualFileHolder.APK -> "${MediaStore.Files.FileColumns.MIME_TYPE} = 'application/vnd.android.package-archive' OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apk'"
            else -> return Pair(0, 0L)
        }

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        var count = 0
        var maxMtime = 0L

        try {
            val queryArgs = Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            }
            globalClass.contentResolver.query(uri, projection, queryArgs, null)?.use { cursor ->
                count = cursor.count
                if (cursor.moveToFirst()) {
                    maxMtime = cursor.getLong(0)
                }
            }
        } catch (_: Exception) {
            try {
                val queryUri = uri.buildUpon().appendQueryParameter("limit", "1").build()
                globalClass.contentResolver.query(queryUri, projection, selection, null, sortOrder)?.use { cursor ->
                    count = cursor.count
                    if (cursor.moveToFirst()) {
                        maxMtime = cursor.getLong(0)
                    }
                }
            } catch (_: Exception) {}
        }
        return Pair(count, maxMtime)
    }

    fun getSearchResult(): ArrayList<ContentHolder> {
        return ArrayList(globalClass.searchManager.searchResults.map { it.file }.distinctBy { it.uniquePath })
    }
}