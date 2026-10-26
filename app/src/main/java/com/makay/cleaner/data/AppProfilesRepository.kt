package com.makay.cleaner.data

import android.content.Context
import android.content.SharedPreferences

enum class AppProfile(val id: String, val titleTr: String, val descTr: String) {
    HOME("home", "Ev", "Normal temizlik sıklığı, medya taramaları açık"),
    WORK("work", "İş", "Wi‑Fi zorunlu otomatik temizlik, daha seyrek tarama"),
    BATTERY("battery", "Pil", "Yalnızca önbellek, şarjdayken, uzun aralık")
}

/**
 * Yazılım profili: yalnızca MakayCleaner davranışını değiştirir (donanım/radio yok).
 */
class AppProfilesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_profiles_prefs", Context.MODE_PRIVATE)
    private val autoClean = AutoCleanRepository(context)

    fun getActive(): AppProfile {
        val id = prefs.getString(KEY_ACTIVE, AppProfile.HOME.id) ?: AppProfile.HOME.id
        return AppProfile.entries.find { it.id == id } ?: AppProfile.HOME
    }

    fun apply(profile: AppProfile) {
        prefs.edit().putString(KEY_ACTIVE, profile.id).apply()
        when (profile) {
            AppProfile.HOME -> autoClean.saveSettings(
                enabled = autoClean.getSettings().first,
                intervalHours = 24,
                wifiOnly = false,
                chargingOnly = false,
                categories = AutoCleanRepository.CategoryFlags(cache = true, oldApk = false, tempJunk = false),
                useTrash = true
            )
            AppProfile.WORK -> autoClean.saveSettings(
                enabled = true,
                intervalHours = 48,
                wifiOnly = true,
                chargingOnly = false,
                categories = AutoCleanRepository.CategoryFlags(cache = true, oldApk = false, tempJunk = false),
                useTrash = true
            )
            AppProfile.BATTERY -> autoClean.saveSettings(
                enabled = true,
                intervalHours = 72,
                wifiOnly = false,
                chargingOnly = true,
                categories = AutoCleanRepository.CategoryFlags(cache = true, oldApk = false, tempJunk = false),
                useTrash = true
            )
        }
    }

    companion object {
        private const val KEY_ACTIVE = "active_profile"
    }
}
