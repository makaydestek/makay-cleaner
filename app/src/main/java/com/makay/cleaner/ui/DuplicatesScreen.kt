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
import com.makay.cleaner.scanner.DuplicateScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.ProManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onRequirePro: () -> Unit = {}
) {
    if (ProManager.requiresPro(ProManager.ProFeature.DUPLICATES, application)) {
        ProGateContent("Yinelenen dosyalar", onNavigateBack, onRequirePro)
        return
    }

    val scanner = remember { DuplicateScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    var groups by remember { mutableStateOf<List<DuplicateScanner.DuplicateGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        groups = withContext(Dispatchers.IO) { scanner.scan() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Yinelenen dosyalar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Yinelenen dosya bulunamadı")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups) { group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${group.files.size} kopya · ${formatBytes(group.sizeBytes)} her biri",
                                fontWeight = FontWeight.SemiBold
                            )
                            group.files.forEachIndexed { index, file ->
                                Text(
                                    "${if (index == 0) "✓ tut" else "○"} ${file.name}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    var deleted = 0
                                    var saved = 0L
                                    withContext(Dispatchers.IO) {
                                        group.files.drop(1).forEach { f ->
                                            val size = f.length()
                                            if (trash.moveToTrash(f, "Yinelenen")) {
                                                deleted++
                                                saved += size
                                            }
                                        }
                                    }
                                    CleaningRecorder.record(application, "Yinelenenler", saved, deleted)
                                    CleanResultTracker.record("Yinelenenler", deleted, saved)
                                    summary = CleanResultTracker.last
                                    groups = withContext(Dispatchers.IO) { scanner.scan() }
                                    Toast.makeText(context, "$deleted kopya taşındı", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Fazlalıkları çöp kutusuna taşı") }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProGateContent(featureName: String, onBack: () -> Unit, onRequirePro: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(featureName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Pro özellik", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "$featureName Pro ile açılır. Ayarlar → Pro’dan etkinleştirin.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequirePro) { Text("Pro’ya git") }
        }
    }
}
