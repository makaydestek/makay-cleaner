package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val trash = remember { RecycleBinRepository(application) }
    val vm: FileAnalysisViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = FileAnalysisViewModel.Factory(application)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val report = state.report
    val loading = state.loading
    val progressText = state.progressText
    val progressCurrent = state.progressCurrent
    val progressTotal = state.progressTotal

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedEmpty by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()
    val widthClass = rememberWidthClass()

    fun runAnalysis() {
        if (!hasPermission) return
        vm.runAnalysis()
    }

    fun cancelAnalysis() {
        vm.cancel()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && state.report == null && !state.loading) vm.runAnalysis()
    }

    val fraction = (progressCurrent.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.txt_dosya_analizi), fontWeight = FontWeight.Bold)
                        report?.let { r ->
                            if (!loading) {
                                Text(
                                    stringResource(R.string.txt_1_s_kullan_l_yor_2_s_dosya, r.storage.usedFormatted, r.scannedFileEstimate),
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
                        TextButton(onClick = { cancelAnalysis() }) { Text(stringResource(R.string.txt_ptal)) }
                    } else {
                        TextButton(onClick = {
                            if (hasPermission) runAnalysis()
                            else PermissionHelper.requestStoragePermission(context)
                        }) { Text(stringResource(R.string.txt_yenile)) }
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
                        Text(stringResource(R.string.txt_1_s_bo_klas_r, selectedEmpty.size), fontWeight = FontWeight.Bold)
                        Button(
                            onClick = { confirmEmpty = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(stringResource(R.string.txt_p_kutusuna_ta)) }
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
                            stringResource(R.string.txt_1_s_3, (fraction * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        )
                        Text(
                            stringResource(R.string.txt_sistem_ve_korumal_klas_rler_atlan_r),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                report == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.txt_analiz_hen_z_al_t_r_lmad))
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { runAnalysis() }) { Text(stringResource(R.string.txt_analiz_et)) }
                    }
                }

                else -> {
                    val r = report!!
                    Column(contentMod.fillMaxSize()) {
                        AnalysisSummaryHeader(r)
                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.txt_t_rler)) })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.txt_klas_rler)) })
                            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.txt_b_y_k)) })
                            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text(stringResource(R.string.txt_son)) })
                            Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text(stringResource(R.string.txt_bo)) })
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
                    Toast.makeText(context, context.getString(R.string.msg_items_restored, n), Toast.LENGTH_SHORT).show()
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
                    stringResource(R.string.txt_1_s_2_s_8, r.storage.usedFormatted, r.storage.totalFormatted),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.txt_bo_1_s_2_s, r.storage.freeFormatted, r.storage.usagePercent.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (r.cancelled) {
                    Text(
                        stringResource(R.string.txt_tarama_erken_durdu_sonu_lar_k_smi_olabilir),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text(
                    stringResource(R.string.txt_analiz_yereldir_dosyalar_n_z_sunucuya_g_nder),
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
                stringResource(R.string.txt_h_zl_i_lemler),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onDupPhotos, label = { Text(stringResource(R.string.txt_yinelenen_foto)) })
                AssistChip(onClick = onDuplicates, label = { Text(stringResource(R.string.txt_yinelenen_dosya)) })
                AssistChip(onClick = onLarge, label = { Text(stringResource(R.string.txt_b_y_k_dosya)) })
            }
        }
        if (types.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.txt_t_r_verisi_yok),
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
                        stringResource(R.string.txt_1_s_dosya_2_s_3, t.fileCount, pct),
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
                Text(stringResource(R.string.txt_klas_r_zeti_bulunamad), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            stringResource(R.string.txt_1_s_dosya, f.fileCount),
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
                        stringResource(R.string.txt_1_s_2_s_9, formatBytes(f.sizeBytes), f.categoryId),
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
                stringResource(R.string.txt_yaln_zca_kullan_c_alan_ndaki_bo_klas_rler_li),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onSelectAll) { Text(stringResource(R.string.txt_t_m_n_se_2)) }
                TextButton(onClick = onClear) { Text(stringResource(R.string.txt_temizle_2)) }
            }
        }
        if (paths.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.txt_bo_klas_r_bulunamad),
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
