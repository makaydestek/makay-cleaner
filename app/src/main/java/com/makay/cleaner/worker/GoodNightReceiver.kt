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
                NotificationManager(context).sendGoodNightReminder()
                repo.scheduleNext()
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (repo.isEnabled()) repo.scheduleNext()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.makay.cleaner.GOOD_NIGHT_REMIND"

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
