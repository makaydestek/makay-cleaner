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
import com.makay.cleaner.util.QuickCleanHelper
import java.text.DecimalFormat

class CleanerWidgetProvider : AppWidgetProvider() {

    private val decimalFormat = DecimalFormat("#.##")

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
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLEAN) {
            val result = QuickCleanHelper.cleanAppCaches(context)
            Toast.makeText(
                context,
                if (result.filesDeleted > 0)
                    "Temizlendi: ${formatBytes(result.spaceSaved)}"
                else
                    "Temizlenecek dosya yok",
                Toast.LENGTH_SHORT
            ).show()
            refreshAll(context)
        }
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
        views.setTextViewText(R.id.tv_usage_percent, "${storageInfo.usagePercent.toInt()}% Dolu")
        views.setProgressBar(R.id.progress_storage, 100, storageInfo.usagePercent.toInt(), false)

        views.setOnClickPendingIntent(
            R.id.tv_usage_percent,
            deepLinkPending(context, appWidgetId, NotificationManager.DEEP_CACHE)
        )
        views.setOnClickPendingIntent(
            R.id.btn_open_media,
            deepLinkPending(context, appWidgetId + 200, NotificationManager.DEEP_MEDIA)
        )
        views.setOnClickPendingIntent(
            R.id.btn_open_toolbox,
            deepLinkPending(context, appWidgetId + 300, NotificationManager.DEEP_TOOLBOX)
        )
        val cleanIntent = Intent(context, CleanerWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CLEAN
        }
        val cleanPending = PendingIntent.getBroadcast(
            context,
            appWidgetId + 1000,
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
        val freeBytes = stat.freeBytes
        val usedBytes = totalBytes - freeBytes
        val usagePercent = (usedBytes.toFloat() / totalBytes.toFloat()) * 100
        return StorageData(
            usedFormatted = formatBytes(usedBytes),
            freeFormatted = formatBytes(freeBytes),
            usagePercent = usagePercent
        )
    }

    private fun formatBytes(bytes: Long): String {
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
