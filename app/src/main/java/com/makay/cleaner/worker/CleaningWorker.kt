package com.makay.cleaner.worker

import android.os.Environment
import android.os.StatFs
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.makay.cleaner.notification.NotificationManager
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PlatformManager
import com.makay.cleaner.util.TvRecommendationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CleaningWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DO_CACHE = "do_cache"
        const val KEY_DO_APK = "do_apk"
        const val KEY_DO_TMP = "do_tmp"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val ids = mutableSetOf<String>()
            if (inputData.getBoolean(KEY_DO_CACHE, true)) ids += "cache"
            if (inputData.getBoolean(KEY_DO_APK, true)) ids += "apk"
            if (inputData.getBoolean(KEY_DO_TMP, false)) {
                ids += "tmp"
                ids += "residue"
            }
            if (ids.isEmpty()) return@withContext Result.success()

            val result = JunkScanHelper.cleanSelected(
                applicationContext,
                ids,
                recordAs = "Otomatik Temizlik"
            )
            val after = JunkScanHelper.scan(applicationContext)
            val score = JunkScanHelper.optimizationScore(after.cleanableBytes)

            if (PlatformManager.isTvMode(applicationContext)) {
                TvRecommendationManager.publishRecommendation(
                    applicationContext,
                    after.cleanableBytes,
                    score
                )
            } else if (result.filesDeleted > 0 || result.spaceSaved > 0L) {
                NotificationManager(applicationContext).sendCleaningComplete(
                    result.spaceSaved,
                    deepLink = NotificationManager.DEEP_STATS
                )
            }

            maybeSendStorageWarning()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun maybeSendStorageWarning() {
        if (PlatformManager.isTvMode(applicationContext)) return
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
