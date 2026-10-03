package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
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

    private var cachedRecentFiles: ArrayList<RecentFile>? = null
    private var cachedRecentKey: String = ""
    private var cachedRecentTimestamp: Long = 0L
    private const val RECENT_CACHE_TTL_MS = 10_000L

    fun getRawRecentFiles(
        recentHours: Int = 24 * 5,
        limit: Int = 100
    ): ArrayList<RecentFile> {
        val now = System.currentTimeMillis()
        val prefs = globalClass.preferencesManager
        val cacheKey = "$recentHours:$limit:${prefs.showHiddenFiles}:${prefs.removeHiddenPathsFromRecentFiles}:${prefs.hideTempAndDbFilesFromRecentFiles}:${prefs.excludedPathsFromRecentFiles.joinToString()}"
        if (cachedRecentFiles != null && cachedRecentKey == cacheKey && (now - cachedRecentTimestamp < RECENT_CACHE_TTL_MS)) {
            return ArrayList(cachedRecentFiles!!)
        }

        val recentFiles = ArrayList<RecentFile>(limit)
        val contentResolver: ContentResolver = globalClass.contentResolver
        val showHiddenFiles = prefs.showHiddenFiles

        val uri: Uri = MediaStore.Files.getContentUri("external")

        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE
        )

        // Build the selection clause and arguments dynamically.
        val selectionClauses = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        // Filter by modification time.
        val time = (System.currentTimeMillis() / 1000) - (recentHours * 60 * 60)
        selectionClauses.add("${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?")
        selectionArgsList.add(time.toString())

        // Exclude directories directly in the query.
        selectionClauses.add("${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL")
        selectionClauses.add("${MediaStore.Files.FileColumns.MEDIA_TYPE} != ${MediaStore.Files.FileColumns.MEDIA_TYPE_NONE}")

        // Handle hidden files
        if (!showHiddenFiles) {
            selectionClauses.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} NOT LIKE '.%'")
        }

        // Exclude specified paths at the database level.
        val excludedPaths = prefs.excludedPathsFromRecentFiles
        excludedPaths.forEach { excludedPath ->
            selectionClauses.add("${MediaStore.Files.FileColumns.DATA} NOT LIKE ?")
            selectionArgsList.add("$excludedPath%")
        }

        // Exclude files within hidden directories
        val excludeHiddenPaths = prefs.removeHiddenPathsFromRecentFiles
        if (excludeHiddenPaths) {
            selectionClauses.add("${MediaStore.Files.FileColumns.DATA} NOT LIKE '%/.%'")
        }

        // Exclude temporary and database cache/lock files.
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

        // Combine all selection clauses.
        val selection = selectionClauses.joinToString(" AND ")
        val selectionArgs = selectionArgsList.toTypedArray()
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val queryArgs = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
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
                appendQueryParameter("limit", limit.toString())
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

            while (it.moveToNext() && recentFiles.size < limit) {
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

        cachedRecentFiles = ArrayList(recentFiles)
        cachedRecentKey = cacheKey
        cachedRecentTimestamp = now
        return recentFiles
    }

    fun getRecentFiles(
        recentHours: Int = 48,
        limit: Int = 200,
        offset: Int = 0
    ): ArrayList<LocalFileHolder> {
        return arrayListOf<LocalFileHolder>().apply {
            addAll(
                getRawRecentFiles(recentHours, limit + offset).drop(offset).take(limit).map { it.file }
            )
        }
    }

    fun getCategoryQuickCheck(type: Int): Pair<Int, Long> {
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
            VirtualFileHolder.RECENT -> "${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL"
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
        return globalClass.searchManager.searchResults.map { it.file } as ArrayList<ContentHolder>
    }
}