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
        maxDepth: Int = 15,
        visited: MutableSet<String> = mutableSetOf()
    ): Boolean {
        if (maxDepth <= 0) return false
        val canonical = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }
        if (!visited.add(canonical)) return false

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
        if (subDirs.isEmpty()) {
            emptyWithinCache.put(cacheKey, false)
            return false
        }

        // Check if any files exist recursively
        val hasFiles = hasAnyFilesRecursively(dir)
        val isEmptyWithin = !hasFiles
        emptyWithinCache.put(cacheKey, isEmptyWithin)
        return isEmptyWithin
    }

    fun clearCache() {
        emptyWithinCache.evictAll()
    }
}
