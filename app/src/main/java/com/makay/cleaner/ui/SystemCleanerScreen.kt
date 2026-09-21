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
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.SystemCleanerScanner
import com.makay.cleaner.util.CleanResultTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCleanerScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val scanner = remember { SystemCleanerScanner() }
    val trash = remember { RecycleBinRepository(application) }
    var items by remember { mutableStateOf<List<SystemCleanerScanner.JunkItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        items = withContext(Dispatchers.IO) { scanner.scan() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sistem temizleyici", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                var deleted = 0
                                var saved = 0L
                                withContext(Dispatchers.IO) {
                                    items.filter { it.file.absolutePath in selected }.forEach { item ->
                                        val size = item.sizeBytes
                                        val ok = if (item.file.isDirectory) {
                                            item.file.delete()
                                        } else {
                                            trash.moveToTrash(item.file, item.reason)
                                        }
                                        if (ok) {
                                            deleted++
                                            saved += size
                                        }
                                    }
                                }
                                CleaningRecorder.record(application, "Sistem temizleyici", saved, deleted)
                                CleanResultTracker.record("Sistem temizleyici", deleted, saved)
                                summary = CleanResultTracker.last
                                items = withContext(Dispatchers.IO) { scanner.scan() }
                                selected = emptySet()
                                Toast.makeText(context, "$deleted öğe temizlendi", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Temizle (${selected.size})") }
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Temizlenecek gereksiz dosya yok")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${items.size} öğe · ${formatBytes(items.sumOf { it.sizeBytes })}")
                        TextButton(onClick = {
                            selected = items.map { it.file.absolutePath }.toSet()
                        }) { Text("Tümünü seç") }
                    }
                }
                items(items, key = { it.file.absolutePath }) { item ->
                    val path = item.file.absolutePath
                    Card(onClick = {
                        selected = if (path in selected) selected - path else selected + path
                    }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = path in selected,
                                onCheckedChange = {
                                    selected = if (it) selected + path else selected - path
                                }
                            )
                            Column(Modifier.weight(1f)) {
                                Text(item.file.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${item.reason} · ${formatBytes(item.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    summary?.let { s ->
        CleanSummaryDialog(
            category = s.category,
            filesDeleted = s.filesDeleted,
            spaceSaved = s.spaceSaved,
            protectedSkipped = s.protectedSkipped,
            movedToTrash = s.movedToTrash,
            onDismiss = { summary = null }
        )
    }
}
