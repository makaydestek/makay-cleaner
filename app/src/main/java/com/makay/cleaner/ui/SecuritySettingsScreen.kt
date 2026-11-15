package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import android.app.Application
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.makay.cleaner.security.AppLockManager
import com.makay.cleaner.security.BiometricManager
import com.makay.cleaner.util.PlatformManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val appLockManager = remember { AppLockManager(application) }
    val biometricManager = remember { BiometricManager(application) }
    val isTv = PlatformManager.rememberIsTvMode()
    
    var isPinEnabled by remember { mutableStateOf(appLockManager.isPinEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(appLockManager.isBiometricEnabled()) }
    var isBiometricAvailable by remember {
        mutableStateOf(!isTv && biometricManager.isBiometricAvailable())
    }
    val storageDegraded = remember { appLockManager.isStorageDegraded() }
    
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var biometricHint by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.txt_g_venlik_ayarlar), fontWeight = FontWeight.Bold) },
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.txt_sistem_dosya_korumas),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.txt_system_vendor_ve_retici_paketleri_silinmez_g),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (storageDegraded) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.txt_ifreli_depo_kullan_lam_yor),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            stringResource(R.string.txt_cihaz_keystore_a_lamad_pin_ayarlar_yedek_dep),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // PIN Kilidi
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                    TvFocusRow(
                    onClick = {
                        if (isPinEnabled) {
                            showRemovePinDialog = true
                        } else if (storageDegraded) {
                            pinError = "Şifreli depo yok; PIN kurulamaz"
                            biometricHint = pinError
                        } else {
                            showSetPinDialog = true
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) { focused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.txt_pin_kilidi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                storageDegraded && !isPinEnabled -> "Şifreli depo yok — kurulamaz"
                                isPinEnabled -> "Aktif"
                                else -> "Kapalı"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (storageDegraded && !isPinEnabled)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (storageDegraded) {
                                    biometricHint = "Şifreli depo yok; PIN kurulamaz"
                                } else {
                                    showSetPinDialog = true
                                }
                            } else {
                                // Onaysız kaldırma yok: yalnızca onay diyaloğunu aç.
                                showRemovePinDialog = true
                            }
                        },
                        enabled = !storageDegraded || isPinEnabled,
                        modifier = Modifier.tvSkipChildFocus()
                    )
                }
            }

            // Biyometrik: TV'de gizle; PIN zorunlu
            if (!isTv) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = if (!isBiometricAvailable || !isPinEnabled) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) else CardDefaults.cardColors()
                ) {
                    TvFocusRow(
                        onClick = {
                            if (!isPinEnabled) {
                                biometricHint = "Önce PIN kilidini açın"
                                return@TvFocusRow
                            }
                            if (isBiometricAvailable) {
                                val next = !isBiometricEnabled
                                if (appLockManager.setBiometricEnabled(next)) {
                                    isBiometricEnabled = next
                                    biometricHint = null
                                } else {
                                    biometricHint = "Biyometrik için PIN gerekli"
                                }
                            }
                        },
                        modifier = Modifier.padding(8.dp)
                    ) { focused ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.txt_biyometrik_kilidi),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    !isPinEnabled -> "Önce PIN gerekli"
                                    !isBiometricAvailable -> "Cihazda biyometrik yok"
                                    isBiometricEnabled -> "Aktif"
                                    else -> "Kapalı"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = when {
                                    !isPinEnabled || !isBiometricAvailable ->
                                        MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            biometricHint?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = { enabled ->
                                if (!isPinEnabled) {
                                    biometricHint = "Önce PIN kilidini açın"
                                    return@Switch
                                }
                                if (isBiometricAvailable) {
                                    if (appLockManager.setBiometricEnabled(enabled)) {
                                        isBiometricEnabled = enabled
                                        biometricHint = null
                                    } else {
                                        biometricHint = "Biyometrik için PIN gerekli"
                                    }
                                }
                            },
                            enabled = isBiometricAvailable && isPinEnabled,
                            modifier = Modifier.tvSkipChildFocus()
                        )
                    }
                }
            }

            // Güvenlik Bilgisi
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.txt_g_venlik_bilgisi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.txt_pin_kodunuz_pbkdf2_120_000_tur_ile_tuzlanara),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.txt_biyometrik_veriler_cihazda_g_venli_ekilde_sa),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.txt_hassas_veriler_encryptedsharedpreferences_il),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // PIN Ayarlama Dialog
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSetPinDialog = false
                newPin = ""
                confirmPin = ""
                pinError = null
            },
            title = { Text(stringResource(R.string.txt_pin_kodu_belirle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { 
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                newPin = it
                                pinError = null
                            }
                        },
                        label = { Text(stringResource(R.string.txt_yeni_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = confirmPin,
                        onValueChange = { 
                            if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                confirmPin = it
                                pinError = null
                            }
                        },
                        label = { Text(stringResource(R.string.txt_pin_tekrar)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    pinError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            storageDegraded -> pinError = "Şifreli depo kullanılamıyor; PIN kurulamaz"
                            newPin.length < AppLockManager.MIN_PIN_LENGTH ->
                                pinError = "PIN en az ${AppLockManager.MIN_PIN_LENGTH} haneli olmalıdır"
                            newPin != confirmPin -> pinError = "PIN'ler eşleşmiyor"
                            !appLockManager.setPin(newPin) ->
                                pinError = "PIN kaydedilemedi (şifreli depo yok)"
                            else -> {
                                isPinEnabled = true
                                showSetPinDialog = false
                                newPin = ""
                                confirmPin = ""
                                pinError = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.txt_kaydet))
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSetPinDialog = false
                    newPin = ""
                    confirmPin = ""
                    pinError = null
                }) {
                    Text(stringResource(R.string.txt_ptal))
                }
            }
        )
    }

    if (showRemovePinDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            title = { Text(stringResource(R.string.txt_pin_kilidini_kapat)) },
            text = { Text(stringResource(R.string.txt_pin_kald_r_l_nca_uygulama_arka_plana_gidince)) },
            confirmButton = {
                TextButton(onClick = {
                    appLockManager.removePin()
                    isPinEnabled = false
                    isBiometricEnabled = false
                    showRemovePinDialog = false
                }) { Text(stringResource(R.string.txt_kapat)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePinDialog = false }) { Text(stringResource(R.string.txt_ptal)) }
            }
        )
    }
}
