package com.raival.compose.file.explorer.screen.main.tab.files.task

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ArchiveManager
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import java.io.File

class CompressTask(
    val sourceContent: List<ContentHolder>
) : Task() {
    private var parameters: CompressTaskParameters? = null
    private var pendingContent = arrayListOf<TaskContentItem>()

    override val metadata = System.currentTimeMillis().toFormattedDate().let { time ->
        TaskMetadata(
            id = id,
            creationTime = time,
            title = globalClass.resources.getString(R.string.compress),
            subtitle = globalClass.resources.getString(R.string.task_subtitle, sourceContent.size),
            displayDetails = sourceContent.joinToString(", ") { it.displayName },
            fullDetails = buildString {
                sourceContent.forEachIndexed { index, source ->
                    append(source.displayName)
                    append("\n")
                }
                append("\n")
                append(time)
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

    override suspend fun validate() = sourceContent.find { !it.isValid() } == null

    private fun markAsFailed(info: String) {
        progressMonitor.apply {
            status = TaskStatus.FAILED
            summary = info
            progress = 0f
        }
    }

    private fun markAsAborted() {
        progressMonitor.apply {
            status = TaskStatus.PAUSED
            summary = globalClass.getString(R.string.task_aborted)
        }
    }

    override suspend fun run() {
        if (parameters == null) {
            markAsFailed(globalClass.getString(R.string.unable_to_continue_task))
            return
        }
        run(parameters!!)
    }

    override suspend fun run(params: TaskParameters) {
        parameters = params as CompressTaskParameters
        progressMonitor.status = TaskStatus.RUNNING
        protect = false

        // Check abortion early
        if (aborted) {
            markAsAborted()
            return
        }

        if (sourceContent.isEmpty()) {
            markAsFailed(globalClass.resources.getString(R.string.task_summary_no_src))
            return
        }

        progressMonitor.apply {
            processName = globalClass.resources.getString(R.string.preparing)
            progress = 0f
        }

        val basePath = sourceContent[0].getParent()?.uniquePath ?: emptyString

        if (pendingContent.isEmpty()) {
            sourceContent.forEachIndexed { index, content ->
                pendingContent.add(
                    TaskContentItem(
                        content = content,
                        relativePath = content.uniquePath.removePrefix("/$basePath"),
                        status = TaskContentStatus.PENDING
                    )
                )
            }
        }

        progressMonitor.apply {
            totalContent = pendingContent.size
            remainingContent = pendingContent.size
            processName = "${globalClass.getString(R.string.compressing)} (0%)"
            progress = 0f
        }

        // Determine the output extension to decide which engine to use
        val destPath = parameters!!.destPath
        val destExt = destPath.substringAfterLast('.', "").lowercase()

        // lib7za handles all formats including zip
        val useNativeCompression = ArchiveManager.isNativeCompressFormat(destExt)

        try {
            if (aborted) {
                markAsAborted()
                return
            }

            if (useNativeCompression) {
                // Native compression via lib7za
                val parentPath = sourceContent.firstOrNull()?.getParent()?.uniquePath?.takeIf {
                    it.isNotEmpty() && File(it).isDirectory
                }
                val sourcePaths = if (parentPath != null) {
                    pendingContent.map { it.content.displayName }
                } else {
                    pendingContent.map { it.content.uniquePath }
                }

                progressMonitor.apply {
                    processName = "${globalClass.getString(R.string.compressing)} (0%)"
                    progress = 0f
                }
                ArchiveManager.compress(
                    sourcePaths = sourcePaths,
                    archivePath = destPath,
                    password = parameters?.password,
                    compressionLevel = parameters?.compressionLevel ?: 5,
                    workingDir = parentPath,
                    isAborted = { aborted },
                    onProgress = { subPercent, currentFile ->
                        if (aborted) return@compress
                        if (subPercent >= 0f) {
                            val pct = subPercent.coerceIn(0.01f, 0.99f)
                            val pctInt = (pct * 100).toInt()
                            progressMonitor.apply {
                                progress = pct
                                if (currentFile.isNotEmpty()) {
                                    contentName = currentFile
                                }
                                processName = "${globalClass.getString(R.string.compressing)} ($pctInt%)"
                            }
                        } else if (currentFile.isNotEmpty()) {
                            progressMonitor.contentName = currentFile
                        }
                    }
                )
                if (aborted) {
                    markAsAborted()
                    return
                }
            } else {
                // Fallback ZIP creation via zip4j with active background progress tracking
                val pwd = parameters?.password
                ZipFile(destPath).use { zipOut ->
                    if (!pwd.isNullOrEmpty()) {
                        zipOut.setPassword(pwd.toCharArray())
                    }
                    zipOut.isRunInThread = true
                    val pm = zipOut.progressMonitor

                    pendingContent.forEachIndexed { index, itemToCompress ->
                        if (aborted) {
                            markAsAborted()
                            return
                        }

                        if (itemToCompress.status == TaskContentStatus.PENDING) {
                            progressMonitor.apply {
                                contentName = itemToCompress.content.displayName
                                remainingContent = pendingContent.size - index
                            }

                            try {
                                if (itemToCompress.content.isFolder) {
                                    addFolderToZip(zipOut, itemToCompress.content)
                                } else {
                                    addFileToZip(zipOut, itemToCompress.content)
                                }

                                while (pm.state == net.lingala.zip4j.progress.ProgressMonitor.State.BUSY) {
                                    if (aborted) {
                                        pm.isCancelAllTasks = true
                                        markAsAborted()
                                        return
                                    }
                                    val subPct = (pm.percentDone / 100f).coerceIn(0f, 1f)
                                    val overallProgress =
                                        ((index + subPct) / pendingContent.size).coerceIn(0f, 0.99f)
                                    val pct = (overallProgress * 100).toInt()

                                    progressMonitor.apply {
                                        if (!pm.fileName.isNullOrEmpty()) {
                                            contentName = pm.fileName.substringAfterLast('/')
                                        }
                                        remainingContent = pendingContent.size - index
                                        progress = overallProgress
                                        processName = "${globalClass.getString(R.string.compressing)} ($pct%)"
                                    }
                                    kotlinx.coroutines.delay(50)
                                }

                                if (pm.result == net.lingala.zip4j.progress.ProgressMonitor.Result.ERROR) {
                                    throw pm.exception ?: Exception("zip4j compression error")
                                }

                                itemToCompress.status = TaskContentStatus.SUCCESS
                            } catch (e: Exception) {
                                logger.logError(e)
                                markAsFailed(
                                    globalClass.resources.getString(
                                        R.string.task_summary_failed,
                                        e.message ?: emptyString
                                    )
                                )
                                return
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (aborted) {
                markAsAborted()
                return
            }
            logger.logError(e)
            markAsFailed(
                globalClass.resources.getString(
                    R.string.task_summary_failed,
                    e.message ?: emptyString
                )
            )
            return
        }

        if (progressMonitor.status == TaskStatus.RUNNING) {
            progressMonitor.apply {
                status = TaskStatus.SUCCESS
                progress = 1.0f
                processName = globalClass.getString(R.string.completed)
                summary = globalClass.getString(R.string.task_completed)
            }
        }
    }

    private fun getZipParameters(): ZipParameters {
        val params = ZipParameters()
        val p = parameters
        if (p != null) {
            if (!p.password.isNullOrEmpty()) {
                params.isEncryptFiles = true
                params.encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                params.aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
            }
            params.compressionLevel = when (p.compressionLevel) {
                0 -> net.lingala.zip4j.model.enums.CompressionLevel.NO_COMPRESSION
                1 -> net.lingala.zip4j.model.enums.CompressionLevel.FASTEST
                3 -> net.lingala.zip4j.model.enums.CompressionLevel.FAST
                5 -> net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                7 -> net.lingala.zip4j.model.enums.CompressionLevel.MAXIMUM
                9 -> net.lingala.zip4j.model.enums.CompressionLevel.ULTRA
                else -> net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
            }
        }
        return params
    }

    private fun addFileToZip(zipOut: ZipFile, fileToCompress: ContentHolder) {
        zipOut.addFile(File(fileToCompress.uniquePath), getZipParameters())
    }

    private fun addFolderToZip(zipOut: ZipFile, folderToCompress: ContentHolder) {
        zipOut.addFolder(File(folderToCompress.uniquePath), getZipParameters())
    }

    override fun setParameters(params: TaskParameters) {
        parameters = params as CompressTaskParameters
    }

    override suspend fun continueTask() {
        if (parameters == null) {
            markAsFailed(globalClass.getString(R.string.unable_to_continue_task))
            return
        }
        run(parameters!!)
    }
}