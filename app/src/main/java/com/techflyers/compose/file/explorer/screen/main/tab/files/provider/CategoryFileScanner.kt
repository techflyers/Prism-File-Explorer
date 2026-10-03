package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import android.provider.MediaStore
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CategoryFileScanner {
    private const val CACHE_TTL_MS = 3 * 60 * 1000L
    private const val MAX_DEPTH = 6
    private const val MAX_FILES = 10_000
    private val skipDirNames = setOf(
        "Android", "lost+found", ".thumbnails", ".Trash", ".trashed",
        ".recycle", "recyclebin", "cache", ".cache"
    )
    private val cache = ConcurrentHashMap<String, CachedScan>()

    private data class CachedScan(
        val timestamp: Long,
        val files: ArrayList<LocalFileHolder>
    )

    fun mergeMediaAndWalk(
        cacheKey: String,
        mediaFiles: List<LocalFileHolder>,
        extensions: Set<String>
    ): ArrayList<LocalFileHolder> {
        val now = System.currentTimeMillis()
        cache[cacheKey]?.let { cached ->
            if (now - cached.timestamp < CACHE_TTL_MS) {
                return ArrayList(cached.files)
            }
        }

        val byPath = LinkedHashMap<String, LocalFileHolder>()
        mediaFiles.forEach { holder ->
            val key = holder.file.absolutePath
            if (key.isNotEmpty()) byPath[key] = holder
        }

        val result = ArrayList(byPath.values)
        cache[cacheKey] = CachedScan(now, result)
        return ArrayList(result)
    }

    suspend fun walkStorageRootsBackground(
        extensions: Set<String>,
        existingPaths: Set<String>,
        onBatchFound: suspend (List<LocalFileHolder>) -> Unit
    ) {
        val seenPaths = HashSet<String>(existingPaths)
        val batch = ArrayList<LocalFileHolder>()
        val devices = StorageProvider.getStorageDevices(globalClass)
        for (device in devices) {
            if (!currentCoroutineContext().isActive) break
            if (device.type != com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.INTERNAL_STORAGE &&
                device.type != com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.EXTERNAL_STORAGE) continue
            val root = (device.contentHolder as? LocalFileHolder)?.file ?: continue
            walkDirectoryBackground(root, extensions, 0, seenPaths, batch, onBatchFound)
            if (seenPaths.size >= MAX_FILES) break
        }
        if (currentCoroutineContext().isActive && batch.isNotEmpty()) {
            onBatchFound(ArrayList(batch))
            batch.clear()
        }
    }

    private suspend fun walkDirectoryBackground(
        dir: File,
        extensions: Set<String>,
        depth: Int,
        seenPaths: HashSet<String>,
        batch: ArrayList<LocalFileHolder>,
        onBatchFound: suspend (List<LocalFileHolder>) -> Unit
    ) {
        if (!currentCoroutineContext().isActive) return
        if (depth > MAX_DEPTH || seenPaths.size >= MAX_FILES) return
        if (!dir.exists() || dir.name in skipDirNames) return

        val children: Array<File>? = dir.listFiles()
        if (children != null) {
            for (child in children) {
                if (!currentCoroutineContext().isActive || seenPaths.size >= MAX_FILES) return
                if (child.isDirectory) {
                    if (!child.name.startsWith(".") && child.name !in skipDirNames) {
                        walkDirectoryBackground(child, extensions, depth + 1, seenPaths, batch, onBatchFound)
                    }
                } else if (child.extension.lowercase() in extensions) {
                    val path = child.absolutePath
                    if (seenPaths.add(path)) {
                        batch.add(
                            LocalFileHolder(
                                file = child,
                                cachedName = child.name,
                                cachedIsDir = false,
                                cachedSize = child.length(),
                                cachedLastModified = child.lastModified()
                            )
                        )
                        if (batch.size >= 50) {
                            onBatchFound(ArrayList(batch))
                            batch.clear()
                        }
                    }
                }
            }
        }
    }

    fun queryMediaByNameSuffixes(suffixes: List<String>): ArrayList<LocalFileHolder> {
        val files = ArrayList<LocalFileHolder>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.SIZE
        )
        val selection = suffixes.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        }
        val args = suffixes.map { "%$it" }.toTypedArray()
        try {
            globalClass.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathColumn)
                    if (!path.isNullOrEmpty()) {
                        val name = cursor.getString(nameColumn) ?: File(path).name
                        val mtime = cursor.getLong(dateColumn) * 1000L
                        val size = cursor.getLong(sizeColumn)
                        files.add(
                            LocalFileHolder(
                                file = File(path),
                                cachedName = name,
                                cachedIsDir = false,
                                cachedSize = size,
                                cachedLastModified = mtime
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return files
    }
}
