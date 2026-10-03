package com.techflyers.compose.file.explorer.screen.main.tab.files.misc

import android.util.LruCache
import java.io.File

object FolderHierarchyChecker {
    private val emptyWithinCache = LruCache<String, Boolean>(1000)

    /**
     * Recursively checks whether the given directory contains any files (depth-first search).
     * Returns true as soon as any file (excluding metadata.json) is encountered.
     */
    fun hasAnyFilesRecursively(
        dir: File,
        maxDepth: Int = 5,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (maxDepth <= 0 || visited.size > 50) return false
        val path = dir.absolutePath
        if (!visited.add(path)) return false

        val children = dir.listFiles() ?: return false
        for (child in children) {
            if (child.name == "metadata.json") continue
            if (child.isFile) {
                return true
            } else if (child.isDirectory) {
                if (hasAnyFilesRecursively(child, maxDepth - 1, visited)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if a folder contains one or more subdirectories and is completely empty of files
     * throughout its entire directory tree (no files at any level).
     */
    fun isFolderEmptyWithin(dir: File): Boolean {
        val cacheKey = "${dir.absolutePath}:${dir.lastModified()}"
        emptyWithinCache.get(cacheKey)?.let { return it }

        // Must have at least one subdirectory directly or nested
        val children = dir.listFiles() ?: return false
        val subDirs = children.filter { it.isDirectory && it.name != "metadata.json" }
        if (subDirs.isEmpty() || subDirs.size > 20) {
            emptyWithinCache.put(cacheKey, false)
            return false
        }

        // Check if any files exist recursively
        val hasFiles = hasAnyFilesRecursively(dir)
        val isEmptyWithin = !hasFiles
        emptyWithinCache.put(cacheKey, isEmptyWithin)
        return isEmptyWithin
    }

    /**
     * Checks if targetPath is the same as sourcePath or is a descendant subdirectory of sourcePath.
     * Prevents moving/copying a directory into itself or into one of its subdirectories.
     */
    fun isChildOrSame(targetPath: String, sourcePath: String): Boolean {
        if (targetPath == sourcePath) return true
        return try {
            val normTarget = File(targetPath).canonicalPath
            val normSource = File(sourcePath).canonicalPath
            normTarget == normSource || normTarget.startsWith(normSource + File.separator)
        } catch (_: Exception) {
            targetPath == sourcePath || targetPath.startsWith(sourcePath + File.separator)
        }
    }

    fun clearCache() {
        emptyWithinCache.evictAll()
    }
}
