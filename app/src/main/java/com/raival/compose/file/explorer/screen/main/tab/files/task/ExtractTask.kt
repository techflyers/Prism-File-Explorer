package com.raival.compose.file.explorer.screen.main.tab.files.task

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import java.io.File

class ExtractTask(
    val archives: List<ContentHolder>
) : Task() {
    private var parameters: ExtractTaskParameters? = null

    override val metadata = System.currentTimeMillis().toFormattedDate().let { time ->
        TaskMetadata(
            id = id,
            creationTime = time,
            title = "Extract Archive",
            subtitle = globalClass.resources.getString(R.string.task_subtitle, archives.size),
            displayDetails = archives.joinToString(", ") { it.displayName },
            fullDetails = buildString {
                archives.forEach { append(it.displayName).append("\n") }
                append("\n").append(time)
            },
            isCancellable = true,
            canMoveToBackground = true
        )
    }

    override val progressMonitor = TaskProgressMonitor(
        status = TaskStatus.PENDING,
        taskTitle = metadata.title,
    )

    override fun getCurrentStatus() = progressMonitor.status

    override suspend fun validate() = archives.find { !it.isValid() } == null

    override suspend fun run() {
        if (parameters == null) {
            parameters = ExtractTaskParameters()
        }
        run(parameters!!)
    }

    override suspend fun run(params: TaskParameters) {
        parameters = params as ExtractTaskParameters
        progressMonitor.status = TaskStatus.RUNNING
        protect = false

        if (aborted) {
            progressMonitor.status = TaskStatus.PAUSED
            progressMonitor.summary = globalClass.getString(R.string.task_aborted)
            return
        }

        if (archives.isEmpty()) {
            progressMonitor.status = TaskStatus.FAILED
            progressMonitor.summary = globalClass.resources.getString(R.string.task_summary_no_src)
            return
        }

        progressMonitor.apply {
            processName = globalClass.resources.getString(R.string.preparing)
            progress = 0f
            totalContent = archives.size
        }

        try {
            archives.forEachIndexed { index, archive ->
                if (aborted) {
                    progressMonitor.status = TaskStatus.PAUSED
                    progressMonitor.summary = globalClass.getString(R.string.task_aborted)
                    return
                }

                val archiveBaseProgress = index.toFloat() / archives.size
                val archiveWeight = 1f / archives.size

                progressMonitor.apply {
                    contentName = archive.displayName
                    remainingContent = archives.size - (index + 1)
                    progress = archiveBaseProgress.coerceIn(0f, 1f)
                    processName = "Extracting (${(archiveBaseProgress * 100).toInt()}%)"
                }

                if (archive is LocalFileHolder || archive is com.raival.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder) {
                    val archiveName = archive.displayName
                    val archivePath = archive.uniquePath
                    // Prefer name without compound suffixes for dest folder
                    val destDirName = when {
                        archiveName.lowercase().endsWith(".tar.gz") ->
                            archiveName.removeSuffix(".tar.gz").removeSuffix(".TAR.GZ")
                        archiveName.lowercase().endsWith(".tar.bz2") ->
                            archiveName.removeSuffix(".tar.bz2").removeSuffix(".TAR.BZ2")
                        archiveName.lowercase().endsWith(".tar.xz") ->
                            archiveName.removeSuffix(".tar.xz").removeSuffix(".TAR.XZ")
                        archiveName.lowercase().endsWith(".tar.zst") ->
                            archiveName.removeSuffix(".tar.zst").removeSuffix(".TAR.ZST")
                        else -> archiveName.substringBeforeLast(".")
                    }
                    val parentPath = archivePath.substringBeforeLast("/")
                    val destDirPath = if (parentPath.isEmpty() || parentPath == "/") "/$destDirName" else "$parentPath/$destDirName"
                    File(destDirPath).mkdirs()

                    val pwd = parameters?.password
                    android.util.Log.d(
                        "ExtractTask",
                        "Native extract (lib7za): $archiveName, " +
                            "password=${if (pwd != null) "***" else "none"}"
                    )
                    // ArchiveManager.resolveAccessibleArchivePath handles native vs fallback copy
                    ArchiveManager.extractAll(
                        archivePath = archivePath,
                        destinationDir = destDirPath,
                        password = pwd,
                        isAborted = { aborted },
                        onProgress = { subPercent, currentFile ->
                            if (aborted) return@extractAll
                            val currentOverall = if (subPercent >= 0f) {
                                (archiveBaseProgress + (subPercent * archiveWeight)).coerceIn(0.01f, 0.99f)
                            } else {
                                progressMonitor.progress
                            }
                            val pctInt = (currentOverall * 100).toInt()
                            progressMonitor.apply {
                                progress = currentOverall
                                if (currentFile.isNotEmpty()) {
                                    contentName = currentFile
                                }
                                processName = "Extracting ($pctInt%)"
                            }
                        }
                    )

                    if (aborted) {
                        progressMonitor.apply {
                            status = TaskStatus.PAUSED
                            summary = globalClass.getString(R.string.task_aborted)
                        }
                        return
                    }
                }
            }

            progressMonitor.apply {
                status = TaskStatus.SUCCESS
                progress = 1.0f
                processName = globalClass.getString(R.string.completed)
                summary = globalClass.getString(R.string.task_completed)
            }
        } catch (e: Exception) {
            if (aborted) {
                progressMonitor.apply {
                    status = TaskStatus.PAUSED
                    summary = globalClass.getString(R.string.task_aborted)
                }
                return
            }
            logger.logError(e)
            progressMonitor.apply {
                status = TaskStatus.FAILED
                summary = globalClass.resources.getString(R.string.task_summary_failed, e.message ?: emptyString)
            }
        }
    }

    override fun setParameters(params: TaskParameters) {
        parameters = params as ExtractTaskParameters
    }

    override suspend fun continueTask() {
        if (parameters == null) {
            parameters = ExtractTaskParameters()
        }
        run(parameters!!)
    }
}
