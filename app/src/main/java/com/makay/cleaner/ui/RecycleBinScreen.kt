package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makay.cleaner.data.RecycleBinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val repo = remember { RecycleBinRepository(application) }
    var items by remember { mutableStateOf(repo.listItems()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    fun refresh() {
        items = repo.listItems()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Çöp kutusu", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                val (saved, count) = withContext(Dispatchers.IO) { repo.emptyBin() }
                                refresh()
                                Toast.makeText(
                                    context,
                                    "$count öğe kalıcı silindi (${formatBytes(saved)})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) { Text("Boşalt") }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Çöp kutusu boş", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "${items.size} öğe · ${formatBytes(items.sumOf { it.sizeBytes })} · 48 saat saklanır",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(items, key = { it.id }) { item ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.category} · ${formatBytes(item.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                dateFmt.format(Date(item.deletedAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) { repo.restore(item.id) }
                                        refresh()
                                        Toast.makeText(
                                            context,
                                            if (ok) "Geri yüklendi" else "Geri yükleme başarısız",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }) { Text("Geri yükle") }
                                TextButton(onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { repo.purgePermanently(item.id) }
                                        refresh()
                                    }
                                }) { Text("Kalıcı sil") }
                            }
                        }
                    }
                }
            }
        }
    }
}
