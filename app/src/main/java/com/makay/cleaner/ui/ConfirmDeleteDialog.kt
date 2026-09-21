package com.makay.cleaner.ui

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
                        text = "$fileCount dosya · ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (previewPaths.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(previewPaths.take(6)) { path ->
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
                        "Dosyalar 48 saat çöp kutusunda tutulur; geri yükleyebilirsiniz."
                    else
                        "Bu işlem geri alınamaz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (moveToTrash) "Çöp kutusuna taşı" else "Sil")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("İptal") }
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
    onOpenTrash: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temizlik tamamlandı", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(category, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text("• $filesDeleted öğe işlendi")
                Text("• ${formatBytes(spaceSaved)} yer açıldı")
                if (protectedSkipped > 0) {
                    Text(
                        "• $protectedSkipped korunan sistem dosyası atlandı",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (movedToTrash) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Öğeler çöp kutusunda. 48 saat içinde geri yükleyebilirsiniz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tamam") }
        },
        dismissButton = if (onOpenTrash != null && movedToTrash) {
            {
                TextButton(onClick = {
                    onDismiss()
                    onOpenTrash()
                }) { Text("Çöp kutusu") }
            }
        } else null
    )
}
