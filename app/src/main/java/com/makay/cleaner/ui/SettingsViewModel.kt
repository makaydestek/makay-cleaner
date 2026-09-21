package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.makay.cleaner.data.AutoCleanRepository
import com.makay.cleaner.data.ThemeRepository
import com.makay.cleaner.ui.theme.ThemeMode
import com.makay.cleaner.util.ProManager
import com.makay.cleaner.util.QuickCleanHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val appVersion: String = "",
    val autoCleanEnabled: Boolean = false,
    val autoCleanIntervalHours: Long = 24,
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val catCache: Boolean = true,
    val catApk: Boolean = true,
    val catTmp: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val lastManualCleanMessage: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val themeRepository = ThemeRepository.getInstance(application)
    private val autoCleanRepository = AutoCleanRepository(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val (enabled, interval) = autoCleanRepository.getSettings()
        val versionName = try {
            val info = getApplication<Application>().packageManager
                .getPackageInfo(getApplication<Application>().packageName, 0)
            info.versionName ?: com.makay.cleaner.BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            com.makay.cleaner.BuildConfig.VERSION_NAME
        }
        val cats = autoCleanRepository.getCategories()
        _uiState.value = _uiState.value.copy(
            themeMode = themeRepository.themeMode.value,
            dynamicColor = themeRepository.dynamicColor.value,
            autoCleanEnabled = enabled,
            autoCleanIntervalHours = interval,
            wifiOnly = autoCleanRepository.getWifiOnly(),
            chargingOnly = autoCleanRepository.getChargingOnly(),
            catCache = cats.cache,
            catApk = cats.oldApk,
            catTmp = cats.tempJunk,
            appVersion = versionName
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        themeRepository.setThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        themeRepository.setDynamicColor(enabled)
        _uiState.value = _uiState.value.copy(dynamicColor = enabled)
    }

    fun setAutoCleanEnabled(enabled: Boolean) {
        persistAuto(enabled = enabled)
    }

    fun setAutoCleanInterval(hours: Long) {
        persistAuto(intervalHours = hours)
    }

    fun setWifiOnly(wifiOnly: Boolean): Boolean {
        if (wifiOnly && ProManager.requiresPro(ProManager.ProFeature.SCHEDULE_ADVANCED, getApplication())) {
            return false
        }
        persistAuto(wifiOnly = wifiOnly)
        return true
    }

    fun setChargingOnly(chargingOnly: Boolean): Boolean {
        if (chargingOnly && ProManager.requiresPro(ProManager.ProFeature.SCHEDULE_ADVANCED, getApplication())) {
            return false
        }
        persistAuto(chargingOnly = chargingOnly)
        return true
    }

    fun setCategoryCache(enabled: Boolean): Boolean {
        persistAuto(catCache = enabled)
        return true
    }

    fun setCategoryApk(enabled: Boolean): Boolean {
        persistAuto(catApk = enabled)
        return true
    }

    fun setCategoryTmp(enabled: Boolean): Boolean {
        if (enabled && ProManager.requiresPro(ProManager.ProFeature.SYSTEM_CLEANER_AUTO, getApplication())) {
            return false
        }
        persistAuto(catTmp = enabled)
        return true
    }

    private fun persistAuto(
        enabled: Boolean = _uiState.value.autoCleanEnabled,
        intervalHours: Long = _uiState.value.autoCleanIntervalHours,
        wifiOnly: Boolean = _uiState.value.wifiOnly,
        chargingOnly: Boolean = _uiState.value.chargingOnly,
        catCache: Boolean = _uiState.value.catCache,
        catApk: Boolean = _uiState.value.catApk,
        catTmp: Boolean = _uiState.value.catTmp
    ) {
        autoCleanRepository.saveSettings(
            enabled,
            intervalHours,
            wifiOnly,
            chargingOnly,
            AutoCleanRepository.CategoryFlags(catCache, catApk, catTmp)
        )
        _uiState.value = _uiState.value.copy(
            autoCleanEnabled = enabled,
            autoCleanIntervalHours = intervalHours,
            wifiOnly = wifiOnly,
            chargingOnly = chargingOnly,
            catCache = catCache,
            catApk = catApk,
            catTmp = catTmp
        )
    }

    fun runManualClean() {
        val result = QuickCleanHelper.cleanAppCaches(getApplication())
        autoCleanRepository.runManualClean()
        _uiState.value = _uiState.value.copy(
            lastManualCleanMessage = if (result.filesDeleted > 0) {
                "${result.filesDeleted} dosya silindi (${formatBytes(result.spaceSaved)})"
            } else {
                "Temizlenecek ek dosya bulunamadı"
            }
        )
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                return SettingsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
