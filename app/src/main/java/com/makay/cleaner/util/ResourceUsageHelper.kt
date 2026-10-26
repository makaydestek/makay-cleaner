package com.makay.cleaner.util

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

data class ResourceAppRow(
    val packageName: String,
    val label: String,
    val totalForegroundMs: Long,
    val lastUsedMs: Long
)

/**
 * UsageStats ile ön planda en çok zaman harcayan uygulamalar.
 * Hibernasyon: yalnızca sistem pil/uygulama ayarlarına yönlendirir.
 */
object ResourceUsageHelper {

    fun topConsumers(context: Context, limit: Int = 25): List<ResourceAppRow> {
        if (!UnusedAppsHelper.hasUsageAccess(context)) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val stats = try {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 7L * 24 * 3600_000, now)
                ?: emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return stats
            .asSequence()
            .filter { it.totalTimeInForeground > 0 }
            .filter { it.packageName != context.packageName }
            .filter { !SystemFileGuard.isProtectedPackageName(it.packageName) }
            .filter { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg.packageName, 0)
                    (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                } catch (_: Exception) {
                    false
                }
            }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
            .map { st ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(st.packageName, 0)).toString()
                } catch (_: Exception) {
                    st.packageName
                }
                ResourceAppRow(st.packageName, label, st.totalTimeInForeground, st.lastTimeUsed)
            }
            .toList()
    }

    fun formatDuration(ms: Long): String {
        val min = ms / 60_000
        return when {
            min < 1 -> "<1 dk"
            min < 60 -> "$min dk"
            else -> "${min / 60} sa ${min % 60} dk"
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
        PermissionMapHelper.openAppDetails(context, packageName)
    }

    fun openBatteryOptimization(context: Context, packageName: String? = null) {
        try {
            if (packageName != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                return
            }
        } catch (_: Exception) {
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
