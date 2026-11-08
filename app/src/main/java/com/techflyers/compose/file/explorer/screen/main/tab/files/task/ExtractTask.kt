package com.techflyers.compose.file.explorer.screen.main.tab.files.task

import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
import com.techflyers.compose.file.explorer.common.toFormattedDate
import com.techflyers.compose.file.explorer.screen.main.tab.files.FilesTab
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.shizuku.ShizukuFileHolder
import com.techflyers.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
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

        // Smartly group and deduplicate multi-part split archives in the parent folder:
        // If the user selected .001, .002, .003 (or tapped .002 / .part2.rar), resolve to each
        // multi-part archive's primary volume once.
        val resolvedArchives = mutableListOf<ContentHolder>()
        val seenPrimaryPaths = linkedSetOf<String>()
        for (item in archives) {
            if (item is LocalFileHolder) {
                val multiInfo = ArchiveManager.resolveMultiPartArchive(item.file)
                val primary = if (multiInfo.isMultiPart && multiInfo.primaryFile.exists()) {
                    LocalFileHolder(multiInfo.primaryFile)
                } else {
                    item
                }
                val canonical = runCatching { primary.file.canonicalPath }.getOrDefault(primary.uniquePath)
                if (seenPrimaryPaths.add(canonical)) {
                    resolvedArchives.add(primary)
                }
            } else {
                if (seenPrimaryPaths.add(item.uniquePath)) {
                    resolvedArchives.add(item)
                }
            }
        }

        progressMonitor.apply {
            processName = globalClass.resources.getString(R.string.preparing)
            progress = 0f
            totalContent = resolvedArchives.size
            remainingContent = resolvedArchives.size
        }

        var currentArchiveHolder: ContentHolder? = null

        try {
            resolvedArchives.forEachIndexed { index, archive ->
                currentArchiveHolder = archive
                if (aborted) {
                    progressMonitor.status = TaskStatus.PAUSED
                    progressMonitor.summary = globalClass.getString(R.string.task_aborted)
                    return
                }

                val archiveBaseProgress = index.toFloat() / resolvedArchives.size
                val archiveWeight = 1f / resolvedArchives.size

                progressMonitor.apply {
                    contentName = archive.displayName
                    remainingContent = resolvedArchives.size - index
                    progress = archiveBaseProgress.coerceIn(0f, 1f)
                    processName = "Extracting (${(archiveBaseProgress * 100).toInt()}%)"
                }

                if (archive is LocalFileHolder || archive is ShizukuFileHolder) {
                    val archiveName = archive.displayName
                    val archivePath = archive.uniquePath
                    val parentPath = archivePath.substringBeforeLast("/").ifEmpty { "/" }

                    val extractInParentDirect = parameters?.extractInParentDirect == true
                    val destDirPath = if (extractInParentDirect) {
                        parentPath
                    } else {
                        val destDirName = ArchiveManager.getCleanArchiveBaseName(archiveName)
                        if (parentPath == "/") "/$destDirName" else "$parentPath/$destDirName"
                    }
                    File(destDirPath).mkdirs()

                    val pwd = parameters?.password
                    android.util.Log.d(
                        "ExtractTask",
                        "Native extract (lib7za): $archiveName -> $destDirPath, " +
                            "password=${if (pwd != null) "***" else "none"}"
                    )

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

                    com.techflyers.compose.file.explorer.screen.main.tab.files.provider.StorageProvider.notifyFileMutated(
                        File(destDirPath)
                    )
                    progressMonitor.remainingContent = resolvedArchives.size - (index + 1)
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
            val activeTab = globalClass.mainActivityManager.getActiveTab() as? FilesTab
            if (e is ArchiveManager.ArchiveOperationException) {
                activeTab?.showArchiveErrorDialog(
                    archiveName = currentArchiveHolder?.displayName ?: "",
                    summary = e.summary,
                    diagnosticLog = e.diagnosticLog
                )
                progressMonitor.apply {
                    status = TaskStatus.FAILED
                    summary = "${e.summary}\n\n${e.diagnosticLog}"
                }
            } else {
                progressMonitor.apply {
                    status = TaskStatus.FAILED
                    summary = globalClass.resources.getString(R.string.task_summary_failed, e.message ?: emptyString)
                }
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
