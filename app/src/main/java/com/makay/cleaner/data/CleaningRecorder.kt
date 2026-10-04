package com.makay.cleaner.data

import android.content.Context

/**
 * Temizlik ve tarama olaylarını analitik / istatistik / öneri metriklerine yazar.
 */
object CleaningRecorder {

    const val METRIC_CACHE = "cache"
    const val METRIC_LARGE = "large"
    const val METRIC_SOCIAL = "social"
    const val METRIC_STORAGE = "storage"

    /** Öneri anahtarı. Metrik kimliği varsa Türkçe ada bakılmaz. */
    internal fun suggestionKey(metric: String?, featureName: String): String? = when (metric) {
        METRIC_CACHE -> "cache_size"
        METRIC_LARGE -> "large_files_count"
        METRIC_SOCIAL -> "social_media_size"
        METRIC_STORAGE -> "storage_scan_bytes"
        else -> when {
            featureName.contains("\u00D6nbelle", ignoreCase = true) -> "cache_size"
            featureName.contains("B\u00FCy\u00FCk", ignoreCase = true) -> "large_files_count"
            featureName.contains("Sosyal", ignoreCase = true) -> "social_media_size"
            featureName.contains("Depolama", ignoreCase = true) -> "storage_scan_bytes"
            else -> null
        }
    }

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
        bytesFound: Long,
        metric: String? = null
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
            val key = suggestionKey(metric, featureName)
            if (key != null) {
                val prefs = appContext.getSharedPreferences("smart_suggestions", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                when (key) {
                    "large_files_count" -> editor.putInt(key, itemsFound)
                    else -> editor.putLong(key, bytesFound)
                }
                editor.apply()
            }
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
