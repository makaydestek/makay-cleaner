package com.makay.cleaner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * PIN / kilit tercihleri için şifreli depo.
 * Keystore açılamazsa uygulama çökmez; düz SharedPreferences’e düşer ve
 * [isEncryptionAvailable] false olur (UI uyarı gösterebilir).
 */
class SecureStorage(context: Context) {

    private val appContext = context.applicationContext

    private val encrypted: SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.onFailure {
        Log.w(TAG, "EncryptedSharedPreferences açılamadı; düz depo kullanılacak")
    }.getOrNull()

    private val prefs: SharedPreferences =
        encrypted ?: appContext.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)

    /** false ise Keystore/şifreleme kullanılamıyor. */
    val isEncryptionAvailable: Boolean get() = encrypted != null

    fun saveString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun saveInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun saveBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "SecureStorage"
        private const val ENCRYPTED_FILE = "secure_prefs"
        private const val FALLBACK_FILE = "secure_prefs_fallback"
    }
}
