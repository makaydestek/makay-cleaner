package com.makay.cleaner.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makay.cleaner.domain.model.SocialMediaFileInfo
import com.makay.cleaner.util.PermissionHelper
import com.makay.cleaner.util.rememberStoragePermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialMediaScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    viewModel: SocialMediaViewModel = viewModel(factory = SocialMediaViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("💬 Sosyal Medya Temizliği", fontWeight = FontWeight.Bold) },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💬 Sosyal Medya Dosyaları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "WhatsApp, Telegram ve diğer sosyal medya uygulamalarının medya dosyalarını tarar ve temizler.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val socialPrefs = remember { com.makay.cleaner.data.SocialMediaPrefs(application) }
            var thresholdMb by remember { mutableFloatStateOf(socialPrefs.getAutoCleanThresholdMb().toFloat()) }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Otomatik öneri eşiği: ${thresholdMb.toInt()} MB",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Sosyal medya boyutu bu eşiği aşınca akıllı önerilerde uyarılır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = thresholdMb,
                        onValueChange = {
                            thresholdMb = it
                            socialPrefs.setAutoCleanThresholdMb(it.toInt())
                        },
                        valueRange = 50f..1000f,
                        steps = 18
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasPermission) {
                PermissionWarningCard(
                    message = "Sosyal medya dosyalarını tarayabilmek için tüm dosyalara erişim izni vermeniz gerekiyor."
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
                        viewModel.scanSocialMediaFiles()
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

            if (uiState.filteredFiles.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                            text = "${uiState.filteredFiles.size} dosya • ${uiState.totalSizeFormatted}",
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
                    items(uiState.filteredFiles, key = { it.id }) { file ->
                        SocialFileItem(
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
                        val selectedFiles = uiState.filteredFiles.filter { it.id in uiState.selectedFiles }
                        val selectedSize = selectedFiles.sumOf { it.sizeBytes }
                        ConfirmDeleteDialog(
                            title = "Sosyal medya temizliği",
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
                        Text("💬", style = MaterialTheme.typography.displayLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.hasScanned) "Dosya bulunamadı" else "Henüz tarama yapılmadı",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Sosyal medya dosyalarını bulmak için 'Tara' butonuna basın",
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
fun SocialFileItem(
    file: SocialMediaFileInfo,
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
                    text = file.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${file.app.displayName} • ${file.category.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = file.sizeFormatted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
