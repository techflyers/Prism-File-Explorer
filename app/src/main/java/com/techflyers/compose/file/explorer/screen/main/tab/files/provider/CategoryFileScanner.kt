package com.techflyers.compose.file.explorer.screen.main.tab.files.provider

import android.provider.MediaStore
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuManager
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object CategoryFileScanner {
    private const val CACHE_TTL_MS = 3 * 60 * 1000L
    private const val MAX_DEPTH = 8
    private const val MAX_FILES = 40_000
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
            val key = canonicalKey(holder.file)
            if (key.isNotEmpty()) byPath[key] = holder
        }

        walkStorageRoots(extensions).forEach { holder ->
            val key = canonicalKey(holder.file)
            if (key.isNotEmpty()) byPath.putIfAbsent(key, holder)
        }

        val result = ArrayList(byPath.values)
        cache[cacheKey] = CachedScan(now, result)
        return ArrayList(result)
    }

    fun queryMediaByNameSuffixes(suffixes: List<String>): ArrayList<LocalFileHolder> {
        val files = ArrayList<LocalFileHolder>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = suffixes.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        }
        val args = suffixes.map { "%$it" }.toTypedArray()
        try {
            globalClass.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathColumn)
                    if (!path.isNullOrEmpty()) {
                        files.add(LocalFileHolder(File(path)))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return files
    }

    private fun walkStorageRoots(extensions: Set<String>): List<LocalFileHolder> {
        val found = ArrayList<LocalFileHolder>()
        val devices = runBlocking { StorageProvider.getStorageDevices(globalClass) }
        for (device in devices) {
            if (device.type != com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.INTERNAL_STORAGE &&
                device.type != com.techflyers.compose.file.explorer.screen.main.tab.files.misc.StorageDeviceType.EXTERNAL_STORAGE) continue
            val root = (device.contentHolder as? LocalFileHolder)?.file ?: continue
            walkDirectory(root, extensions, 0, found)
            if (found.size >= MAX_FILES) break
        }
        return found
    }

    private fun walkDirectory(
        dir: File,
        extensions: Set<String>,
        depth: Int,
        out: ArrayList<LocalFileHolder>
    ) {
        if (depth > MAX_DEPTH || out.size >= MAX_FILES) return
        if (!dir.exists() || dir.name in skipDirNames) return

        val children: Array<File>? = dir.listFiles()
        if (children != null) {
            for (child in children) {
                if (out.size >= MAX_FILES) return
                if (child.isDirectory) {
                    if (!child.name.startsWith(".") && child.name !in skipDirNames) {
                        walkDirectory(child, extensions, depth + 1, out)
                    }
                } else if (child.extension.lowercase() in extensions) {
                    out.add(LocalFileHolder(child))
                }
            }
            return
        }

        if (ShizukuManager.isPrivileged) {
            try {
                val entries = ShizukuManager.listFiles(dir.absolutePath)
                for (entry in entries) {
                    if (out.size >= MAX_FILES) return
                    if (entry.isDirectory) {
                        if (!entry.name.startsWith(".") && entry.name !in skipDirNames) {
                            walkDirectory(File(entry.path), extensions, depth + 1, out)
                        }
                    } else if (entry.name.substringAfterLast('.', "").lowercase() in extensions) {
                        out.add(LocalFileHolder(File(entry.path)))
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun canonicalKey(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
    }
}
