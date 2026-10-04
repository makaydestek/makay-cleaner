package com.makay.cleaner.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    viewModel: NotificationViewModel = viewModel(factory = NotificationViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bildirim Ayarlari", fontWeight = FontWeight.Bold) },
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
                    onClick = { viewModel.setReminderEnabled(!uiState.reminderEnabled) },
                    modifier = Modifier.padding(8.dp)
                ) { focused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temizlik Hatirlatmasi",
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
                        onCheckedChange = { viewModel.setReminderEnabled(it) },
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
                            text = "Hatirlatma Araligi",
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
                        text = "Bildirim Turleri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Temizlik Tamamlandi",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Temizlik islemi bittiginde bildirim",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Depolama Uyarisı",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Depolama alani azaldiginda uyari",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Temizlik Hatirlatmasi",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Duzenli temizlik hatirlatmalari",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}