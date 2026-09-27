package com.techflyers.compose.file.explorer.screen.viewer.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.techflyers.compose.file.explorer.R

class MediaNotificationHelper(
    private val context: Context,
    private val sessionTag: String,
    private val notificationId: Int,
    private val targetActivityClass: Class<*>,
    private val initialUri: Uri,
    private val instanceId: String,
    private val onPlayPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSeekTo: (Long) -> Unit,
    private val onStop: () -> Unit
) {
    companion object {
        const val CHANNEL_ID = "prism_media_playback"
        private const val ACTION_PLAY_PAUSE = "com.techflyers.compose.file.explorer.ACTION_MEDIA_PLAY_PAUSE"
        private const val ACTION_NEXT = "com.techflyers.compose.file.explorer.ACTION_MEDIA_NEXT"
        private const val ACTION_PREVIOUS = "com.techflyers.compose.file.explorer.ACTION_MEDIA_PREVIOUS"
        private const val ACTION_STOP = "com.techflyers.compose.file.explorer.ACTION_MEDIA_STOP"
    }

    private val actionPlayPause = "$ACTION_PLAY_PAUSE.$instanceId"
    private val actionNext = "$ACTION_NEXT.$instanceId"
    private val actionPrevious = "$ACTION_PREVIOUS.$instanceId"
    private val actionStop = "$ACTION_STOP.$instanceId"

    private var mediaSession: MediaSession? = null
    private var isReceiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                actionPlayPause -> mainHandler.post { onPlayPause() }
                actionNext -> mainHandler.post { onNext() }
                actionPrevious -> mainHandler.post { onPrevious() }
                actionStop -> mainHandler.post { onStop() }
            }
        }
    }

    init {
        createNotificationChannel()
        setupMediaSession()
        registerReceiver()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.media_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun setupMediaSession() {
        val session = MediaSession(context, "$sessionTag.$instanceId")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { mainHandler.post { onPlayPause() } }
            override fun onPause() { mainHandler.post { onPlayPause() } }
            override fun onSkipToNext() { mainHandler.post { onNext() } }
            override fun onSkipToPrevious() { mainHandler.post { onPrevious() } }
            override fun onSeekTo(pos: Long) { mainHandler.post { this@MediaNotificationHelper.onSeekTo(pos) } }
            override fun onStop() { mainHandler.post { this@MediaNotificationHelper.onStop() } }
        })

        val contentIntent = Intent(context, targetActivityClass).apply {
            action = Intent.ACTION_VIEW
            data = initialUri
            putExtra("uid", instanceId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        session.setSessionActivity(pendingIntent)
        session.isActive = true
        mediaSession = session
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(actionPlayPause)
                addAction(actionNext)
                addAction(actionPrevious)
                addAction(actionStop)
            }
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    fun update(
        title: String,
        artist: String?,
        album: String?,
        albumArt: Bitmap?,
        isPlaying: Boolean,
        hasPrevious: Boolean,
        hasNext: Boolean,
        positionMs: Long = 0L,
        durationMs: Long = 0L
    ) {
        val session = mediaSession ?: return

        // 1. Update PlaybackState
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                positionMs,
                if (isPlaying) 1.0f else 0.0f
            )
        session.setPlaybackState(stateBuilder.build())

        // 2. Update MediaMetadata
        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist ?: "")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album ?: "")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        if (albumArt != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt)
        }
        session.setMetadata(metaBuilder.build())

        // 3. Build Notification
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val contentIntent = Intent(context, targetActivityClass).apply {
            action = Intent.ACTION_VIEW
            data = initialUri
            putExtra("uid", instanceId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder
            .setContentTitle(title)
            .setContentText(artist?.ifEmpty { null } ?: album?.ifEmpty { null } ?: "")
            .setSmallIcon(R.drawable.app_icon)
            .setContentIntent(contentPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        if (albumArt != null) {
            notificationBuilder.setLargeIcon(albumArt)
        }

        val actionIndices = mutableListOf<Int>()
        var actionIndex = 0

        // Previous Action
        if (hasPrevious) {
            val prevPending = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 1,
                Intent(actionPrevious).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val prevAction = Notification.Action.Builder(
                android.R.drawable.ic_media_previous,
                "Previous",
                prevPending
            ).build()
            notificationBuilder.addAction(prevAction)
            actionIndices.add(actionIndex++)
        }

        // Play / Pause Action
        val playPausePending = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            Intent(actionPlayPause).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseAction = Notification.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPausePending
        ).build()
        notificationBuilder.addAction(playPauseAction)
        actionIndices.add(actionIndex++)

        // Next Action
        if (hasNext) {
            val nextPending = PendingIntent.getBroadcast(
                context,
                notificationId * 10 + 3,
                Intent(actionNext).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextAction = Notification.Action.Builder(
                android.R.drawable.ic_media_next,
                "Next",
                nextPending
            ).build()
            notificationBuilder.addAction(nextAction)
            actionIndices.add(actionIndex++)
        }

        // MediaStyle
        val mediaStyle = Notification.MediaStyle()
            .setMediaSession(session.sessionToken)
            .setShowActionsInCompactView(*actionIndices.toIntArray())
        notificationBuilder.style = mediaStyle

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission check
        }
    }

    fun release() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.cancel(notificationId)
        } catch (_: Exception) {}

        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }

        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}
