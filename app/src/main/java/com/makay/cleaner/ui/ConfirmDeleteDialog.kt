package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    fileCount: Int = 0,
    totalBytes: Long = 0L,
    moveToTrash: Boolean = true,
    previewPaths: List<String> = emptyList()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (fileCount > 0) {
                    Text(
                        text = stringResource(R.string.txt_1_s_dosya_2_s, fileCount, formatBytes(totalBytes)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (previewPaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(previewPaths.take(6), key = { it }) { path ->
                            AsyncImage(
                                model = if (path.startsWith("content://") || path.startsWith("file://")) {
                                    path
                                } else {
                                    File(path)
                                },
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(message)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (moveToTrash)
                        "Dosyalar orijinal konumdan kaldırılıp 48 saat uygulama çöp kutusunda tutulur. " +
                            "Galeri / F Dosya Yöneticisi bir süre eski indeksi gösterebilir; kısa süre sonra yenilenir."
                    else
                        "Bu işlem geri alınamaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (moveToTrash) stringResource(R.string.msg_trash) else stringResource(R.string.msg_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.txt_ptal)) }
        }
    )
}

@Composable
fun CleanSummaryDialog(
    category: String,
    filesDeleted: Int,
    spaceSaved: Long,
    protectedSkipped: Int,
    movedToTrash: Boolean,
    onDismiss: () -> Unit,
    onOpenTrash: (() -> Unit)? = null,
    onUndo: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.txt_temizlik_tamamland), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(category, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.txt_1_s_e_i_lendi, filesDeleted))
                Text(stringResource(R.string.txt_1_s_yer_a_ld, formatBytes(spaceSaved)))
                if (protectedSkipped > 0) {
                    Text(
                        stringResource(R.string.txt_1_s_korunan_sistem_dosyas_atland, protectedSkipped),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (movedToTrash) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.txt_eler_p_kutusunda_48_saat_i_inde_geri_y_kleye),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.txt_tamam)) }
        },
        dismissButton = {
            val extra = when {
                onUndo != null && movedToTrash -> "Geri al"
                onOpenTrash != null && movedToTrash -> "Çöp kutusu"
                else -> null
            }
            if (extra != null) {
                TextButton(onClick = {
                    if (onUndo != null) onUndo() else onOpenTrash?.invoke()
                    onDismiss()
                }) { Text(extra) }
            }
        }
    )
}
