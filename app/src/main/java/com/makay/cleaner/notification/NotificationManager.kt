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

/**
 * Bildirim metinleri kaynaklardan (`strings.xml`, `values-en`) gelir; böylece
 * cihaz dili ile bildirimler de uyumlu olur.
 */
class NotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_CLEANING_REMINDER = "cleaning_reminder"
        const val CHANNEL_CLEANING_COMPLETE = "cleaning_complete"
        const val CHANNEL_STORAGE_WARNING = "storage_warning"

        const val NOTIFICATION_ID_REMINDER = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
        const val NOTIFICATION_ID_WARNING = 1003
        const val NOTIFICATION_ID_FAILED = 1004
        const val NOTIFICATION_ID_GOOD_NIGHT = 1005

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
        const val DEEP_GOOD_NIGHT = "good_night"
        const val DEEP_LIGHT_CLEAN = "light_clean"

        /** Depolama uyarısı spam'ini önlemek için minimum aralık. */
        private const val WARNING_COOLDOWN_MS = 24L * 60L * 60L * 1000L
        private const val PREFS = "notification_state"
        private const val KEY_LAST_WARNING_MS = "last_storage_warning_ms"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    /**
     * Android 13+ bildirim izni ve sistem geneli bildirim anahtarı kontrolü.
     * İzin yoksa `notify` sessizce düşer; bunu erkenden tespit ediyoruz.
     */
    private fun canNotify(): Boolean {
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return com.makay.cleaner.util.PermissionHelper.hasNotificationPermission(context)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isTv = PlatformManager.isTvMode(context)
            val reminderChannel = NotificationChannel(
                CHANNEL_CLEANING_REMINDER,
                context.getString(R.string.channel_reminder_name),
                if (isTv) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_reminder_desc)
                enableVibration(!isTv)
                if (isTv) setSound(null, null)
            }
            val completeChannel = NotificationChannel(
                CHANNEL_CLEANING_COMPLETE,
                context.getString(R.string.channel_complete_name),
                if (isTv) NotificationManager.IMPORTANCE_MIN else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_complete_desc)
                enableVibration(!isTv)
                if (isTv) setSound(null, null)
            }
            val warningChannel = NotificationChannel(
                CHANNEL_STORAGE_WARNING,
                context.getString(R.string.channel_warning_name),
                if (isTv) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_warning_desc)
                enableVibration(!isTv)
                if (isTv) setSound(null, null)
            }
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(completeChannel)
            notificationManager.createNotificationChannel(warningChannel)
        }
    }

    private fun createPendingIntent(deepLink: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
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
        if (!canNotify()) return
        val pendingIntent = createPendingIntent(DEEP_SUGGESTIONS, NOTIFICATION_ID_REMINDER)
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_reminder_title))
            .setContentText(context.getString(R.string.notif_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
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
        if (!canNotify()) return
        // Yalnızca gerçek kazanç veya hata varsa bildir; "0 B temizlendi" spam'i olmasın.
        val hasGain = spaceSaved > 0L || trashedBytes > 0L || filesDeleted > 0
        if (!hasGain && failedCount == 0) return
        val pendingIntent = createPendingIntent(deepLink, NOTIFICATION_ID_COMPLETE)
        val savedText = formatBytes(spaceSaved)
        val extras = buildList {
            if (filesDeleted > 0) add(context.getString(R.string.notif_extra_files, filesDeleted))
            if (trashedBytes > 0L) add(context.getString(R.string.notif_extra_trashed, formatBytes(trashedBytes)))
            if (failedCount > 0) add(context.getString(R.string.notif_extra_failed, failedCount))
            if (skippedCount > 0) add(context.getString(R.string.notif_extra_skipped, skippedCount))
        }
        val separator = " · "
        val detail = when {
            extras.isEmpty() -> context.getString(R.string.notif_saved_space, savedText)
            spaceSaved > 0L || trashedBytes > 0L -> "$savedText$separator${extras.joinToString(separator)}"
            else -> extras.joinToString(separator)
        }
        val title = when {
            failedCount > 0 && hasGain -> context.getString(R.string.notif_title_partial)
            failedCount > 0 -> context.getString(R.string.notif_title_failed)
            else -> context.getString(R.string.notif_title_complete)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_COMPLETE)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    fun sendCleaningFailed(message: String) {
        if (PlatformManager.isTvMode(context)) return
        if (!canNotify()) return
        val pendingIntent = createPendingIntent(DEEP_STATS, NOTIFICATION_ID_FAILED)
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_COMPLETE)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_title_autoclean_stopped))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_FAILED, notification)
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun sendStorageWarning(usagePercent: Int = 90, deepLink: String = DEEP_CACHE) {
        if (PlatformManager.isTvMode(context)) return
        if (!canNotify()) return
        // Aynı uyarıyı günde bir kereden fazla gönderme.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_WARNING_MS, 0L)
        if (last > 0L && now - last < WARNING_COOLDOWN_MS) return
        val pendingIntent = createPendingIntent(deepLink, NOTIFICATION_ID_WARNING)
        val notification = NotificationCompat.Builder(context, CHANNEL_STORAGE_WARNING)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_warning_title))
            .setContentText(context.getString(R.string.notif_warning_text, usagePercent))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_WARNING, notification)
        prefs.edit().putLong(KEY_LAST_WARNING_MS, now).apply()
    }

    /** İyi Geceler: Wi‑Fi ayarına tek dokunuş (otomatik kapatma yok). */
    fun sendGoodNightReminder() {
        if (PlatformManager.isTvMode(context)) return
        if (!canNotify()) return
        val wifiIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val wifiPi = try {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_GOOD_NIGHT,
                wifiIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } catch (_: Exception) {
            createPendingIntent(DEEP_GOOD_NIGHT, NOTIFICATION_ID_GOOD_NIGHT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_CLEANING_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notif_goodnight_title))
            .setContentText(context.getString(R.string.notif_goodnight_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(wifiPi)
            .addAction(0, context.getString(R.string.notif_action_wifi), wifiPi)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_GOOD_NIGHT, notification)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}
