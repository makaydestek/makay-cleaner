package com.makay.cleaner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.makay.cleaner.notification.NotificationWorker
import java.util.concurrent.TimeUnit

class NotificationRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val WORK_TAG_REMINDER = "notification_reminder_work"
        const val KEY_REMINDER_ENABLED = "reminder_enabled"
        const val KEY_REMINDER_INTERVAL_HOURS = "reminder_interval_hours"
    }

    fun saveNotificationSettings(enabled: Boolean, intervalHours: Long) {
        prefs.edit()
            .putBoolean(KEY_REMINDER_ENABLED, enabled)
            .putLong(KEY_REMINDER_INTERVAL_HOURS, intervalHours)
            .apply()

        if (enabled) {
            scheduleReminder(intervalHours)
        } else {
            cancelReminder()
        }
    }

    fun getNotificationSettings(): Pair<Boolean, Long> {
        val enabled = prefs.getBoolean(KEY_REMINDER_ENABLED, true)
        val intervalHours = prefs.getLong(KEY_REMINDER_INTERVAL_HOURS, 24)
        return Pair(enabled, intervalHours)
    }

    private fun scheduleReminder(intervalHours: Long) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<NotificationWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES
            )
            .addTag(WORK_TAG_REMINDER)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_TAG_REMINDER,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelReminder() {
        workManager.cancelUniqueWork(WORK_TAG_REMINDER)
    }
}