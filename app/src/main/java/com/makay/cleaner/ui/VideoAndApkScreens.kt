package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import android.app.Application
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
import coil.compose.AsyncImage
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.VideoArchiveHelper
import com.makay.cleaner.scanner.VideoItem
import com.makay.cleaner.scanner.VideoScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoToolsScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val scanner = remember { VideoScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var pendingTrashVideo by remember { mutableStateOf<VideoItem?>(null) }
    var pendingArchiveVideo by remember { mutableStateOf<VideoItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()
    val hasStoragePermission = rememberStoragePermissionState()

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            loading = true
            videos = withContext(Dispatchers.IO) { scanner.scan() }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_video_ara_lar), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            val mod = if (widthClass != AppWidthClass.Compact) {
                Modifier.widthIn(max = 840.dp).fillMaxWidth()
            } else Modifier.fillMaxWidth()
            Box(mod) {
                when {
                    !hasStoragePermission -> PermissionWarningCard(
                        "Video taraması için depolama erişimi gerekir. İzin verilmezse dosyalar taranmaz."
                    ) { PermissionHelper.requestStoragePermission(context) }
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.txt_50_mb_zeri_video_bulunamad))
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                "${videos.size} video · ${formatBytes(videos.sumOf { it.sizeBytes })}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.txt_not_ger_ek_video_yeniden_kodlama_yok_kopyala),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.txt_ar_iv_download_makayarchive_videos_p_48_saat),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(videos, key = { it.id }) { video ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = video.uri,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(video.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(
                                            stringResource(R.string.txt_1_s_2_sx_3_s, formatBytes(video.sizeBytes), video.width, video.height),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            stringResource(R.string.txt_720p_tahmini_1_s, formatBytes(video.estimated720pBytes)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        VideoArchiveHelper.copyToArchive(application, video) != null
                                                    }
                                                    AppNotice.show(if (ok) "Arşive kopyalandı" else "Arşiv başarısız")
                                                }
                                            }) { Text(stringResource(R.string.txt_kopyala)) }
                                            TextButton(onClick = { pendingArchiveVideo = video }) {
                                                Text(stringResource(R.string.txt_ta_ar_iv))
                                            }
                                            TextButton(onClick = { pendingTrashVideo = video }) {
                                                Text(stringResource(R.string.txt_text))
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

    pendingArchiveVideo?.let { video ->
        ConfirmDeleteDialog(
            title = "Arşive taşınsın mı?",
            message = "Video önce arşive kopyalanır, ardından orijinal çöp kutusuna taşınır.",
            fileCount = 1,
            totalBytes = video.sizeBytes,
            moveToTrash = true,
            onConfirm = {
                pendingArchiveVideo = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        VideoArchiveHelper.moveOriginalToTrashAfterArchive(application, video, trash)
                    }
                    if (ok) {
                        CleaningRecorder.record(application, "Video arşiv", video.sizeBytes, 1)
                        CleanResultTracker.record("Video arşiv", 1, video.sizeBytes)
                        summary = CleanResultTracker.last
                        videos = withContext(Dispatchers.IO) { scanner.scan() }
                    }
                    AppNotice.show(if (ok) "Arşivlendi + çöp kutusu" else "İşlem başarısız")
                }
            },
            onDismiss = { pendingArchiveVideo = null }
        )
    }

    pendingTrashVideo?.let { video ->
        ConfirmDeleteDialog(
            title = "Video çöp kutusuna taşınsın mı?",
            message = "${video.displayName} 48 saat içinde geri yüklenebilir.",
            fileCount = 1,
            totalBytes = video.sizeBytes,
            moveToTrash = true,
            onConfirm = {
                pendingTrashVideo = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        val path = video.path
                        if (!path.isNullOrBlank()) {
                            trash.movePathToTrash(path, "Video")
                        } else {
                            trash.moveUriToTrash(
                                video.uri,
                                video.displayName,
                                video.sizeBytes,
                                "Video"
                            )
                        }
                    }
                    if (ok) {
                        CleaningRecorder.record(application, "Video", video.sizeBytes, 1)
                        videos = withContext(Dispatchers.IO) { scanner.scan() }
                    }
                }
            },
            onDismiss = { pendingTrashVideo = null }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkRemnantScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val scanner = remember { com.makay.cleaner.scanner.ApkRemnantScanner() }
    val trash = remember { RecycleBinRepository(application) }
    var items by remember { mutableStateOf<List<com.makay.cleaner.scanner.ApkRemnant>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var confirmApkDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()
    val hasStoragePermission = rememberStoragePermissionState()

    fun cleanSelectedApks() {
        scope.launch {
            var n = 0
            var saved = 0L
            withContext(Dispatchers.IO) {
                items.filter { it.file.absolutePath in selected }.forEach {
                    val size = it.sizeBytes
                    if (trash.moveToTrash(it.file, "APK")) {
                        n++
                        saved += size
                    }
                }
            }
            CleaningRecorder.record(application, "APK kalıntı", saved, n)
            items = withContext(Dispatchers.IO) { scanner.scan() }
            selected = emptySet()
            AppNotice.show(context.getString(R.string.msg_apk_moved, n))
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
                title = { Text(stringResource(R.string.txt_apk_kal_nt_lar), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { confirmApkDelete = true }) {
                            Text(stringResource(R.string.txt_temizle_1_s_3, selected.size))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            if (!hasStoragePermission) {
                PermissionWarningCard(
                    "APK kalıntılarını taramak ve seçtiklerinizi temizlemek için depolama erişimi gerekir."
                ) { PermissionHelper.requestStoragePermission(context) }
                return@Box
            }
            val mod = if (widthClass != AppWidthClass.Compact) {
                Modifier.widthIn(max = 840.dp).fillMaxWidth()
            } else Modifier.fillMaxWidth()
            Box(mod) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.txt_eski_apk_kal_nt_s_yok_7_g_n))
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                "${items.size} APK · ${formatBytes(items.sumOf { it.sizeBytes })}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = {
                                selected = items.map { it.file.absolutePath }.toSet()
                            }) { Text(stringResource(R.string.txt_t_m_n_se_2)) }
                        }
                        items(items, key = { it.file.absolutePath }) { apk ->
                            val path = apk.file.absolutePath
                            Card(
                                onClick = {
                                    selected = if (path in selected) selected - path else selected + path
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                        Text(apk.file.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                        Text(
                                            stringResource(R.string.txt_1_s_2_s_g_n_nce, formatBytes(apk.sizeBytes), apk.ageDays),
                                            style = MaterialTheme.typography.bodySmall
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

    if (confirmApkDelete) {
        ConfirmDeleteDialog(
            title = "Seçilen APK'lar çöp kutusuna taşınsın mı?",
            message = "${selected.size} APK dosyası 48 saat içinde geri yüklenebilir.",
            fileCount = selected.size,
            totalBytes = items.filter { it.file.absolutePath in selected }.sumOf { it.sizeBytes },
            moveToTrash = true,
            onConfirm = {
                confirmApkDelete = false
                cleanSelectedApks()
            },
            onDismiss = { confirmApkDelete = false }
        )
    }
}
