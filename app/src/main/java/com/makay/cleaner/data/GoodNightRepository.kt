package com.makay.cleaner.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.makay.cleaner.worker.GoodNightReceiver
import java.util.Calendar

/**
 * İyi Geceler: Wi‑Fi/veriyi otomatik kapatmaz.
 * Belirlenen saatte hatırlatma bildirimi + tek dokunuşla sistem Wi‑Fi ayarı.
 */
class GoodNightRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("good_night_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun getHour(): Int = prefs.getInt(KEY_HOUR, 23)
    fun getMinute(): Int = prefs.getInt(KEY_MINUTE, 0)

    fun save(enabled: Boolean, hour: Int, minute: Int) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
        if (enabled) scheduleNext() else cancel()
    }

    fun scheduleNext() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val trigger = nextTriggerMs(getHour(), getMinute())
        val pi = pendingIntent()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                @Suppress("DEPRECATION")
                am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (_: Exception) {
            try {
                am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
            } catch (_: Exception) {
            }
        }
    }

    fun cancel() {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GoodNightReceiver::class.java).apply {
            action = GoodNightReceiver.ACTION_REMIND
        }
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val REQ = 7101

        fun nextTriggerMs(hour: Int, minute: Int): Long {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis() + 15_000) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            return cal.timeInMillis
        }
    }
}
