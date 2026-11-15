package com.makay.cleaner.ui

import com.makay.cleaner.R
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    // Android 13+ galeri okuma izni. Verilmese bile uygulama açılır; yalnızca
    // medya taramaları sınırlı çalışır.
    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsGranted()
    }

    if (uiState.isGranted) {
        LaunchedEffect(Unit) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                !com.makay.cleaner.util.PermissionHelper.hasMediaPermission(context)
            ) {
                mediaLauncher.launch(com.makay.cleaner.util.PermissionHelper.mediaPermissionRequest())
            } else {
                onPermissionsGranted()
            }
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
                Text(stringResource(R.string.txt_depolama_iznini_a), style = MaterialTheme.typography.titleMedium)
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
                Text(stringResource(R.string.txt_zin_ver_ve_devam_et), style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onPermissionsDenied) {
            Text(stringResource(R.string.txt_imdi_de_il))
        }

        if (uiState.showRationale && !uiState.needsAllFilesAccess) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.txt_zin_verilmeden_temizlik_i_lemi_yap_lamaz),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
