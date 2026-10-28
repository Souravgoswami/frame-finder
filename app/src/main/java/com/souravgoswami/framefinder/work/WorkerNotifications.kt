package com.souravgoswami.framefinder.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.souravgoswami.framefinder.MainActivity
import com.souravgoswami.framefinder.R

object WorkerNotifications {
    private const val CHANNEL = "framefinder_analysis_progress_v2"

    fun foregroundInfo(
        context: Context,
        notificationId: Int,
        title: String,
        done: Int,
        total: Int,
        estimatedRemainingMillis: Long?
    ): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Photo analysis progress", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "High priority progress while Frame Finder analyzes the local photo library"
                enableLights(true)
                lightColor = Color.rgb(106, 148, 255)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        val launch = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val progressText = if (total > 0) {
            buildString {
                append(formatScanProgress(done, total))
                estimatedRemainingMillis?.let {
                    append(", ")
                    append(formatEstimatedRemaining(it))
                }
            }
        } else {
            "Preparing…"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(progressText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(progressText))
            .setSubText("Frame Finder")
            .setProgress(total.coerceAtLeast(0), done.coerceAtLeast(0), total <= 0)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setColor(Color.rgb(106, 148, 255))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(launch)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}
