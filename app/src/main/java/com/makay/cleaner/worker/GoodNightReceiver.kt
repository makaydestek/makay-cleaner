package com.makay.cleaner.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.makay.cleaner.data.GoodNightRepository
import com.makay.cleaner.notification.NotificationManager

/**
 * İyi Geceler hatırlatması + boot sonrası yeniden planlama.
 */
class GoodNightReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val repo = GoodNightRepository(context)
        when (action) {
            ACTION_REMIND -> {
                if (!repo.isEnabled()) return
                try {
                    NotificationManager(context).sendGoodNightReminder()
                } catch (_: Exception) {
                } finally {
                    // Bildirim başarısız olsa bile bir sonraki alarm kurulmalı.
                    repo.scheduleNext()
                }
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // Saat / saat dilimi değişince hedef saat kaymasın.
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_EXACT_ALARM_PERMISSION_CHANGED -> {
                if (repo.isEnabled()) repo.scheduleNext()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.makay.cleaner.GOOD_NIGHT_REMIND"
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        fun openWifiSettings(context: Context) {
            val actions = listOf(
                Settings.ACTION_WIFI_SETTINGS,
                Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS
            )
            for (a in actions) {
                try {
                    context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return
                } catch (_: Exception) {
                }
            }
        }
    }
}
