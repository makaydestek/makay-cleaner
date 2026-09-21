package com.makay.cleaner.util

import android.content.Context

/**
 * Pro kapısı. PRO_GATING_ENABLED=false iken tüm özellikler ücretsizdir;
 * Pro ekranı ve kod girişi gizlenir. İleride tekrar açmak için true yapın.
 */
object ProManager {

    /** false = MAKAY-PRO pasif; herkes full özellik kullanır. */
    const val PRO_GATING_ENABLED = false

    private const val PREFS = "pro_prefs"
    private const val KEY_PRO = "is_pro"
    private const val UNLOCK_CODE = "MAKAY-PRO"

    fun isProUiVisible(): Boolean = PRO_GATING_ENABLED

    fun isPro(context: Context): Boolean {
        if (!PRO_GATING_ENABLED) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO, false)
    }

    fun setPro(context: Context, enabled: Boolean) {
        if (!PRO_GATING_ENABLED) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRO, enabled)
            .apply()
    }

    fun unlockWithCode(context: Context, code: String): Boolean {
        if (!PRO_GATING_ENABLED) return true
        val ok = code.trim().equals(UNLOCK_CODE, ignoreCase = true)
        if (ok) setPro(context, true)
        return ok
    }

    fun requiresPro(feature: ProFeature, context: Context): Boolean =
        PRO_GATING_ENABLED && feature.proOnly && !isPro(context)

    enum class ProFeature(val proOnly: Boolean) {
        SCHEDULE_ADVANCED(true),
        SIMILAR_PHOTOS(true),
        MEDIA_COMPRESS(true),
        DUPLICATES(true),
        SYSTEM_CLEANER_AUTO(true)
    }
}
