package com.makay.cleaner.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveString(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defaultValue: String = ""): String {
        return encryptedPrefs.getString(key, defaultValue) ?: defaultValue
    }

    fun saveInt(key: String, value: Int) {
        encryptedPrefs.edit().putInt(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return encryptedPrefs.getInt(key, defaultValue)
    }

    fun saveBoolean(key: String, value: Boolean) {
        encryptedPrefs.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return encryptedPrefs.getBoolean(key, defaultValue)
    }

    fun remove(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun clear() {
        encryptedPrefs.edit().clear().apply()
    }
}