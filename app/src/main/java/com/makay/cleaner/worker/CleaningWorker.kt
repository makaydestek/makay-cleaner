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
import kotlinx.coroutines.withTimeout

class CleaningWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DO_CACHE = "do_cache"
        const val KEY_DO_APK = "do_apk"
        const val KEY_DO_TMP = "do_tmp"
        const val KEY_USE_TRASH = "use_trash"
        /** WorkManager sonsuz retry önleme. */
        const val MAX_ATTEMPTS = 3
        private const val WORK_TIMEOUT_MS = 90_000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            if (runAttemptCount >= MAX_ATTEMPTS) {
                notifyFailure("Otomatik temizlik birkaç denemeden sonra durduruldu.")
                return@withContext Result.failure()
            }

            val ids = mutableSetOf<String>()
            // Varsayılan: yalnızca önbellek (dar / güvenli).
            if (inputData.getBoolean(KEY_DO_CACHE, true)) ids += "cache"
            if (inputData.getBoolean(KEY_DO_APK, false)) ids += "apk"
            if (inputData.getBoolean(KEY_DO_TMP, false)) {
                ids += "tmp"
                ids += "residue"
            }
            if (ids.isEmpty()) return@withContext Result.success()

            val useTrash = inputData.getBoolean(KEY_USE_TRASH, true)

            val result = withTimeout(WORK_TIMEOUT_MS) {
                JunkScanHelper.cleanSelected(
                    applicationContext,
                    ids,
                    recordAs = "Otomatik Temizlik",
                    useTrash = useTrash,
                    reclaimSystemCache = "cache" in ids
                )
            }
            val after = withTimeout(WORK_TIMEOUT_MS) {
                JunkScanHelper.scan(applicationContext)
            }
            val score = JunkScanHelper.optimizationScore(after.cleanableBytes)

            if (PlatformManager.isTvMode(applicationContext)) {
                TvRecommendationManager.publishRecommendation(
                    applicationContext,
                    after.cleanableBytes,
                    score
                )
            } else if (
                result.filesDeleted > 0 ||
                result.spaceSaved > 0L ||
                result.movedToTrashBytes > 0L ||
                result.failedCount > 0 ||
                result.skippedProtected > 0
            ) {
                NotificationManager(applicationContext).sendCleaningComplete(
                    spaceSaved = result.spaceSaved,
                    deepLink = NotificationManager.DEEP_STATS,
                    filesDeleted = result.filesDeleted,
                    failedCount = result.failedCount,
                    skippedCount = result.skippedProtected,
                    trashedBytes = result.movedToTrashBytes
                )
            }

            maybeSendStorageWarning()
            Result.success()
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                notifyFailure("Otomatik temizlik zaman aşımına uğradı.")
                Result.failure()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) {
                notifyFailure("Otomatik temizlik başarısız oldu.")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private fun notifyFailure(message: String) {
        if (PlatformManager.isTvMode(applicationContext)) return
        runCatching {
            NotificationManager(applicationContext).sendCleaningFailed(message)
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
