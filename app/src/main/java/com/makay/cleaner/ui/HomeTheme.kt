package com.makay.cleaner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal val HomeOrange = Color(0xFFFF9F0A)
internal val HomeGreen = Color(0xFF30D158)
internal val HomeCyan = Color(0xFF64D2FF)
internal val HomeRed = Color(0xFFFF453A)
internal val HomeWhatsApp = Color(0xFF25D366)

@Composable
internal fun homeIsLight() = MaterialTheme.colorScheme.background.luminance() > 0.45f

@Composable
internal fun homeBg() = if (homeIsLight()) Color(0xFFF4F4F6) else Color(0xFF000000)

@Composable
internal fun homeCard() = if (homeIsLight()) Color(0xFFFFFFFF) else Color(0xFF2C2C2E)

@Composable
internal fun homeTextPrimary() = if (homeIsLight()) Color(0xFF111111) else Color(0xFFF2F2F7)

@Composable
internal fun homeTextSecondary() = if (homeIsLight()) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)

@Composable
internal fun homeAccent() = MaterialTheme.colorScheme.primary

@Composable
internal fun homeOutline() = if (homeIsLight()) Color(0xFF1C1C1E) else Color(0xFFAEAEB2)
