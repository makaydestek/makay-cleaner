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
        const val NOTIFICATION_ID_FAILED = 1004

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

    fun sendCleaningReminder() {
        if (PlatformManager.isTvMode(context)) return
        val pendingIntent = createPendingIntent(DEEP_SUGGESTIONS, NOTIFICATION_ID_REMINDER)
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_REMINDER)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("🧹 Temizlik Zamanı!")
            .setContentText("Cihazınızı temizlemek için Makay Cleaner'ı açın.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_REMINDER, notification)
    }

    fun sendCleaningComplete(
        spaceSaved: Long,
        deepLink: String = DEEP_STATS,
        filesDeleted: Int = 0,
        failedCount: Int = 0,
        skippedCount: Int = 0,
        trashedBytes: Long = 0L
    ) {
        if (PlatformManager.isTvMode(context)) return
        val pendingIntent = createPendingIntent(deepLink, NOTIFICATION_ID_COMPLETE)
        val savedText = formatBytes(spaceSaved)
        val extras = buildList {
            if (filesDeleted > 0) add("$filesDeleted dosya")
            if (trashedBytes > 0L) add("${formatBytes(trashedBytes)} çöpe")
            if (failedCount > 0) add("$failedCount başarısız")
            if (skippedCount > 0) add("$skippedCount korumalı atlandı")
        }
        val detail = when {
            extras.isEmpty() -> "$savedText alan kazandırıldı."
            spaceSaved > 0L || trashedBytes > 0L ->
                "$savedText · ${extras.joinToString(" · ")}"
            else -> extras.joinToString(" · ")
        }
        val title = when {
            failedCount > 0 && (filesDeleted > 0 || spaceSaved > 0L || trashedBytes > 0L) ->
                "Temizlik kısmen tamamlandı"
            failedCount > 0 -> "Temizlik sorunlu bitti"
            else -> "Temizlik tamamlandı"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_COMPLETE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    fun sendCleaningFailed(message: String) {
        if (PlatformManager.isTvMode(context)) return
        val pendingIntent = createPendingIntent(DEEP_STATS, NOTIFICATION_ID_FAILED)
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_COMPLETE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Otomatik temizlik durdu")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_FAILED, notification)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun sendStorageWarning(usagePercent: Int = 90, deepLink: String = DEEP_CACHE) {
        if (PlatformManager.isTvMode(context)) return
        val pendingIntent = createPendingIntent(deepLink, NOTIFICATION_ID_WARNING)
        val notification = NotificationCompat.Builder(context, CHANNEL_STORAGE_WARNING)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("⚠️ Depolama Uyarısı")
            .setContentText("Depolama alanı %$usagePercent dolu. Temizlik yapın!")
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
