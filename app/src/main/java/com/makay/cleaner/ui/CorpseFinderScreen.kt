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
import com.makay.cleaner.scanner.CorpseFinder
import com.makay.cleaner.util.CleanResultTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorpseFinderScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val finder = remember { CorpseFinder(application) }
    var corpses by remember { mutableStateOf<List<CorpseFinder.Corpse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        corpses = withContext(Dispatchers.IO) { finder.scan() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kalıntı bulucu", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (corpses.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                var deleted = 0
                                var saved = 0L
                                withContext(Dispatchers.IO) {
                                    corpses.forEach { c ->
                                        val (s, n) = finder.deleteCorpse(c)
                                        saved += s
                                        deleted += n
                                    }
                                }
                                CleaningRecorder.record(application, "Kalıntı bulucu", saved, deleted)
                                CleanResultTracker.record("Kalıntı bulucu", deleted, saved, movedToTrash = false)
                                summary = CleanResultTracker.last
                                corpses = withContext(Dispatchers.IO) { finder.scan() }
                                Toast.makeText(context, "Kalıntılar temizlendi", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Tümünü temizle") }
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            corpses.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Kaldırılmış uygulama kalıntısı yok")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "Yüklü olmayan uygulamaların Android/data|obb|media klasörleri",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(corpses, key = { it.path }) { corpse ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(corpse.packageName, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${corpse.fileCount} dosya · ${formatBytes(corpse.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    val (s, n) = withContext(Dispatchers.IO) { finder.deleteCorpse(corpse) }
                                    CleaningRecorder.record(application, "Kalıntı bulucu", s, n)
                                    CleanResultTracker.record("Kalıntı bulucu", n, s, movedToTrash = false)
                                    summary = CleanResultTracker.last
                                    corpses = withContext(Dispatchers.IO) { finder.scan() }
                                }
                            }) { Text("Temizle") }
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
            movedToTrash = false,
            onDismiss = { summary = null }
        )
    }
}
