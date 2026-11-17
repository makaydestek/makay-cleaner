package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val trash = remember { RecycleBinRepository(application) }
    val prefs = remember { DuplicatePhotoPrefs(application) }
    val vm: DuplicateVideosViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = DuplicateVideosViewModel.Factory(application)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val groups = state.groups
    val loading = state.loading
    val hasScanned = state.hasScanned
    val progressText = state.progressText
    val progressCurrent = state.progressCurrent
    val progressTotal = state.progressTotal
    val buckets = state.buckets

    var selectedPaths by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var lockedPaths by rememberSaveable(stateSaver = stringSetSaver) {
        mutableStateOf<Set<String>>(emptySet())
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var scanMode by rememberSaveable { mutableStateOf(prefs.scanMode) }
    var selectedBucket by rememberSaveable { mutableStateOf(prefs.selectedBucket) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    fun runScan() {
        if (!hasPermission) return
        vm.scan(scanMode, selectedBucket)
    }

    // Tarama tamamlanınca varsayılan seçimi uygula (en iyi hariç).
    LaunchedEffect(state.scanId) {
        if (state.scanId == 0) return@LaunchedEffect
        val defaults = mutableSetOf<String>()
        state.groups.forEach { g ->
            g.videos.drop(1).forEach { v ->
                if (v.path !in lockedPaths && v.bucketName !in prefs.protectedBuckets) {
                    defaults.add(v.path)
                }
            }
        }
        selectedPaths = defaults
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.loadBuckets()
    }

    val selectedCount = groups.flatMap { it.videos }.count { it.path in selectedPaths }
    val selectedBytes = groups.flatMap { it.videos }.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
    val fraction = (progressCurrent.toFloat() / progressTotal).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.txt_yinelenen_videolar), fontWeight = FontWeight.Bold)
                        if (com.makay.cleaner.util.PermissionHelper.hasPartialVisualMediaPermission(context)) {
                            Text(
                                stringResource(R.string.media_scope_partial_videos),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
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
                        loading -> TextButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.txt_ptal)) }
                        hasScanned -> TextButton(onClick = { runScan() }) { Text(stringResource(R.string.txt_yenile)) }
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
                            Text(stringResource(R.string.txt_1_s_se_ili_2, selectedCount), fontWeight = FontWeight.Bold)
                            Text(formatBytes(selectedBytes), color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text(stringResource(R.string.txt_p_kutusuna_ta)) }
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
                        Text(stringResource(R.string.txt_1_s_2, (fraction * 100).toInt()))
                    }
                }

                !hasScanned -> {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.txt_tarama_kapsam), fontWeight = FontWeight.Bold)
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
                                        label = { Text(stringResource(R.string.txt_1_s_2_s_7, b.name, b.count)) }
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(R.string.txt_ayn_boyuttaki_videolar_k_smi_hash_ile_kar_la),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { runScan() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            enabled = scanMode != DuplicatePhotoPrefs.MODE_FOLDER || !selectedBucket.isNullOrBlank()
                        ) { Text(stringResource(R.string.txt_tara), fontWeight = FontWeight.Bold) }
                    }
                }

                groups.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.txt_yinelenen_video_bulunamad))
                        OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.txt_ayarlar_de_i_tir)) }
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
                            }) { Text(stringResource(R.string.txt_fazlal_klar_se)) }
                            TextButton(onClick = {
                                val all = groups.flatMap { g -> g.videos.map { it.path } }.toSet()
                                selectedPaths = selectedPaths - all
                            }) { Text(stringResource(R.string.txt_se_imi_temizle)) }
                        }
                    }
                    items(groups, key = { it.id }) { group ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    stringResource(R.string.txt_1_s_video_fazlal_k_2_s, group.videos.size, formatBytes(group.wasteBytes)),
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
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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
                    Toast.makeText(context, context.getString(R.string.msg_items_restored, n), Toast.LENGTH_SHORT).show()
                    runScan()
                }
            }
        )
    }
}
