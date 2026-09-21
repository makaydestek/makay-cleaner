package com.makay.cleaner.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import com.makay.cleaner.domain.model.AppCacheInfo
import java.io.File
import java.text.DecimalFormat

class AppCacheRepository(private val context: Context) {

    private val decimalFormat = DecimalFormat("#.##")
    private val packageManager = context.packageManager

    data class CacheFileEntry(
        val file: File,
        val appName: String,
        val packageName: String
    )

    /**
     * Erişilebilir önbellek: her zaman kendi cache;
     * diğer uygulamalar yalnızca MANAGE_EXTERNAL_STORAGE açıksa
     * (aksi halde listelenir ama silinemez → "bulunamadı" uyarısına yol açar).
     */
    fun scanAccessibleCacheFiles(): List<CacheFileEntry> {
        val result = mutableListOf<CacheFileEntry>()
        val ownPackage = context.packageName

        collectFiles(context.cacheDir, ownPackage, "Makay Cleaner", result)
        context.codeCacheDir?.let {
            collectFiles(it, ownPackage, "Makay Cleaner", result)
        }
        context.externalCacheDir?.let {
            collectFiles(it, ownPackage, "Makay Cleaner", result)
        }

        val canManageOthers = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
        if (canManageOthers) {
            val androidData = File(Environment.getExternalStorageDirectory(), "Android/data")
            if (androidData.exists() && androidData.canRead()) {
                androidData.listFiles()?.forEach { appDir ->
                    if (!appDir.isDirectory) return@forEach
                    val pkg = appDir.name
                    if (pkg == ownPackage) return@forEach
                    if (!com.makay.cleaner.util.SystemFileGuard.canCleanPackageCache(context, pkg)) {
                        return@forEach
                    }
                    val label = resolveAppLabel(pkg)
                    val cacheCandidates = listOf(
                        File(appDir, "cache"),
                        File(appDir, "code_cache"),
                        File(appDir, "files/cache"),
                        File(appDir, "files/.cache")
                    )
                    cacheCandidates.forEach { cacheDir ->
                        // Yalnızca yazılabilir (silinebilir) cache klasörleri
                        if (cacheDir.exists() && cacheDir.canWrite()) {
                            collectFiles(cacheDir, pkg, label, result)
                        }
                    }
                }
            }
        }

        return result
            .filter { com.makay.cleaner.util.SystemFileGuard.canDelete(it.file) }
            .filter { it.file.canWrite() || it.file.parentFile?.canWrite() == true }
            .sortedByDescending { it.file.length() }
    }

    fun getAllAppsWithCache(): List<AppCacheInfo> {
        val apps = mutableListOf<AppCacheInfo>()
        val installedApps = packageManager.getInstalledPackages(PackageManager.GET_META_DATA)

        installedApps.forEach { packageInfo ->
            try {
                val applicationInfo = packageInfo.applicationInfo ?: return@forEach
                val appName = applicationInfo.loadLabel(packageManager).toString()
                val packageName = packageInfo.packageName
                val cacheSize = calculateAppCacheSize(packageName)
                val isSystemApp = applicationInfo.flags and
                    android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0

                if (cacheSize > 0 || packageName == context.packageName) {
                    apps.add(
                        AppCacheInfo(
                            packageName = packageName,
                            appName = appName,
                            cacheSizeBytes = cacheSize,
                            cacheSizeFormatted = formatBytes(cacheSize),
                            isSystemApp = isSystemApp
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }

        return apps.sortedByDescending { it.cacheSizeBytes }
    }

    private fun calculateAppCacheSize(packageName: String): Long {
        var totalSize = 0L

        if (packageName == context.packageName) {
            totalSize += calculateDirSize(context.cacheDir)
            context.externalCacheDir?.let { totalSize += calculateDirSize(it) }
            totalSize += calculateDirSize(context.codeCacheDir)
        }

        try {
            val externalCache = File(
                Environment.getExternalStorageDirectory(),
                "Android/data/$packageName/cache"
            )
            totalSize += calculateDirSize(externalCache)
        } catch (_: Exception) {
        }

        return totalSize
    }

    private fun collectFiles(
        dir: File?,
        packageName: String,
        appName: String,
        result: MutableList<CacheFileEntry>
    ) {
        if (dir == null || !dir.exists() || !dir.canRead()) return
        try {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    result.add(CacheFileEntry(file, appName, packageName))
                } else if (file.isDirectory) {
                    collectFiles(file, packageName, appName, result)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun calculateDirSize(directory: File?): Long {
        if (directory == null || !directory.exists()) return 0L
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += when {
                    file.isFile -> file.length()
                    file.isDirectory -> calculateDirSize(file)
                    else -> 0L
                }
            }
        } catch (_: SecurityException) {
        }
        return size
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
