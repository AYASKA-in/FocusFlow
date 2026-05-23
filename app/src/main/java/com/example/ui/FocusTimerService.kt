package com.example.ui

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusTimerService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2026
        private const val CHANNEL_ID = "focusflow_timer_channel"
        private const val CHANNEL_NAME = "Focus Timer Session"
        
        // Actions
        const val ACTION_START = "START"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_TICK = "TICK"
        const val ACTION_COMPLETED = "COMPLETED"
        
        // Actions from Notification Buttons
        const val ACTION_NOTIF_PAUSE = "NOTIF_PAUSE"
        const val ACTION_NOTIF_RESUME = "NOTIF_RESUME"
        const val ACTION_NOTIF_END = "NOTIF_NOTIF_END"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_STICKY

        when (action) {
            ACTION_START -> {
                showRunningNotification(startForeground = true)
            }
            ACTION_TICK -> {
                showRunningNotification(startForeground = false)
            }
            ACTION_PAUSE -> {
                showPausedNotification(startForeground = true)
            }
            ACTION_COMPLETED -> {
                showCompletedNotification()
            }
            ACTION_NOTIF_PAUSE -> {
                TimerManager.pauseTimer(applicationContext)
                showPausedNotification(startForeground = true)
            }
            ACTION_NOTIF_RESUME -> {
                TimerManager.resumeTimer(applicationContext)
                showRunningNotification(startForeground = true)
            }
            ACTION_NOTIF_END -> {
                // End and save early
                val db = AppDatabase.getDatabase(applicationContext)
                val repository = com.example.data.FocusFlowRepository(db)
                TimerManager.endAndSaveSessionEarly(applicationContext, repository)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showRunningNotification(startForeground: Boolean) {
        val taskName = TimerManager.activeTimerTaskName.value
        val remainingSecs = TimerManager.timerSecondsRemaining.value
        val isBreak = TimerManager.isBreakMode.value
        
        val timeFormatted = formatTime(remainingSecs)
        val titleText = if (isBreak) "On Soft Break" else "Focus Session Active"
        val subtitleText = if (isBreak) "$timeFormatted • Resting: $taskName" else "$timeFormatted • Doing: $taskName"

        // Pause action
        val pauseIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_NOTIF_PAUSE }
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // End session action
        val endIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_NOTIF_END }
        val endPendingIntent = PendingIntent.getService(this, 2, endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Content intent
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentTitle(titleText)
            .setContentText(subtitleText)
            .setSmallIcon(android.R.drawable.presence_online) // Use built-in system drawable as safe placeholder
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Early", endPendingIntent)
            .setColor(0xFF10B981.toInt()) // Soft premium green/teal accent tint
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (startForeground) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun showPausedNotification(startForeground: Boolean) {
        val taskName = TimerManager.activeTimerTaskName.value
        val remainingSecs = TimerManager.timerSecondsRemaining.value
        val isBreak = TimerManager.isBreakMode.value
        
        val timeFormatted = formatTime(remainingSecs)
        val titleText = if (isBreak) "Break Paused" else "Focus Session Paused"
        val subtitleText = "$timeFormatted • $taskName"

        // Resume action
        val resumeIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_NOTIF_RESUME }
        val resumePendingIntent = PendingIntent.getService(this, 3, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // End session action
        val endIntent = Intent(this, FocusTimerService::class.java).apply { action = ACTION_NOTIF_END }
        val endPendingIntent = PendingIntent.getService(this, 4, endIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Content intent
        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setContentTitle(titleText)
            .setContentText(subtitleText)
            .setSmallIcon(android.R.drawable.presence_invisible) // built-in pause-adjacent status indicator
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Early", endPendingIntent)
            .setColor(0xFFF59E0B.toInt()) // Soft amber tint for pause phase
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (startForeground) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletedNotification() {
        val wasBreak = TimerManager.isBreakMode.value // break mode state updates during completion trigger

        val titleText = if (!wasBreak) "Relaxation Session Begins!" else "Focus Block Over!"
        val bodyText = if (!wasBreak) "Awesome work. Transitioning to a short mindfulness break." else "Focus session has run its course. Stretched and refreshed?"

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(this, 5, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(false) // Not ongoing - can swipe away!
            .setAutoCancel(true)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setSmallIcon(android.R.drawable.star_on)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentIntent(contentPendingIntent)
            .setColor(0xFF10B981.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 10, notification)

        // Since it's completed and the timer isn't active anymore, we can stop the ongoing foreground service
        androidx.core.app.ServiceCompat.stopForeground(this, androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Shows active deep focus timer notification and buttons."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
