package com.makay.cleaner.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlatformManager {
    private const val PREFS = "platform_prefs"
    private const val KEY_PLATFORM = "platform_mode"

    enum class Platform {
        MOBILE, TV
    }

    private val _currentPlatform = MutableStateFlow(Platform.MOBILE)
    val currentPlatform: StateFlow<Platform> = _currentPlatform.asStateFlow()

    fun init(context: Context) {
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PLATFORM, null)
        if (saved != null) {
            runCatching {
                _currentPlatform.value = Platform.valueOf(saved)
            }
        }
    }

    fun isTvMode(context: Context): Boolean {
        return getPlatform(context) == Platform.TV
    }

    fun getPlatform(context: Context): Platform {
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PLATFORM, null)
        return if (saved != null) {
            runCatching { Platform.valueOf(saved) }.getOrDefault(Platform.MOBILE)
        } else {
            Platform.MOBILE
        }
    }

    fun setPlatform(context: Context, platform: Platform) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLATFORM, platform.name)
            .apply()
        _currentPlatform.value = platform
    }

    @Composable
    fun rememberIsTvMode(): Boolean {
        val context = LocalContext.current
        init(context)
        val platform by currentPlatform.collectAsState()
        return platform == Platform.TV
    }
}
