package com.makay.cleaner.data

import android.content.Context

/**
 * Temizlik ve tarama olaylarını analitik / istatistik / öneri metriklerine yazar.
 */
object CleaningRecorder {

    fun record(
        context: Context,
        featureName: String,
        spaceSaved: Long,
        filesDeleted: Int
    ) {
        if (spaceSaved <= 0 && filesDeleted <= 0) return
        val appContext = context.applicationContext
        try {
            AnalyticsRepository(appContext).recordCleaningEvent(featureName, spaceSaved.coerceAtLeast(0))
        } catch (_: Exception) {
        }
        try {
            CleaningStatsRepository(appContext).addCleaningRecord(
                spaceSaved = spaceSaved.coerceAtLeast(0),
                filesDeleted = filesDeleted.coerceAtLeast(0),
                category = featureName
            )
        } catch (_: Exception) {
        }
    }

    fun recordScan(
        context: Context,
        featureName: String,
        itemsFound: Int,
        bytesFound: Long
    ) {
        val appContext = context.applicationContext
        try {
            AnalyticsRepository(appContext).recordCleaningEvent("Tarama: $featureName", 0L)
        } catch (_: Exception) {
        }
        try {
            CleaningStatsRepository(appContext).addScanRecord(itemsFound, bytesFound, featureName)
        } catch (_: Exception) {
        }

        try {
            val prefs = appContext.getSharedPreferences("smart_suggestions", Context.MODE_PRIVATE)
            val editor = prefs.edit()
            when {
                featureName.contains("Önbellek", ignoreCase = true) ->
                    editor.putLong("cache_size", bytesFound)
                featureName.contains("Büyük", ignoreCase = true) ->
                    editor.putInt("large_files_count", itemsFound)
                featureName.contains("Sosyal", ignoreCase = true) ->
                    editor.putLong("social_media_size", bytesFound)
                featureName.contains("Depolama", ignoreCase = true) ->
                    editor.putLong("storage_scan_bytes", bytesFound)
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    fun updateUnusedAppsCount(context: Context, count: Int) {
        try {
            context.applicationContext
                .getSharedPreferences("smart_suggestions", Context.MODE_PRIVATE)
                .edit()
                .putInt("unused_apps_count", count)
                .apply()
        } catch (_: Exception) {
        }
    }
}
