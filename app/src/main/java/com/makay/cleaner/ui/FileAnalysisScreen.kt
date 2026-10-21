package com.makay.cleaner.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.domain.model.StorageCategory
import com.makay.cleaner.scanner.FileAnalysisScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.SystemFileGuard
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pro tarzı dosya analizi: tür pasta, klasörler, büyük/son dosyalar, boş klasörler.
 * Salt okuma ağırlıklıdır; silme yalnızca kullanıcı onayı + çöp kutusu ile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileAnalysisScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onOpenLargeFiles: () -> Unit = {},
    onOpenDuplicatePhotos: () -> Unit = {},
    onOpenDuplicates: () -> Unit = {},
    onOpenMediaClean: () -> Unit = {},
    onOpenApk: () -> Unit = {}
) {
    val scanner = remember { FileAnalysisScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    var report by remember { mutableStateOf<FileAnalysisScanner.AnalysisReport?>(null) }
    var loading by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("Hazırlanıyor…") }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(6) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedEmpty by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()
    val cancelFlag = remember { AtomicBoolean(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val widthClass = rememberWidthClass()

    fun runAnalysis() {
        if (!hasPermission) return
        scanJob?.cancel()
        cancelFlag.set(false)
        scanJob = scope.launch {
            loading = true
            progressText = "Analiz başlıyor…"
            progressCurrent = 0
            progressTotal = 6
            SystemFileGuard.consumeSkippedCount()
            val main = Handler(Looper.getMainLooper())
            val listener = FileAnalysisScanner.ProgressListener { phase, cur, tot ->
                main.post {
                    progressText = phase
                    progressCurrent = cur
                    progressTotal = tot.coerceAtLeast(1)
                }
            }
            val result = withContext(Dispatchers.IO) {
                scanner.analyze(cancelFlag, listener)
            }
            report = result
            selectedEmpty = emptySet()
            loading = false
            if (!result.cancelled) {
                CleaningRecorder.recordScan(
                    application,
                    "Dosya Analizi",
                    result.scannedFileEstimate,
                    result.types.sumOf { it.sizeBytes },
                    CleaningRecorder.METRIC_STORAGE
                )
            }
        }
    }

    fun cancelAnalysis() {
        cancelFlag.set(true)
        scanJob?.cancel()
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelFlag.set(true)
            scanJob?.cancel()
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && report == null && !loading) runAnalysis()
    }

    val fraction = (progressCurrent.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dosya analizi", fontWeight = FontWeight.Bold)
                        report?.let { r ->
                            if (!loading) {
                                Text(
                                    "${r.storage.usedFormatted} kullanılıyor · ${r.scannedFileEstimate} dosya",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    TvIconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (loading) {
                        TextButton(onClick = { cancelAnalysis() }) { Text("İptal") }
                    } else {
                        TextButton(onClick = {
                            if (hasPermission) runAnalysis()
                            else PermissionHelper.requestStoragePermission(context)
                        }) { Text("Yenile") }
                    }
                }
            )
        },
        bottomBar = {
            if (!loading && selectedEmpty.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedEmpty.size} boş klasör", fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { confirmEmpty = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("Çöp kutusuna taşı") }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentMod = if (widthClass != AppWidthClass.Compact) {
                Modifier.widthIn(max = 900.dp).fillMaxWidth()
            } else Modifier.fillMaxWidth()

            when {
                !hasPermission -> PermissionWarningCard(
                    "Dosya analizi için depolama erişimi gerekir. Tarama cihazınızda kalır; dosyalar yüklenmez."
                ) { PermissionHelper.requestStoragePermission(context) }

                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        CircularProgressIndicator(progress = { fraction })
                        Spacer(Modifier.height(12.dp))
                        Text(progressText, fontWeight = FontWeight.Medium)
                        Text(
                            "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                        Text(
                            "Sistem ve korumalı klasörler atlanır.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Analiz henüz çalıştırılmadı")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { runAnalysis() }) { Text("Analiz et") }
                    }
                }

                else -> {
                    val r = report!!
                    Column(contentMod.fillMaxSize()) {
                        AnalysisSummaryHeader(r)
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Türler") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Klasörler") })
                            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Büyük") })
                            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Son") })
                            Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Boş") })
                        }
                        when (tab) {
                            0 -> TypesTab(
                                types = r.types,
                                onOpenAction = { id ->
                                    when (id) {
                                        "images" -> onOpenDuplicatePhotos()
                                        "videos" -> onOpenMediaClean()
                                        "apk" -> onOpenApk()
                                        "docs", "archives", "downloads", "audio" -> onOpenLargeFiles()
                                        else -> onOpenLargeFiles()
                                    }
                                },
                                onDuplicates = onOpenDuplicates,
                                onDupPhotos = onOpenDuplicatePhotos,
                                onLarge = onOpenLargeFiles
                            )
                            1 -> FoldersTab(r.topFolders)
                            2 -> FilesTab(r.largestFiles, emptyLabel = "20 MB üzeri dosya bulunamadı")
                            3 -> FilesTab(r.recentFiles, emptyLabel = "Son dosya bulunamadı")
                            4 -> EmptyFoldersTab(
                                paths = r.emptyFolders,
                                selected = selectedEmpty,
                                onToggle = { path ->
                                    selectedEmpty = if (path in selectedEmpty) {
                                        selectedEmpty - path
                                    } else {
                                        selectedEmpty + path
                                    }
                                },
                                onSelectAll = {
                                    selectedEmpty = r.emptyFolders.toSet()
                                },
                                onClear = { selectedEmpty = emptySet() }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDeleteDialog(
            title = "Boş klasörleri temizle",
            message = "Seçili boş klasörler 48 saat çöp kutusunda tutulur; geri yükleyebilirsiniz.",
            onConfirm = {
                confirmEmpty = false
                val paths = selectedEmpty.toList()
                scope.launch {
                    var deleted = 0
                    withContext(Dispatchers.IO) {
                        trash.beginBatch()
                        paths.forEach { path ->
                            if (trash.movePathToTrash(path, "Boş klasör")) deleted++
                        }
                    }
                    if (deleted > 0) {
                        CleaningRecorder.record(application, "Boş klasör", 0L, deleted)
                        CleanResultTracker.record("Boş klasör", deleted, 0L)
                        summary = CleanResultTracker.last
                    }
                    Toast.makeText(
                        context,
                        if (deleted > 0) "$deleted klasör taşındı" else "Taşınamadı",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedEmpty = emptySet()
                    runAnalysis()
                }
            },
            onDismiss = { confirmEmpty = false },
            fileCount = selectedEmpty.size,
            totalBytes = 0L,
            moveToTrash = true,
            previewPaths = selectedEmpty.take(6).toList()
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
                    runAnalysis()
                }
            }
        )
    }
}

