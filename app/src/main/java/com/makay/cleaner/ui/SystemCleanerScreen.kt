package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
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
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCleanerScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val scanner = remember { SystemCleanerScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    var items by remember { mutableStateOf<List<SystemCleanerScanner.JunkItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasStoragePermission = rememberStoragePermissionState()

    fun cleanSelected() {
        scope.launch {
            var deleted = 0
            var saved = 0L
            withContext(Dispatchers.IO) {
                val targets = items.filter { it.file.absolutePath in selected }
                trash.beginBatch()
                targets.forEach { item ->
                    val size = item.sizeBytes
                    // Dizinler de çöp üzerinden gider; File.delete() ile
                    // geri alınamaz kalıcı silme yapılmaz.
                    val ok = com.makay.cleaner.util.FilePurger.remove(
                        application,
                        item.file,
                        trash = trash,
                        category = item.reason
                    )
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
            Toast.makeText(
                context,
                "$deleted öğe çöp kutusuna taşındı (${formatBytes(saved)})",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            loading = true
            items = withContext(Dispatchers.IO) { scanner.scan() }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_sistem_temizleyici), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.txt_temizle_1_s_2, selected.size))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            !hasStoragePermission -> Box(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                PermissionWarningCard(
                    "Gereksiz dosyaları taramak ve seçtiklerinizi temizlemek için depolama erişimi gerekir."
                ) { PermissionHelper.requestStoragePermission(context) }
            }
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.txt_temizlenecek_gereksiz_dosya_yok))
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
                        Text(stringResource(R.string.msg_items_size, items.size, formatBytes(items.sumOf { it.sizeBytes })))
                        TextButton(onClick = {
                            selected = items.map { it.file.absolutePath }.toSet()
                        }) { Text(stringResource(R.string.txt_t_m_n_se_2)) }
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
                                    stringResource(R.string.txt_1_s_2_s_16, item.reason, formatBytes(item.sizeBytes)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.txt_se_ilenler_p_kutusuna_ta_ns_n_m)) },
            text = {
                Text(
                    "${selected.size} öğe (${formatBytes(items.filter { it.file.absolutePath in selected }.sumOf { it.sizeBytes })}) " +
                        "çöp kutusuna taşınır ve 48 saat içinde geri yüklenebilir."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    cleanSelected()
                }) { Text(stringResource(R.string.txt_pe_ta)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.txt_vazge)) }
            }
        )
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
