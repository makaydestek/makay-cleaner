package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.makay.cleaner.scanner.PersonMismatchScanner
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Klasörde “asıl kişi” referansı ile farklı kişi fotoğraflarını bulur;
 * Sil (çöp) / Taşı / Kopyala.
 *
 * Sonuçlar Activity-scoped ViewModel + disk arşivinde tutulur;
 * geri navigasyon tamamlanan taramayı silmez.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonMismatchScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val vm: PersonMismatchViewModel = if (activity != null) {
        viewModel(
            viewModelStoreOwner = activity,
            factory = PersonMismatchViewModel.Factory(application)
        )
    } else {
        viewModel(factory = PersonMismatchViewModel.Factory(application))
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val hasPermission = rememberStoragePermissionState()

    var bucketMenu by remember { mutableStateOf(false) }
    var albumDialog by remember { mutableStateOf<AlbumAction?>(null) }
    var albumName by remember { mutableStateOf("") }
    var leaveWhileScanning by remember { mutableStateOf(false) }

    val pickRefs = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) vm.addReferences(uris)
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) vm.refreshBuckets()
    }

    LaunchedEffect(state.toastMessage) {
        val msg = state.toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        vm.consumeToast()
    }

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        vm.consumeError()
    }

    fun goBack() {
        vm.persistArchiveBlocking()
        onNavigateBack()
    }

    BackHandler {
        if (state.scanning) {
            leaveWhileScanning = true
        } else {
            goBack()
        }
    }

    val selectedItems = remember(state.candidates, state.selected) {
        state.candidates.filter { it.path in state.selected }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Klasörde farklı kişi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.scanning) leaveWhileScanning = true
                        else goBack()
                    }) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (state.scanning) {
                        TextButton(onClick = { vm.cancelScan() }) { Text("İptal") }
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
                .padding(horizontal = 16.dp)
        ) {
            // LazyColumn: büyük aday listelerinde OOM / UI çökmesini önler
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item(key = "intro") {
                    Text(
                        "Örnek fotoğraflarla asıl kişiyi belirleyin; klasördeki tüm fotoğraflar " +
                            "MobileFaceNet YZ modeli ile taranır. Eşleşmeyenler listelenir — Sil / Taşı / Kopyala. " +
                            "Son tarama otomatik arşivlenir; geri tuşu sonuçları silmez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.archiveRestored && state.candidates.isNotEmpty()) {
                    item(key = "archive_banner") {
                        val whenText = if (state.archiveSavedAt > 0L) {
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                .format(Date(state.archiveSavedAt))
                        } else {
                            "önceki oturum"
                        }
                        AssistChip(
                            onClick = {},
                            label = {
                                Text("Son tarama yüklendi · $whenText · ${state.candidates.size} aday")
                            }
                        )
                    }
                }

                item(key = "bucket_label") {
                    Text("1. Klasör", fontWeight = FontWeight.SemiBold)
                }
                item(key = "bucket_field") {
                    ExposedDropdownMenuBox(
                        expanded = bucketMenu,
                        onExpandedChange = { bucketMenu = it }
                    ) {
                        OutlinedTextField(
                            value = state.selectedBucket
                                ?: if (state.loadingBuckets) "Yükleniyor…" else "Klasör seçin",
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
                            state.buckets.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text("${b.name} (${b.count})") },
                                    onClick = {
                                        vm.selectBucket(b.name)
                                        bucketMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                item(key = "refs_label") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("2. Asıl kişi örnekleri", fontWeight = FontWeight.SemiBold)
                        Text(
                            "1–${PersonMismatchScanner.MAX_REFERENCES} net, kameraya bakan yüz fotoğrafı seçin (aynı kişi). " +
                                "Daha fazla örnek = daha doğru YZ eşleşme.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item(key = "refs_row") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.referenceUris, key = { it.toString() }) { uri ->
                            Box {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    "×",
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .clickable { vm.removeReference(uri) }
                                        .padding(4.dp),
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        item {
                            OutlinedButton(onClick = { pickRefs.launch("image/*") }) {
                                Text(if (state.referenceUris.isEmpty()) "Örnek seç" else "Ekle")
                            }
                        }
                    }
                }

                item(key = "threshold") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("3. Hassasiyet (YZ)", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Eşik: %${(state.threshold * 100).toInt()} — düşük = aynı kişiyi daha iyi tutar; " +
                                "yüksek = daha katı (daha çok aday). Öneri: %45–%55",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.threshold,
                            onValueChange = { vm.setThreshold(it) },
                            valueRange = 0.30f..0.70f,
                            steps = 39
                        )
                    }
                }

                item(key = "scan_btn") {
                    Button(
                        onClick = { vm.runScan() },
                        enabled = !state.scanning && !state.busy &&
                            state.selectedBucket != null && state.referenceUris.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.scanning) "Taranıyor…" else "Tara")
                    }
                }

                if (state.scanning) {
                    item(key = "progress") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = {
                                    state.progressCur.toFloat() /
                                        state.progressTot.coerceAtLeast(1)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${state.progressText} (${state.progressCur} / ${state.progressTot})",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                "Tarama arka planda sürer; geri tuşu sonuçları silmez.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (state.hasScanned && !state.scanning) {
                    item(key = "results_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                if (state.candidates.isEmpty()) "Farklı kişi adayı bulunamadı."
                                else "${state.candidates.size} aday · ${state.selected.size} seçili",
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.candidates.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { vm.selectAll() }) { Text("Tümünü seç") }
                                    TextButton(onClick = { vm.clearSelection() }) { Text("Seçimi kaldır") }
                                }
                            }
                        }
                    }
                }

                items(
                    items = state.candidates,
                    key = { it.path }
                ) { item ->
                    val on = item.path in state.selected
                    Card(
                        onClick = { vm.toggleSelected(item.path) },
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
                                onCheckedChange = { vm.toggleSelected(item.path) }
                            )
                        }
                    }
                }
            }

            if (selectedItems.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { vm.applyTrash(selectedItems) },
                        enabled = !state.busy && !state.scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Sil") }
                    OutlinedButton(
                        onClick = {
                            albumName = ""
                            albumDialog = AlbumAction.MOVE
                        },
                        enabled = !state.busy && !state.scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Taşı") }
                    OutlinedButton(
                        onClick = {
                            albumName = ""
                            albumDialog = AlbumAction.COPY
                        },
                        enabled = !state.busy && !state.scanning,
                        modifier = Modifier.weight(1f)
                    ) { Text("Kopyala") }
                }
            }
        }
    }

    if (leaveWhileScanning) {
        AlertDialog(
            onDismissRequest = { leaveWhileScanning = false },
            title = { Text("Tarama sürüyor") },
            text = {
                Text(
                    "Tarama arka planda devam eder ve bitince arşivlenir. " +
                        "Çıkmak ister misiniz, yoksa taramayı durdurup kısmi sonucu kaydetmek mi?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    leaveWhileScanning = false
                    // Tarama ViewModel’de devam eder; sonuç arşive yazılır
                    goBack()
                }) { Text("Arka planda sürdür") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        leaveWhileScanning = false
                        vm.cancelScan()
                        goBack()
                    }) { Text("Durdur ve kaydet") }
                    TextButton(onClick = { leaveWhileScanning = false }) { Text("Kal") }
                }
            }
        )
    }

    albumDialog?.let { action ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) albumDialog = null },
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
                    if (state.buckets.isNotEmpty()) {
                        Text("Mevcut klasörler:", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.buckets.take(20), key = { it.name }) { b ->
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
                    enabled = albumName.isNotBlank() && !state.busy,
                    onClick = {
                        val name = albumName
                        val items = selectedItems
                        val move = action == AlbumAction.MOVE
                        albumDialog = null
                        vm.applyCopyOrMove(items, move, name)
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
