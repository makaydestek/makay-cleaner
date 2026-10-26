package com.makay.cleaner.util

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class PermissionAppRow(
    val packageName: String,
    val label: String,
    val criticalPermissions: List<String>,
    val lastUsedMs: Long,
    val suspicionScore: Int
)

/**
 * Yüklü uygulamaların kritik izinlerini listeler.
 * Aksiyon: yalnızca sistem uygulama ayarlarına yönlendirme (force-stop yok).
 */
object PermissionMapHelper {

    private val CRITICAL = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.GET_ACCOUNTS
    )

    fun permissionLabel(permission: String): String = when (permission) {
        Manifest.permission.CAMERA -> "Kamera"
        Manifest.permission.RECORD_AUDIO -> "Mikrofon"
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION -> "Konum"
        Manifest.permission.READ_CONTACTS -> "Kişiler"
        Manifest.permission.READ_SMS -> "SMS"
        Manifest.permission.READ_CALL_LOG -> "Arama kaydı"
        Manifest.permission.READ_PHONE_STATE -> "Telefon"
        Manifest.permission.GET_ACCOUNTS -> "Hesaplar"
        else -> permission.substringAfterLast('.')
    }

    fun scan(context: Context): List<PermissionAppRow> {
        val pm = context.packageManager
        val lastUsed = lastUsedMap(context)
        val now = System.currentTimeMillis()
        val unusedThreshold = now - 60L * 24 * 3600_000

        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .filter { it.packageName != context.packageName }
            .filter { !SystemFileGuard.isProtectedPackageName(it.packageName) }
            .mapNotNull { info ->
                val granted = grantedCritical(pm, info.packageName)
                if (granted.isEmpty()) return@mapNotNull null
                val label = try {
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    info.packageName
                }
                val last = lastUsed[info.packageName] ?: 0L
                var score = granted.size * 10
                if (last in 1 until unusedThreshold) score += 25
                if (granted.any { it.contains("LOCATION") || it == Manifest.permission.CAMERA }) score += 10
                if (granted.any { it == Manifest.permission.READ_SMS || it == Manifest.permission.READ_CALL_LOG }) {
                    score += 20
                }
                PermissionAppRow(info.packageName, label, granted.map { permissionLabel(it) }.distinct(), last, score)
            }
            .sortedByDescending { it.suspicionScore }
            .toList()
    }

    private fun grantedCritical(pm: PackageManager, packageName: String): List<String> {
        return CRITICAL.filter { perm ->
            try {
                pm.checkPermission(perm, packageName) == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun lastUsedMap(context: Context): Map<String, Long> {
        if (!UnusedAppsHelper.hasUsageAccess(context)) return emptyMap()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as? android.app.usage.UsageStatsManager ?: return emptyMap()
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_BEST,
                now - 90L * 24 * 3600_000,
                now
            ) ?: return emptyMap()
            stats.associate { it.packageName to it.lastTimeUsed }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
        try {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
