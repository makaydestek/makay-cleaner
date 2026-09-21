package com.makay.cleaner.worker

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.notification.NotificationManager
import com.makay.cleaner.scanner.SystemCleanerScanner
import com.makay.cleaner.util.QuickCleanHelper
import com.makay.cleaner.util.SystemFileGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CleaningWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DO_CACHE = "do_cache"
        const val KEY_DO_APK = "do_apk"
        const val KEY_DO_TMP = "do_tmp"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val doCache = inputData.getBoolean(KEY_DO_CACHE, true)
            val doApk = inputData.getBoolean(KEY_DO_APK, true)
            val doTmp = inputData.getBoolean(KEY_DO_TMP, false)

            var totalSaved = 0L
            var totalDeleted = 0

            if (doCache) {
                val quick = QuickCleanHelper.cleanAppCaches(applicationContext)
                val external = cleanAccessibleUserCaches()
                totalSaved += quick.spaceSaved + external.first
                totalDeleted += quick.filesDeleted + external.second
            }
            if (doApk) {
                val downloads = cleanOldApksInDownloads()
                totalSaved += downloads.first
                totalDeleted += downloads.second
            }
            if (doTmp) {
                val junk = cleanTempJunk()
                totalSaved += junk.first
                totalDeleted += junk.second
            }

            if (totalDeleted > 0) {
                CleaningRecorder.record(
                    applicationContext,
                    "Otomatik Temizlik",
                    totalSaved,
                    totalDeleted
                )
                NotificationManager(applicationContext).sendCleaningComplete(
                    totalSaved,
                    deepLink = NotificationManager.DEEP_STATS
                )
            }

            maybeSendStorageWarning()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun cleanAccessibleUserCaches(): Pair<Long, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            return 0L to 0
        }
        var saved = 0L
        var count = 0
        try {
            val androidData = File(Environment.getExternalStorageDirectory(), "Android/data")
            androidData.listFiles()?.forEach { appDir ->
                val pkg = appDir.name
                if (!SystemFileGuard.canCleanPackageCache(applicationContext, pkg)) return@forEach
                val cache = File(appDir, "cache")
                if (!cache.exists() || !cache.canWrite()) return@forEach
                if (SystemFileGuard.isProtectedPath(cache.absolutePath)) return@forEach

                cache.listFiles()?.forEach { file ->
                    try {
                        if (!SystemFileGuard.canDelete(file)) return@forEach
                        val size = if (file.isFile) file.length() else dirSize(file)
                        if (file.deleteRecursively()) {
                            saved += size
                            count++
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return saved to count
    }

    private fun cleanOldApksInDownloads(): Pair<Long, Int> {
        var saved = 0L
        var count = 0
        val trash = RecycleBinRepository(applicationContext)
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) return 0L to 0
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            downloads.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach
                if (!file.extension.equals("apk", true)) return@forEach
                if (file.lastModified() >= cutoff) return@forEach
                if (!SystemFileGuard.canDelete(file)) return@forEach
                val size = file.length()
                if (trash.moveToTrash(file, "APK otomatik")) {
                    saved += size
                    count++
                }
            }
        } catch (_: Exception) {
        }
        return saved to count
    }

    private fun cleanTempJunk(): Pair<Long, Int> {
        var saved = 0L
        var count = 0
        val trash = RecycleBinRepository(applicationContext)
        try {
            SystemCleanerScanner().scan(maxItems = 400).forEach { item ->
                if (item.reason.contains("Geçici") || item.reason.contains("Log") || item.reason.contains("Küçük")) {
                    if (item.file.isFile && trash.moveToTrash(item.file, item.reason)) {
                        saved += item.sizeBytes
                        count++
                    }
                }
            }
        } catch (_: Exception) {
        }
        return saved to count
    }

    private fun dirSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isFile) f.length() else dirSize(f)
        }
        return size
    }

    private fun maybeSendStorageWarning() {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val total = stat.totalBytes.toDouble()
            val free = stat.availableBytes.toDouble()
            val usedPercent = if (total > 0) (((total - free) / total) * 100).toInt() else 0
            if (usedPercent >= 90) {
                NotificationManager(applicationContext).sendStorageWarning(
                    usagePercent = usedPercent,
                    deepLink = NotificationManager.DEEP_CACHE
                )
            }
        } catch (_: Exception) {
        }
    }
}
