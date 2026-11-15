package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    onNavigateToExclusions: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(application))
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeRepository = remember { ThemeRepository.getInstance(application) }
    val currentTheme by themeRepository.themeMode.collectAsStateWithLifecycle()
    val currentDynamic by themeRepository.dynamicColor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val updateVm: AppUpdateViewModel? = if (activity != null) {
        viewModel(viewModelStoreOwner = activity, factory = AppUpdateViewModel.Factory(application))
    } else null
    val updateState = updateVm?.state?.collectAsStateWithLifecycle()?.value
    val intervals = listOf(6L, 12L, 24L, 48L)

    LaunchedEffect(uiState.lastManualCleanMessage) {
        uiState.lastManualCleanMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeManualCleanMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_ayarlar), fontWeight = FontWeight.Bold) },
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
                text = stringResource(R.string.txt_g_r_n_m),
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
                        text = stringResource(R.string.txt_tema_modu),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.txt_uygulaman_n_g_r_n_m_temas_n_se_in),
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
                            text = stringResource(R.string.txt_dinamik_renk),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.txt_android_12_cihazlarda_duvar_ka_d_na_g_re_ren),
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
            val currentLang by LanguageManager.lang.collectAsStateWithLifecycle()
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
                        text = stringResource(R.string.txt_ngilizce_u_an_yaln_zca_ilk_a_l_rehberini_de),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.txt_otomatik_temizlik_2),
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
                                text = stringResource(R.string.txt_otomatik_temizlik),
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
                            text = stringResource(R.string.txt_aral_k),
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
                                        Text(stringResource(R.string.txt_1_ss, hours))
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
                        Text(stringResource(R.string.txt_kategoriler), fontWeight = FontWeight.SemiBold)
                        SettingsToggleRow("Önbellek", uiState.catCache) { viewModel.setCategoryCache(it) }
                        SettingsToggleRow("Eski APK", uiState.catApk) { viewModel.setCategoryApk(it) }
                        SettingsToggleRow("Geçici / junk", uiState.catTmp) { viewModel.setCategoryTmp(it) }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsToggleRow(
                            "APK / indirme için çöp kutusu",
                            uiState.useTrash
                        ) { viewModel.setUseTrash(it) }
                        Text(
                            text = if (uiState.useTrash)
                                "Önerilen: APK ve indirmeler çöpe alınır; önbellek kalıcı silinir."
                            else
                                "Kapalı: seçili kategoriler kalıcı silinir (geri alınamaz).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            TvActionButton(
                onClick = { viewModel.runManualClean() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.manualCleanRunning,
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    if (uiState.manualCleanRunning) "⏳ Temizleniyor…" else "⚡ Şimdi Temizle",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            TvActionButton(
                onClick = onNavigateToRecycleBin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(stringResource(R.string.txt_p_kutusu_2), style = MaterialTheme.typography.titleMedium)
            }

            TvActionButton(
                onClick = onNavigateToExclusions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = MaterialTheme.colorScheme.primary
            ) {
                Text(stringResource(R.string.txt_hari_tutulanlar_2), style = MaterialTheme.typography.titleMedium)
            }

            if (ProManager.isProUiVisible()) {
                TvActionButton(
                    onClick = onNavigateToPro,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    idle = Color(0xFF2E7D32)
                ) {
                    Text(stringResource(R.string.txt_pro_gizlilik), style = MaterialTheme.typography.titleMedium)
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
                Text(stringResource(R.string.txt_bildirim_ayarlar), style = MaterialTheme.typography.titleMedium)
            }

            TvActionButton(
                onClick = onNavigateToSecurity,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                idle = Color(0xFFFF5722)
            ) {
                Text(stringResource(R.string.txt_g_venlik_ayarlar), style = MaterialTheme.typography.titleMedium)
            }

            Text(
                text = stringResource(R.string.txt_hakk_nda),
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
                        text = stringResource(R.string.txt_makay_cleaner),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.txt_s_r_m_1_s, uiState.appVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.txt_geli_tirici),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.txt_bu_uygulaman_n_tasar_m_ve_geli_tirmesi_murat),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.txt_2026_t_m_haklar_sakl_d_r_sideload_yerel_da_t),
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
