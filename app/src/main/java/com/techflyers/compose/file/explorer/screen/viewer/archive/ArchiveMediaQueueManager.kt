package com.techflyers.compose.file.explorer.screen.viewer.archive

import android.content.Intent
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ArchiveMediaItem(
    val internalPath: String,
    val destinationPath: String,
    val name: String
)

data class ArchiveMediaSession(
    val sessionId: String,
    val archivePath: String,
    val password: String?,
    val destinationDir: String,
    val items: List<ArchiveMediaItem>
)

object ArchiveMediaQueueManager {
    const val EXTRA_ARCHIVE_SESSION_ID = "extra_archive_session_id"
    const val EXTRA_ARCHIVE_PATH = "extra_archive_path"
    const val EXTRA_ARCHIVE_PASSWORD = "extra_archive_password"
    const val EXTRA_ARCHIVE_INTERNAL_PATHS = "extra_archive_internal_paths"
    const val EXTRA_ARCHIVE_DEST_PATHS = "extra_archive_dest_paths"
    const val EXTRA_ARCHIVE_DEST_DIR = "extra_archive_dest_dir"
    const val EXTRA_ARCHIVE_INITIAL_INDEX = "extra_archive_initial_index"

    private const val TAG = "ArchiveMediaQueue"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessions = ConcurrentHashMap<String, ArchiveMediaSession>()
    private val extractedPaths = ConcurrentHashMap.newKeySet<String>()
    private val pathFlows = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val extractionMutex = Mutex()

    fun createSession(
        archivePath: String,
        password: String?,
        destinationDir: String,
        items: List<ArchiveMediaItem>
    ): ArchiveMediaSession {
        val sessionId = UUID.randomUUID().toString()
        val session = ArchiveMediaSession(
            sessionId = sessionId,
            archivePath = archivePath,
            password = password,
            destinationDir = destinationDir,
            items = items
        )
        sessions[sessionId] = session
        return session
    }

    fun getSession(sessionId: String): ArchiveMediaSession? = sessions[sessionId]

    fun getOrCreateSession(intent: Intent?): ArchiveMediaSession? {
        if (intent == null) return null
        val sessionId = intent.getStringExtra(EXTRA_ARCHIVE_SESSION_ID)
        if (!sessionId.isNullOrEmpty() && sessions.containsKey(sessionId)) {
            return sessions[sessionId]
        }

        val archivePath = intent.getStringExtra(EXTRA_ARCHIVE_PATH) ?: return null
        val password = intent.getStringExtra(EXTRA_ARCHIVE_PASSWORD)
        val destinationDir = intent.getStringExtra(EXTRA_ARCHIVE_DEST_DIR) ?: return null
        val internalPaths = intent.getStringArrayListExtra(EXTRA_ARCHIVE_INTERNAL_PATHS) ?: return null
        val destPaths = intent.getStringArrayListExtra(EXTRA_ARCHIVE_DEST_PATHS) ?: return null

        if (internalPaths.size != destPaths.size) return null

        val items = internalPaths.indices.map { i ->
            val internal = internalPaths[i]
            val dest = destPaths[i]
            ArchiveMediaItem(
                internalPath = internal,
                destinationPath = dest,
                name = File(dest).name
            )
        }

        val newSessionId = sessionId ?: UUID.randomUUID().toString()
        val session = ArchiveMediaSession(
            sessionId = newSessionId,
            archivePath = archivePath,
            password = password,
            destinationDir = destinationDir,
            items = items
        )
        sessions[newSessionId] = session
        return session
    }

    fun isExtracted(destinationPath: String): Boolean {
        if (extractedPaths.contains(destinationPath)) return true
        val file = File(destinationPath)
        val exists = file.exists() && file.length() > 0
        if (exists) {
            extractedPaths.add(destinationPath)
            getFlowForPath(destinationPath).value = true
        }
        return exists
    }

    fun getFlowForPath(destinationPath: String): MutableStateFlow<Boolean> {
        return pathFlows.computeIfAbsent(destinationPath) {
            MutableStateFlow(isExtracted(destinationPath))
        }
    }

    suspend fun ensureExtracted(session: ArchiveMediaSession, index: Int): Boolean {
        val item = session.items.getOrNull(index) ?: return false
        if (isExtracted(item.destinationPath)) return true

        return withContext(Dispatchers.IO) {
            extractionMutex.withLock {
                if (isExtracted(item.destinationPath)) return@withLock true

                try {
                    android.util.Log.d(TAG, "Extracting single item index $index: ${item.name}")
                    extractItemsInternal(session, listOf(item))
                    val success = isExtracted(item.destinationPath)
                    if (success) {
                        getFlowForPath(item.destinationPath).value = true
                    }
                    success
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Failed extracting index $index: ${e.message}", e)
                    logger.logError(e)
                    false
                }
            }
        }
    }

    fun prefetchWindow(session: ArchiveMediaSession, centerIndex: Int, windowSize: Int = 3) {
        val start = (centerIndex - windowSize).coerceAtLeast(0)
        val end = (centerIndex + windowSize).coerceAtMost(session.items.lastIndex)

        val unextracted = (start..end).mapNotNull { idx ->
            val item = session.items.getOrNull(idx)
            if (item != null && !isExtracted(item.destinationPath)) item else null
        }

        if (unextracted.isEmpty()) return

        val jobKey = "${session.sessionId}_window_${start}_$end"
        if (activeJobs[jobKey]?.isActive == true) return

        val job = scope.launch {
            extractionMutex.withLock {
                val stillNeeded = unextracted.filter { !isExtracted(it.destinationPath) }
                if (stillNeeded.isNotEmpty()) {
                    try {
                        android.util.Log.d(TAG, "Prefetching ${stillNeeded.size} items around index $centerIndex")
                        extractItemsInternal(session, stillNeeded)
                        for (item in stillNeeded) {
                            if (isExtracted(item.destinationPath)) {
                                getFlowForPath(item.destinationPath).value = true
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Prefetch error: ${e.message}", e)
                    }
                }
            }
        }
        activeJobs[jobKey] = job
    }

    private suspend fun extractItemsInternal(session: ArchiveMediaSession, items: List<ArchiveMediaItem>) {
        if (items.isEmpty()) return
        val sourceFile = File(session.archivePath)
        val sourceName = sourceFile.name
        val ext = sourceFile.extension.lowercase()

        val internalPaths = items.map { it.internalPath }

        if (ArchiveManager.isNativeArchivePath(sourceName) || ArchiveManager.isNativeArchive(ext)) {
            android.util.Log.d(TAG, "Extracting ${items.size} items via 7za...")
            ArchiveManager.extractMembers(
                archivePath = session.archivePath,
                internalPaths = internalPaths,
                destinationDir = session.destinationDir,
                password = session.password
            )
        } else {
            android.util.Log.d(TAG, "Extracting ${items.size} items via zip4j...")
            val zipFile = if (!session.password.isNullOrEmpty()) {
                net.lingala.zip4j.ZipFile(File(session.archivePath), session.password.toCharArray())
            } else {
                net.lingala.zip4j.ZipFile(File(session.archivePath))
            }
            zipFile.use { zf ->
                for (item in items) {
                    try {
                        zf.extractFile(item.internalPath, session.destinationDir)
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "zip4j error for ${item.internalPath}: ${e.message}")
                    }
                }
            }
        }

        // Mark all successfully extracted items
        for (item in items) {
            val file = File(item.destinationPath)
            if (file.exists() && file.length() > 0) {
                extractedPaths.add(item.destinationPath)
            }
        }
    }
}
