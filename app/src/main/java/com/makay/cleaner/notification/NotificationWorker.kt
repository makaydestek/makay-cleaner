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
            
            // Temizlik hatırlatması gönder
            notificationManager.sendCleaningReminder()
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}