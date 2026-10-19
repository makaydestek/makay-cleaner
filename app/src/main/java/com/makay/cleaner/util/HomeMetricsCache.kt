package com.makay.cleaner.util

import android.content.Context

/** Ana ekran / widget arasında hafif skor önbelleği (ağır tarama yok). */
object HomeMetricsCache {
    private const val PREFS = "makay_home_metrics"
    private const val KEY_SCORE = "score"
    private const val KEY_JUNK = "junk_bytes"
    private const val KEY_USAGE = "usage_percent"

    fun save(context: Context, score: Int, junkBytes: Long, usagePercent: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SCORE, score.coerceIn(0, 100))
            .putLong(KEY_JUNK, junkBytes.coerceAtLeast(0L))
            .putFloat(KEY_USAGE, usagePercent.coerceIn(0f, 100f))
            .apply()
    }

    fun score(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_SCORE, 100)

    fun junkBytes(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_JUNK, 0L)
}
