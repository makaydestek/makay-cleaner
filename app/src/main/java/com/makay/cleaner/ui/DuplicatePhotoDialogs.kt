@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.makay.cleaner.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.scanner.DuplicatePhotoScanner
import com.makay.cleaner.scanner.SelectionAssistant
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ExcludeBucketsDialog(
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
internal fun FormatFilterDialog(
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
internal fun SelectionAssistantDialog(
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
internal fun PhotoPreviewDialog(
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
internal fun SideBySideCompareDialog(
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
internal fun ComparePane(
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
