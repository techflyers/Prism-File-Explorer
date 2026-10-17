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

object StorageProvider {
    suspend fun getStorageDevices(context: Context): List<StorageDevice> {
        val storageDevices = mutableListOf<StorageDevice>()

        storageDevices.add(getPrimaryInternalStorage(context))

        val externalStoragePairs = getExternalStorageDirectories(context)

        for ((label, externalDir) in externalStoragePairs) {
            if (externalDir.absolutePath == Environment.getExternalStorageDirectory().absolutePath) {
                continue
            }

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

        return storageDevices
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

    private fun getExternalStorageDirectories(context: Context): List<Pair<String, File>> {
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

        val files = ArrayList<LocalFileHolder>()
        val contentResolver: ContentResolver = globalClass.contentResolver

        val uri: Uri = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME, // Required for SORT_BY_NAME
            MediaStore.Files.FileColumns.DATE_MODIFIED, // Required for SORT_BY_DATE
            MediaStore.Files.FileColumns.SIZE         // Required for SORT_BY_SIZE
        )

        // Build the selection clause to match any of the provided MIME types.
        val selection =
            mimeTypes.joinToString(" OR ") { "${MediaStore.Files.FileColumns.MIME_TYPE} = ?" }
        val selectionArgs = mimeTypes

        // EFFICIENT SORTING: Build the SQL ORDER BY clause to handle sorting and reversal.
        val sortOrder = when (sortingPrefs?.sortMethod) {
            SORT_BY_NAME -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ${if (sortingPrefs.reverseSorting) "DESC" else "ASC"}"
            SORT_BY_DATE -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ${if (sortingPrefs.reverseSorting) "ASC" else "DESC"}"
            SORT_BY_SIZE -> "${MediaStore.Files.FileColumns.SIZE} ${if (sortingPrefs.reverseSorting) "ASC" else "DESC"}"
            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }

        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            val sortCol = when (sortingPrefs?.sortMethod) {
                SORT_BY_NAME -> MediaStore.Files.FileColumns.DISPLAY_NAME
                SORT_BY_SIZE -> MediaStore.Files.FileColumns.SIZE
                else -> MediaStore.Files.FileColumns.DATE_MODIFIED
            }
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortCol))
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                if (sortingPrefs?.reverseSorting == true) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, queryArgs, null)
        } catch (_: Exception) {
            val fallbackUri = uri.buildUpon().appendQueryParameter("limit", "$offset,$limit").build()
            try {
                cursor = contentResolver.query(fallbackUri, projection, selection, selectionArgs, sortOrder)
            } catch (_: Exception) {}
        }

        cursor?.use {
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (it.moveToNext()) {
                val path = it.getString(pathColumn)
                if (!path.isNullOrEmpty()) {
                    val name = it.getString(nameColumn) ?: File(path).name
                    val dateModified = it.getLong(dateColumn) * 1000L
                    val size = it.getLong(sizeColumn)
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
        val files = ArrayList<LocalFileHolder>()
        val contentResolver: ContentResolver = globalClass.contentResolver
        val uri: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apk' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apks' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xapk' OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apkm'"
        val selectionArgs = arrayOf("application/vnd.android.package-archive")

        val sortOrder = when (sortingPrefs?.sortMethod) {
            SORT_BY_NAME -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ${if (sortingPrefs.reverseSorting) "DESC" else "ASC"}"
            SORT_BY_DATE -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ${if (sortingPrefs.reverseSorting) "ASC" else "DESC"}"
            SORT_BY_SIZE -> "${MediaStore.Files.FileColumns.SIZE} ${if (sortingPrefs.reverseSorting) "ASC" else "DESC"}"
            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }

        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            val sortCol = when (sortingPrefs?.sortMethod) {
                SORT_BY_NAME -> MediaStore.Files.FileColumns.DISPLAY_NAME
                SORT_BY_SIZE -> MediaStore.Files.FileColumns.SIZE
                else -> MediaStore.Files.FileColumns.DATE_MODIFIED
            }
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortCol))
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                if (sortingPrefs?.reverseSorting == true) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, queryArgs, null)
        } catch (_: Exception) {
            val fallbackUri = uri.buildUpon().appendQueryParameter("limit", "$offset,$limit").build()
            try {
                cursor = contentResolver.query(fallbackUri, projection, selection, selectionArgs, sortOrder)
            } catch (_: Exception) {}
        }

        cursor?.use {
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (it.moveToNext()) {
                val path = it.getString(pathCol)
                if (!path.isNullOrEmpty()) {
                    val name = it.getString(nameCol) ?: File(path).name
                    val dateModified = it.getLong(dateCol) * 1000L
                    val size = it.getLong(sizeCol)
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
     * Generic helper function to fetch media files of a specific type.
     */
    private fun getMediaFiles(
        mediaType: Int,
        sortingPrefs: FileSortingPrefs?,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        val files = ArrayList<LocalFileHolder>()
        val contentResolver: ContentResolver = globalClass.contentResolver

        val uri: Uri = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME, // Required for SORT_BY_NAME
            MediaStore.Files.FileColumns.DATE_MODIFIED, // Required for SORT_BY_DATE
            MediaStore.Files.FileColumns.SIZE         // Required for SORT_BY_SIZE
        )

        // Filter the results to only the media type we want (images, video, etc.).
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val selectionArgs = arrayOf(mediaType.toString())

        val sortOrder = when (sortingPrefs?.sortMethod) {
            SORT_BY_NAME -> "${MediaStore.Files.FileColumns.DISPLAY_NAME} ${if (sortingPrefs.reverseSorting) "DESC" else "ASC"}"
            SORT_BY_DATE -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} ${if (sortingPrefs.reverseSorting) "ASC" else "DESC"}"
            SORT_BY_SIZE -> "${MediaStore.Files.FileColumns.SIZE} ${if (sortingPrefs.reverseSorting) "ASC" else "DESC"}"
            SORT_BY_TYPE -> {
                val direction = if (sortingPrefs.reverseSorting) "DESC" else "ASC"
                """
            CASE
                WHEN INSTR(${MediaStore.Files.FileColumns.DISPLAY_NAME}, '.') = 0 THEN 1
                ELSE 0
            END,
            SUBSTR(${MediaStore.Files.FileColumns.DISPLAY_NAME}, INSTR(${MediaStore.Files.FileColumns.DISPLAY_NAME}, '.') + 1) $direction
            """.trimIndent()
            }

            else -> "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        }

        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
            val sortCol = when (sortingPrefs?.sortMethod) {
                SORT_BY_NAME -> MediaStore.Files.FileColumns.DISPLAY_NAME
                SORT_BY_SIZE -> MediaStore.Files.FileColumns.SIZE
                else -> MediaStore.Files.FileColumns.DATE_MODIFIED
            }
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortCol))
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                if (sortingPrefs?.reverseSorting == true) ContentResolver.QUERY_SORT_DIRECTION_ASCENDING
                else ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, queryArgs, null)
        } catch (_: Exception) {
            val fallbackUri = uri.buildUpon().appendQueryParameter("limit", "$offset,$limit").build()
            try {
                cursor = contentResolver.query(fallbackUri, projection, selection, selectionArgs, sortOrder)
            } catch (_: Exception) {}
        }

        cursor?.use {
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)

            while (it.moveToNext()) {
                val path = it.getString(pathColumn)
                if (!path.isNullOrEmpty()) {
                    val name = it.getString(nameColumn) ?: File(path).name
                    val dateModified = it.getLong(dateColumn) * 1000L
                    val size = it.getLong(sizeColumn)
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

    fun getBookmarks() = globalClass.preferencesManager.bookmarks
        .map { LocalFileHolder(File(it)) } as ArrayList<LocalFileHolder>

    @Volatile
    private var cachedAllDeviceFiles: ArrayList<RecentFile>? = null
    @Volatile
    private var cachedAllDeviceKey: String = ""
    @Volatile
    private var cachedAllDeviceTimestamp: Long = 0L
    @Volatile
    private var lastMutationCounter: Long = 0L
    private const val RECENT_CACHE_TTL_MS = 20_000L

    private val recentlyMutatedFiles = ConcurrentHashMap<String, Long>()

    private val tempDbSuffixes = listOf(
        ".tmp", ".temp", ".bak", ".log",
        ".crdownload", ".part",
        "-shm", "-wal", "-journal",
        ".db-shm", ".db-wal", ".db-journal"
    )

    private val skippedSystemDirNames = setOf(
        "lost+found", ".thumbnails", ".Trash", ".trashed", ".recycle", "recyclebin"
    )

    fun invalidateRecentFilesCache() {
        cachedAllDeviceTimestamp = 0L
        lastMutationCounter++
    }

    fun notifyFileMutated(file: File) {
        try {
            if (file.exists() && file.isFile) {
                recentlyMutatedFiles[file.absolutePath] = System.currentTimeMillis()
            } else if (file.exists() && file.isDirectory) {
                file.walkTopDown().maxDepth(4).forEach { child ->
                    if (child.isFile) {
                        recentlyMutatedFiles[child.absolutePath] = System.currentTimeMillis()
                    }
                }
            }
            invalidateRecentFilesCache()
            MediaScannerConnection.scanFile(
                globalClass,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (_: Exception) {}
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

    @Synchronized
    private fun collectAllDeviceFilesSorted(): ArrayList<RecentFile> {
        val now = System.currentTimeMillis()
        val prefs = globalClass.preferencesManager
        val showHiddenFiles = prefs.showHiddenFiles
        val excludeHiddenPaths = prefs.removeHiddenPathsFromRecentFiles
        val hideTempAndDb = prefs.hideTempAndDbFilesFromRecentFiles
        val excludedPaths = prefs.excludedPathsFromRecentFiles
        val recycleBinPath = runCatching { globalClass.recycleBinDir.file.absolutePath }.getOrDefault("")

        val cacheKey = "$showHiddenFiles:$excludeHiddenPaths:$hideTempAndDb:${excludedPaths.joinToString()}:$lastMutationCounter"
        val existingCache = cachedAllDeviceFiles
        if (existingCache != null && cachedAllDeviceKey == cacheKey && (now - cachedAllDeviceTimestamp < RECENT_CACHE_TTL_MS)) {
            return existingCache
        }

        val fileMap = HashMap<String, RecentFile>(4096)

        fun addOrUpdateFile(file: File, name: String, size: Long, timestampMs: Long) {
            val path = file.absolutePath
            if (!shouldIncludePath(path, name, showHiddenFiles, excludeHiddenPaths, hideTempAndDb, excludedPaths, recycleBinPath)) {
                return
            }
            val existing = fileMap[path]
            if (existing == null || timestampMs > existing.lastModified * 1000L) {
                val holder = LocalFileHolder(
                    file = file,
                    cachedName = name,
                    cachedIsDir = false,
                    cachedSize = size,
                    cachedLastModified = timestampMs
                )
                fileMap[path] = RecentFile(
                    name = name,
                    path = path,
                    lastModified = timestampMs / 1000L,
                    file = holder
                )
            }
        }

        // 1. Always include recently created/modified files tracked in-app
        val staleMutated = mutableListOf<String>()
        for ((path, recordedTime) in recentlyMutatedFiles) {
            val f = File(path)
            if (f.exists() && f.isFile) {
                val mtime = maxOf(f.lastModified(), recordedTime)
                addOrUpdateFile(f, f.name, f.length(), mtime)
            } else if (!f.exists()) {
                staleMutated.add(path)
            }
        }
        staleMutated.forEach { recentlyMutatedFiles.remove(it) }

        // 2. Walk storage roots directly on the filesystem so newly created files in Download
        // or any internal/external directory are always found regardless of MediaStore lag.
        val roots = mutableListOf<File>()
        val primaryRoot = Environment.getExternalStorageDirectory()
        if (primaryRoot.exists()) roots.add(primaryRoot)
        for ((_, extDir) in getExternalStorageDirectories(globalClass)) {
            if (extDir.exists() && roots.none { it.absolutePath == extDir.absolutePath }) {
                roots.add(extDir)
            }
        }

        fun walkDir(dir: File, depth: Int) {
            if (depth > 14) return
            val dirPath = dir.absolutePath
            val dirName = dir.name
            if (dirName in skippedSystemDirNames) return
            if (recycleBinPath.isNotEmpty() && dirPath.startsWith(recycleBinPath)) return
            if ((excludeHiddenPaths || !showHiddenFiles) && dirName.startsWith(".")) return
            if (hideTempAndDb && (dirPath.endsWith("/Android/data") || dirPath.endsWith("/Android/obb"))) return
            for (excluded in excludedPaths) {
                if (excluded.isNotEmpty() && dirPath.startsWith(excluded)) return
            }

            val children = dir.listFiles() ?: return
            for (child in children) {
                val childName = child.name
                if ((excludeHiddenPaths || !showHiddenFiles) && childName.startsWith(".")) continue
                if (child.isDirectory) {
                    walkDir(child, depth + 1)
                } else if (child.isFile) {
                    val mtime = child.lastModified()
                    addOrUpdateFile(child, childName, child.length(), mtime)
                }
            }
        }

        for (root in roots) {
            try {
                walkDir(root, 0)
            } catch (_: Exception) {}
        }

        // 3. Merge MediaStore.Files (using max of DATE_ADDED and DATE_MODIFIED for creation date accuracy)
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

                while (cursor.moveToNext()) {
                    val filePath = cursor.getString(colPath) ?: continue
                    val f = File(filePath)
                    if (!f.exists() || f.isDirectory) continue
                    val name = cursor.getString(colName) ?: f.name
                    val modSec = cursor.getLong(colMod)
                    val addSec = if (colAdd >= 0) cursor.getLong(colAdd) else 0L
                    val bestSec = maxOf(modSec, addSec)
                    val timestampMs = maxOf(bestSec * 1000L, f.lastModified())
                    val size = if (colSize >= 0) cursor.getLong(colSize).takeIf { it > 0 } ?: f.length() else f.length()
                    addOrUpdateFile(f, name, size, timestampMs)
                }
            }
        } catch (_: Exception) {}

        // Sort from newer to older
        val sorted = ArrayList(fileMap.values)
        sorted.sortWith { a, b ->
            b.file.lastModified.compareTo(a.file.lastModified)
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
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
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
        val isExtensiveScan = !globalClass.preferencesManager.removeHiddenPathsFromRecentFiles
        if (!isExtensiveScan) {
            return getLegacyMediaStoreRecentFiles(
                recentHours = if (recentHours <= 0) 24 else recentHours,
                limit = if (limit <= 0) 25 else limit
            )
        }
        val allSorted = collectAllDeviceFilesSorted()
        val effectiveLimit = if (limit <= 0) allSorted.size else minOf(limit, allSorted.size)
        return ArrayList(allSorted.subList(0, effectiveLimit))
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
        val isExtensiveScan = !globalClass.preferencesManager.removeHiddenPathsFromRecentFiles
        if (!isExtensiveScan) {
            val legacy = getLegacyMediaStoreRecentFiles(
                recentHours = if (recentHours <= 0) 48 else recentHours,
                limit = limit + offset
            ).map { it.file }
            val sortedLegacy = applySorting(legacy, sortingPrefs)
            if (offset >= sortedLegacy.size) return arrayListOf()
            val end = minOf(offset + limit, sortedLegacy.size)
            return ArrayList(sortedLegacy.subList(offset, end))
        }
        val allSorted = collectAllDeviceFilesSorted()
        val configuredMax = globalClass.preferencesManager.recentFilesLimit
        val capped = if (configuredMax > 0) {
            allSorted.take(configuredMax)
        } else {
            allSorted
        }
        val holders = applySorting(capped.map { it.file }, sortingPrefs)
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
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
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