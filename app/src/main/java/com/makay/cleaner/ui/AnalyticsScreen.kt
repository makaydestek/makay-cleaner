package com.makay.cleaner.ui

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    LaunchedEffect(Unit) {
        viewModel.loadAnalytics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Gelişmiş Analitikler", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Toplam İstatistikler
            item {
                Text(
                    text = "Genel İstatistikler",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticsStatCard(
                        title = "Toplam Temizlik",
                        value = "${uiState.totalCleanings}",
                        subtitle = "işlem",
                        modifier = Modifier.weight(1f),
                        color = primaryColor
                    )
                    AnalyticsStatCard(
                        title = "Kazanılan Alan",
                        value = uiState.totalSpaceSavedFormatted,
                        subtitle = "toplam",
                        modifier = Modifier.weight(1f),
                        color = secondaryColor
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnalyticsStatCard(
                        title = "Ortalama/Gün",
                        value = String.format("%.1f", uiState.averageDailyCleanings),
                        subtitle = "temizlik",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    AnalyticsStatCard(
                        title = "Son Temizlik",
                        value = uiState.lastCleanedFormatted.take(10),
                        subtitle = "tarih",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 7 Günlük Grafik
            if (uiState.last7DaysUsage.isNotEmpty()) {
                item {
                    Text(
                        text = "Son 7 Gün Aktivitesi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Günlük Temizlik Sayısı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            Canvas(modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)) {
                                val width = size.width
                                val height = size.height
                                val maxCleanings = uiState.maxDailyCleanings.toFloat()
                                val barWidth = width / uiState.last7DaysUsage.size * 0.6f
                                val spacing = width / uiState.last7DaysUsage.size

                                uiState.last7DaysUsage.forEachIndexed { index, daily ->
                                    val barHeight = if (maxCleanings > 0) {
                                        (daily.cleanings / maxCleanings) * (height - 20f)
                                    } else 0f
                                    val x = index * spacing + (spacing - barWidth) / 2
                                    val y = height - barHeight - 20f

                                    drawRoundRect(
                                        color = primaryColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barHeight),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Gün etiketleri
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                uiState.last7DaysUsage.forEach { daily ->
                                    val dayName = try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        val date = sdf.parse(daily.date)
                                        val daySdf = SimpleDateFormat("EEE", Locale.forLanguageTag("tr"))
                                        daySdf.format(date ?: Date()).take(3)
                                    } catch (e: Exception) {
                                        "?"
                                    }
                                    Text(
                                        text = dayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 30 Günlük Grafik
            if (uiState.last30DaysUsage.isNotEmpty()) {
                item {
                    Text(
                        text = "Son 30 Gün Aktivitesi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Aylık temizlik trendi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            ) {
                                val width = size.width
                                val height = size.height
                                val maxCleanings = uiState.max30DayCleanings.toFloat().coerceAtLeast(1f)
                                val barWidth = width / uiState.last30DaysUsage.size * 0.7f
                                val spacing = width / uiState.last30DaysUsage.size
                                uiState.last30DaysUsage.forEachIndexed { index, daily ->
                                    val barHeight = (daily.cleanings / maxCleanings) * (height - 20f)
                                    val x = index * spacing + (spacing - barWidth) / 2
                                    val y = height - barHeight - 20f
                                    drawRoundRect(
                                        color = secondaryColor,
                                        topLeft = Offset(x, y),
                                        size = Size(barWidth, barHeight.coerceAtLeast(0f)),
                                        cornerRadius = CornerRadius(2f, 2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // En Çok Kullanılan Özellikler
            if (uiState.mostUsedFeatures.isNotEmpty()) {
                item {
                    Text(
                        text = "En Çok Kullanılan Özellikler (30 Gün)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(uiState.mostUsedFeatures.take(5)) { (feature, count) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feature,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Badge(
                                containerColor = primaryColor
                            ) {
                                Text("$count kez")
                            }
                        }
                    }
                }
            }

            // Veri Dışa Aktarma
            item {
                Text(
                    text = "Veri Yönetimi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val jsonData = viewModel.exportData()
                            // JSON verisi gösterilebilir veya paylaştırılabilir
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("📤 Dışa Aktar")
                    }

                    OutlinedButton(
                        onClick = { viewModel.clearAnalytics() },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("️ Temizle")
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun AnalyticsStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.7f)
            )
        }
    }
}