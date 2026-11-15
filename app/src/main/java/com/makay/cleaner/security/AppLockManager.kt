package com.makay.cleaner.security

import android.content.Context
import android.os.SystemClock
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class AppLockManager(context: Context) {

    private val appContext = context.applicationContext
    private val secureStorage = SecureStorage(appContext)
    private val legacyPrefs = context.getSharedPreferences("app_lock_prefs", Context.MODE_PRIVATE)
    /** Şifreleme çökse bile "kilit kuruluydu" bilgisini taşıyan kurcalanamaz işaret. */
    private val guardPrefs = context.getSharedPreferences("app_lock_guard", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_ENABLED = "pin_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH_VERSION = "pin_hash_version"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_LAST_UNLOCK = "last_unlock_elapsed"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until_elapsed"
        private const val GUARD_CONFIGURED = "lock_configured"

        private const val HASH_VERSION_PBKDF2 = 2
        private const val LEGACY_HASH_VERSION = 1
        const val RELOCK_TIMEOUT_MS = 60_000L
        const val MIN_PIN_LENGTH = 6
        private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        private const val LOCKOUT_STEP_MS = 30_000L
        private const val LOCKOUT_MAX_MS = 15 * 60_000L
    }

    init {
        migrateFromLegacyIfNeeded()
    }

    private fun migrateFromLegacyIfNeeded() {
        if (secureStorage.getBoolean("migrated", false)) return
        val legacyHash = legacyPrefs.getString(KEY_PIN_HASH, null)
        if (legacyHash != null) {
            secureStorage.saveString(KEY_PIN_HASH, legacyHash)
            secureStorage.saveString(KEY_PIN_HASH_VERSION, LEGACY_HASH_VERSION.toString())
            secureStorage.saveBoolean(KEY_PIN_ENABLED, legacyPrefs.getBoolean(KEY_PIN_ENABLED, false))
            secureStorage.saveBoolean(KEY_BIOMETRIC_ENABLED, legacyPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false))
            if (legacyPrefs.getBoolean(KEY_PIN_ENABLED, false)) markGuardConfigured()
            legacyPrefs.edit().clear().apply()
        }
        secureStorage.saveBoolean("migrated", true, commit = true)
    }

    private fun hashPin(pin: String, salt: String): String = PinHash.derive(pin, salt)

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Şifreli depo yokken yeni PIN kurulumu güvenilir değildir.
     * @return false ise degraded mode; UI kullanıcıyı uyarmalı
     */
    fun setPin(pin: String): Boolean {
        if (isStorageDegraded()) return false
        if (pin.length < MIN_PIN_LENGTH || pin.any { !it.isDigit() }) return false
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        // Tek edit + commit: süreç arada ölürse "hash var / enabled yok" gibi
        // tutarsız durum oluşmasın.
        val ok = try {
            val prefs = secureStorage
            prefs.saveString(KEY_PIN_SALT, salt)
            prefs.saveString(KEY_PIN_HASH, hash)
            prefs.saveString(KEY_PIN_HASH_VERSION, HASH_VERSION_PBKDF2.toString())
            prefs.saveBoolean(KEY_PIN_ENABLED, true, commit = true)
            prefs.getBoolean(KEY_PIN_ENABLED, false) && prefs.getString(KEY_PIN_HASH).isNotEmpty()
        } catch (_: Exception) {
            false
        }
        if (ok) {
            markGuardConfigured()
            secureStorage.saveInt(KEY_FAILED_ATTEMPTS, 0, commit = true)
            secureStorage.saveLong(KEY_LOCKOUT_UNTIL, 0L, commit = true)
        }
        return ok
    }

    fun verifyPin(pin: String): Boolean {
        if (isLockedOut()) return false
        val savedHash = secureStorage.getString(KEY_PIN_HASH)
        if (savedHash.isEmpty()) return false
        val salt = secureStorage.getString(KEY_PIN_SALT)
        // Salt yoksa doğrulamayı reddet: salt'sız (eski) hash'e sessiz düşüş
        // kilit atlatma yoludur. Kullanıcı PIN'i yeniden kurmalı.
        if (salt.isEmpty()) {
            registerFailure()
            return false
        }
        val version = secureStorage.getInt(KEY_PIN_HASH_VERSION, LEGACY_HASH_VERSION)
        val ok = when {
            savedHash.startsWith("pbkdf2$") -> PinHash.verify(pin, salt, savedHash)
            version >= HASH_VERSION_PBKDF2 -> PinHash.verify(pin, salt, savedHash)
            else -> {
                // Eski tek-tur SHA-256 doğrulaması; başarılıysa PBKDF2'ye yükselt.
                val legacyOk = PinHash.withSalt(pin, salt) == savedHash
                if (legacyOk) {
                    secureStorage.saveString(KEY_PIN_HASH, hashPin(pin, salt))
                    secureStorage.saveString(KEY_PIN_HASH_VERSION, HASH_VERSION_PBKDF2.toString(), commit = true)
                }
                legacyOk
            }
        }
        if (ok) {
            secureStorage.saveInt(KEY_FAILED_ATTEMPTS, 0, commit = true)
            secureStorage.saveLong(KEY_LOCKOUT_UNTIL, 0L, commit = true)
            markUnlocked()
        } else {
            registerFailure()
        }
        return ok
    }

    /** Eski (salt'sız) hash uyumluluğu yalnızca migration için; aktif akışta kullanılmaz. */
    @Suppress("unused")
    private fun hashPinLegacy(pin: String): String = PinHash.legacy(pin)

    fun removePin() {
        secureStorage.remove(KEY_PIN_HASH, commit = true)
        secureStorage.remove(KEY_PIN_SALT)
        secureStorage.remove(KEY_PIN_HASH_VERSION)
        secureStorage.saveBoolean(KEY_PIN_ENABLED, false, commit = true)
        secureStorage.saveBoolean(KEY_BIOMETRIC_ENABLED, false)
        secureStorage.saveInt(KEY_FAILED_ATTEMPTS, 0)
        secureStorage.saveLong(KEY_LOCKOUT_UNTIL, 0L)
        guardPrefs.edit().remove(GUARD_CONFIGURED).commit()
    }

    /** Kilit verisi kaybolduğunda cihaz kilidi ile doğrulandıktan sonra guard'ı kaldır. */
    fun clearGuardAfterDeviceAuth() {
        guardPrefs.edit().remove(GUARD_CONFIGURED).commit()
        secureStorage.saveBoolean(KEY_PIN_ENABLED, false, commit = true)
    }

    fun isPinEnabled(): Boolean = secureStorage.getBoolean(KEY_PIN_ENABLED, false)

    /**
     * PIN daha önce kurulmuş ama şifreli depo okunamıyorsa true.
     * Bu durumda uygulama fail-closed davranır (kilitsiz açılmaz).
     */
    fun isGuardLocked(): Boolean =
        !isPinEnabled() && guardPrefs.getBoolean(GUARD_CONFIGURED, false)

    /** Fail-closed: kilit verisi kaybolsa bile kilitli say. */
    fun isAnyLockEnabled(): Boolean = isPinEnabled() || isGuardLocked()

    /**
     * Biyometrik yalnızca PIN açıkken etkinleştirilebilir (biyometrik-only kilit yasak).
     * @return true ise yeni durum kaydedildi
     */
    fun setBiometricEnabled(enabled: Boolean): Boolean {
        if (enabled && !isPinEnabled()) return false
        secureStorage.saveBoolean(KEY_BIOMETRIC_ENABLED, enabled)
        return true
    }

    fun isBiometricEnabled(): Boolean =
        isPinEnabled() && secureStorage.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    /** Şifreli depo kullanılamıyorsa true — UI uyarı göstermeli. */
    fun isStorageDegraded(): Boolean = !secureStorage.isEncryptionAvailable

    fun markUnlocked() {
        // Monotonik saat: duvar saati geri alınarak yeniden kilitlenme atlatılamaz.
        secureStorage.saveLong(KEY_LAST_UNLOCK, SystemClock.elapsedRealtime(), commit = true)
    }

    fun shouldLock(): Boolean {
        if (!isAnyLockEnabled()) return false
        if (isGuardLocked()) return true
        val last = secureStorage.getLong(KEY_LAST_UNLOCK, 0L)
        if (last == 0L) return true
        val now = SystemClock.elapsedRealtime()
        // elapsedRealtime yeniden başlarsa (cihaz yeniden açıldı) negatif fark -> kilitli.
        val elapsed = now - last
        if (elapsed < 0) return true
        return elapsed > RELOCK_TIMEOUT_MS
    }

    /** Yanlış deneme sonrası kalan bekleme (ms). 0 ise kilit yok. */
    fun lockoutRemainingMs(): Long {
        val until = secureStorage.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (until <= 0L) return 0L
        val remain = until - SystemClock.elapsedRealtime()
        return remain.coerceAtLeast(0L)
    }

    fun isLockedOut(): Boolean = lockoutRemainingMs() > 0L

    private fun registerFailure() {
        val attempts = secureStorage.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        secureStorage.saveInt(KEY_FAILED_ATTEMPTS, attempts, commit = true)
        if (attempts % MAX_ATTEMPTS_BEFORE_LOCKOUT == 0) {
            val steps = attempts / MAX_ATTEMPTS_BEFORE_LOCKOUT
            val backoff = (LOCKOUT_STEP_MS * steps).coerceAtMost(LOCKOUT_MAX_MS)
            secureStorage.saveLong(
                KEY_LOCKOUT_UNTIL,
                SystemClock.elapsedRealtime() + backoff,
                commit = true
            )
        }
    }

    private fun markGuardConfigured() {
        guardPrefs.edit().putBoolean(GUARD_CONFIGURED, true).commit()
    }
}

internal object PinHash {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    /** Yeni format: `pbkdf2$<iterasyon>$<hex>` (salt ayrı alanda tutulur). */
    fun derive(pin: String, salt: String, iterations: Int = ITERATIONS): String {
        val hex = pbkdf2Hex(pin, salt, iterations)
        return "pbkdf2\$$iterations\$$hex"
    }

    fun verify(pin: String, salt: String, stored: String): Boolean {
        if (!stored.startsWith("pbkdf2$")) return false
        val parts = stored.split('$')
        if (parts.size != 3) return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val expected = parts[2]
        val actual = pbkdf2Hex(pin, salt, iterations)
        return constantTimeEquals(actual, expected)
    }

    /** Eski (tek tur SHA-256) format — yalnızca geriye dönük doğrulama/migration. */
    fun withSalt(pin: String, salt: String): String = sha256(salt + pin)

    fun legacy(pin: String): String = sha256(pin)

    private fun pbkdf2Hex(pin: String, salt: String, iterations: Int): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(Charsets.UTF_8), iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        try {
            return factory.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
        } finally {
            spec.clearPassword()
        }
    }

    private fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}
