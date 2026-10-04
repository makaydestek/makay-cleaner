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
import com.makay.cleaner.data.ThemeRepository
import com.makay.cleaner.ui.theme.ThemeMode
import com.makay.cleaner.update.AppUpdateViewModel
import com.makay.cleaner.update.findActivity
import com.makay.cleaner.util.LanguageManager
import com.makay.cleaner.util.PlatformManager
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
                        TvFocusRow(
                            onClick = {
                                themeRepository.setThemeMode(mode)
                                viewModel.setThemeMode(mode)
                            }
                        ) { focused ->
                            RadioButton(
                                selected = currentTheme == mode,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus(),
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                TvFocusRow(
                    onClick = {
                        themeRepository.setDynamicColor(!currentDynamic)
                        viewModel.setDynamicColor(!currentDynamic)
                    },
                    modifier = Modifier.padding(8.dp)
                ) { focused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dinamik Renk",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
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
                        },
                        modifier = Modifier.tvSkipChildFocus(),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            val currentPlatform = PlatformManager.rememberIsTvMode()
            val currentLang by LanguageManager.lang.collectAsState()
            LanguageManager.init(context)

            Text(
                text = LanguageManager.t("Cihaz ve dil", "Device and language", context),
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
                        LanguageManager.t("Arayüz", "Interface", context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PlatformManager.Platform.entries.forEach { platform ->
                        val selected = if (currentPlatform) {
                            platform == PlatformManager.Platform.TV
                        } else {
                            platform == PlatformManager.Platform.MOBILE
                        }
                        TvFocusRow(
                            onClick = { PlatformManager.setPlatform(context, platform) }
                        ) { _ ->
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                if (platform == PlatformManager.Platform.TV) "Android TV" else "Mobil / tablet",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LanguageManager.Lang.entries.forEach { lang ->
                        TvFocusRow(
                            onClick = { LanguageManager.set(context, lang) }
                        ) { _ ->
                            RadioButton(
                                selected = currentLang == lang,
                                onClick = null,
                                modifier = Modifier.tvSkipChildFocus()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(lang.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        text = "İngilizce şu an yalnızca ilk açılış rehberini değiştirir. Uygulamanın geri kalanı Türkçe kalır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
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
                    TvFocusRow(
                        onClick = { viewModel.setAutoCleanEnabled(!uiState.autoCleanEnabled) }
                    ) { focused ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Otomatik Temizlik",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
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
                            onCheckedChange = { viewModel.setAutoCleanEnabled(it) },
                            modifier = Modifier.tvSkipChildFocus()
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
                                val (chipSrc, chipFocused) = rememberTvInteraction()
                                FilterChip(
                                    selected = uiState.autoCleanIntervalHours == hours,
                                    onClick = { viewModel.setAutoCleanInterval(hours) },
                                    interactionSource = chipSrc,
                                    modifier = Modifier.makayFocus(corner = 20.dp),
                                    label = {
                                        Text("${hours}s")
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow("Yalnızca Wi‑Fi", uiState.wifiOnly) { viewModel.setWifiOnly(it) }
                        SettingsToggleRow("Yalnızca şarjdayken", uiState.chargingOnly) { viewModel.setChargingOnly(it) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Kategoriler", fontWeight = FontWeight.SemiBold)
                        SettingsToggleRow("Önbellek", uiState.catCache) { viewModel.setCategoryCache(it) }
                        SettingsToggleRow("Eski APK", uiState.catApk) { viewModel.setCategoryApk(it) }
                        SettingsToggleRow("Geçici / junk", uiState.catTmp) { viewModel.setCategoryTmp(it) }
                    }
                }
            }

            TvActionButton(
                onClick = { viewModel.runManualClean() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text("⚡ Şimdi Temizle", style = MaterialTheme.typography.titleMedium)
            }

            TvActionButton(
                onClick = onNavigateToRecycleBin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text("🗑️ Çöp kutusu", style = MaterialTheme.typography.titleMedium)
            }

            if (ProManager.isProUiVisible()) {
                TvActionButton(
                    onClick = onNavigateToPro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    idle = Color(0xFF2E7D32)
                ) {
                    Text("⭐ Pro / Gizlilik", style = MaterialTheme.typography.titleMedium)
                }
            }

            TvActionButton(
                onClick = { updateVm?.checkManual() },
                enabled = updateVm != null && updateState?.checking != true && updateState?.downloading != true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = Color(0xFF1565C0)
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

            TvActionButton(
                onClick = onNavigateToNotifications,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text("🔔 Bildirim Ayarları", style = MaterialTheme.typography.titleMedium)
            }

            TvActionButton(
                onClick = onNavigateToSecurity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = Color(0xFFFF5722)
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

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TvFocusRow(onClick = { onCheckedChange(!checked) }) { focused ->
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.tvSkipChildFocus()
        )
    }
}
