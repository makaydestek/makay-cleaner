package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
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
import com.makay.cleaner.data.MediaHubRepository
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.MediaCompressHelper
import com.makay.cleaner.util.ProManager
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaHubScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val repo = remember { MediaHubRepository(application) }
    val trash = remember { RecycleBinRepository(application) }
    var buckets by remember { mutableStateOf<List<MediaHubRepository.MediaBucket>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var summary by remember { mutableStateOf<CleanResultTracker.Summary?>(null) }
    var pendingBucket by remember { mutableStateOf<MediaHubRepository.MediaBucket?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loading = true
            buckets = withContext(Dispatchers.IO) { repo.scanBuckets() }
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medya merkezi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        when {
            !hasPermission -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                PermissionWarningCard(
                    message = "Medya taraması için depolama izni gerekli. İzin yokken 0 dosya temiz anlamına gelmez."
                ) { PermissionHelper.requestStoragePermission(context) }
            }
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            buckets.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Medya kategorisi bulunamadı")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(buckets, key = { it.id }) { bucket ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(bucket.title, fontWeight = FontWeight.Bold)
                            Text(bucket.description, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Text("${bucket.fileCount} dosya · ${formatBytes(bucket.totalBytes)}")
                            Spacer(Modifier.height(8.dp))
                            if (bucket.samplePaths.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(bucket.samplePaths) { p ->
                                        AsyncImage(
                                            model = File(p),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            TextButton(onClick = { pendingBucket = bucket }) {
                                Text("Kategoriyi çöp kutusuna taşı")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingBucket?.let { bucket ->
        ConfirmDeleteDialog(
            title = bucket.title,
            message = "Bu kategorideki dosyalar 48 saat çöp kutusunda tutulur.",
            onConfirm = {
                val target = bucket
                pendingBucket = null
                scope.launch {
                    var deleted = 0
                    var saved = 0L
                    withContext(Dispatchers.IO) {
                        trash.beginBatch()
                        repo.pathsForBucket(target.id).forEach { path ->
                            val f = File(path)
                            val size = f.length()
                            if (trash.movePathToTrash(path, target.title)) {
                                deleted++
                                saved += size
                            }
                        }
                    }
                    if (deleted > 0) {
                        CleaningRecorder.record(application, target.title, saved, deleted)
                        CleanResultTracker.record(target.title, deleted, saved)
                        summary = CleanResultTracker.last
                    }
                    buckets = withContext(Dispatchers.IO) { repo.scanBuckets() }
                    val expected = target.fileCount
                    val failed = (expected - deleted).coerceAtLeast(0)
                    Toast.makeText(
                        context,
                        when {
                            deleted == 0 -> "Hiçbir dosya taşınamadı (izin / sistem koruması)"
                            failed > 0 -> "$deleted taşındı, $failed taşınamadı"
                            else -> "$deleted dosya çöp kutusuna taşındı"
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { pendingBucket = null },
            fileCount = bucket.fileCount,
            totalBytes = bucket.totalBytes,
            moveToTrash = true,
            previewPaths = bucket.samplePaths
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
                    buckets = withContext(Dispatchers.IO) { repo.scanBuckets() }
                    Toast.makeText(context, "$n öğe geri yüklendi", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaCompressScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onRequirePro: () -> Unit = {}
) {
    if (ProManager.requiresPro(ProManager.ProFeature.MEDIA_COMPRESS, application)) {
        ProGateContent("Medya sıkıştırma", onNavigateBack, onRequirePro)
        return
    }

    val hub = remember { MediaHubRepository(application) }
    var quality by remember { mutableFloatStateOf(70f) }
    var busy by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medya sıkıştırma", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Kamera fotoğraflarını JPEG olarak sıkıştırır (orijinal silinmez).")
            Spacer(Modifier.height(16.dp))
            Text("Kalite: ${quality.toInt()}")
            Slider(value = quality, onValueChange = { quality = it }, valueRange = 40f..90f)
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        val paths = withContext(Dispatchers.IO) {
                            hub.pathsForBucket("camera").take(30)
                        }
                        val results = withContext(Dispatchers.IO) {
                            MediaCompressHelper.compressMany(paths, quality.toInt())
                        }
                        val saved = results.sumOf { (it.originalSize - it.newSize).coerceAtLeast(0) }
                        resultText = "${results.size} foto sıkıştırıldı · ${formatBytes(saved)} kazanıldı"
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Sıkıştırılıyor…" else "İlk 30 kamerayı sıkıştır") }
            resultText?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
