package com.makay.cleaner.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit,
    viewModel: PermissionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.checkPermissions()
        } else {
            viewModel.requestPermissionsAgain()
        }
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkPermissions()
    }

    if (uiState.isGranted) {
        LaunchedEffect(Unit) {
            onPermissionsGranted()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = context.getString(com.makay.cleaner.R.string.permission_required),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (uiState.needsAllFilesAccess) {
                "Tarama ve seçtiğiniz dosyaları temizleme özelliği için Android ayarlarında depolama erişimini açmanız gerekir. Bu özel izin, Android 11 ve üzeri cihazlarda paylaşılan depolamadaki dosyaları yönetmeye olanak tanır. İzin vermezseniz bu özellik çalışmaz; diğer ekranları kullanabilirsiniz."
            } else {
                "Tarama ve seçtiğiniz dosyaları temizleme özelliği için depolama izni gerekir. İzin vermezseniz bu özellik çalışmaz; diğer ekranları kullanabilirsiniz."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (uiState.needsAllFilesAccess) {
            Button(
                onClick = {
                    val intent = viewModel.getAllFilesAccessIntent()
                    try {
                        settingsLauncher.launch(intent)
                    } catch (_: Exception) {
                        settingsLauncher.launch(
                            android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Depolama iznini aç", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("İzin ver ve devam et", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onPermissionsDenied) {
            Text("Şimdi değil")
        }

        if (uiState.showRationale && !uiState.needsAllFilesAccess) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "İzin verilmeden temizlik işlemi yapılamaz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
