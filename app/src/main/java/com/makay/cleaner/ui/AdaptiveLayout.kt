package com.makay.cleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWidthClass { Compact, Medium, Expanded }

@Composable
fun rememberWidthClass(): AppWidthClass {
    val width = LocalConfiguration.current.screenWidthDp
    return when {
        width >= 840 -> AppWidthClass.Expanded
        width >= 600 -> AppWidthClass.Medium
        else -> AppWidthClass.Compact
    }
}

@Composable
fun MakayAdaptiveContent(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 840.dp,
    content: @Composable () -> Unit
) {
    val widthClass = rememberWidthClass()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val useCap = widthClass != AppWidthClass.Compact
        Box(
            modifier = if (useCap) {
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            content()
        }
    }
}

@Composable
fun adaptiveColumns(): Int = when (rememberWidthClass()) {
    AppWidthClass.Compact -> 2
    AppWidthClass.Medium -> 3
    AppWidthClass.Expanded -> 4
}
