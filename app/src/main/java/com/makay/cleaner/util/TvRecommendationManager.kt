package com.makay.cleaner.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.makay.cleaner.MainActivity
import com.makay.cleaner.notification.NotificationManager as MakayNotify

object TvRecommendationManager {

    fun publishRecommendation(context: Context, junkBytes: Long, score: Int = 100) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!PlatformManager.isTvMode(context)) return
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    ?: return
            val channelId = "makay_tv_recommendations"
            val channel = android.app.NotificationChannel(
                channelId,
                "Makay Cleaner TV Önerileri",
                android.app.NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Android TV ana ekran temizlik önerileri"
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)

            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MakayNotify.EXTRA_DEEP_LINK, MakayNotify.DEEP_HOME)
            }
            val pending = PendingIntent.getActivity(
                context,
                999,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val formatted = formatBytes(junkBytes)
            val text = if (junkBytes > 0L) {
                "Skor %$score · $formatted silinebilir junk"
            } else {
                "Skor %$score · cihaz iyi durumda"
            }
            val notification = android.app.Notification.Builder(context, channelId)
                .setContentTitle("Makay Cleaner")
                .setContentText(text)
                .setSmallIcon(com.makay.cleaner.R.mipmap.ic_launcher)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_RECOMMENDATION)
                .build()
            notificationManager.notify(999, notification)
        } catch (_: Exception) {
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
