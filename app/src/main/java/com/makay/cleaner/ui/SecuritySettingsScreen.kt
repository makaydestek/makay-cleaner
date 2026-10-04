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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.makay.cleaner.security.AppLockManager
import com.makay.cleaner.security.BiometricManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    application: Application,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val appLockManager = remember { AppLockManager(application) }
    val biometricManager = remember { BiometricManager(application) }
    
    var isPinEnabled by remember { mutableStateOf(appLockManager.isPinEnabled()) }
    var isBiometricEnabled by remember { mutableStateOf(appLockManager.isBiometricEnabled()) }
    var isBiometricAvailable by remember { mutableStateOf(biometricManager.isBiometricAvailable()) }
    
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showRemovePinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔐 Güvenlik Ayarları", fontWeight = FontWeight.Bold) },
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
                        "Sistem dosya koruması",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "/system, /vendor ve üretici paketleri silinmez. Güvenlik taraması virüs motoru değildir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        } else {
                            showSetPinDialog = true
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) { focused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PIN Kilidi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isPinEnabled) "Aktif" else "Kapalı",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPinEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showSetPinDialog = true
                            } else {
                                appLockManager.removePin()
                                isPinEnabled = false
                            }
                        },
                        modifier = Modifier.tvSkipChildFocus()
                    )
                }
            }

            // Biyometrik Kilidi
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = if (!isBiometricAvailable) CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) else CardDefaults.cardColors()
            ) {
                TvFocusRow(
                    onClick = {
                        if (isBiometricAvailable) {
                            appLockManager.setBiometricEnabled(!isBiometricEnabled)
                            isBiometricEnabled = !isBiometricEnabled
                        }
                    },
                    modifier = Modifier.padding(8.dp)
                ) { focused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biyometrik Kilidi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                !isBiometricAvailable -> "Cihazda biyometrik yok"
                                isBiometricEnabled -> "Aktif"
                                else -> "Kapalı"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                !isBiometricAvailable -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { enabled ->
                            if (isBiometricAvailable) {
                                appLockManager.setBiometricEnabled(enabled)
                                isBiometricEnabled = enabled
                            }
                        },
                        enabled = isBiometricAvailable,
                        modifier = Modifier.tvSkipChildFocus()
                    )
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
                        text = " Güvenlik Bilgisi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• PIN kodunuz SHA-256 ile şifrelenir",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Biyometrik veriler cihazda güvenli şekilde saklanır",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Hassas veriler EncryptedSharedPreferences ile korunur",
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
            title = { Text("PIN Kodu Belirle") },
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
                        label = { Text("Yeni PIN") },
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
                        label = { Text("PIN Tekrar") },
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
                            newPin.length < 4 -> pinError = "PIN en az 4 haneli olmalıdır"
                            newPin != confirmPin -> pinError = "PIN'ler eşleşmiyor"
                            else -> {
                                appLockManager.setPin(newPin)
                                isPinEnabled = true
                                showSetPinDialog = false
                                newPin = ""
                                confirmPin = ""
                                pinError = null
                            }
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showSetPinDialog = false
                    newPin = ""
                    confirmPin = ""
                    pinError = null
                }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showRemovePinDialog) {
        AlertDialog(
            onDismissRequest = { showRemovePinDialog = false },
            title = { Text("PIN kilidini kapat") },
            text = { Text("PIN kaldırılınca uygulama arka plana gidince kilitlenmez.") },
            confirmButton = {
                TextButton(onClick = {
                    appLockManager.removePin()
                    isPinEnabled = false
                    showRemovePinDialog = false
                }) { Text("Kapat") }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePinDialog = false }) { Text("İptal") }
            }
        )
    }
}