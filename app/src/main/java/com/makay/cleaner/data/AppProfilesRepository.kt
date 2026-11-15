package com.makay.cleaner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import com.makay.cleaner.R

enum class AppProfile(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int
) {
    HOME(
        "home",
        R.string.profile_home_title,
        R.string.profile_home_desc
    ),
    WORK(
        "work",
        R.string.profile_work_title,
        R.string.profile_work_desc
    ),
    BATTERY(
        "battery",
        R.string.profile_battery_title,
        R.string.profile_battery_desc
    )
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
        // Profil otomatik temizliği KAPALIYSA zorla açmaz.
        val keepEnabled = autoClean.getSettings().first
        when (profile) {
            AppProfile.HOME -> autoClean.saveSettings(
                enabled = keepEnabled,
                intervalHours = 24,
                wifiOnly = false,
                chargingOnly = false,
                categories = AutoCleanRepository.CategoryFlags(cache = true, oldApk = false, tempJunk = false),
                useTrash = true
            )
            AppProfile.WORK -> autoClean.saveSettings(
                enabled = keepEnabled,
                intervalHours = 48,
                wifiOnly = true,
                chargingOnly = false,
                categories = AutoCleanRepository.CategoryFlags(cache = true, oldApk = false, tempJunk = false),
                useTrash = true
            )
            AppProfile.BATTERY -> autoClean.saveSettings(
                enabled = keepEnabled,
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
