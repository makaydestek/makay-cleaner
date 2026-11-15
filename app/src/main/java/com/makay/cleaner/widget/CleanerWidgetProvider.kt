package com.makay.cleaner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.widget.RemoteViews
import android.widget.Toast
import com.makay.cleaner.MainActivity
import com.makay.cleaner.R
import com.makay.cleaner.notification.NotificationManager
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PlatformManager
import com.makay.cleaner.util.TvRecommendationManager
import java.text.DecimalFormat

class CleanerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_CLEAN) {
            val pending = goAsync()
            Thread {
                try {
                    if (PlatformManager.isTvMode(context)) {
                        val report = JunkScanHelper.scan(context)
                        val score = JunkScanHelper.optimizationScore(report.cleanableBytes)
                        TvRecommendationManager.publishRecommendation(context, report.cleanableBytes, score)
                    } else {
                        val result = JunkScanHelper.cleanSelected(
                            context,
                            setOf("cache"),
                            recordAs = context.getString(R.string.record_widget),
                            useTrash = false,
                            reclaimSystemCache = true
                        )
                        val text = if (result.spaceSaved > 0L) {
                            context.getString(R.string.widget_freed, formatBytes(result.spaceSaved))
                        } else {
                            context.getString(R.string.widget_nothing)
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                        }
                    }
                    refreshAll(context)
                } catch (t: Throwable) {
                    com.makay.cleaner.util.SoftErrorLogger.warn("Widget", "hızlı temizlik başarısız", t)
                } finally {
                    pending.finish()
                }
            }.start()
            return
        }
        super.onReceive(context, intent)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_cleaner)
        val storageInfo = getStorageInfo()

        views.setTextViewText(R.id.tv_used_storage, storageInfo.usedFormatted)
        views.setTextViewText(R.id.tv_free_storage, storageInfo.freeFormatted)
        views.setTextViewText(R.id.tv_usage_percent, context.getString(R.string.widget_percent_full, storageInfo.usagePercent.toInt()))
        views.setProgressBar(R.id.progress_storage, 100, storageInfo.usagePercent.toInt(), false)

        // Çakışmasız requestCode tahsisi: her widget için 10'luk blok.
        val base = appWidgetId * 10
        views.setOnClickPendingIntent(
            R.id.tv_usage_percent,
            deepLinkPending(context, base + 1, NotificationManager.DEEP_CACHE)
        )
        views.setOnClickPendingIntent(
            R.id.btn_open_media,
            deepLinkPending(context, base + 2, NotificationManager.DEEP_MEDIA)
        )
        views.setOnClickPendingIntent(
            R.id.btn_open_toolbox,
            deepLinkPending(context, base + 3, NotificationManager.DEEP_TOOLBOX)
        )
        val cleanIntent = Intent(context, CleanerWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CLEAN
        }
        val cleanPending = PendingIntent.getBroadcast(
            context,
            base + 4,
            cleanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btn_quick_clean, cleanPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun deepLinkPending(context: Context, requestCode: Int, deepLink: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationManager.EXTRA_DEEP_LINK, deepLink)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getStorageInfo(): StorageData {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0)
        val usagePercent = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) * 100 else 0f
        return StorageData(
            usedFormatted = formatBytes(usedBytes),
            freeFormatted = formatBytes(freeBytes),
            usagePercent = usagePercent
        )
    }

    private fun formatBytes(bytes: Long): String {
        // DecimalFormat thread-safe değil; her çağrıda yerel örnek.
        val decimalFormat = DecimalFormat("#.##")
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    data class StorageData(
        val usedFormatted: String,
        val freeFormatted: String,
        val usagePercent: Float
    )

    companion object {
        const val ACTION_WIDGET_CLEAN = "com.makay.cleaner.WIDGET_QUICK_CLEAN"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CleanerWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, CleanerWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
