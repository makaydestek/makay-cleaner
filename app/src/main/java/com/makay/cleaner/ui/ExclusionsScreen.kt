package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makay.cleaner.data.CleanExclusionsRepository
import com.makay.cleaner.util.SystemCacheReclaimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExclusionsScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val repo = remember { CleanExclusionsRepository(application) }
    var systemItems by remember { mutableStateOf(CleanExclusionsRepository.systemDefaults()) }
    var userItems by remember { mutableStateOf(repo.listUser()) }
    var lockedBytes by remember { mutableStateOf(0L) }
    var showAdd by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var titleInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        lockedBytes = withContext(Dispatchers.IO) {
            runCatching { SystemCacheReclaimer.queryCacheBytes(application) }.getOrDefault(0L)
        }
        userItems = repo.listUser()
        systemItems = CleanExclusionsRepository.systemDefaults()
    }

    fun refreshUser() {
        userItems = repo.listUser()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hariç tutulanlar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(onClick = { showAdd = true }) { Text("Ekle") }
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
                    "Bu klasörler temizlikten çıkarılır. Sistem maddeleri Xiaomi/Samsung’un silebildiği " +
                        "ama sideload uygulamanın silemediği alanlardır. Skor bunları saymaz.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
            if (lockedBytes > 0L) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Kilitli sistem önbelleği",
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Yaklaşık ${formatBytes(lockedBytes)} — dosya listesi değil, sistem sayacı. " +
                                    "OEM güvenlik uygulaması temizleyebilir; Makay silemez.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Sistem (kaldırılamaz)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(systemItems, key = { it.id }) { item ->
                ExclusionRow(
                    title = item.title,
                    path = item.path,
                    reason = item.reason,
                    badge = "Sistem",
                    onRemove = null
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sizin ekledikleriniz",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    TextButton(onClick = { showAdd = true }) { Text("+ Yol ekle") }
                }
            }
            if (userItems.isEmpty()) {
                item {
                    Text(
                        "Henüz kullanıcı hariç tutması yok. İstediğiniz klasör yolunu ekleyebilirsiniz.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                items(userItems, key = { it.id }) { item ->
                    ExclusionRow(
                        title = item.title,
                        path = item.path,
                        reason = item.reason,
                        badge = "Siz",
                        onRemove = {
                            scope.launch {
                                withContext(Dispatchers.IO) { repo.removeUser(item.id) }
                                refreshUser()
                                Toast.makeText(context, "Kaldırıldı", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Hariç tutulan yol ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Örn. /storage/emulated/0/DCIM/Camera veya Download/İş",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        label = { Text("Yol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Başlık (isteğe bağlı)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val added = withContext(Dispatchers.IO) {
                                repo.addUserPath(pathInput, titleInput.ifBlank { null })
                            }
                            if (added != null) {
                                pathInput = ""
                                titleInput = ""
                                showAdd = false
                                refreshUser()
                                Toast.makeText(context, "Eklendi", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Geçersiz yol veya zaten listede / sistem korumasında",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text("Kaydet") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Vazgeç") }
            }
        )
    }
}

@Composable
private fun ExclusionRow(
    title: String,
    path: String,
    reason: String,
    badge: String,
    onRemove: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    badge,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onRemove != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) {
                    Text("Kaldır")
                }
            }
        }
    }
}
