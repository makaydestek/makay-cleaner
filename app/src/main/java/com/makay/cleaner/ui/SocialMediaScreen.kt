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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hasPermission = rememberStoragePermissionState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_sosyal_medya_temizli_i), fontWeight = FontWeight.Bold) },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.txt_sosyal_medya_dosyalar),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.txt_whatsapp_telegram_ve_di_er_sosyal_medya_uygu),
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
                        stringResource(R.string.txt_otomatik_neri_e_i_i_1_s_mb, thresholdMb.toInt()),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.txt_sosyal_medya_boyutu_bu_e_i_i_a_nca_ak_ll_ner),
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
                    Text(stringResource(R.string.txt_taran_yor))
                } else {
                    Text(stringResource(R.string.txt_tara_2), style = MaterialTheme.typography.titleMedium)
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
                            text = stringResource(R.string.txt_bulunan_dosya),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.txt_1_s_dosya_2_s_6, uiState.filteredFiles.size, uiState.totalSizeFormatted),
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
                        text = stringResource(R.string.txt_1_s_dosya_se_ildi_3, uiState.selectedFiles.size),
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
                            stringResource(R.string.txt_se_ili_dosyalar_sil_1_s_3, uiState.selectedFiles.size),
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
                            text = stringResource(R.string.txt_sosyal_medya_dosyalar_n_bulmak_i_in_tara_but),
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
                    text = stringResource(R.string.txt_1_s_2_s_15, file.app.displayName, file.category.displayName),
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
