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
import com.makay.cleaner.domain.model.LargeFileInfo
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFileScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    viewModel: LargeFileViewModel = viewModel(factory = LargeFileViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔍 Büyük Dosya Avcısı", fontWeight = FontWeight.Bold) },
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
                    message = "Büyük dosyaları tarayabilmek için tüm dosyalara erişim izni vermeniz gerekiyor."
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
                        viewModel.scanLargeFiles()
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
                    Text(
                        "🔍 Tara (>${uiState.minSizeMB} MB)",
                        style = MaterialTheme.typography.titleMedium
                    )
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
                            text = "Bulunan Dosya",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${uiState.files.size} dosya • ${uiState.totalSizeFormatted}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.selectAll() }) { Text("Tümünü Seç") }
                    TextButton(onClick = { viewModel.deselectAll() }) { Text("Temizle") }
                }

                if (uiState.selectedFiles.isNotEmpty()) {
                    Text(
                        text = "${uiState.selectedFiles.size} dosya seçildi",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.files, key = { it.id }) { file ->
                        LargeFileItem(
                            file = file,
                            isSelected = file.id in uiState.selectedFiles,
                            onSelectionChanged = { viewModel.toggleFileSelection(file.id) }
                        )
                    }
                }

                if (uiState.selectedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var showConfirm by remember { mutableStateOf(false) }
                    if (showConfirm) {
                        val selectedFiles = uiState.files.filter { it.id in uiState.selectedFiles }
                        val selectedSize = selectedFiles.sumOf { it.sizeBytes }
                        ConfirmDeleteDialog(
                            title = "Büyük dosyaları temizle",
                            message = "Seçili dosyalar çöp kutusuna taşınacak.",
                            onConfirm = {
                                showConfirm = false
                                viewModel.deleteSelectedFiles()
                            },
                            onDismiss = { showConfirm = false },
                            fileCount = uiState.selectedFiles.size,
                            totalBytes = selectedSize,
                            moveToTrash = true,
                            previewPaths = selectedFiles.take(6).map { it.filePath }
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
                            "🗑️ Seçili Dosyaları Sil (${uiState.selectedFiles.size})",
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
                        Text("🔍", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.hasScanned) "Büyük dosya bulunamadı" else "Henüz tarama yapılmadı",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "10 MB'dan büyük dosyaları bulmak için 'Tara' butonuna basın",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LargeFileItem(
    file: LargeFileInfo,
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
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${file.category.displayName} • ${file.dateFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = file.sizeFormatted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
