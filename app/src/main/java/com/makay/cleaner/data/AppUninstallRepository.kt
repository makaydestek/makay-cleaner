package com.makay.cleaner.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import com.makay.cleaner.domain.model.AppUninstallInfo
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppUninstallRepository(private val context: Context) {

    private val decimalFormat = DecimalFormat("#.##")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val packageManager = context.packageManager
    private val pendingPrefs = context.getSharedPreferences("pending_uninstall", Context.MODE_PRIVATE)

    /**
     * Tüm yüklü uygulamaları boyuta göre sıralı getirir
     */
    fun getAllApps(): List<AppUninstallInfo> {
        val apps = mutableListOf<AppUninstallInfo>()
        
        val installedApps = packageManager.getInstalledPackages(0)
        
        installedApps.forEach { packageInfo ->
            try {
                val appName = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() 
                    ?: packageInfo.packageName
                val packageName = packageInfo.packageName
                val isSystemApp = (packageInfo.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                val versionName = packageInfo.versionName ?: "Bilinmiyor"
                
                // Uygulama boyutunu hesapla
                val sizeBytes = calculateAppSize(packageName)
                
                // Kurulum tarihi
                val installDate = getInstallDate(packageName)
                
                apps.add(
                    AppUninstallInfo(
                        packageName = packageName,
                        appName = appName,
                        sizeBytes = sizeBytes,
                        sizeFormatted = formatBytes(sizeBytes),
                        isSystemApp = isSystemApp,
                        versionName = versionName,
                        installDate = installDate,
                        installDateFormatted = dateFormat.format(Date(installDate))
                    )
                )
            } catch (e: Exception) {
                // Bazı uygulamalar erişilemeyebilir
            }
        }
        
        // Boyuta göre sırala (büyükten küçüğe)
        return apps.sortedByDescending { it.sizeBytes }
    }

    /**
     * Uygulamanın toplam boyutunu hesaplar (StorageStatsManager öncelikli)
     */
    private fun calculateAppSize(packageName: String): Long {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val storageStatsManager =
                    context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val uuid = appInfo.storageUuid
                val stats = storageStatsManager.queryStatsForPackage(uuid, packageName, Process.myUserHandle())
                return stats.appBytes + stats.dataBytes + stats.cacheBytes
            } catch (_: Exception) {
            }
        }

        var totalSize = 0L
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val apk = File(appInfo.sourceDir)
            if (apk.exists()) totalSize += apk.length()
            appInfo.splitSourceDirs?.forEach { split ->
                val f = File(split)
                if (f.exists()) totalSize += f.length()
            }
            val external = File(Environment.getExternalStorageDirectory(), "Android/data/$packageName")
            totalSize += calculateDirSize(external)
        } catch (_: Exception) {
        }
        return totalSize
    }

    /**
     * Bir klasörün toplam boyutunu hesaplar (rekürsif)
     */
    private fun calculateDirSize(directory: File): Long {
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isFile) {
                    file.length()
                } else if (file.isDirectory) {
                    calculateDirSize(file)
                } else {
                    0L
                }
            }
        } catch (e: SecurityException) {
            // İzin hatası
        }
        return size
    }

    /**
     * Uygulamanın kurulum tarihini alır
     */
    private fun getInstallDate(packageName: String): Long {
        return try {
            val appInfo = packageManager.getPackageInfo(packageName, 0)
            appInfo.firstInstallTime
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    /**
     * Byte cinsinden boyutu okunabilir formata çevirir
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    /**
     * Uygulama kaldırma intent'i oluşturur
     */
    fun getUninstallIntent(packageName: String): Intent {
        markPendingUninstall(packageName)
        return Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    fun markPendingUninstall(packageName: String) {
        pendingPrefs.edit().putString("pending_package", packageName).apply()
    }

    fun getPendingUninstallPackage(): String? =
        pendingPrefs.getString("pending_package", null)

    fun clearPendingUninstall() {
        pendingPrefs.edit().remove("pending_package").apply()
    }

    /**
     * Kaldırılan uygulamanın harici kalıntı dosyalarını temizler.
     * @return silinen bayt miktarı
     */
    fun cleanLeftoverFiles(packageName: String): Pair<Long, Int> {
        // Sistem uygulamaları için kalıntı temizliği yapılmaz
        if (!SystemFileGuard.canCleanPackageCache(context, packageName)) {
            return 0L to 0
        }
        var spaceSaved = 0L
        var filesDeleted = 0
        val external = Environment.getExternalStorageDirectory()
        val leftoverRoots = listOf(
            File(external, "Android/data/$packageName"),
            File(external, "Android/obb/$packageName"),
            File(external, "Android/media/$packageName"),
            File(external, "Download/$packageName"),
            File(external, packageName)
        )

        leftoverRoots.forEach { root ->
            if (SystemFileGuard.isProtectedPath(root.absolutePath)) return@forEach
            val (saved, count) = deleteRecursivelyCounted(root)
            spaceSaved += saved
            filesDeleted += count
        }

        return spaceSaved to filesDeleted
    }

    /**
     * Bekleyen kaldırma tamamlandıysa kalıntıları temizler.
     */
    fun processPendingUninstallCleanup(): Pair<String, Pair<Long, Int>>? {
        val packageName = getPendingUninstallPackage() ?: return null
        val stillInstalled = try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

        if (stillInstalled) return null

        val result = cleanLeftoverFiles(packageName)
        clearPendingUninstall()
        if (result.first > 0 || result.second > 0) {
            CleaningRecorder.record(context, "Uygulama Kalıntıları", result.first, result.second)
        }
        return packageName to result
    }

    private fun deleteRecursivelyCounted(file: File?): Pair<Long, Int> {
        if (file == null || !file.exists()) return 0L to 0
        if (!SystemFileGuard.canDelete(file)) return 0L to 0
        var saved = 0L
        var count = 0
        try {
            if (file.isDirectory) {
                file.listFiles()?.forEach { child ->
                    val (s, c) = deleteRecursivelyCounted(child)
                    saved += s
                    count += c
                }
            } else {
                saved = file.length()
            }
            if (file.delete()) {
                if (!file.isDirectory) count++
            }
        } catch (_: Exception) {
        }
        return saved to count
    }
}