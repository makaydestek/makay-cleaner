package com.makay.cleaner.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCacheScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    viewModel: AppCacheViewModel = viewModel(factory = AppCacheViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uygulama Önbelleği", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (!hasPermission) {
                PermissionWarningCard(
                    message = "Önbellek dosyalarını tarayabilmek için tüm dosyalara erişim izni vermeniz gerekiyor."
                ) {
                    PermissionHelper.requestStoragePermission(context)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    if (!hasPermission) {
                        PermissionHelper.requestStoragePermission(context)
                    } else {
                        viewModel.scanCache()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Taranıyor...")
                } else {
                    Text("🔍 Tara", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            uiState.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.files.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Toplam Önbellek",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatBytes(uiState.totalCacheSize),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = { viewModel.selectAll() }) { Text("Tümünü Seç") }
                    TextButton(onClick = { viewModel.clearSelection() }) { Text("Temizle") }
                }

                if (uiState.selectedPaths.isNotEmpty()) {
                    Text(
                        text = "${uiState.selectedPaths.size} dosya seçildi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.files, key = { it.file.absolutePath }) { entry ->
                        CacheFileItem(
                            file = entry.file,
                            subtitle = entry.appName,
                            isSelected = entry.file.absolutePath in uiState.selectedPaths,
                            onSelectionChanged = {
                                viewModel.toggleSelection(entry.file.absolutePath)
                            }
                        )
                    }
                }

                if (uiState.selectedPaths.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var showConfirm by remember { mutableStateOf(false) }
                    if (showConfirm) {
                        val selectedSize = uiState.files
                            .filter { it.file.absolutePath in uiState.selectedPaths }
                            .sumOf { it.file.length() }
                        ConfirmDeleteDialog(
                            title = "Dosyaları temizle",
                            message = "Seçili önbellek dosyaları çöp kutusuna taşınacak.",
                            onConfirm = {
                                showConfirm = false
                                viewModel.deleteSelected()
                            },
                            onDismiss = { showConfirm = false },
                            fileCount = uiState.selectedPaths.size,
                            totalBytes = selectedSize,
                            moveToTrash = true
                        )
                    }
                    Button(
                        onClick = { showConfirm = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            "🗑️ Seçili Dosyaları Sil (${uiState.selectedPaths.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            } else if (!uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🗑️", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.hasScanned) "Önbellek dosyası bulunamadı" else "Henüz tarama yapılmadı",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Başlamak için 'Tara' butonuna basın",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSummary) {
        val summary = com.makay.cleaner.util.CleanResultTracker.last
        if (summary != null) {
            CleanSummaryDialog(
                category = summary.category,
                filesDeleted = summary.filesDeleted,
                spaceSaved = summary.spaceSaved,
                protectedSkipped = summary.protectedSkipped,
                movedToTrash = summary.movedToTrash,
                onDismiss = { viewModel.clearSummary() }
            )
        } else {
            viewModel.clearSummary()
        }
    }
}

@Composable
fun PermissionWarningCard(message: String, onRequestPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "⚠️ Depolama İzni Gerekli",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("İzin Ver")
            }
        }
    }
}

@Composable
fun CacheFileItem(
    file: File,
    subtitle: String = file.absolutePath,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChanged
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = formatBytes(file.length()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
