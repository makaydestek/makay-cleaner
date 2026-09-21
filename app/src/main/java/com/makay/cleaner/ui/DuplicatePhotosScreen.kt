package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.DuplicatePhotoScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.SystemFileGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Duplicate Photos Fixer Pro tarzı: aynı/benzer fotoğraf grupları,
 * küçük resimli liste, seçerek çöp kutusuna taşıma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatePhotosScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val scanner = remember { DuplicatePhotoScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    var tabIndex by remember { mutableIntStateOf(0) }
    var exactGroups by remember { mutableStateOf<List<DuplicatePhotoScanner.PhotoGroup>>(emptyList()) }
    var similarGroups by remember { mutableStateOf<List<DuplicatePhotoScanner.PhotoGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var progressText by remember { mutableStateOf("Fotoğraflar taranıyor…") }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()

    fun runScan() {
        scope.launch {
            loading = true
            progressText = "Aynı fotoğraflar aranıyor…"
            SystemFileGuard.consumeSkippedCount()
            exactGroups = withContext(Dispatchers.IO) { scanner.scanExact() }
            progressText = "Benzer fotoğraflar aranıyor…"
            similarGroups = withContext(Dispatchers.IO) { scanner.scanSimilar() }
            // Varsayılan: her grupta en yeni hariç hepsi seçili
            val defaults = mutableSetOf<String>()
            (exactGroups + similarGroups).forEach { g ->
                g.photos.drop(1).forEach { defaults.add(it.path) }
            }
            selectedPaths = defaults
            loading = false
        }
    }

    LaunchedEffect(Unit) { runScan() }

    val activeGroups = if (tabIndex == 0) exactGroups else similarGroups
    val selectedInTab = activeGroups.flatMap { it.photos }.count { it.path in selectedPaths }
    val selectedBytes = activeGroups.flatMap { it.photos }
        .filter { it.path in selectedPaths }
        .sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Yinelenen fotoğraflar", fontWeight = FontWeight.Bold)
                        if (!loading) {
                            Text(
                                "${exactGroups.size + similarGroups.size} grup · ${formatBytes(
                                    (exactGroups + similarGroups).sumOf { it.wasteBytes }
                                )} kazanılabilir",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    TextButton(onClick = { runScan() }, enabled = !loading) {
                        Text("Yenile")
                    }
                }
            )
        },
        bottomBar = {
            if (!loading && selectedInTab > 0) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "$selectedInTab seçili",
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                formatBytes(selectedBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Button(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Çöp kutusuna taşı")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentMod = if (widthClass != AppWidthClass.Compact) {
                Modifier.widthIn(max = 900.dp).fillMaxWidth()
            } else Modifier.fillMaxWidth()

            Column(contentMod.fillMaxSize()) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(
                        selected = tabIndex == 0,
                        onClick = { tabIndex = 0 },
                        text = { Text("Aynı (${exactGroups.size})") }
                    )
                    Tab(
                        selected = tabIndex == 1,
                        onClick = { tabIndex = 1 },
                        text = { Text("Benzer (${similarGroups.size})") }
                    )
                }

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(progressText)
                            Text(
                                "Telefon ve tablet galerisi taranır; sistem dosyalarına dokunulmaz.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    activeGroups.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (tabIndex == 0) "Aynı (birebir) fotoğraf bulunamadı"
                            else "Benzer fotoğraf grubu bulunamadı"
                        )
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        item {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(onClick = {
                                    val paths = activeGroups.flatMap { g ->
                                        g.photos.drop(1).map { it.path }
                                    }.toSet()
                                    selectedPaths = selectedPaths + paths
                                }) { Text("Fazlalıkları seç") }
                                TextButton(onClick = {
                                    val paths = activeGroups.flatMap { g -> g.photos.map { it.path } }.toSet()
                                    selectedPaths = selectedPaths - paths
                                }) { Text("Seçimi temizle") }
                            }
                        }
                        items(activeGroups, key = { it.id }) { group ->
                            DuplicatePhotoGroupCard(
                                group = group,
                                selectedPaths = selectedPaths,
                                onToggle = { path ->
                                    selectedPaths = if (path in selectedPaths) {
                                        selectedPaths - path
                                    } else {
                                        selectedPaths + path
                                    }
                                },
                                onKeepBest = {
                                    val keep = group.photos.first().path
                                    val rest = group.photos.drop(1).map { it.path }.toSet()
                                    selectedPaths = (selectedPaths - keep) + rest
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val preview = activeGroups.flatMap { it.photos }
            .filter { it.path in selectedPaths }
            .take(6)
            .map { it.path }
        ConfirmDeleteDialog(
            title = "Yinelenen fotoğrafları temizle",
            message = "Seçili fotoğraflar 48 saat çöp kutusunda tutulur; geri yükleyebilirsiniz.",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    var deleted = 0
                    var saved = 0L
                    SystemFileGuard.consumeSkippedCount()
                    withContext(Dispatchers.IO) {
                        activeGroups.flatMap { it.photos }
                            .filter { it.path in selectedPaths }
                            .forEach { photo ->
                                val size = photo.sizeBytes
                                val ok = trash.moveToTrash(File(photo.path), "Yinelenen foto")
                                if (ok) {
                                    deleted++
                                    saved += size
                                }
                            }
                    }
                    if (deleted > 0) {
                        CleaningRecorder.record(application, "Yinelenen foto", saved, deleted)
                        CleanResultTracker.record("Yinelenen foto", deleted, saved)
                        summary = CleanResultTracker.last
                    }
                    Toast.makeText(
                        context,
                        if (deleted > 0) "$deleted foto çöp kutusuna taşındı" else "Silinemedi",
                        Toast.LENGTH_SHORT
                    ).show()
                    runScan()
                }
            },
            onDismiss = { confirmDelete = false },
            fileCount = selectedInTab,
            totalBytes = selectedBytes,
            moveToTrash = true,
            previewPaths = preview
        )
    }

    summary?.let { s ->
        CleanSummaryDialog(
            category = s.category,
            filesDeleted = s.filesDeleted,
            spaceSaved = s.spaceSaved,
            protectedSkipped = s.protectedSkipped,
            movedToTrash = true,
            onDismiss = { summary = null }
        )
    }
}

@Composable
private fun DuplicatePhotoGroupCard(
    group: DuplicatePhotoScanner.PhotoGroup,
    selectedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onKeepBest: () -> Unit
) {
    val typeLabel = if (group.type == DuplicatePhotoScanner.GroupType.EXACT) "Aynı" else "Benzer"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "$typeLabel · ${group.photos.size} fotoğraf",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Fazlalık: ${formatBytes(group.wasteBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onKeepBest) { Text("En yeniyi tut") }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(group.photos, key = { it.path }) { photo ->
                    val selected = photo.path in selectedPaths
                    val isKeep = photo == group.photos.first() && !selected
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(108.dp)
                            .clickable { onToggle(photo.path) }
                    ) {
                        Box {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.displayName,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .then(
                                        if (selected) Modifier.border(
                                            2.dp,
                                            MaterialTheme.colorScheme.error,
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { onToggle(photo.path) },
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                        Text(
                            if (isKeep) "Tutulacak" else formatBytes(photo.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isKeep) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
