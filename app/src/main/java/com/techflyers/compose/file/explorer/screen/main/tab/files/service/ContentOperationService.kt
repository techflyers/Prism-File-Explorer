package com.techflyers.compose.file.explorer.screen.main.tab.files.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.App.Companion.logger
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.screen.main.MainActivity
import com.techflyers.compose.file.explorer.screen.main.tab.files.task.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ContentOperationService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Tasks ids and whether they are actively executing
    private val runningTaskIds = ConcurrentHashMap<String, Boolean>()
    @Volatile
    private var isInForeground = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForegroundSafely()
    }

    private fun promoteToForegroundSafely() {
        try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                type
            )
            isInForeground = true
        } catch (e: Exception) {
            // Prevents ForegroundServiceStartNotAllowedException crash on Android 12-15+
            // when dataSync quota is exhausted or app is in a restricted background state.
            logger.logError(e)
            isInForeground = false
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val runningChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.task_running_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            getString(R.string.action_required),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(runningChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotification(): Notification {
        val activeTasksCount = runningTaskIds.count { it.value }
        val userActionRequired = runningTaskIds.values.any { !it }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(globalClass.getString(R.string.tasks))
            .setContentText(
                if (userActionRequired) getString(R.string.action_required)
                else globalClass.getString(R.string.task_running_notification_text)
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(createMainActivityPendingIntent())
            .setOngoing(activeTasksCount > 0)
            .setNumber(activeTasksCount.coerceAtLeast(1))
            .build()
    }

    private fun postActionRequiredNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val alertNotification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(globalClass.getString(R.string.tasks))
            .setContentText(getString(R.string.action_required))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(createMainActivityPendingIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        notificationManager.notify(ALERT_NOTIFICATION_ID, alertNotification)
    }

    private fun dismissActionRequiredNotificationIfResolved() {
        if (runningTaskIds.values.none { !it }) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(ALERT_NOTIFICATION_ID)
        }
    }

    private fun updateNotification() {
        if (!isInForeground && runningTaskIds.values.any { it }) {
            promoteToForegroundSafely()
        }
        if (isInForeground) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun stopServiceIfIdle() {
        val hasActivelyRunningTask = runningTaskIds.values.any { it }
        if (!hasActivelyRunningTask) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            isInForeground = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Do not keep an empty service alive if recreated by the OS
            stopServiceIfIdle()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_START_TASK -> {
                val taskId = intent.getStringExtra(TASK_ID)
                if (taskId != null) {
                    startTask(taskId)
                } else {
                    stopServiceIfIdle()
                }
            }

            ACTION_REMOVE_TASK -> {
                val taskId = intent.getStringExtra(TASK_ID)
                if (taskId != null) {
                    removeTask(taskId)
                } else {
                    stopServiceIfIdle()
                }
            }

            else -> {
                stopServiceIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    private fun removeTask(taskId: String) {
        if (runningTaskIds.containsKey(taskId)) {
            if (runningTaskIds[taskId] == true) {
                globalClass.showMsg(R.string.task_is_running)
            } else {
                runningTaskIds.remove(taskId)
                dismissActionRequiredNotificationIfResolved()
                if (runningTaskIds.values.any { it }) {
                    updateNotification()
                } else {
                    stopServiceIfIdle()
                }
            }
        } else {
            dismissActionRequiredNotificationIfResolved()
            stopServiceIfIdle()
        }
    }

    private fun startTask(taskId: String) {
        if (runningTaskIds[taskId] == true) {
            return
        }

        val task = globalClass.taskManager.getTask(taskId)
        if (task == null) {
            stopServiceIfIdle()
            return
        }

        runningTaskIds[taskId] = true
        dismissActionRequiredNotificationIfResolved()
        updateNotification()

        serviceScope.launch {
            try {
                task.run()
                globalClass.taskManager.handleTaskStatusChange(task, task.getCurrentStatus())
            } catch (e: Exception) {
                logger.logError(e)
                globalClass.taskManager.handleTaskStatusChange(task, TaskStatus.FAILED)
            } finally {
                globalClass.mainActivityManager.resumeActiveTab()
                if (task.getCurrentStatus() == TaskStatus.SUCCESS) {
                    runningTaskIds.remove(taskId)
                } else {
                    // Task paused/conflicted/failed and awaits user decision.
                    // Do NOT keep the foreground service alive indefinitely waiting for user input,
                    // as Android 14/15 enforces a strict timeout on dataSync foreground services.
                    runningTaskIds.remove(taskId)
                    postActionRequiredNotification()
                }

                if (runningTaskIds.values.any { it }) {
                    updateNotification()
                } else {
                    stopServiceIfIdle()
                }
            }
        }
    }

    override fun onTimeout(startId: Int) {
        handleForegroundTimeout(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        handleForegroundTimeout(startId)
    }

    private fun handleForegroundTimeout(startId: Int) {
        logger.logError(IllegalStateException("ContentOperationService foreground timeout reached (startId=$startId); stopping service gracefully"))
        runningTaskIds.keys.forEach { taskId ->
            globalClass.taskManager.getTask(taskId)?.abortTask()
        }
        runningTaskIds.clear()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        isInForeground = false
        stopSelf(startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        isInForeground = false

        // Abort any still-running tasks
        runningTaskIds.forEach { (taskId, isRunning) ->
            if (isRunning) {
                globalClass.taskManager.getTask(taskId)?.abortTask()
            }
        }
        runningTaskIds.clear()

        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TASK_ID = "taskId"
        private const val NOTIFICATION_ID = 1297
        private const val ALERT_NOTIFICATION_ID = 1298
        private const val CHANNEL_ID = "task_background_service"
        private const val ALERT_CHANNEL_ID = "task_action_required_channel"
        const val ACTION_START_TASK = "ACTION_START_TASK"
        const val ACTION_REMOVE_TASK = "ACTION_REMOVE_TASK"

        fun startNewBackgroundTask(context: Context, taskId: String) {
            val intent = Intent(context, ContentOperationService::class.java).apply {
                action = ACTION_START_TASK
                putExtra(TASK_ID, taskId)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                try {
                    context.startService(intent)
                } catch (inner: Exception) {
                    logger.logError(inner)
                }
            }
        }

        fun removeBackgroundTask(context: Context, taskId: String) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(ALERT_NOTIFICATION_ID)
            val intent = Intent(context, ContentOperationService::class.java).apply {
                action = ACTION_REMOVE_TASK
                putExtra(TASK_ID, taskId)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service may already be stopped, which is expected when idle
            }
        }
    }
}
