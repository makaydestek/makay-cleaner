package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
import com.makay.cleaner.domain.model.FileCategory
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_b_y_k_dosya_avc_s), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TvIconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.tvRequestInitialFocus()
                    ) {
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
                    Text(stringResource(R.string.txt_taran_yor))
                } else {
                    Text(
                        stringResource(R.string.txt_tara_1_s_mb, uiState.minSizeMB),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (hasPermission) {
                Text(stringResource(R.string.txt_boyut), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 50, 200).forEach { mb ->
                        FilterChip(
                            selected = uiState.minSizeMB == mb,
                            onClick = { viewModel.updateMinSize(mb) },
                            label = { Text(stringResource(R.string.txt_1_s_mb, mb)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.txt_t_r), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.categoryFilter == null,
                        onClick = { viewModel.setCategoryFilter(null) },
                        label = { Text(stringResource(R.string.txt_t_m)) }
                    )
                    FileCategory.entries.take(4).forEach { cat ->
                        FilterChip(
                            selected = uiState.categoryFilter == cat,
                            onClick = { viewModel.setCategoryFilter(cat) },
                            label = { Text(cat.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.txt_tarih), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.maxAgeDays == null,
                        onClick = { viewModel.setMaxAgeDays(null) },
                        label = { Text(stringResource(R.string.txt_t_m)) }
                    )
                    FilterChip(
                        selected = uiState.maxAgeDays == 7,
                        onClick = { viewModel.setMaxAgeDays(7) },
                        label = { Text(stringResource(R.string.txt_7_g_n)) }
                    )
                    FilterChip(
                        selected = uiState.maxAgeDays == 30,
                        onClick = { viewModel.setMaxAgeDays(30) },
                        label = { Text(stringResource(R.string.txt_30_g_n)) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            uiState.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
            }
            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.visibleFiles.isNotEmpty() || (uiState.hasScanned && uiState.files.isNotEmpty())) {
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
                            text = stringResource(R.string.txt_bulunan_dosya),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.txt_1_s_dosya_2_s_4, uiState.visibleFiles.size, uiState.totalSizeFormatted),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.selectAll() }) { Text(stringResource(R.string.txt_t_m_n_se_2)) }
                    TextButton(onClick = { viewModel.deselectAll() }) { Text(stringResource(R.string.txt_se_imi_kald_r)) }
                }

                if (uiState.selectedFiles.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.txt_1_s_dosya_se_ildi_2, uiState.selectedFiles.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.visibleFiles.groupBy { it.category }.forEach { (category, files) ->
                        item(key = "cat_${category.name}") {
                            Text(
                                stringResource(R.string.txt_1_s_2_s_11, category.displayName, files.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(files, key = { it.id }) { file ->
                            LargeFileItem(
                                file = file,
                                isSelected = file.id in uiState.selectedFiles,
                                onSelectionChanged = { viewModel.toggleFileSelection(file.id) }
                            )
                        }
                    }
                }

                if (uiState.selectedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    var showConfirm by remember { mutableStateOf(false) }
                    if (showConfirm) {
                        val selectedFiles = uiState.visibleFiles.filter { it.id in uiState.selectedFiles }
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
                            stringResource(R.string.txt_se_ili_dosyalar_sil_1_s_2, uiState.selectedFiles.size),
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
                            text = stringResource(R.string.txt_10_mb_dan_b_y_k_dosyalar_bulmak_i_in_tara_bu),
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
                    text = stringResource(R.string.txt_1_s_2_s_12, file.category.displayName, file.dateFormatted),
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
