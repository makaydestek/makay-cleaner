package com.makay.cleaner.ui

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

@Composable
fun AppLockScreen(
    application: Application,
    onSuccess: () -> Unit
) {
    val appLockManager = remember { AppLockManager(application) }
    val biometricManager = remember { BiometricManager(application) }
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val pinEnabled = appLockManager.isPinEnabled()
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

    LaunchedEffect(biometricEnabled, pinEnabled) {
        if (biometricEnabled && !pinEnabled) {
            startBiometric()
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
                text = "Uygulama Kilitli",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    pinEnabled -> "Erişmek için PIN kodunuzu girin"
                    biometricEnabled -> "Biyometrik doğrulama gerekli"
                    else -> "Kilit aktif"
                },
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
                    label = { Text("PIN Kodu") },
                    placeholder = { Text("4-6 haneli PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null,
                    supportingText = {
                        errorMessage?.let {
                            Text(text = it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                Button(
                    onClick = {
                        if (pin.length < 4) {
                            errorMessage = "PIN en az 4 haneli olmalıdır"
                            return@Button
                        }
                        isVerifying = true
                        if (appLockManager.verifyPin(pin)) {
                            unlock()
                        } else {
                            errorMessage = "Yanlış PIN kodu"
                            pin = ""
                        }
                        isVerifying = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = pin.isNotEmpty() && !isVerifying
                ) {
                    Text("Doğrula", style = MaterialTheme.typography.titleMedium)
                }
            } else {
                errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }

            if (biometricEnabled) {
                OutlinedButton(
                    onClick = { startBiometric() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Biyometrik ile giriş yap")
                }
            }
        }
    }
}