@Composable
private fun AnalysisSummaryHeader(r: FileAnalysisScanner.AnalysisReport) {
    val cats = r.types.map { it.toCategory() }.filter { it.sizeBytes > 0 }
    Card(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (cats.isNotEmpty()) {
                FileAnalysisPie(cats, Modifier.size(88.dp))
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "${r.storage.usedFormatted} / ${r.storage.totalFormatted}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Boş: ${r.storage.freeFormatted} · %${r.storage.usagePercent.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (r.cancelled) {
                    Text(
                        "Tarama erken durdu; sonuçlar kısmi olabilir.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    "Analiz yereldir; dosyalarınız sunucuya gönderilmez.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TypesTab(
    types: List<FileAnalysisScanner.TypeStat>,
    onOpenAction: (String) -> Unit,
    onDuplicates: () -> Unit,
    onDupPhotos: () -> Unit,
    onLarge: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "Hızlı işlemler",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onDupPhotos, label = { Text("Yinelenen foto") })
                AssistChip(onClick = onDuplicates, label = { Text("Yinelenen dosya") })
                AssistChip(onClick = onLarge, label = { Text("Büyük dosya") })
            }
        }
        if (types.isEmpty()) {
            item {
                Text(
                    "Tür verisi yok",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(types, key = { it.id }) { t ->
            val total = types.sumOf { it.sizeBytes }.coerceAtLeast(1L)
            val pct = (t.sizeBytes * 100f / total).toInt()
            Card(
                onClick = { onOpenAction(t.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(12.dp)) {
                                drawCircle(Color(t.color))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(t.name, fontWeight = FontWeight.SemiBold)
                        }
                        Text(formatBytes(t.sizeBytes), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${t.fileCount} dosya · %$pct",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { (t.sizeBytes.toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = Color(t.color)
                    )
                }
            }
        }
    }
}

@Composable
private fun FoldersTab(folders: List<FileAnalysisScanner.FolderStat>) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (folders.isEmpty()) {
            item {
                Text("Klasör özeti bulunamadı", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(folders, key = { it.name }) { f ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "${f.fileCount} dosya",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(formatBytes(f.sizeBytes), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FilesTab(
    files: List<FileAnalysisScanner.FileEntry>,
    emptyLabel: String
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (files.isEmpty()) {
            item { Text(emptyLabel, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(files, key = { it.path }) { f ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(f.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatBytes(f.sizeBytes)} · ${f.categoryId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        f.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFoldersTab(
    paths: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Text(
                "Yalnızca kullanıcı alanındaki boş klasörler listelenir. Sistem klasörleri korunur.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onSelectAll) { Text("Tümünü seç") }
                TextButton(onClick = onClear) { Text("Temizle") }
            }
        }
        if (paths.isEmpty()) {
            item {
                Text(
                    "Boş klasör bulunamadı",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(paths, key = { it }) { path ->
            val checked = path in selected
            Card(
                onClick = { onToggle(path) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onToggle(path) })
                    Column(Modifier.weight(1f)) {
                        Text(
                            File(path).name,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileAnalysisPie(
    categories: List<StorageCategory>,
    modifier: Modifier = Modifier
) {
    val total = categories.sumOf { it.sizeBytes }.coerceAtLeast(1L).toFloat()
    val colors = categories.map { Color(it.color) }
    val holeColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier) {
        val diameter = minOf(size.width, size.height) * 0.92f
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val pieSize = Size(diameter, diameter)
        var start = -90f
        categories.forEachIndexed { index, cat ->
            val sweep = (cat.sizeBytes / total) * 360f
            drawArc(
                color = colors.getOrElse(index) { Color.Gray },
                startAngle = start,
                sweepAngle = sweep.coerceAtLeast(1.2f),
                useCenter = true,
                topLeft = topLeft,
                size = pieSize
            )
            start += sweep
        }
        val hole = diameter * 0.52f
        drawCircle(
            color = holeColor,
            radius = hole / 2f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}
