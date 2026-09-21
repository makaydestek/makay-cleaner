package com.makay.cleaner.security

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

class AppLockManager(context: Context) {

    private val secureStorage = SecureStorage(context.applicationContext)
    private val legacyPrefs = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LAST_UNLOCK = "last_unlock_ms"
        const val RELOCK_TIMEOUT_MS = 60_000L
    }

    init {
        migrateFromLegacyIfNeeded()
    }

    private fun migrateFromLegacyIfNeeded() {
        if (secureStorage.getBoolean("migrated", false)) return
        val legacyHash = legacyPrefs.getString(KEY_PIN_HASH, null)
        if (legacyHash != null) {
            secureStorage.saveString(KEY_PIN_HASH, legacyHash)
            secureStorage.saveBoolean(KEY_PIN_ENABLED, legacyPrefs.getBoolean(KEY_PIN_ENABLED, false))
            secureStorage.saveBoolean(KEY_BIOMETRIC_ENABLED, legacyPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
            legacyPrefs.edit().clear().apply()
        }
        secureStorage.saveBoolean("migrated", true)
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest((salt + pin).toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun setPin(pin: String) {
        val salt = generateSalt()
        secureStorage.saveString(KEY_PIN_SALT, salt)
        secureStorage.saveString(KEY_PIN_HASH, hashPin(pin, salt))
        secureStorage.saveBoolean(KEY_PIN_ENABLED, true)
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = secureStorage.getString(KEY_PIN_HASH)
        if (savedHash.isEmpty()) return false
        val salt = secureStorage.getString(KEY_PIN_SALT)
        val ok = if (salt.isNotEmpty()) {
            hashPin(pin, salt) == savedHash
        } else {
            // Eski saltsız hash uyumluluğu
            hashPinLegacy(pin) == savedHash
        }
        if (ok) markUnlocked()
        return ok
    }

    private fun hashPinLegacy(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun removePin() {
        secureStorage.remove(KEY_PIN_HASH)
        secureStorage.remove(KEY_PIN_SALT)
        secureStorage.saveBoolean(KEY_PIN_ENABLED, false)
    }

    fun isPinEnabled(): Boolean = secureStorage.getBoolean(KEY_PIN_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        secureStorage.saveBoolean(KEY_BIOMETRIC_ENABLED, enabled)
    }

    fun isBiometricEnabled(): Boolean = secureStorage.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun isAnyLockEnabled(): Boolean = isPinEnabled() || isBiometricEnabled()

    fun markUnlocked() {
        secureStorage.saveString(KEY_LAST_UNLOCK, System.currentTimeMillis().toString())
    }

    fun shouldLock(): Boolean {
        if (!isAnyLockEnabled()) return false
        val last = secureStorage.getString(KEY_LAST_UNLOCK).toLongOrNull() ?: 0L
        if (last == 0L) return true
        return System.currentTimeMillis() - last > RELOCK_TIMEOUT_MS
    }
}
