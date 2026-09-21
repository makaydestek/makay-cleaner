package com.makay.cleaner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.cleaner.util.ProManager
import android.app.Application

/**
 * P0: Yinelenen foto / benzer / dosya / medya araçlarını tek hub altında toplar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCleanHubScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    val showProBadge = ProManager.isProUiVisible() && !ProManager.isPro(application)
    val items = listOf(
        HubItem("dup_photos", "Yinelenen fotoğraflar", "Aynı ve benzer fotoğraf grupları — seçerek çöp kutusuna", false),
        HubItem("media_hub", "Medya kategorileri", "Ekran görüntüsü, kamera, düşük boyut, Google Photos yerel", false),
        HubItem("video", "Video araçları", "Büyük video analizi — arşiv / taşı (yeniden kodlama yok)", false),
        HubItem("similar", "Benzer fotoğraflar (gelişmiş)", "dHash ile ek benzerlik taraması", false),
        HubItem("duplicates", "Yinelenen dosyalar", "Tüm depolamada hash ile kopya dosya", false),
        HubItem("compress", "JPEG sıkıştırma", "Fotoğrafları kalite düşürerek küçült", false)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medya temizliği", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Foto, video ve kopya medya araçları tek yerde. Sistem dosyalarına dokunulmaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(items) { item ->
                Card(
                    onClick = { onOpen(item.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, fontWeight = FontWeight.Bold)
                            if (showProBadge && item.pro) {
                                Text("Pro 🔒", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(item.desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private data class HubItem(
    val id: String,
    val title: String,
    val desc: String,
    val pro: Boolean
)
