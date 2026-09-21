package com.makay.cleaner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.makay.cleaner.data.ThemeRepository

/** Xiaomi Security tarzı AMOLED + neon vurgu */
val MakayPrimary = Color(0xFF3F8CFF)
val MakaySecondary = Color(0xFF2EB050)
val MakayTertiary = Color(0xFFFF9F0A)
val MakayBackground = Color(0xFFF5F5F5)
val MakaySurface = Color(0xFFFFFFFF)
val MakayError = Color(0xFFFF453A)
val MakayAmoled = Color(0xFF000000)
val MakayCardDark = Color(0xFF1C1C1E)
val MakayJunkOrange = Color(0xFFFF6B35)

private val LightColorScheme = lightColorScheme(
    primary = MakayPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0A2A66),
    secondary = MakaySecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8F0D4),
    onSecondaryContainer = Color(0xFF0A3D1A),
    tertiary = MakayTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4C2),
    onTertiaryContainer = Color(0xFF5C3500),
    background = MakayBackground,
    onBackground = Color(0xFF1C1C1E),
    surface = MakaySurface,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFEEEEF0),
    onSurfaceVariant = Color(0xFF636366),
    error = MakayError,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = MakayPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A3A7A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = MakaySecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A4A2A),
    onSecondaryContainer = Color(0xFFC8F0D4),
    tertiary = MakayTertiary,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF5C3500),
    onTertiaryContainer = Color(0xFFFFE4C2),
    background = MakayAmoled,
    onBackground = Color.White,
    surface = MakayCardDark,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF8E8E93),
    error = MakayError,
    onError = Color.White
)

@Composable
fun MakayCleanerTheme(
    themeRepository: ThemeRepository? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = themeRepository?.dynamicColor?.collectAsState()?.value ?: dynamicColor
    val themeMode = themeRepository?.themeMode?.collectAsState()?.value

    val resolvedDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM, null -> darkTheme
    }

    val colorScheme = when {
        useDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
