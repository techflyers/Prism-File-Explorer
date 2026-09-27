package com.techflyers.compose.file.explorer.screen.main.tab.files.task

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ScheduledFileTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val move: Boolean,
    val sourcePath: String,
    val destPath: String,
    val regex: String,
    val destPattern: String = "\${name}.\${ext}",
    val delayMinutes: Long = 0,
    val periodicMinutes: Long = 0,
    val enabled: Boolean = true
)

object ScheduledTaskStore {
    private const val PREFS = "scheduled_file_tasks"
    private const val KEY = "tasks"
    private val gson = Gson()

    fun load(): List<ScheduledFileTask> {
        val json = prefs().getString(KEY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<List<ScheduledFileTask>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(tasks: List<ScheduledFileTask>) {
        prefs().edit().putString(KEY, gson.toJson(tasks)).apply()
    }

    fun upsert(task: ScheduledFileTask) {
        val next = load().filter { it.id != task.id } + task
        save(next)
        schedule(task)
    }

    fun delete(id: String) {
        save(load().filter { it.id != id })
        WorkManager.getInstance(globalClass).cancelUniqueWork(workName(id))
    }

    fun setEnabled(id: String, enabled: Boolean) {
        val updated = load().map { if (it.id == id) it.copy(enabled = enabled) else it }
        save(updated)
        val task = updated.find { it.id == id } ?: return
        if (enabled) schedule(task) else {
            WorkManager.getInstance(globalClass).cancelUniqueWork(workName(id))
        }
    }

    fun schedule(task: ScheduledFileTask) {
        if (!task.enabled) return
        val wm = WorkManager.getInstance(globalClass)
        val data = workDataOf("taskId" to task.id)
        val constraints = Constraints.Builder().build()
        if (task.periodicMinutes >= 15) {
            val request = PeriodicWorkRequestBuilder<ScheduledFileTaskWorker>(
                task.periodicMinutes, TimeUnit.MINUTES
            ).setInputData(data).setConstraints(constraints).build()
            wm.enqueueUniquePeriodicWork(workName(task.id), ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            val request = OneTimeWorkRequestBuilder<ScheduledFileTaskWorker>()
                .setInitialDelay(task.delayMinutes.coerceAtLeast(0), TimeUnit.MINUTES)
                .setInputData(data)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
        }
    }

    fun runNow(task: ScheduledFileTask, inBackground: Boolean = false) {
        val sourceDir = File(task.sourcePath)
        val destDir = File(task.destPath)
        if (!sourceDir.isDirectory || !destDir.exists()) return
        val pattern = try {
            Regex(task.regex)
        } catch (_: Exception) {
            return
        }
        val matches = sourceDir.listFiles()?.filter { it.isFile && pattern.containsMatchIn(it.name) }
            ?: return
        if (matches.isEmpty()) return
        val sources = matches.map { file ->
            val holder = LocalFileHolder(file)
            val name = file.nameWithoutExtension
            val ext = file.extension
            var targetName = task.destPattern
                .replace("\${name}", name)
                .replace("\${ext}", ext)
            if (targetName.contains('$')) {
                try {
                    val match = pattern.find(file.name)
                    if (match != null) {
                        targetName = pattern.replace(file.name, targetName)
                    }
                } catch (_: Exception) {
                }
            }
            holder to targetName
        }
        val copy = CopyTask(sources.map { it.first }, task.move)
        sources.forEach { (holder, target) ->
            if (target.isNotBlank() && target != holder.displayName) {
                copy.setCustomTargetName(holder.uniquePath, target)
            }
        }
        if (inBackground) {
            kotlinx.coroutines.runBlocking {
                copy.run(CopyTaskParameters(LocalFileHolder(destDir)))
            }
        } else {
            globalClass.taskManager.addTaskAndRun(copy, CopyTaskParameters(LocalFileHolder(destDir)))
        }
    }

    private fun workName(id: String) = "scheduled_file_task_$id"

    private fun prefs() = globalClass.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

class ScheduledFileTaskWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): androidx.work.ListenableWorker.Result {
        val id = inputData.getString("taskId") ?: return androidx.work.ListenableWorker.Result.failure()
        val task = ScheduledTaskStore.load().find { it.id == id && it.enabled } ?: return androidx.work.ListenableWorker.Result.success()
        return try {
            ScheduledTaskStore.runNow(task, inBackground = true)
            androidx.work.ListenableWorker.Result.success()
        } catch (_: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}
