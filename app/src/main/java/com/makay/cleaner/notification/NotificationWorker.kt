package com.makay.cleaner.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val notificationManager = NotificationManager(applicationContext)
            val usage = runCatching {
                val path = android.os.Environment.getDataDirectory()
                val stat = android.os.StatFs(path.path)
                val used = stat.totalBytes - stat.freeBytes
                ((used.toFloat() / stat.totalBytes.toFloat()) * 100f).toInt()
            }.getOrNull()
            notificationManager.sendCleaningReminder(usage)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}