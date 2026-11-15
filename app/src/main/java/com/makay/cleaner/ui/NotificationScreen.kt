package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    viewModel: NotificationViewModel = viewModel(factory = NotificationViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNotificationRationale by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setReminderEnabled(true)
    }

    fun setReminderEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModel.setReminderEnabled(false)
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            showNotificationRationale = true
        } else {
            viewModel.setReminderEnabled(true)
        }
    }

    if (showNotificationRationale) {
        AlertDialog(
            onDismissRequest = { showNotificationRationale = false },
            title = { Text(stringResource(R.string.txt_bildirim_izni)) },
            text = { Text(stringResource(R.string.txt_temizlik_hat_rlatmalar_n_g_sterebilmek_i_in)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationRationale = false
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.txt_devam_et)) }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationRationale = false }) { Text(stringResource(R.string.txt_imdi_de_il)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_bildirim_ayarlari), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TvIconButton(onClick = onNavigateBack) {
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Temizlik Hatırlatması
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                TvFocusRow(
                    onClick = { setReminderEnabled(!uiState.reminderEnabled) },
                    modifier = Modifier.padding(8.dp)
                ) { focused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.txt_temizlik_hatirlatmasi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (uiState.reminderEnabled) 
                                "Her ${uiState.reminderIntervalHours} saatte bir" 
                            else 
                                "Kapali",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.reminderEnabled,
                        onCheckedChange = { setReminderEnabled(it) },
                        modifier = Modifier.tvSkipChildFocus()
                    )
                }
            }

            // Hatırlatma Aralığı
            if (uiState.reminderEnabled) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.txt_hatirlatma_araligi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        listOf(6L, 12L, 24L, 48L, 168L).forEach { hours ->
                            TvFocusRow(
                                onClick = { viewModel.setReminderInterval(hours) }
                            ) { focused ->
                                RadioButton(
                                    selected = uiState.reminderIntervalHours == hours,
                                    onClick = null,
                                    modifier = Modifier.tvSkipChildFocus(),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (hours) {
                                        6L -> "Her 6 saatte"
                                        12L -> "Her 12 saatte"
                                        24L -> "Gunluk (24 saat)"
                                        48L -> "2 Gunde bir"
                                        168L -> "Haftalik"
                                        else -> "$hours saatte"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Bildirim Türleri
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.txt_bildirim_turleri),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.txt_temizlik_tamamlandi),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.txt_temizlik_islemi_bittiginde_bildirim),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.txt_depolama_uyaris),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.txt_depolama_alani_azaldiginda_uyari),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.txt_temizlik_hatirlatmasi),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.txt_duzenli_temizlik_hatirlatmalari),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
