package com.techflyers.compose.file.explorer.screen.main.tab.files.task

import java.util.concurrent.ConcurrentHashMap

object DeleteTimestampJournal {
    private const val WINDOW_MS = 10 * 60 * 1000L
    private val parentEvents = ConcurrentHashMap<String, Long>()

    fun record(parentPath: String, timestamp: Long = System.currentTimeMillis()) {
        parentEvents[parentPath] = timestamp
    }

    fun shouldItalicize(itemPath: String, lastModified: Long): Boolean {
        val now = System.currentTimeMillis()
        val parent = itemPath.substringBeforeLast('/', itemPath)
        val event = parentEvents[parent] ?: parentEvents[itemPath]
        if (event != null && now - event <= WINDOW_MS && kotlin.math.abs(lastModified - event) <= WINDOW_MS) {
            return true
        }
        val recycle = runCatching { com.techflyers.compose.file.explorer.App.globalClass.recycleBinDir.uniquePath }.getOrNull()
        return recycle != null && itemPath.startsWith(recycle)
    }
}
