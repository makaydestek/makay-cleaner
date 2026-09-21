package com.makay.cleaner.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PermissionState(
    val isGranted: Boolean = false,
    val showRationale: Boolean = false,
    val needsAllFilesAccess: Boolean = false
)

class PermissionViewModel(private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionState())
    val uiState: StateFlow<PermissionState> = _uiState.asStateFlow()

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        val isGranted = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                hasPermission(Manifest.permission.READ_MEDIA_IMAGES) &&
                hasPermission(Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> {
                hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        _uiState.value = _uiState.value.copy(
            isGranted = isGranted,
            needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !isGranted,
            showRationale = !isGranted
        )
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun getAllFilesAccessIntent(): Intent {
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun requestPermissionsAgain() {
        _uiState.value = _uiState.value.copy(showRationale = false)
    }
}