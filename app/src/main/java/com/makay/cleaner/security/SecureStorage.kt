@file:Suppress("DEPRECATION")

package com.makay.cleaner.security

import android.content.Context
import android.content.SharedPreferences
import com.makay.cleaner.util.SoftErrorLogger
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * PIN / kilit tercihleri için şifreli depo.
 * Keystore açılamazsa uygulama çökmez; düz SharedPreferences'e düşer ve
 * [isEncryptionAvailable] false olur. Bu durumda yeni PIN kurulumu reddedilir.
 *
 * Örnek süreç-geneli tekildir: aynı şifreli dosyaya birden fazla
 * EncryptedSharedPreferences örneği açmak eşzamanlı `edit()` kayıplarına
 * ve anahtar/önbellek tutarsızlığına yol açar.
 */
class SecureStorage(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        encryptedPrefs(appContext) ?: appContext.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)

    /** false ise Keystore/şifreleme kullanılamıyor. */
    val isEncryptionAvailable: Boolean get() = encryptedPrefs(appContext) != null

    fun saveString(key: String, value: String, commit: Boolean = false) {
        val e = prefs.edit().putString(key, value)
        if (commit) e.commit() else e.apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return runCatching { prefs.getString(key, defaultValue) }.getOrNull() ?: defaultValue
    }

    fun saveInt(key: String, value: Int, commit: Boolean = false) {
        val e = prefs.edit().putInt(key, value)
        if (commit) e.commit() else e.apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return runCatching { prefs.getInt(key, defaultValue) }.getOrNull() ?: defaultValue
    }

    fun saveBoolean(key: String, value: Boolean, commit: Boolean = false) {
        val e = prefs.edit().putBoolean(key, value)
        if (commit) e.commit() else e.apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return runCatching { prefs.getBoolean(key, defaultValue) }.getOrNull() ?: defaultValue
    }

    fun saveLong(key: String, value: Long, commit: Boolean = false) {
        val e = prefs.edit().putLong(key, value)
        if (commit) e.commit() else e.apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return runCatching { prefs.getLong(key, defaultValue) }.getOrNull() ?: defaultValue
    }

    fun remove(key: String, commit: Boolean = false) {
        val e = prefs.edit().remove(key)
        if (commit) e.commit() else e.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SecureStorage"
        private const val ENCRYPTED_FILE = "secure_prefs"
        private const val FALLBACK_FILE = "secure_prefs_fallback"

        @Volatile
        private var cachedEncrypted: SharedPreferences? = null

        @Volatile
        private var initAttempted = false

        private fun encryptedPrefs(context: Context): SharedPreferences? {
            if (initAttempted) return cachedEncrypted
            synchronized(this) {
                if (initAttempted) return cachedEncrypted
                cachedEncrypted = runCatching {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        ENCRYPTED_FILE,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }.onFailure { error ->
                    SoftErrorLogger.warn(
                        TAG,
                        "EncryptedSharedPreferences açılamadı; düz depo kullanılacak (degraded)",
                        error
                    )
                }.getOrNull()
                initAttempted = true
                return cachedEncrypted
            }
        }
    }
}
