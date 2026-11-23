package com.makay.cleaner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

/** Uygulama içindeki kısa işlem geri bildirimlerini ortak Compose katmanına taşır. */
internal object AppNotice {
    private data class Notice(val message: String, val isError: Boolean)
    private val messages = MutableSharedFlow<Notice>(extraBufferCapacity = 16)

    fun show(message: String) {
        val normalized = message.trim()
        if (normalized.isNotEmpty()) {
            val lower = normalized.lowercase()
            val isError = listOf(
                "başarısız", "açılamadı", "başlatılamadı", "silinemedi", "taşınamadı",
                "hata", "geçersiz", "failed", "invalid", "could not"
            )
                .any(lower::contains)
            messages.tryEmit(Notice(normalized, isError))
        }
    }

    @Composable
    fun Host() {
        var notice by remember { mutableStateOf<Notice?>(null) }
        LaunchedEffect(Unit) {
            messages.collectLatest { next ->
                notice = next
                delay(2_800)
                if (notice == next) notice = null
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = notice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xF222252B), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(30.dp),
                        shape = CircleShape,
                        color = if (notice?.isError == true) Color(0xFF542B31) else Color(0xFF244B3B)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (notice?.isError == true) Icons.Rounded.Close else Icons.Rounded.Check,
                                contentDescription = null,
                                tint = if (notice?.isError == true) Color(0xFFFF8A8A) else Color(0xFF68D6A3),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = notice?.message.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
