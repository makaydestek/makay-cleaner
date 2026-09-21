package com.makay.cleaner.data

import android.content.Context

/**
 * Sosyal medya otomatik temizlik eşiği (bayt).
 */
class SocialMediaPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("social_media_prefs", Context.MODE_PRIVATE)

    fun getAutoCleanThresholdMb(): Int = prefs.getInt("auto_threshold_mb", 200)

    fun setAutoCleanThresholdMb(mb: Int) {
        prefs.edit().putInt("auto_threshold_mb", mb.coerceIn(50, 2000)).apply()
    }

    fun shouldSuggestClean(totalBytes: Long): Boolean {
        return totalBytes >= getAutoCleanThresholdMb() * 1024L * 1024L
    }
}
