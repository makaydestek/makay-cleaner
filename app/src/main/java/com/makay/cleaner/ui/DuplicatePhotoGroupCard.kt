package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.makay.cleaner.scanner.DuplicatePhotoScanner

@Composable
internal fun DuplicatePhotoGroupCard(
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
                        stringResource(R.string.txt_1_s_2_s_foto_raf, typeLabel, group.photos.size),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.txt_fazlal_k_1_s, formatBytes(group.wasteBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    if (group.photos.size >= 2) {
                        TextButton(onClick = {
                            onCompare(group.photos[0], group.photos[1])
                        }) { Text(stringResource(R.string.txt_kar_la_t_r)) }
                    }
                    TextButton(onClick = onKeepBest) { Text(stringResource(R.string.txt_en_iyiyi_tut)) }
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
