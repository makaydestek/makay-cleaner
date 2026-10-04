package com.makay.cleaner.util

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

data class UnusedAppInfo(
    val packageName: String,
    val label: String,
    val lastUsedMs: Long,
    val installTimeMs: Long,
    val isLikelyUnused: Boolean
)

/**
 * Kullanılmayan uygulama tahmini.
 * Kullanım istatistiği yalnızca zaten verilmişse okunur; sideload uyarısı için izin istenmez.
 */
object UnusedAppsHelper {

    fun getUnusedApps(context: Context, unusedDays: Int = 60): List<UnusedAppInfo> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val threshold = now - unusedDays * 24L * 3600_000
        val lastUsedMap = lastUsedMap(context)

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .filter { !SystemFileGuard.isProtectedPackageName(it.packageName) }
            .map { info ->
                val label = try {
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    info.packageName
                }
                val install = try {
                    pm.getPackageInfo(info.packageName, 0).firstInstallTime
                } catch (_: Exception) {
                    0L
                }
                val lastUsed = lastUsedMap[info.packageName] ?: 0L
                val unused = if (lastUsed > 0L) {
                    lastUsed < threshold
                } else {
                    install > 0 && install < threshold
                }
                UnusedAppInfo(info.packageName, label, lastUsed, install, unused)
            }
            .filter { it.isLikelyUnused }
            .sortedBy { if (it.lastUsedMs > 0) it.lastUsedMs else it.installTimeMs }
            .toList()
    }

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= 29) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun lastUsedMap(context: Context): Map<String, Long> {
        if (!hasUsageAccess(context)) return emptyMap()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                now - 90L * 24 * 3600_000,
                now
            ) ?: return emptyMap()
            stats.associate { it.packageName to it.lastTimeUsed }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
