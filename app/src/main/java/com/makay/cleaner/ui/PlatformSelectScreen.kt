package com.makay.cleaner.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.util.PlatformManager

@Composable
fun PlatformSelectScreen(
    context: Context,
    onSelected: () -> Unit
) {
    val isTvMode = PlatformManager.rememberIsTvMode()
    val mobileFocus = rememberTvFocusRequester()
    val tvFocus = rememberTvFocusRequester()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Makay Cleaner",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Lütfen kullanım yapacağınız platformu seçin",
                color = Color(0xFFAEAEB2),
                fontSize = 15.sp
            )
            Spacer(Modifier.height(36.dp))

            val (mobileSrc, mobileFocused) = rememberTvInteraction()
            Button(
                onClick = {
                    PlatformManager.setPlatform(context, PlatformManager.Platform.MOBILE)
                    onSelected()
                },
                interactionSource = mobileSrc,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(if (isTvMode) Modifier.tvFocusable(mobileFocus) else Modifier),
                shape = RoundedCornerShape(16.dp),
                colors = tvButtonColors(mobileFocused, Color(0xFF3F8CFF))
            ) {
                Text("📱 Mobil / Tablet Sürümü", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            val (tvSrc, tvFocused) = rememberTvInteraction()
            Button(
                onClick = {
                    PlatformManager.setPlatform(context, PlatformManager.Platform.TV)
                    onSelected()
                },
                interactionSource = tvSrc,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(if (isTvMode) Modifier.tvFocusable(tvFocus) else Modifier)
                    .then(if (isTvMode) Modifier.tvRequestInitialFocus() else Modifier),
                shape = RoundedCornerShape(16.dp),
                colors = tvButtonColors(tvFocused, Color(0xFF30D158))
            ) {
                Text("📺 Android TV Sürümü", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
