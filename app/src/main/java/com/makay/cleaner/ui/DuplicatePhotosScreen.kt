package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.DuplicatePhotoScanner
import com.makay.cleaner.scanner.SelectionAssistant
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.SystemFileGuard
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tek yinelenen-foto ekranı: tarama ayarları → gözden geçirme → güvenli temizlik.
 * Medya hub ve Medya sekmesi aynı deneyime bağlanır.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatePhotosScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    initialTab: Int = 0
) {
    val trash = remember { RecycleBinRepository(application) }
    val prefs = remember { DuplicatePhotoPrefs(application) }
    val vm: DuplicatePhotosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = DuplicatePhotosViewModel.Factory(application)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val exactGroups = state.exactGroups
    val similarGroups = state.similarGroups
    val loading = state.loading
    val hasScanned = state.hasScanned
    val cancelledEarly = state.cancelledEarly
    val progressText = state.progressText
    val progressCurrent = state.progressCurrent
    val progressTotal = state.progressTotal
    val buckets = state.buckets

    var tabIndex by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var selectedPaths by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var showExclude by rememberSaveable { mutableStateOf(false) }
    var showProtect by rememberSaveable { mutableStateOf(false) }
    var showFormats by rememberSaveable { mutableStateOf(false) }
    var showSelectionAssistant by rememberSaveable { mutableStateOf(false) }
    var previewPhoto by remember { mutableStateOf<DuplicatePhotoScanner.PhotoItem?>(null) }
    var previewGroup by remember { mutableStateOf<DuplicatePhotoScanner.PhotoGroup?>(null) }
    var compareLeft by remember { mutableStateOf<DuplicatePhotoScanner.PhotoItem?>(null) }
    var compareRight by remember { mutableStateOf<DuplicatePhotoScanner.PhotoItem?>(null) }
    var lockedPaths by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }

    var scanMode by remember { mutableStateOf(prefs.scanMode) }
    var matchingLevel by remember { mutableFloatStateOf(prefs.matchingLevel.toFloat()) }
    var timeIntervalSec by remember { mutableIntStateOf(prefs.timeIntervalSec) }
    var gpsRadius by remember { mutableIntStateOf(prefs.gpsRadiusMeters) }
    var includeRotated by remember { mutableStateOf(prefs.includeRotatedFlipped) }
    var selectedBucket by remember { mutableStateOf(prefs.selectedBucket) }
    var excludedBuckets by remember { mutableStateOf(prefs.excludedBuckets) }
    var protectedBuckets by remember { mutableStateOf(prefs.protectedBuckets) }
    var allowedFormats by remember { mutableStateOf(prefs.allowedFormats) }
    var selectionRules by remember { mutableStateOf(prefs.selectionRules) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()
    val hasStoragePermission = rememberStoragePermissionState()

    fun persistPrefs() {
        prefs.scanMode = scanMode
        prefs.matchingLevel = matchingLevel.toInt().coerceIn(0, 2)
        prefs.timeIntervalSec = timeIntervalSec
        prefs.gpsRadiusMeters = gpsRadius
        prefs.includeRotatedFlipped = includeRotated
        prefs.selectedBucket = selectedBucket
        prefs.excludedBuckets = excludedBuckets
        prefs.protectedBuckets = protectedBuckets
        prefs.allowedFormats = allowedFormats
        prefs.selectionRulesCsv = SelectionAssistant.serializeRules(selectionRules)
    }

    fun applyAutoMark(groups: List<DuplicatePhotoScanner.PhotoGroup>): Set<String> {
        return SelectionAssistant.autoMarkAll(
            groups = groups,
            rules = selectionRules,
            protectedBuckets = protectedBuckets,
            lockedPaths = lockedPaths,
            folderPriority = prefs.folderPriority
        )
    }

    fun currentOptions(): DuplicatePhotoScanner.ScanOptions {
        persistPrefs()
        return DuplicatePhotoScanner.ScanOptions.fromPrefs(prefs)
    }

    fun runScan() {
        if (!hasStoragePermission) return
        vm.scan(currentOptions())
    }

    fun cancelScan() {
        vm.cancel()
    }

    // Tarama tamamlandığında (veya döndürme sonrası VM'den geldiğinde)
    // akıllı seçimi bir kez uygula.
    LaunchedEffect(state.scanId) {
        if (state.scanId > 0) {
            selectedPaths = applyAutoMark(state.allGroups)
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) vm.loadBuckets()
    }

    val activeGroups = if (tabIndex == 0) exactGroups else similarGroups
    val selectedInTab = activeGroups.flatMap { it.photos }.count { it.path in selectedPaths }
    val selectedBytes = activeGroups.flatMap { it.photos }
        .filter { it.path in selectedPaths }
        .sumOf { it.sizeBytes }
    val progressFraction = (progressCurrent.toFloat() / progressTotal.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.txt_yinelenen_foto_raflar), fontWeight = FontWeight.Bold)
                        if (com.makay.cleaner.util.PermissionHelper.hasPartialVisualMediaPermission(context)) {
                            Text(
                                stringResource(R.string.media_scope_partial_photos),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (hasScanned && !loading) {
                            Text(
                                "${exactGroups.size + similarGroups.size} grup · ${formatBytes(
                                    (exactGroups + similarGroups).sumOf { it.wasteBytes }
                                )} kazanılabilir",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else if (!hasScanned) {
                            Text(
                                stringResource(R.string.txt_tara_g_zden_ge_ir_p_kutusuna_ta),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    when {
                        loading -> TextButton(onClick = { cancelScan() }) { Text(stringResource(R.string.txt_ptal)) }
                        hasScanned -> TextButton(
                            onClick = {
                                if (hasStoragePermission) runScan()
                                else PermissionHelper.requestStoragePermission(context)
                            }
                        ) { Text(stringResource(R.string.txt_yenile)) }
                    }
                }
            )
        },
        bottomBar = {
            if (hasStoragePermission && !loading && hasScanned && selectedInTab > 0) {
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
                            Text(stringResource(R.string.txt_1_s_se_ili, selectedInTab), fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.txt_p_kutusuna_ta))
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
                when {
                    !hasStoragePermission -> PermissionWarningCard(
                        "Yinelenen fotoğrafları taramak ve seçtiklerinizi çöp kutusuna taşımak için depolama erişimi gerekir."
                    ) { PermissionHelper.requestStoragePermission(context) }

                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            CircularProgressIndicator(progress = { progressFraction })
                            Spacer(Modifier.height(12.dp))
                            Text(progressText, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(R.string.txt_1_s_2_s_3_s, (progressFraction * 100).toInt(), progressCurrent, progressTotal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                            Text(
                                stringResource(R.string.txt_tarama_cihaz_n_zda_kal_r_foto_raflar_y_klenm),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }

                    !hasScanned -> {
                        ScanSetupPanel(
                            scanMode = scanMode,
                            onScanMode = { scanMode = it },
                            matchingLevel = matchingLevel,
                            onMatchingLevel = { matchingLevel = it },
                            showAdvanced = showAdvanced,
                            onToggleAdvanced = { showAdvanced = !showAdvanced },
                            timeIntervalSec = timeIntervalSec,
                            onTimeInterval = { timeIntervalSec = it },
                            gpsRadius = gpsRadius,
                            onGpsRadius = { gpsRadius = it },
                            includeRotated = includeRotated,
                            onIncludeRotated = { includeRotated = it },
                            selectedBucket = selectedBucket,
                            onSelectBucket = { selectedBucket = it },
                            buckets = buckets,
                            excludedBuckets = excludedBuckets,
                            protectedBuckets = protectedBuckets,
                            allowedFormats = allowedFormats,
                            onOpenExclude = { showExclude = true },
                            onOpenProtect = { showProtect = true },
                            onOpenFormats = { showFormats = true },
                            onOpenSelectionAssistant = { showSelectionAssistant = true },
                            onStartScan = { runScan() }
                        )
                    }

                    else -> {
                        ScanSetupCompact(
                            scanMode = scanMode,
                            matchingLevel = matchingLevel.toInt(),
                            onEdit = {
                                vm.reset()
                            }
                        )
                        TabRow(selectedTabIndex = tabIndex) {
                            Tab(
                                selected = tabIndex == 0,
                                onClick = { tabIndex = 0 },
                                text = { Text(stringResource(R.string.txt_ayn_1_s, exactGroups.size)) }
                            )
                            Tab(
                                selected = tabIndex == 1,
                                onClick = { tabIndex = 1 },
                                text = { Text(stringResource(R.string.txt_benzer_1_s, similarGroups.size)) }
                            )
                        }
                        if (activeGroups.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = when {
                                            cancelledEarly -> "Tarama iptal edildi veya zaman aşımına uğradı"
                                            tabIndex == 0 -> "Aynı (birebir) fotoğraf bulunamadı"
                                            else -> "Benzer fotoğraf grubu bulunamadı"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    OutlinedButton(onClick = {
                                        vm.reset()
                                    }) { Text(stringResource(R.string.txt_tarama_ayarlar_n_de_i_tir)) }
                                }
                            }
                        } else {
                            LazyColumn(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 88.dp)
                            ) {
                                item {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TextButton(onClick = {
                                                selectedPaths = selectedPaths + applyAutoMark(activeGroups)
                                            }) { Text(stringResource(R.string.txt_ak_ll_se)) }
                                            TextButton(onClick = {
                                                val paths = activeGroups.flatMap { g ->
                                                    g.photos.map { it.path }
                                                }.toSet()
                                                selectedPaths = selectedPaths - paths
                                            }) { Text(stringResource(R.string.txt_se_imi_temizle)) }
                                        }
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TextButton(onClick = {
                                                val all = activeGroups.flatMap { g -> g.photos.map { it.path } }.toSet()
                                                val unlocked = all - lockedPaths
                                                val currently = selectedPaths.intersect(unlocked)
                                                selectedPaths = (selectedPaths - unlocked) + (unlocked - currently)
                                            }) { Text(stringResource(R.string.txt_se_imi_tersine_evir)) }
                                            TextButton(onClick = { showSelectionAssistant = true }) {
                                                Text(stringResource(R.string.txt_se_im_asistan))
                                            }
                                        }
                                    }
                                }
                                items(activeGroups, key = { it.id }) { group ->
                                    DuplicatePhotoGroupCard(
                                        group = group,
                                        selectedPaths = selectedPaths,
                                        lockedPaths = lockedPaths,
                                        protectedBuckets = protectedBuckets,
                                        onToggle = { path ->
                                            if (path in lockedPaths) return@DuplicatePhotoGroupCard
                                            selectedPaths = if (path in selectedPaths) {
                                                selectedPaths - path
                                            } else {
                                                selectedPaths + path
                                            }
                                        },
                                        onToggleLock = { path ->
                                            lockedPaths = if (path in lockedPaths) {
                                                lockedPaths - path
                                            } else {
                                                lockedPaths + path
                                            }
                                            selectedPaths = selectedPaths - path
                                        },
                                        onKeepBest = {
                                            val result = SelectionAssistant.markForDeletion(
                                                photos = group.photos,
                                                rules = selectionRules,
                                                protectedBuckets = protectedBuckets,
                                                lockedPaths = lockedPaths,
                                                folderPriority = prefs.folderPriority
                                            )
                                            val keep = result.keepPath
                                            if (keep != null) {
                                                selectedPaths = (selectedPaths - keep) + result.deletePaths
                                            }
                                        },
                                        onPreview = { photo ->
                                            previewGroup = group
                                            previewPhoto = photo
                                        },
                                        onCompare = { a, b ->
                                            compareLeft = a
                                            compareRight = b
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExclude) {
        ExcludeBucketsDialog(
            title = "Hariç tutulan klasörler",
            buckets = buckets,
            selected = excludedBuckets,
            onDismiss = { showExclude = false },
            onConfirm = {
                excludedBuckets = it
                prefs.excludedBuckets = it
                showExclude = false
            }
        )
    }
    if (showProtect) {
        ExcludeBucketsDialog(
            title = "Korumalı klasörler",
            buckets = buckets,
            selected = protectedBuckets,
            onDismiss = { showProtect = false },
            onConfirm = {
                protectedBuckets = it
                prefs.protectedBuckets = it
                showProtect = false
            }
        )
    }
    if (showFormats) {
        FormatFilterDialog(
            selected = allowedFormats,
            onDismiss = { showFormats = false },
            onConfirm = {
                allowedFormats = it
                prefs.allowedFormats = it
                showFormats = false
            }
        )
    }
    if (showSelectionAssistant) {
        SelectionAssistantDialog(
            rules = selectionRules,
            onDismiss = { showSelectionAssistant = false },
            onConfirm = { rules ->
                selectionRules = rules
                prefs.selectionRulesCsv = SelectionAssistant.serializeRules(rules)
                showSelectionAssistant = false
                if (hasScanned) {
                    selectedPaths = applyAutoMark(exactGroups + similarGroups)
                }
            }
        )
    }

    previewPhoto?.let { photo ->
        PhotoPreviewDialog(
            photo = photo,
            group = previewGroup,
            selectedPaths = selectedPaths,
            lockedPaths = lockedPaths,
            onToggle = { path ->
                if (path in lockedPaths) return@PhotoPreviewDialog
                selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
            },
            onSelect = { previewPhoto = it },
            onCompareWith = { other ->
                compareLeft = photo
                compareRight = other
            },
            onDismiss = {
                previewPhoto = null
                previewGroup = null
            }
        )
    }

    val left = compareLeft
    val right = compareRight
    if (left != null && right != null) {
        SideBySideCompareDialog(
            left = left,
            right = right,
            selectedPaths = selectedPaths,
            lockedPaths = lockedPaths,
            onToggle = { path ->
                if (path in lockedPaths) return@SideBySideCompareDialog
                selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
            },
            onDismiss = {
                compareLeft = null
                compareRight = null
            }
        )
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
                        trash.beginBatch()
                        activeGroups.flatMap { it.photos }
                            .filter { it.path in selectedPaths }
                            .forEach { p ->
                                val size = p.sizeBytes
                                val ok = trash.movePathToTrash(p.uri.toString(), "Yinelenen foto")
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
                    AppNotice.show(if (deleted > 0) "$deleted foto çöp kutusuna taşındı" else "Silinemedi")
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
            onDismiss = { summary = null },
            onUndo = {
                scope.launch {
                    val (n, _) = withContext(Dispatchers.IO) { trash.undoLastBatch() }
                    AppNotice.show(context.getString(R.string.msg_items_restored, n))
                    runScan()
                }
            }
        )
    }
}
