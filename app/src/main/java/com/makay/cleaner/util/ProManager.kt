package com.makay.cleaner.util

import android.content.Context
import com.makay.cleaner.BuildConfig
import java.security.MessageDigest

/**
 * Pro kapısı. [BuildConfig.PRO_GATING_ENABLED]=false iken tüm özellikler ücretsizdir;
 * Pro ekranı ve kod girişi gizlenir.
 *
 * Açma kodu kaynakta düz metin tutulmaz; [BuildConfig.PRO_UNLOCK_CODE_SHA256]
 * (local.properties → PRO_UNLOCK_CODE_SHA256) ile karşılaştırılır.
 */
object ProManager {

    val PRO_GATING_ENABLED: Boolean = BuildConfig.PRO_GATING_ENABLED

    private const val PREFS = "pro_prefs"
    private const val KEY_PRO = "is_pro"

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
        val expected = BuildConfig.PRO_UNLOCK_CODE_SHA256.trim().lowercase()
        if (expected.length != 64) {
            SoftErrorLogger.warn("ProManager", "PRO_UNLOCK_CODE_SHA256 tanimli degil; unlock reddedildi")
            return false
        }
        val ok = sha256Hex(code.trim().uppercase()) == expected
        if (ok) setPro(context, true)
        return ok
    }

    fun requiresPro(feature: ProFeature, context: Context): Boolean =
        PRO_GATING_ENABLED && feature.proOnly && !isPro(context)

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    enum class ProFeature(val proOnly: Boolean) {
        SCHEDULE_ADVANCED(true),
        SIMILAR_PHOTOS(true),
        MEDIA_COMPRESS(true),
        DUPLICATES(true),
        SYSTEM_CLEANER_AUTO(true)
    }
}
