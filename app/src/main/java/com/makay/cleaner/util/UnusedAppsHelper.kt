package com.makay.cleaner.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class UnusedAppInfo(
    val packageName: String,
    val label: String,
    val lastUsedMs: Long,
    val installTimeMs: Long,
    val isLikelyUnused: Boolean
)

/**
 * Kullanılmayan uygulama tahmini — yalnızca kurulum tarihi.
 * PACKAGE_USAGE_STATS / kısıtlı ayar istenmez (sideload güvenlik uyarısı olmaz).
 */
object UnusedAppsHelper {

    fun getUnusedApps(context: Context, unusedDays: Int = 60): List<UnusedAppInfo> {
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val threshold = now - unusedDays * 24L * 3600_000

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
                val unused = install > 0 && install < threshold
                UnusedAppInfo(info.packageName, label, 0L, install, unused)
            }
            .filter { it.isLikelyUnused }
            .sortedBy { it.installTimeMs }
            .toList()
    }

    /** Geriye uyumluluk — her zaman true (izin istemiyoruz). */
    fun hasUsageAccess(context: Context): Boolean = true
}
