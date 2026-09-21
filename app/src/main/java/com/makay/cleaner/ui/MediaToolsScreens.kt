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
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.MediaHubRepository
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.MediaCompressHelper
import com.makay.cleaner.util.ProManager
import com.makay.cleaner.scanner.SimilarPhotoScanner
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        buckets = withContext(Dispatchers.IO) { repo.scanBuckets() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medya merkezi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        when {
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
                            TextButton(onClick = {
                                scope.launch {
                                    var deleted = 0
                                    var saved = 0L
                                    withContext(Dispatchers.IO) {
                                        repo.pathsForBucket(bucket.id).forEach { path ->
                                            val f = File(path)
                                            val size = f.length()
                                            if (trash.moveToTrash(f, bucket.title)) {
                                                deleted++
                                                saved += size
                                            }
                                        }
                                    }
                                    CleaningRecorder.record(application, bucket.title, saved, deleted)
                                    CleanResultTracker.record(bucket.title, deleted, saved)
                                    summary = CleanResultTracker.last
                                    buckets = withContext(Dispatchers.IO) { repo.scanBuckets() }
                                    Toast.makeText(context, "$deleted dosya taşındı", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Kategoriyi çöp kutusuna taşı") }
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
fun SimilarPhotosScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onRequirePro: () -> Unit = {}
) {
    if (ProManager.requiresPro(ProManager.ProFeature.SIMILAR_PHOTOS, application)) {
        ProGateContent("Benzer fotoğraflar", onNavigateBack, onRequirePro)
        return
    }

    val scanner = remember { SimilarPhotoScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    var groups by remember { mutableStateOf<List<SimilarPhotoScanner.SimilarGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        groups = withContext(Dispatchers.IO) { scanner.scan() }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Benzer fotoğraflar", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Benzer grup bulunamadı")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(groups) { group ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${group.paths.size} benzer · ${formatBytes(group.totalSize)}",
                                fontWeight = FontWeight.SemiBold
                            )
                            group.paths.take(4).forEach { Text(File(it).name, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = {
                                scope.launch {
                                    var deleted = 0
                                    var saved = 0L
                                    withContext(Dispatchers.IO) {
                                        group.paths.drop(1).forEach { path ->
                                            val f = File(path)
                                            val size = f.length()
                                            if (trash.moveToTrash(f, "Benzer foto")) {
                                                deleted++
                                                saved += size
                                            }
                                        }
                                    }
                                    CleaningRecorder.record(application, "Benzer foto", saved, deleted)
                                    Toast.makeText(context, "$deleted foto taşındı", Toast.LENGTH_SHORT).show()
                                    groups = withContext(Dispatchers.IO) { scanner.scan() }
                                }
                            }) { Text("Fazlalıkları çöp kutusuna taşı") }
                        }
                    }
                }
            }
        }
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
                    IconButton(onClick = onNavigateBack) {
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
