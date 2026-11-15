package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.makay.cleaner.security.AppLockManager
import com.makay.cleaner.security.BiometricManager
import com.makay.cleaner.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppLockScreen(
    application: Application,
    onSuccess: () -> Unit
) {
    val appLockManager = remember { AppLockManager(application) }
    val biometricManager = remember { BiometricManager(application) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    var lockoutMs by remember { mutableLongStateOf(appLockManager.lockoutRemainingMs()) }
    val pinEnabled = appLockManager.isPinEnabled()
    val guardLocked = appLockManager.isGuardLocked()
    val storageDegraded = appLockManager.isStorageDegraded()
    val biometricEnabled = appLockManager.isBiometricEnabled() && biometricManager.isBiometricAvailable()

    fun unlock() {
        appLockManager.markUnlocked()
        onSuccess()
    }

    fun startBiometric() {
        val activity = PermissionHelper.findActivity(context) as? FragmentActivity
        if (activity == null) {
            errorMessage = "Biyometrik doğrulama bu ekranda başlatılamadı"
            return
        }
        biometricManager.authenticate(
            activity = activity,
            onSuccess = { unlock() },
            onError = { msg -> errorMessage = msg }
        )
    }

    fun startDeviceCredential() {
        val activity = PermissionHelper.findActivity(context) as? FragmentActivity
        if (activity == null) {
            errorMessage = "Cihaz doğrulaması bu ekranda başlatılamadı"
            return
        }
        biometricManager.authenticateDeviceCredential(
            activity = activity,
            onSuccess = {
                // Kilit verisi kayıpsa guard'ı kaldır; aksi halde her açılışta kilitlenir.
                if (guardLocked) appLockManager.clearGuardAfterDeviceAuth()
                unlock()
            },
            onError = { msg -> errorMessage = msg }
        )
    }

    // Kilitliyken biyometrik hızlandırıcı: yalnızca PIN açıkken ve otomatik değil,
    // kullanıcı butona bastığında. (Otomatik prompt bazı cihazlarda iptal döngüsü
    // oluşturuyordu.)
    LaunchedEffect(lockoutMs) {
        if (lockoutMs <= 0L) return@LaunchedEffect
        while (lockoutMs > 0L) {
            delay(1_000)
            lockoutMs = appLockManager.lockoutRemainingMs()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(text = "🔐", fontSize = 64.sp)
            Text(
                text = stringResource(R.string.txt_uygulama_kilitli),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            when {
                guardLocked -> {
                    Text(
                        text = stringResource(R.string.txt_kilit_kayd_okunamad_g_venlik_gere_i_uygulama),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.txt_devam_etmek_i_in_cihaz_n_z_n_ekran_kilidini),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { startDeviceCredential() },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text(stringResource(R.string.txt_cihaz_kilidi_ile_do_rula)) }
                }
                else -> {
                    if (storageDegraded) {
                        Text(
                            text = stringResource(R.string.txt_uyar_ifreli_depo_kullan_lam_yor_kilit_g_veni),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = if (pinEnabled) "Erişmek için 6 haneli PIN kodunuzu girin"
                        else "Kilit aktif",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (pinEnabled) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = {
                                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                                    pin = it
                                    errorMessage = null
                                }
                            },
                            label = { Text(stringResource(R.string.txt_pin_kodu)) },
                            placeholder = { Text(stringResource(R.string.txt_6_haneli_pin)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = lockoutMs <= 0L && !isVerifying,
                            modifier = Modifier.fillMaxWidth(),
                            isError = errorMessage != null,
                            supportingText = {
                                errorMessage?.let {
                                    Text(text = it, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )

                        if (lockoutMs > 0L) {
                            Text(
                                text = stringResource(R.string.txt_ok_fazla_hatal_deneme_1_s_sn_sonra_tekrar_de, (lockoutMs / 1000 + 1)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            onClick = {
                                if (pin.length < AppLockManager.MIN_PIN_LENGTH) {
                                    errorMessage = "PIN en az 6 haneli olmalıdır"
                                    return@Button
                                }
                                isVerifying = true
                                scope.launch {
                                    // PBKDF2 120k iterasyon ana thread'de çalıştırılmamalı.
                                    val ok = withContext(Dispatchers.Default) {
                                        appLockManager.verifyPin(pin)
                                    }
                                    isVerifying = false
                                    if (ok) {
                                        unlock()
                                    } else {
                                        lockoutMs = appLockManager.lockoutRemainingMs()
                                        errorMessage = if (lockoutMs > 0L)
                                            "Çok fazla hatalı deneme. Lütfen bekleyin."
                                        else
                                            "Yanlış PIN kodu"
                                        pin = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = pin.isNotEmpty() && !isVerifying && lockoutMs <= 0L
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.txt_do_rula), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    } else {
                        errorMessage?.let {
                            Text(text = it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = { startDeviceCredential() },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text(stringResource(R.string.txt_cihaz_kilidi_ile_do_rula)) }
                    }

                    if (biometricEnabled) {
                        OutlinedButton(
                            onClick = { startBiometric() },
                            enabled = !isVerifying,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(stringResource(R.string.txt_biyometrik_ile_giri_yap))
                        }
                    }
                }
            }
        }
    }
}
