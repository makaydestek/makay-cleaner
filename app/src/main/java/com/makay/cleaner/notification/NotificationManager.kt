package com.makay.cleaner.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.makay.cleaner.MainActivity
import com.makay.cleaner.R
import com.makay.cleaner.util.PlatformManager

class NotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CLEANING_REMINDER = "cleaning_reminder"
        const val CHANNEL_CLEANING_COMPLETE = "cleaning_complete"
        const val CHANNEL_STORAGE_WARNING = "storage_warning"

        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
        const val NOTIFICATION_ID_WARNING = 1003

        const val EXTRA_DEEP_LINK = "deep_link"
        const val DEEP_HOME = "home"
        const val DEEP_CACHE = "cache"
        const val DEEP_STATS = "stats"
        const val DEEP_SUGGESTIONS = "suggestions"
        const val DEEP_TOOLBOX = "toolbox"
        const val DEEP_RECYCLE = "recycle"
        const val DEEP_LARGE = "large"
        const val DEEP_MEDIA = "media"
        const val DEEP_DUP_PHOTOS = "dup_photos"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val reminderChannel = NotificationChannel(
                CHANNEL_CLEANING_REMINDER,
                "Temizlik Hatırlatmaları",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Düzenli temizlik hatırlatmaları"
                enableVibration(!PlatformManager.isTvMode(context))
                if (PlatformManager.isTvMode(context)) {
                    setSound(null, null)
                    importance = NotificationManager.IMPORTANCE_MIN
                }
            }
            val completeChannel = NotificationChannel(
                CHANNEL_CLEANING_COMPLETE,
                "Temizlik Tamamlandı",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Temizlik işlemi tamamlandığında bildirim"
                enableVibration(!PlatformManager.isTvMode(context))
                if (PlatformManager.isTvMode(context)) {
                    setSound(null, null)
                    importance = NotificationManager.IMPORTANCE_MIN
                }
            }
            val warningChannel = NotificationChannel(
                CHANNEL_STORAGE_WARNING,
                "Depolama Uyarıları",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Depolama alanı azaldığında uyarı"
                enableVibration(!PlatformManager.isTvMode(context))
                if (PlatformManager.isTvMode(context)) {
                    setSound(null, null)
                    importance = NotificationManager.IMPORTANCE_LOW
                }
            }
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(completeChannel)
            notificationManager.createNotificationChannel(warningChannel)
        }
    }

    private fun createPendingIntent(deepLink: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DEEP_LINK, deepLink)
            action = "com.makay.cleaner.DEEP_LINK"
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun sendCleaningReminder(usagePercent: Int? = null) {
        if (PlatformManager.isTvMode(context)) return
        // Ana ekran → tek dokunuş Optimizasyon
        val pendingIntent = createPendingIntent(DEEP_HOME, NOTIFICATION_ID_REMINDER)
        val body = if (usagePercent != null) {
            "Cihaz %$usagePercent dolu — tek dokunuşla optimize edin"
        } else {
            "Tek dokunuşla optimize etmek için Makay Cleaner’ı açın"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Optimizasyon zamanı")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_REMINDER, notification)
    }

    fun sendCleaningComplete(spaceSaved: Long, deepLink: String = DEEP_HOME) {
        if (PlatformManager.isTvMode(context)) return
        val pendingIntent = createPendingIntent(deepLink, NOTIFICATION_ID_COMPLETE)
        val savedText = when {
            spaceSaved < 1024 * 1024 -> "${spaceSaved / 1024} KB"
            spaceSaved < 1024 * 1024 * 1024 -> "%.1f MB".format(spaceSaved / (1024.0 * 1024.0))
            else -> "%.2f GB".format(spaceSaved / (1024.0 * 1024.0 * 1024.0))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_COMPLETE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Temizlik tamamlandı")
            .setContentText("$savedText alan kazandırıldı")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    fun sendStorageWarning(usagePercent: Int = 90, deepLink: String = DEEP_HOME) {
        if (PlatformManager.isTvMode(context)) return
        val pendingIntent = createPendingIntent(deepLink, NOTIFICATION_ID_WARNING)
        val notification = NotificationCompat.Builder(context, CHANNEL_STORAGE_WARNING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Depolama uyarısı")
            .setContentText("Depolama %$usagePercent dolu — tek dokunuşla optimize edin")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_WARNING, notification)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
