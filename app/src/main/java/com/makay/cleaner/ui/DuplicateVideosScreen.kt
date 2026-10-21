package com.makay.cleaner.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.border
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
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.DuplicateVideoScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.SystemFileGuard
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Yinelenen video: exact hash grupları, akıllı seçim, çöp kutusu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicateVideosScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val scanner = remember { DuplicateVideoScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    val prefs = remember { DuplicatePhotoPrefs(application) }

    var groups by remember { mutableStateOf<List<DuplicateVideoScanner.VideoGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("Hazırlanıyor…") }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(1) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lockedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var scanMode by remember { mutableStateOf(prefs.scanMode) }
    var selectedBucket by remember { mutableStateOf(prefs.selectedBucket) }
    var buckets by remember { mutableStateOf(emptyList<com.makay.cleaner.scanner.DuplicatePhotoScanner.BucketInfo>()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()
    val cancelFlag = remember { AtomicBoolean(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun runScan() {
        if (!hasPermission) return
        scanJob?.cancel()
        cancelFlag.set(false)
        hasScanned = true
        scanJob = scope.launch {
            loading = true
            SystemFileGuard.consumeSkippedCount()
            val main = Handler(Looper.getMainLooper())
            val listener = DuplicateVideoScanner.ProgressListener { phase, cur, tot ->
                main.post {
                    progressText = phase
                    progressCurrent = cur
                    progressTotal = tot.coerceAtLeast(1)
                }
            }
            groups = withContext(Dispatchers.IO) {
                scanner.scanExact(
                    mode = scanMode,
                    bucketName = if (scanMode == DuplicatePhotoPrefs.MODE_FOLDER) selectedBucket else null,
                    excludedBuckets = prefs.excludedBuckets,
                    allowedFormats = DuplicatePhotoPrefs.VIDEO_FORMATS,
                    quick = scanMode == DuplicatePhotoPrefs.MODE_QUICK,
                    cancelled = cancelFlag,
                    progress = listener
                )
            }
            // Varsayılan: en iyi (ilk) hariç seç
            val defaults = mutableSetOf<String>()
            groups.forEach { g ->
                g.videos.drop(1).forEach { v ->
                    if (v.path !in lockedPaths && v.bucketName !in prefs.protectedBuckets) {
                        defaults.add(v.path)
                    }
                }
            }
            selectedPaths = defaults
            loading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelFlag.set(true)
            scanJob?.cancel()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            buckets = withContext(Dispatchers.IO) { scanner.listBuckets() }
        }
    }

    val selectedCount = groups.flatMap { it.videos }.count { it.path in selectedPaths }
    val selectedBytes = groups.flatMap { it.videos }.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
    val fraction = (progressCurrent.toFloat() / progressTotal).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Yinelenen videolar", fontWeight = FontWeight.Bold)
                        if (hasScanned && !loading) {
                            Text(
                                "${groups.size} grup · ${formatBytes(groups.sumOf { it.wasteBytes })}",
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
                    when {
                        loading -> TextButton(onClick = {
                            cancelFlag.set(true)
                            scanJob?.cancel()
                            loading = false
                        }) { Text("İptal") }
                        hasScanned -> TextButton(onClick = { runScan() }) { Text("Yenile") }
                    }
                }
            )
        },
        bottomBar = {
            if (!loading && hasScanned && selectedCount > 0) {
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
                            Text("$selectedCount seçili", fontWeight = FontWeight.Bold)
                            Text(formatBytes(selectedBytes), color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Çöp kutusuna taşı") }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasPermission -> PermissionWarningCard(
                    "Yinelenen videoları taramak için depolama erişimi gerekir."
                ) { PermissionHelper.requestStoragePermission(context) }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(progress = { fraction })
                        Spacer(Modifier.height(12.dp))
                        Text(progressText)
                        Text("${(fraction * 100).toInt()}%")
                    }
                }

                !hasScanned -> {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Tarama kapsamı", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                DuplicatePhotoPrefs.MODE_QUICK to "Hızlı",
                                DuplicatePhotoPrefs.MODE_CAMERA to "Kamera",
                                DuplicatePhotoPrefs.MODE_FOLDER to "Klasör",
                                DuplicatePhotoPrefs.MODE_FULL to "Tam"
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = scanMode == id,
                                    onClick = { scanMode = id },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (scanMode == DuplicatePhotoPrefs.MODE_FOLDER) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(buckets.take(30)) { b ->
                                    FilterChip(
                                        selected = selectedBucket == b.name,
                                        onClick = { selectedBucket = b.name },
                                        label = { Text("${b.name} (${b.count})") }
                                    )
                                }
                            }
                        }
                        Text(
                            "Aynı boyuttaki videolar kısmi hash ile karşılaştırılır. Sistem dosyalarına dokunulmaz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { runScan() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = scanMode != DuplicatePhotoPrefs.MODE_FOLDER || !selectedBucket.isNullOrBlank()
                        ) { Text("Tara", fontWeight = FontWeight.Bold) }
                    }
                }

                groups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Yinelenen video bulunamadı")
                        OutlinedButton(onClick = { hasScanned = false }) { Text("Ayarları değiştir") }
                    }
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                val paths = groups.flatMap { g -> g.videos.drop(1).map { it.path } }
                                    .filter { it !in lockedPaths }.toSet()
                                selectedPaths = selectedPaths + paths
                            }) { Text("Fazlalıkları seç") }
                            TextButton(onClick = {
                                val all = groups.flatMap { g -> g.videos.map { it.path } }.toSet()
                                selectedPaths = selectedPaths - all
                            }) { Text("Seçimi temizle") }
                        }
                    }
                    items(groups, key = { it.id }) { group ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "${group.videos.size} video · Fazlalık ${formatBytes(group.wasteBytes)}",
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(group.videos, key = { it.path }) { video ->
                                        val selected = video.path in selectedPaths
                                        val locked = video.path in lockedPaths
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(120.dp)
                                        ) {
                                            Box {
                                                AsyncImage(
                                                    model = video.uri,
                                                    contentDescription = video.displayName,
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
                                                    onCheckedChange = {
                                                        if (!locked) {
                                                            selectedPaths = if (selected) {
                                                                selectedPaths - video.path
                                                            } else {
                                                                selectedPaths + video.path
                                                            }
                                                        }
                                                    },
                                                    enabled = !locked,
                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                )
                                            }
                                            Text(
                                                formatBytes(video.sizeBytes),
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1
                                            )
                                            TextButton(
                                                onClick = {
                                                    lockedPaths = if (locked) {
                                                        lockedPaths - video.path
                                                    } else {
                                                        lockedPaths + video.path
                                                    }
                                                    selectedPaths = selectedPaths - video.path
                                                },
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    if (locked) "Kilidi aç" else "Kilitle",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Yinelenen videoları temizle",
            message = "Seçili videolar 48 saat çöp kutusunda tutulur; geri yükleyebilirsiniz.",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    var deleted = 0
                    var saved = 0L
                    withContext(Dispatchers.IO) {
                        trash.beginBatch()
                        groups.flatMap { it.videos }
                            .filter { it.path in selectedPaths }
                            .forEach { v ->
                                if (trash.movePathToTrash(v.uri.toString(), "Yinelenen video")) {
                                    deleted++
                                    saved += v.sizeBytes
                                }
                            }
                    }
                    if (deleted > 0) {
                        CleaningRecorder.record(application, "Yinelenen video", saved, deleted)
                        CleanResultTracker.record("Yinelenen video", deleted, saved)
                        summary = CleanResultTracker.last
                    }
                    Toast.makeText(
                        context,
                        if (deleted > 0) "$deleted video taşındı" else "Silinemedi",
                        Toast.LENGTH_SHORT
                    ).show()
                    runScan()
                }
            },
            onDismiss = { confirmDelete = false },
            fileCount = selectedCount,
            totalBytes = selectedBytes,
            moveToTrash = true,
            previewPaths = groups.flatMap { it.videos }
                .filter { it.path in selectedPaths }
                .take(6)
                .map { it.path }
        )
    }

    summary?.let { s ->
        CleanSummaryDialog(
            category = s.category,
            filesDeleted = s.filesDeleted,
            spaceSaved = s.spaceSaved,
            protectedSkipped = s.protectedSkipped,
            movedToTrash = true,
            onDismiss = { summary = null },
            onUndo = {
                scope.launch {
                    val (n, _) = withContext(Dispatchers.IO) { trash.undoLastBatch() }
                    Toast.makeText(context, "$n öğe geri yüklendi", Toast.LENGTH_SHORT).show()
                    runScan()
                }
            }
        )
    }
}
