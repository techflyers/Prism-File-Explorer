package com.raival.compose.file.explorer.screen.terminal

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

class TerminalSessionService : Service() {

    private val sessions = hashMapOf<SessionId, TerminalSession>()
    private val sessionWorkDirs = mutableMapOf<SessionId, SessionPwd>()
    val sessionList = mutableStateListOf<String>()
    var currentSession = mutableStateOf("main")

    inner class SessionBinder : Binder() {
        fun getService(): TerminalSessionService = this@TerminalSessionService

        fun createSession(id: SessionId, client: TerminalSessionClient, activity: TerminalActivity): SessionInfo {
            val isExtraction = !isTerminalInstalled(activity) &&
                activity.installNextStage == NEXT_STAGE.EXTRACTION

            if (activity.installNextStage == NEXT_STAGE.EXTRACTION) {
                activity.installNextStage = NEXT_STAGE.NONE
            }

            val (session, pwd) = MkSession.createSession(activity, client, id, isExtraction)
            sessions[id] = session
            sessionWorkDirs[id] = pwd
            sessionList.add(id)
            updateNotification()
            return SessionInfo(id, pwd, session)
        }

        fun getSession(id: SessionId): TerminalSession? = sessions[id]

        fun getSessionInfoByPwd(pwd: SessionPwd): SessionInfo? =
            sessionWorkDirs.keys.find { sessionWorkDirs[it] == pwd }
                ?.let { SessionInfo(it, sessionWorkDirs[it]!!, sessions[it]!!) }

        fun terminateSession(id: SessionId) {
            sessions[id]?.apply { if (emulator != null) finishIfRunning() }
            sessions.remove(id)
            sessionList.remove(id)
            sessionWorkDirs.remove(id)
            if (sessions.isEmpty()) stopSelf() else updateNotification()
        }
    }

    private val binder = SessionBinder()
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PrismTerminal::SessionService")
        StatUpdater.start(this)
    }

    override fun onDestroy() {
        sessions.forEach { it.value.finishIfRunning() }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        StatUpdater.stop()
        super.onDestroy()
    }

    @SuppressLint("WakelockTimeout", "Wakelock")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_EXIT -> actionExit()
            ACTION_WAKE_LOCK -> {
                if (wakeLock?.isHeld == true) wakeLock?.release() else wakeLock?.acquire()
                updateNotification()
            }
        }
        return START_NOT_STICKY
    }

    fun actionExit() {
        sessions.forEach { it.value.finishIfRunning() }
        stopSelf()
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun createNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, TerminalActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exitPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TerminalSessionService::class.java).apply { action = ACTION_EXIT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val wakeLockPendingIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TerminalSessionService::class.java).apply { action = ACTION_WAKE_LOCK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, TERMINAL_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Prism Terminal")
            .setContentText("${sessions.size} session(s) running${if (wakeLock?.isHeld == true) " (wake lock)" else ""}")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(openIntent)
            .addAction(NotificationCompat.Action.Builder(null, "Exit", exitPendingIntent).build())
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    if (wakeLock?.isHeld == true) "Release Wake Lock" else "Acquire Wake Lock",
                    wakeLockPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            TERMINAL_NOTIFICATION_CHANNEL_ID,
            "Terminal Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Prism terminal background sessions" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        runCatching { notificationManager.notify(NOTIFICATION_ID, createNotification()) }
            .onFailure { it.printStackTrace() }
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        const val ACTION_EXIT = "ACTION_TERMINAL_EXIT"
        const val ACTION_WAKE_LOCK = "ACTION_TERMINAL_WAKE_LOCK"
    }
}
