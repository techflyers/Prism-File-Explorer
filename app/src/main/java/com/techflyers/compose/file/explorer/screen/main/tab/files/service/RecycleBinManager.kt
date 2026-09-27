package com.techflyers.compose.file.explorer.screen.main.tab.files.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.techflyers.compose.file.explorer.App
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class RecycleBinCleanupWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val app = (appContext.applicationContext as? App) ?: runCatching { App.globalClass }.getOrNull()
            val prefs = app?.preferencesManager
            if (prefs != null && prefs.autoEmptyRecycleBin) {
                RecycleBinManager.purgeExpiredFiles(prefs.recycleBinRetentionDays)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object RecycleBinManager {
    private const val WORK_NAME = "recycle_bin_periodic_cleanup"

    /**
     * Purges expired items and empty subdirectories from the Recycle Bin.
     *
     * @param retentionDays Files older than this many days will be permanently deleted.
     * @return The number of items/directories purged.
     */
    fun purgeExpiredFiles(retentionDays: Int): Int {
        if (retentionDays <= 0) return 0
        val retentionMs = retentionDays.toLong() * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val binDir = runCatching { globalClass.recycleBinDir.file }.getOrNull() ?: return 0
        if (!binDir.exists() || !binDir.isDirectory) return 0

        var purgedCount = 0
        val subDirs = binDir.listFiles() ?: return 0

        for (subDir in subDirs) {
            if (subDir.isDirectory) {
                // Prune empty subdirectories immediately
                val children = subDir.listFiles() ?: emptyArray()
                val userFiles = children.filter { it.name != "metadata.json" }
                if (userFiles.isEmpty()) {
                    subDir.deleteRecursively()
                    continue
                }

                val metadataFile = File(subDir, "metadata.json")
                if (metadataFile.exists()) {
                    try {
                        val json = JSONObject(metadataFile.readText())
                        val items = json.optJSONArray("items") ?: JSONArray()
                        val folderTimestamp = subDir.name.toLongOrNull() ?: subDir.lastModified()

                        if (items.length() == 0) {
                            if (now - folderTimestamp >= retentionMs) {
                                if (subDir.deleteRecursively()) purgedCount++
                            }
                        } else {
                            // Check expiration per item
                            val remainingItems = JSONArray()
                            var expiredInBatch = 0

                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                val itemTime = item.optLong("deletedAt", folderTimestamp)
                                if (now - itemTime >= retentionMs) {
                                    val name = item.optString("name")
                                    if (name.isNotEmpty()) {
                                        File(subDir, name).deleteRecursively()
                                    }
                                    expiredInBatch++
                                    purgedCount++
                                } else {
                                    remainingItems.put(item)
                                }
                            }

                            if (expiredInBatch == items.length()) {
                                // All items expired — delete the entire batch folder
                                subDir.deleteRecursively()
                            } else if (expiredInBatch > 0) {
                                val remainingUserFiles = subDir.listFiles()?.filter { it.name != "metadata.json" } ?: emptyList()
                                if (remainingUserFiles.isEmpty()) {
                                    subDir.deleteRecursively()
                                } else {
                                    json.put("items", remainingItems)
                                    metadataFile.writeText(json.toString(2))
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Fallback on corrupt metadata: use folder name timestamp or last modified
                        val timestamp = subDir.name.toLongOrNull() ?: subDir.lastModified()
                        if (now - timestamp >= retentionMs) {
                            if (subDir.deleteRecursively()) purgedCount++
                        }
                    }
                } else {
                    val timestamp = subDir.name.toLongOrNull() ?: subDir.lastModified()
                    if (now - timestamp >= retentionMs) {
                        if (subDir.deleteRecursively()) purgedCount++
                    }
                }
            } else if (subDir.isFile && subDir.name != "metadata.json") {
                if (now - subDir.lastModified() >= retentionMs) {
                    if (subDir.delete()) purgedCount++
                }
            }
        }

        if (purgedCount > 0) {
            notifyOpenRecycleBinTabs()
        }

        return purgedCount
    }

    /**
     * Permanently empties all items from the Recycle Bin.
     * @return Total bytes freed.
     */
    fun emptyRecycleBin(): Long {
        val binDir = runCatching { globalClass.recycleBinDir.file }.getOrNull() ?: return 0L
        if (!binDir.exists() || !binDir.isDirectory) return 0L
        var bytesFreed = 0L

        fun calcSize(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { calcSize(it) }
            } else if (file.name != "metadata.json") {
                bytesFreed += file.length()
            }
        }

        binDir.listFiles()?.forEach {
            calcSize(it)
            it.deleteRecursively()
        }

        notifyOpenRecycleBinTabs()
        return bytesFreed
    }

    /**
     * Calculates the total size of all user files stored in the Recycle Bin.
     */
    fun getRecycleBinSize(): Long {
        val binDir = runCatching { globalClass.recycleBinDir.file }.getOrNull() ?: return 0L
        if (!binDir.exists() || !binDir.isDirectory) return 0L
        var total = 0L

        fun calculateSize(file: File) {
            if (file.isDirectory) {
                file.listFiles()?.forEach { calculateSize(it) }
            } else if (file.name != "metadata.json") {
                total += file.length()
            }
        }

        binDir.listFiles()?.forEach { calculateSize(it) }
        return total
    }

    /**
     * Counts the total number of user files/folders in the Recycle Bin.
     */
    fun getRecycleBinItemCount(): Int {
        val binDir = runCatching { globalClass.recycleBinDir.file }.getOrNull() ?: return 0
        if (!binDir.exists() || !binDir.isDirectory) return 0
        var count = 0

        binDir.listFiles()?.forEach { subDir ->
            if (subDir.isDirectory) {
                val files = subDir.listFiles()?.filter { it.name != "metadata.json" } ?: emptyList()
                count += files.size
            } else if (subDir.name != "metadata.json") {
                count++
            }
        }
        return count
    }

    /**
     * Schedules periodic 24-hour cleanup with WorkManager.
     */
    fun schedulePeriodicCleanup(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupRequest = PeriodicWorkRequestBuilder<RecycleBinCleanupWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            cleanupRequest
        )
    }

    /**
     * Cancels periodic cleanup work.
     */
    fun cancelPeriodicCleanup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Refreshes any active or background FilesTabs that are currently displaying the Recycle Bin.
     */
    private fun notifyOpenRecycleBinTabs() {
        runCatching {
            val binPath = globalClass.recycleBinDir.uniquePath
            globalClass.mainActivityManager.state.value.tabs.forEach { tab ->
                if (tab is FilesTab) {
                    val activePath = tab.activeFolder.uniquePath
                    if (activePath.startsWith(binPath)) {
                        tab.unselectAllFiles(false)
                        tab.quickReloadFiles()
                    }
                }
            }
        }
    }
}
