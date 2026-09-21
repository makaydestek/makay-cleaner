package com.makay.cleaner.data

import android.content.Context
import android.content.SharedPreferences
import com.makay.cleaner.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemeRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getSavedTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color", true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    private fun getSavedTheme(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    companion object {
        @Volatile
        private var instance: ThemeRepository? = null

        fun getInstance(context: Context): ThemeRepository {
            return instance ?: synchronized(this) {
                instance ?: ThemeRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
