package com.makay.cleaner.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.PersonMismatchScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.FilePurger
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.PhotoAlbumMover
import com.makay.cleaner.util.rememberStoragePermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Klasörde “asıl kişi” referansı ile farklı kişi fotoğraflarını bulur;
 * Sil (çöp) / Taşı / Kopyala.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonMismatchScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanner = remember { PersonMismatchScanner(application) }
    val trash = remember { RecycleBinRepository(application) }
    val hasPermission = rememberStoragePermissionState()

    var buckets by remember { mutableStateOf<List<PersonMismatchScanner.BucketInfo>>(emptyList()) }
    var selectedBucket by remember { mutableStateOf<String?>(null) }
    var bucketMenu by remember { mutableStateOf(false) }
    var referenceUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var candidates by remember { mutableStateOf<List<PersonMismatchScanner.MismatchCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loadingBuckets by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var hasScanned by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressCur by remember { mutableIntStateOf(0) }
    var progressTot by remember { mutableIntStateOf(1) }
    var threshold by remember { mutableFloatStateOf(PersonMismatchScanner.DEFAULT_MATCH_THRESHOLD) }
    var albumDialog by remember { mutableStateOf<AlbumAction?>(null) }
    var albumName by remember { mutableStateOf("") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    val cancelFlag = remember { AtomicBoolean(false) }

    val pickRefs = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            referenceUris = (referenceUris + uris).distinct().take(12)
        }
    }

    fun refreshBuckets() {
        if (!hasPermission) return
        scope.launch {
            loadingBuckets = true
            buckets = withContext(Dispatchers.IO) { scanner.listBuckets() }
            if (selectedBucket == null) {
                selectedBucket = buckets.firstOrNull()?.name
            }
            loadingBuckets = false
        }
    }

    LaunchedEffect(hasPermission) { refreshBuckets() }
    DisposableEffect(Unit) {
        onDispose { scanner.close() }
    }

    fun runScan() {
        val bucket = selectedBucket ?: return
        if (referenceUris.isEmpty()) {
            Toast.makeText(context, "Önce asıl kişi için örnek foto seçin", Toast.LENGTH_SHORT).show()
            return
        }
        scanJob?.cancel()
        cancelFlag.set(false)
        scanJob = scope.launch {
            scanning = true
            hasScanned = true
            candidates = emptyList()
            selected = emptySet()
            progressText = "Referans yüzler çıkarılıyor…"
            progressCur = 0
            progressTot = 1
            val mainHandler = Handler(Looper.getMainLooper())
            val result = withContext(Dispatchers.Default) {
                val refs = scanner.buildReferences(referenceUris)
                if (refs.embeddings.isEmpty()) {
                    return@withContext ScanOutcome.NoFace(refs.skippedNoFace, refs.errors)
                }
                val found = scanner.scan(
                    bucketName = bucket,
                    references = refs.embeddings,
                    matchThreshold = threshold,
                    cancelled = cancelFlag,
                    progress = PersonMismatchScanner.ProgressListener { phase, cur, tot ->
                        mainHandler.post {
                            progressText = phase
                            progressCur = cur
                            progressTot = tot.coerceAtLeast(1)
                        }
                    }
                )
                ScanOutcome.Ok(found, refs.embeddings.size, refs.skippedNoFace)
            }
            scanning = false
            when (result) {
                is ScanOutcome.NoFace -> {
                    Toast.makeText(
                        context,
                        "Referanslarda yüz bulunamadı (yüz yok: ${result.skipped}, hata: ${result.errors})",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is ScanOutcome.Ok -> {
                    candidates = result.items
                    if (result.skippedRefs > 0) {
                        Toast.makeText(
                            context,
                            "${result.refCount} yüz kaydı · ${result.skippedRefs} örnekte yüz yoktu",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    fun applyTrash(paths: List<PersonMismatchScanner.MismatchCandidate>) {
        scope.launch {
            busy = true
            var n = 0
            var saved = 0L
            withContext(Dispatchers.IO) {
                trash.beginBatch()
                paths.forEach { item ->
                    val f = File(item.path)
                    val size = item.sizeBytes
                    val ok = if (f.isFile) {
                        FilePurger.remove(context, f, trash = trash, category = "Farklı kişi")
                    } else {
                        trash.moveUriToTrash(item.uri, item.displayName, size, "Farklı kişi")
                    }
                    if (ok) {
                        n++
                        saved += size
                    }
                }
            }
            if (n > 0) {
                CleaningRecorder.record(application, "Farklı kişi", saved, n)
                CleanResultTracker.record("Farklı kişi", n, saved)
            }
            busy = false
            Toast.makeText(
                context,
                if (n > 0) "$n foto çöp kutusuna taşındı" else "Silinemedi",
                Toast.LENGTH_SHORT
            ).show()
            val done = paths.map { it.path }.toSet()
            candidates = candidates.filterNot { it.path in done }
            selected = emptySet()
        }
    }

    fun applyCopyOrMove(paths: List<PersonMismatchScanner.MismatchCandidate>, move: Boolean, album: String) {
        scope.launch {
            busy = true
            val donePaths = mutableSetOf<String>()
            withContext(Dispatchers.IO) {
                paths.forEach { item ->
                    val ok = if (move) {
                        PhotoAlbumMover.moveToAlbum(
                            context, item.path, item.uri, item.displayName, album
                        )
                    } else {
                        PhotoAlbumMover.copyToAlbum(
                            context, item.path, item.uri, item.displayName, album
                        )
                    }
                    if (ok) donePaths += item.path
                }
            }
            busy = false
            val verb = if (move) "taşındı" else "kopyalandı"
            Toast.makeText(
                context,
                if (donePaths.isNotEmpty()) "${donePaths.size} foto «$album» albümüne $verb"
                else "İşlem başarısız",
                Toast.LENGTH_SHORT
            ).show()
            if (move && donePaths.isNotEmpty()) {
                candidates = candidates.filterNot { it.path in donePaths }
                selected = selected - donePaths
            }
        }
    }

    val selectedItems = remember(candidates, selected) {
        candidates.filter { it.path in selected }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Klasörde farklı kişi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (scanning) {
                        TextButton(onClick = {
                            cancelFlag.set(true)
                            scanJob?.cancel()
                            scanning = false
                        }) { Text("İptal") }
                    }
                }
            )
        }
    ) { padding ->
        if (!hasPermission) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                PermissionWarningCard(
                    message = "Klasör taraması için depolama / medya izni gerekli."
                ) { PermissionHelper.requestStoragePermission(context) }
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Column(
                Modifier
                    .weight(1f, fill = true)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Örnek fotoğraflarla asıl kişiyi belirleyin; klasördeki tüm fotoğraflar " +
                        "MobileFaceNet YZ modeli ile taranır. Eşleşmeyenler listelenir — Sil / Taşı / Kopyala.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("1. Klasör", fontWeight = FontWeight.SemiBold)
                ExposedDropdownMenuBox(
                    expanded = bucketMenu,
                    onExpandedChange = { bucketMenu = it }
                ) {
                    OutlinedTextField(
                        value = selectedBucket ?: if (loadingBuckets) "Yükleniyor…" else "Klasör seçin",
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bucketMenu) },
                        label = { Text("Albüm / klasör") }
                    )
                    ExposedDropdownMenu(
                        expanded = bucketMenu,
                        onDismissRequest = { bucketMenu = false }
                    ) {
                        buckets.forEach { b ->
                            DropdownMenuItem(
                                text = { Text("${b.name} (${b.count})") },
                                onClick = {
                                    selectedBucket = b.name
                                    bucketMenu = false
                                    candidates = emptyList()
                                    hasScanned = false
                                }
                            )
                        }
                    }
                }

                Text("2. Asıl kişi örnekleri", fontWeight = FontWeight.SemiBold)
                Text(
                    "1–12 net, kameraya bakan yüz fotoğrafı seçin (aynı kişi). Daha fazla örnek = daha doğru YZ eşleşme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(referenceUris, key = { it.toString() }) { uri ->
                        Box {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                "×",
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clickable {
                                        referenceUris = referenceUris.filterNot { it == uri }
                                    }
                                    .padding(4.dp),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    item {
                        OutlinedButton(onClick = { pickRefs.launch("image/*") }) {
                            Text(if (referenceUris.isEmpty()) "Örnek seç" else "Ekle")
                        }
                    }
                }

                Text("3. Hassasiyet (YZ)", fontWeight = FontWeight.SemiBold)
                Text(
                    "Eşik: %${(threshold * 100).toInt()} — düşük = aynı kişiyi daha iyi tutar; " +
                        "yüksek = daha katı (daha çok aday). Öneri: %50–%58",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = 0.35f..0.75f,
                    steps = 39
                )

                Button(
                    onClick = { runScan() },
                    enabled = !scanning && !busy && selectedBucket != null && referenceUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (scanning) "Taranıyor…" else "Tara")
                }

                if (scanning) {
                    LinearProgressIndicator(
                        progress = { progressCur.toFloat() / progressTot.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "$progressText ($progressCur / $progressTot)",
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (hasScanned && !scanning) {
                    Text(
                        if (candidates.isEmpty()) "Farklı kişi adayı bulunamadı."
                        else "${candidates.size} aday · ${selected.size} seçili",
                        fontWeight = FontWeight.SemiBold
                    )
                    if (candidates.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                selected = candidates.map { it.path }.toSet()
                            }) { Text("Tümünü seç") }
                            TextButton(onClick = { selected = emptySet() }) { Text("Seçimi kaldır") }
                        }
                    }
                }

                candidates.forEach { item ->
                    val on = item.path in selected
                    Card(
                        onClick = {
                            selected = if (on) selected - item.path else selected + item.path
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (on)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.displayName,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatBytes(item.sizeBytes)} · ${item.faceCount} yüz · " +
                                        "%${(item.bestSimilarity * 100).toInt()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    item.reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Checkbox(
                                checked = on,
                                onCheckedChange = {
                                    selected = if (it) selected + item.path else selected - item.path
                                }
                            )
                        }
                    }
                }
            }

            if (selectedItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { applyTrash(selectedItems) },
                        enabled = !busy && !scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sil") }
                    OutlinedButton(
                        onClick = {
                            albumName = ""
                            albumDialog = AlbumAction.MOVE
                        },
                        enabled = !busy && !scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Taşı") }
                    OutlinedButton(
                        onClick = {
                            albumName = ""
                            albumDialog = AlbumAction.COPY
                        },
                        enabled = !busy && !scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Kopyala") }
                }
            }
        }
    }

    albumDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!busy) albumDialog = null },
            title = {
                Text(if (action == AlbumAction.MOVE) "Albüme taşı" else "Albüme kopyala")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Hedef albüm adı (Pictures altında oluşur). Örn: Denizim, Diger",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = albumName,
                        onValueChange = { albumName = it },
                        singleLine = true,
                        label = { Text("Albüm adı") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (buckets.isNotEmpty()) {
                        Text("Mevcut klasörler:", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(buckets.take(20), key = { it.name }) { b ->
                                AssistChip(
                                    onClick = { albumName = b.name },
                                    label = { Text(b.name) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = albumName.isNotBlank() && !busy,
                    onClick = {
                        val name = albumName
                        val items = selectedItems
                        val move = action == AlbumAction.MOVE
                        albumDialog = null
                        applyCopyOrMove(items, move, name)
                    }
                ) { Text(if (action == AlbumAction.MOVE) "Taşı" else "Kopyala") }
            },
            dismissButton = {
                TextButton(onClick = { albumDialog = null }) { Text("Vazgeç") }
            }
        )
    }
}

private enum class AlbumAction { MOVE, COPY }

private sealed class ScanOutcome {
    data class NoFace(val skipped: Int, val errors: Int) : ScanOutcome()
    data class Ok(
        val items: List<PersonMismatchScanner.MismatchCandidate>,
        val refCount: Int,
        val skippedRefs: Int
    ) : ScanOutcome()
}
