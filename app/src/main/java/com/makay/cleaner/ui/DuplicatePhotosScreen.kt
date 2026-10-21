package com.makay.cleaner.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
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
    val scanner = remember { DuplicatePhotoScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    val prefs = remember { DuplicatePhotoPrefs(application) }

    var tabIndex by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var exactGroups by remember { mutableStateOf<List<DuplicatePhotoScanner.PhotoGroup>>(emptyList()) }
    var similarGroups by remember { mutableStateOf<List<DuplicatePhotoScanner.PhotoGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("Hazırlanıyor…") }
    var progressCurrent by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(1) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var cancelledEarly by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var showExclude by remember { mutableStateOf(false) }
    var showProtect by remember { mutableStateOf(false) }
    var showFormats by remember { mutableStateOf(false) }
    var showSelectionAssistant by remember { mutableStateOf(false) }
    var previewPhoto by remember { mutableStateOf<DuplicatePhotoScanner.PhotoItem?>(null) }
    var previewGroup by remember { mutableStateOf<DuplicatePhotoScanner.PhotoGroup?>(null) }
    var compareLeft by remember { mutableStateOf<DuplicatePhotoScanner.PhotoItem?>(null) }
    var compareRight by remember { mutableStateOf<DuplicatePhotoScanner.PhotoItem?>(null) }
    var lockedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

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
    var buckets by remember { mutableStateOf<List<DuplicatePhotoScanner.BucketInfo>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()
    val hasStoragePermission = rememberStoragePermissionState()
    val cancelFlag = remember { AtomicBoolean(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

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
        scanJob?.cancel()
        cancelFlag.set(false)
        cancelledEarly = false
        hasScanned = true
        scanJob = scope.launch {
            loading = true
            progressText = "Tarama başlıyor…"
            progressCurrent = 0
            progressTotal = 1
            SystemFileGuard.consumeSkippedCount()
            val options = currentOptions()
            val mainHandler = Handler(Looper.getMainLooper())
            val listener = DuplicatePhotoScanner.ProgressListener { phase, cur, tot ->
                mainHandler.post {
                    progressText = phase
                    progressCurrent = cur
                    progressTotal = tot.coerceAtLeast(1)
                }
            }
            exactGroups = withContext(Dispatchers.IO) {
                scanner.scanExact(options, cancelFlag, listener)
            }
            if (cancelFlag.get()) {
                cancelledEarly = true
                loading = false
                return@launch
            }
            similarGroups = withContext(Dispatchers.IO) {
                scanner.scanSimilar(options, cancelFlag, listener)
            }
            cancelledEarly = cancelFlag.get()
            selectedPaths = applyAutoMark(exactGroups + similarGroups)
            loading = false
        }
    }

    fun cancelScan() {
        cancelFlag.set(true)
        scanJob?.cancel()
        loading = false
        cancelledEarly = true
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelFlag.set(true)
            scanJob?.cancel()
        }
    }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            buckets = withContext(Dispatchers.IO) { scanner.listBuckets() }
        }
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
                        Text("Yinelenen fotoğraflar", fontWeight = FontWeight.Bold)
                        if (hasScanned && !loading) {
                            Text(
                                "${exactGroups.size + similarGroups.size} grup · ${formatBytes(
                                    (exactGroups + similarGroups).sumOf { it.wasteBytes }
                                )} kazanılabilir",
                                style = MaterialTheme.typography.labelSmall
                            )
                        } else if (!hasScanned) {
                            Text(
                                "Tara → gözden geçir → çöp kutusuna taşı",
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
                        loading -> TextButton(onClick = { cancelScan() }) { Text("İptal") }
                        hasScanned -> TextButton(
                            onClick = {
                                if (hasStoragePermission) runScan()
                                else PermissionHelper.requestStoragePermission(context)
                            }
                        ) { Text("Yenile") }
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
                            Text("$selectedInTab seçili", fontWeight = FontWeight.Bold)
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
                                "${(progressFraction * 100).toInt()}% · $progressCurrent / $progressTotal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                            Text(
                                "Tarama cihazınızda kalır; fotoğraflar yüklenmez. Sistem dosyalarına dokunulmaz.",
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
                                hasScanned = false
                                exactGroups = emptyList()
                                similarGroups = emptyList()
                            }
                        )
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
                                        hasScanned = false
                                    }) { Text("Tarama ayarlarını değiştir") }
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
                                            }) { Text("Akıllı seç") }
                                            TextButton(onClick = {
                                                val paths = activeGroups.flatMap { g ->
                                                    g.photos.map { it.path }
                                                }.toSet()
                                                selectedPaths = selectedPaths - paths
                                            }) { Text("Seçimi temizle") }
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
                                            }) { Text("Seçimi tersine çevir") }
                                            TextButton(onClick = { showSelectionAssistant = true }) {
                                                Text("Seçim asistanı")
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

@Composable
private fun ScanSetupPanel(
    scanMode: String,
    onScanMode: (String) -> Unit,
    matchingLevel: Float,
    onMatchingLevel: (Float) -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    timeIntervalSec: Int,
    onTimeInterval: (Int) -> Unit,
    gpsRadius: Int,
    onGpsRadius: (Int) -> Unit,
    includeRotated: Boolean,
    onIncludeRotated: (Boolean) -> Unit,
    selectedBucket: String?,
    onSelectBucket: (String?) -> Unit,
    buckets: List<DuplicatePhotoScanner.BucketInfo>,
    excludedBuckets: Set<String>,
    protectedBuckets: Set<String>,
    allowedFormats: Set<String>,
    onOpenExclude: () -> Unit,
    onOpenProtect: () -> Unit,
    onOpenFormats: () -> Unit,
    onOpenSelectionAssistant: () -> Unit,
    onStartScan: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "1. Tarama kapsamı",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Galeri cihazınızda taranır; hiçbir fotoğraf sunucuya gönderilmez.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Hızlı", scanMode == DuplicatePhotoPrefs.MODE_QUICK) {
                onScanMode(DuplicatePhotoPrefs.MODE_QUICK)
            }
            ModeChip("Kamera", scanMode == DuplicatePhotoPrefs.MODE_CAMERA) {
                onScanMode(DuplicatePhotoPrefs.MODE_CAMERA)
            }
            ModeChip("Klasör", scanMode == DuplicatePhotoPrefs.MODE_FOLDER) {
                onScanMode(DuplicatePhotoPrefs.MODE_FOLDER)
            }
            ModeChip("Tam", scanMode == DuplicatePhotoPrefs.MODE_FULL) {
                onScanMode(DuplicatePhotoPrefs.MODE_FULL)
            }
        }
        Text(
            when (scanMode) {
                DuplicatePhotoPrefs.MODE_QUICK -> "Son fotoğraflarda hızlı tarama"
                DuplicatePhotoPrefs.MODE_CAMERA -> "Yalnızca kamera / DCIM klasörü"
                DuplicatePhotoPrefs.MODE_FOLDER -> "Seçtiğiniz albüm klasörü"
                else -> "Tüm galeri (harici depolama dahil)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (scanMode == DuplicatePhotoPrefs.MODE_FOLDER) {
            if (buckets.isEmpty()) {
                Text("Albüm bulunamadı", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(buckets.take(40), key = { it.name }) { b ->
                        FilterChip(
                            selected = selectedBucket == b.name,
                            onClick = { onSelectBucket(b.name) },
                            label = { Text("${b.name} (${b.count})") }
                        )
                    }
                }
            }
        }

        Text(
            "2. Benzerlik seviyesi",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        val levelLabel = when (matchingLevel.toInt().coerceIn(0, 2)) {
            0 -> "Düşük — daha fazla benzer sonuç"
            2 -> "Yüksek — neredeyse aynı kareler"
            else -> "Orta — dengeli (önerilen)"
        }
        Text(levelLabel, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = matchingLevel,
            onValueChange = { onMatchingLevel(it) },
            valueRange = 0f..2f,
            steps = 1
        )

        TextButton(onClick = onToggleAdvanced) {
            Text(if (showAdvanced) "Gelişmiş ayarları gizle" else "Gelişmiş ayarlar")
        }
        if (showAdvanced) {
            Text("Zaman aralığı: ${if (timeIntervalSec == 0) "kapalı" else "$timeIntervalSec sn"}")
            Slider(
                value = timeIntervalSec.toFloat().coerceIn(0f, 3600f),
                onValueChange = { onTimeInterval(it.toInt()) },
                valueRange = 0f..3600f
            )
            Text("GPS yakınlığı: ${if (gpsRadius == 0) "kapalı" else "$gpsRadius m"}")
            Slider(
                value = gpsRadius.toFloat(),
                onValueChange = { onGpsRadius(it.toInt()) },
                valueRange = 0f..100f
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Döndürülmüş / aynalanmış kopyalar")
                Switch(checked = includeRotated, onCheckedChange = onIncludeRotated)
            }
            OutlinedButton(onClick = onOpenExclude, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (excludedBuckets.isEmpty()) "Hariç tutulan klasörler"
                    else "Hariç tutulanlar (${excludedBuckets.size})"
                )
            }
            OutlinedButton(onClick = onOpenProtect, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (protectedBuckets.isEmpty()) "Korumalı klasörler"
                    else "Korumalı (${protectedBuckets.size}) — auto-mark dokunmaz"
                )
            }
            OutlinedButton(onClick = onOpenFormats, modifier = Modifier.fillMaxWidth()) {
                Text("Format filtresi (${allowedFormats.size})")
            }
            OutlinedButton(onClick = onOpenSelectionAssistant, modifier = Modifier.fillMaxWidth()) {
                Text("Seçim asistanı (öncelik kuralları)")
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onStartScan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = scanMode != DuplicatePhotoPrefs.MODE_FOLDER || !selectedBucket.isNullOrBlank()
        ) {
            Text("Tara", fontWeight = FontWeight.Bold)
        }
        Text(
            "Sonuçlarda fazlalıklar seçim asistanı kurallarına göre işaretlenir; silmeden önce önizleyin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScanSetupCompact(
    scanMode: String,
    matchingLevel: Int,
    onEdit: () -> Unit
) {
    val modeLabel = when (scanMode) {
        DuplicatePhotoPrefs.MODE_QUICK -> "Hızlı"
        DuplicatePhotoPrefs.MODE_CAMERA -> "Kamera"
        DuplicatePhotoPrefs.MODE_FOLDER -> "Klasör"
        else -> "Tam"
    }
    val matchLabel = when (matchingLevel) {
        0 -> "Benzerlik: düşük"
        2 -> "Benzerlik: yüksek"
        else -> "Benzerlik: orta"
    }
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$modeLabel · $matchLabel", style = MaterialTheme.typography.bodyMedium)
            Text("Ayarlar", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RowScope.ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun ExcludeBucketsDialog(
    title: String,
    buckets: List<DuplicatePhotoScanner.BucketInfo>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var local by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(buckets, key = { it.name }) { b ->
                    val checked = b.name in local
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                local = if (checked) local - b.name else local + b.name
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                local = if (it) local + b.name else local - b.name
                            }
                        )
                        Text("${b.name} (${b.count})")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(local) }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
private fun FormatFilterDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    var local by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dosya formatları") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(DuplicatePhotoPrefs.ALL_FORMAT_OPTIONS, key = { it.first }) { (ext, label) ->
                    val checked = ext in local ||
                        (ext == "jpg" && "jpeg" in local) ||
                        (ext == "heic" && "heif" in local)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                local = if (checked) local - ext else local + ext
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                local = if (it) local + ext else local - ext
                            }
                        )
                        Text("$label (.$ext)")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(local.ifEmpty { DuplicatePhotoPrefs.DEFAULT_FORMATS })
            }) { Text("Kaydet") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
private fun SelectionAssistantDialog(
    rules: List<SelectionAssistant.Rule>,
    onDismiss: () -> Unit,
    onConfirm: (List<SelectionAssistant.Rule>) -> Unit
) {
    var local by remember { mutableStateOf(rules) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seçim asistanı") },
        text = {
            Column {
                Text(
                    "Öncelik sırası (üstteki kural daha baskın). Tutulacak kopya buna göre seçilir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                local.forEachIndexed { index, rule ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${index + 1}. ${rule.label}", fontWeight = FontWeight.SemiBold)
                                Text(rule.detail, style = MaterialTheme.typography.labelSmall)
                            }
                            Column {
                                TextButton(
                                    onClick = {
                                        if (index > 0) {
                                            val m = local.toMutableList()
                                            val tmp = m[index - 1]
                                            m[index - 1] = m[index]
                                            m[index] = tmp
                                            local = m
                                        }
                                    },
                                    enabled = index > 0,
                                    contentPadding = PaddingValues(4.dp)
                                ) { Text("↑") }
                                TextButton(
                                    onClick = {
                                        if (index < local.lastIndex) {
                                            val m = local.toMutableList()
                                            val tmp = m[index + 1]
                                            m[index + 1] = m[index]
                                            m[index] = tmp
                                            local = m
                                        }
                                    },
                                    enabled = index < local.lastIndex,
                                    contentPadding = PaddingValues(4.dp)
                                ) { Text("↓") }
                            }
                        }
                    }
                }
                TextButton(onClick = { local = SelectionAssistant.defaultOrder }) {
                    Text("Varsayılana dön")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(local) }) { Text("Uygula") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
        }
    )
}

