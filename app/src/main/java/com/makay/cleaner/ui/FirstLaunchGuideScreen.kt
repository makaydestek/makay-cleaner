package com.makay.cleaner.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.util.LanguageManager

@Composable
fun FirstLaunchGuideScreen(
    context: Context,
    onDone: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf(
        Triple(
            LanguageManager.t("Depolama izni", "Storage permission", context),
            LanguageManager.t(
                "Junk ve medya boyutları için Android 11+ tüm dosyalara erişim gerekir. İzin yoksa 0 B görünür.",
                "Android 11+ needs All files access for junk and media sizes. Without it you may see 0 B.",
                context
            ),
            "1/4"
        ),
        Triple(
            LanguageManager.t("Optimizasyon", "Optimize", context),
            LanguageManager.t(
                "Ana ekrandaki Optimizasyon yalnızca silinebilir junk’ı temizler. Skor bu kısma göre yükselir.",
                "Home Optimize cleans only removable junk. The score follows that amount.",
                context
            ),
            "2/4"
        ),
        Triple(
            LanguageManager.t("Çöp kutusu", "Recycle bin", context),
            LanguageManager.t(
                "Silinen uygun dosyalar 48 saat geri alınabilir. Süre dolunca kalıcı silinir.",
                "Eligible files stay in the recycle bin for 48 hours, then they are deleted permanently.",
                context
            ),
            "3/4"
        ),
        Triple(
            LanguageManager.t("Hariç tutulanlar", "Exclusions", context),
            LanguageManager.t(
                "Sistem klasörleri Hariç tutulanlar’da listelenir; skor bunları saymaz. İsterseniz kendi yollarınızı da ekleyebilirsiniz. Silinebilir junk yoksa skor %100’dür.",
                "System folders are listed under Exclusions; the score ignores them. You can add your own paths. With no removable junk the score is 100%.",
                context
            ),
            "4/4"
        )
    )
    val current = steps[step]

    Surface(Modifier.fillMaxSize(), color = Color.Black) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                LanguageManager.t("Hızlı rehber", "Quick guide", context),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(current.third, color = Color(0xFF8E8E93), fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            Text(
                current.first,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                current.second,
                color = Color(0xFFAEAEB2),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            TvActionButton(
                onClick = {
                    if (step < steps.lastIndex) step++ else onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                idle = Color(0xFF3F8CFF)
            ) {
                Text(
                    if (step < steps.lastIndex)
                        LanguageManager.t("İleri", "Next", context)
                    else
                        LanguageManager.t("Başla", "Start", context),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onDone, modifier = Modifier.makayFocus(corner = 10.dp)) {
                Text(
                    LanguageManager.t("Atla", "Skip", context),
                    color = Color(0xFF8E8E93)
                )
            }
        }
    }
}

fun markFirstLaunchDone(context: Context) {
    context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("first_launch_done", true)
        .apply()
}

fun isFirstLaunchDone(context: Context): Boolean =
    context.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE)
        .getBoolean("first_launch_done", false)
