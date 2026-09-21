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
import coil.compose.AsyncImage
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.VideoArchiveHelper
import com.makay.cleaner.scanner.VideoItem
import com.makay.cleaner.scanner.VideoScanner
import com.makay.cleaner.util.CleanResultTracker
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()

    LaunchedEffect(Unit) {
        loading = true
        videos = withContext(Dispatchers.IO) { scanner.scan() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video araçları", fontWeight = FontWeight.Bold) },
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
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    videos.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("50 MB üzeri video bulunamadı")
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
                                "Not: Gerçek video yeniden kodlama yok. Kopyala = arşiv kopyası; Taşı+arşiv = arşiv + çöp; Çöp = soft-delete.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Arşiv: Download/MakayArchive/Videos · Çöp: 48 saat geri alma",
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
                                            "${formatBytes(video.sizeBytes)} · ${video.width}x${video.height}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "720p tahmini: ${formatBytes(video.estimated720pBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Row {
                                            TextButton(onClick = {
                                                scope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        VideoArchiveHelper.copyToArchive(application, video) != null
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        if (ok) "Arşive kopyalandı" else "Arşiv başarısız",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }) { Text("Kopyala") }
                                            TextButton(onClick = {
                                                scope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        VideoArchiveHelper.moveOriginalToTrashAfterArchive(
                                                            application, video, trash
                                                        )
                                                    }
                                                    if (ok) {
                                                        CleaningRecorder.record(
                                                            application, "Video arşiv", video.sizeBytes, 1
                                                        )
                                                        CleanResultTracker.record(
                                                            "Video arşiv", 1, video.sizeBytes
                                                        )
                                                        summary = CleanResultTracker.last
                                                        videos = withContext(Dispatchers.IO) { scanner.scan() }
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        if (ok) "Arşivlendi + çöp kutusu" else "İşlem başarısız",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }) { Text("Taşı+arşiv") }
                                            TextButton(onClick = {
                                                scope.launch {
                                                    val ok = withContext(Dispatchers.IO) {
                                                        if (!video.path.isNullOrBlank()) {
                                                            trash.movePathToTrash(video.path, "Video")
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
                                                        CleaningRecorder.record(
                                                            application, "Video", video.sizeBytes, 1
                                                        )
                                                        videos = withContext(Dispatchers.IO) { scanner.scan() }
                                                    }
                                                }
                                            }) { Text("Çöp") }
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val widthClass = rememberWidthClass()

    LaunchedEffect(Unit) {
        loading = true
        items = withContext(Dispatchers.IO) { scanner.scan() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("APK kalıntıları", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = {
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
                                Toast.makeText(context, "$n APK çöp kutusuna", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Temizle (${selected.size})") }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            Modifier.fillMaxSize().padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            val mod = if (widthClass != AppWidthClass.Compact) {
                Modifier.widthIn(max = 840.dp).fillMaxWidth()
            } else Modifier.fillMaxWidth()
            Box(mod) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Eski APK kalıntısı yok (7+ gün)")
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
                            }) { Text("Tümünü seç") }
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
                                            "${formatBytes(apk.sizeBytes)} · ${apk.ageDays} gün önce",
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
}
