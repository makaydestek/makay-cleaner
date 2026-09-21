package com.makay.cleaner.ui

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.makay.cleaner.BuildConfig
import com.makay.cleaner.data.ThemeRepository
import com.makay.cleaner.ui.theme.ThemeMode
import com.makay.cleaner.update.AppUpdateViewModel
import com.makay.cleaner.update.findActivity
import com.makay.cleaner.util.ProManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    application: Application,
    onNavigateBack: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSecurity: () -> Unit = {},
    onNavigateToPro: () -> Unit = {},
    onNavigateToRecycleBin: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val themeRepository = remember { ThemeRepository.getInstance(application) }
    val currentTheme by themeRepository.themeMode.collectAsState()
    val currentDynamic by themeRepository.dynamicColor.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val updateVm: AppUpdateViewModel? = if (activity != null) {
        viewModel(viewModelStoreOwner = activity, factory = AppUpdateViewModel.Factory(application))
    } else null
    val updateState = updateVm?.state?.collectAsState()?.value
    val intervals = listOf(6L, 12L, 24L, 48L)

    LaunchedEffect(uiState.lastManualCleanMessage) {
        uiState.lastManualCleanMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar", fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Görünüm",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tema Modu",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Uygulamanın görünüm temasını seçin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    ThemeMode.values().forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = {
                                    themeRepository.setThemeMode(mode)
                                    viewModel.setThemeMode(mode)
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = mode.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dinamik Renk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Android 12+ cihazlarda duvar kağıdına göre renk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = currentDynamic,
                        onCheckedChange = {
                            themeRepository.setDynamicColor(it)
                            viewModel.setDynamicColor(it)
                        }
                    )
                }
            }

            Text(
                text = "🤖 Otomatik Temizlik",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Otomatik Temizlik",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.autoCleanEnabled)
                                    "Her ${uiState.autoCleanIntervalHours} saatte bir"
                                else
                                    "Kapalı",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.autoCleanEnabled,
                            onCheckedChange = { viewModel.setAutoCleanEnabled(it) }
                        )
                    }

                    if (uiState.autoCleanEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Aralık",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            intervals.forEach { hours ->
                                FilterChip(
                                    selected = uiState.autoCleanIntervalHours == hours,
                                    onClick = { viewModel.setAutoCleanInterval(hours) },
                                    label = { Text("${hours}s") }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Yalnızca Wi‑Fi")
                            Switch(
                                checked = uiState.wifiOnly,
                                onCheckedChange = { viewModel.setWifiOnly(it) }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Yalnızca şarjdayken")
                            Switch(
                                checked = uiState.chargingOnly,
                                onCheckedChange = { viewModel.setChargingOnly(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Kategoriler", fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Önbellek")
                            Switch(
                                checked = uiState.catCache,
                                onCheckedChange = { viewModel.setCategoryCache(it) }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Eski APK")
                            Switch(
                                checked = uiState.catApk,
                                onCheckedChange = { viewModel.setCategoryApk(it) }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Geçici / junk")
                            Switch(
                                checked = uiState.catTmp,
                                onCheckedChange = { viewModel.setCategoryTmp(it) }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.runManualClean() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("⚡ Şimdi Temizle", style = MaterialTheme.typography.titleMedium)
            }

            Button(
                onClick = onNavigateToRecycleBin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("🗑️ Çöp kutusu", style = MaterialTheme.typography.titleMedium)
            }

            if (ProManager.isProUiVisible()) {
                Button(
                    onClick = onNavigateToPro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("⭐ Pro / Gizlilik", style = MaterialTheme.typography.titleMedium)
                }
            }

            Button(
                onClick = { updateVm?.checkManual() },
                enabled = updateVm != null && updateState?.checking != true && updateState?.downloading != true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text(
                    when {
                        updateState?.checking == true -> "Kontrol ediliyor…"
                        updateState?.downloading == true -> "İndiriliyor…"
                        else -> "🔄 Güncelleme kontrol et"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
            val msg = updateState?.statusMessage ?: updateState?.error
            if (!msg.isNullOrBlank()) {
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (updateState?.error != null)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Kaynak: github.com/makaydestek/makay-cleaner · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onNavigateToNotifications,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("🔔 Bildirim Ayarları", style = MaterialTheme.typography.titleMedium)
            }

            Button(
                onClick = onNavigateToSecurity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
            ) {
                Text("🔐 Güvenlik Ayarları", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = "ℹ️ Hakkında",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Makay Cleaner",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sürüm: ${uiState.appVersion}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Geliştirici",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bu uygulamanın tasarımı ve geliştirmesi Murat AKAY tarafından yapılmıştır.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "© 2026 Tüm hakları saklıdır. Sideload / yerel dağıtım için hazırlanmıştır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