@Composable
private fun PhotoPreviewDialog(
    photo: DuplicatePhotoScanner.PhotoItem,
    group: DuplicatePhotoScanner.PhotoGroup?,
    selectedPaths: Set<String>,
    lockedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onSelect: (DuplicatePhotoScanner.PhotoItem) -> Unit,
    onCompareWith: (DuplicatePhotoScanner.PhotoItem) -> Unit,
    onDismiss: () -> Unit
) {
    val dateFmt = remember {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.forLanguageTag("tr"))
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Kapat", color = Color.White)
                    }
                    Row {
                        if (group != null && group.photos.size > 1) {
                            val other = group.photos.firstOrNull { it.path != photo.path }
                            if (other != null) {
                                TextButton(onClick = { onCompareWith(other) }) {
                                    Text("Karşılaştır", color = Color.White)
                                }
                            }
                        }
                        FilterChip(
                            selected = photo.path in selectedPaths,
                            onClick = { onToggle(photo.path) },
                            enabled = photo.path !in lockedPaths,
                            label = {
                                Text(
                                    when {
                                        photo.path in lockedPaths -> "Kilitli"
                                        photo.path in selectedPaths -> "Seçili (silinecek)"
                                        else -> "Tutulacak"
                                    }
                                )
                            }
                        )
                    }
                }
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.displayName,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    contentScale = ContentScale.Fit
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(16.dp)
                ) {
                    Text(photo.displayName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(
                        buildString {
                            append("${photo.width}×${photo.height}")
                            append(" · ${formatBytes(photo.sizeBytes)}")
                            if (photo.bucketName.isNotBlank()) append(" · ${photo.bucketName}")
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    val whenMs = photo.dateTaken.takeIf { it > 0 } ?: photo.dateAdded
                    if (whenMs > 0) {
                        Text(
                            dateFmt.format(Date(whenMs)),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Text(
                        photo.path,
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (group != null && group.photos.size > 1) {
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(group.photos, key = { it.path }) { p ->
                                AsyncImage(
                                    model = p.uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            if (p.path == photo.path) 2.dp else 0.dp,
                                            Color.White,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelect(p) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBySideCompareDialog(
    left: DuplicatePhotoScanner.PhotoItem,
    right: DuplicatePhotoScanner.PhotoItem,
    selectedPaths: Set<String>,
    lockedPaths: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Yan yana karşılaştırma", color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Kapat", color = Color.White) }
                }
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ComparePane(
                        photo = left,
                        selected = left.path in selectedPaths,
                        locked = left.path in lockedPaths,
                        onToggle = { onToggle(left.path) },
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                    )
                    ComparePane(
                        photo = right,
                        selected = right.path in selectedPaths,
                        locked = right.path in lockedPaths,
                        onToggle = { onToggle(right.path) },
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparePane(
    photo: DuplicatePhotoScanner.PhotoItem,
    selected: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.height(6.dp))
        Text(
            photo.displayName,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            buildString {
                append("${photo.width}×${photo.height}")
                append(" · ${formatBytes(photo.sizeBytes)}")
                if (photo.hasMetadata) append(" · EXIF")
            },
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelSmall
        )
        FilterChip(
            selected = selected,
            onClick = onToggle,
            enabled = !locked,
            label = {
                Text(
                    when {
                        locked -> "Kilitli"
                        selected -> "Silinecek"
                        else -> "Tut"
                    }
                )
            }
        )
    }
}

@Composable
private fun DuplicatePhotoGroupCard(
    group: DuplicatePhotoScanner.PhotoGroup,
    selectedPaths: Set<String>,
    lockedPaths: Set<String>,
    protectedBuckets: Set<String>,
    onToggle: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onKeepBest: () -> Unit,
    onPreview: (DuplicatePhotoScanner.PhotoItem) -> Unit,
    onCompare: (DuplicatePhotoScanner.PhotoItem, DuplicatePhotoScanner.PhotoItem) -> Unit
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
                Column(Modifier.weight(1f)) {
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
                Row {
                    if (group.photos.size >= 2) {
                        TextButton(onClick = {
                            onCompare(group.photos[0], group.photos[1])
                        }) { Text("Karşılaştır") }
                    }
                    TextButton(onClick = onKeepBest) { Text("En iyiyi tut") }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(group.photos, key = { it.path }) { photo ->
                    val selected = photo.path in selectedPaths
                    val locked = photo.path in lockedPaths
                    val protected = photo.bucketName in protectedBuckets
                    val isKeep = !selected && !locked
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(108.dp)
                    ) {
                        Box {
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = photo.displayName,
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPreview(photo) }
                                    .then(
                                        when {
                                            locked -> Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.tertiary,
                                                RoundedCornerShape(12.dp)
                                            )
                                            selected -> Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.error,
                                                RoundedCornerShape(12.dp)
                                            )
                                            else -> Modifier
                                        }
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { onToggle(photo.path) },
                                enabled = !locked && !protected,
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                        }
                        Text(
                            when {
                                locked -> "🔒 Kilitli"
                                protected -> "Korumalı"
                                isKeep -> "Tutulacak"
                                else -> if (photo.width > 0) "${photo.width}×${photo.height}"
                                else formatBytes(photo.sizeBytes)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                locked || isKeep -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1
                        )
                        TextButton(
                            onClick = { onToggleLock(photo.path) },
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
