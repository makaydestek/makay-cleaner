package com.makay.cleaner.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.AppUninstallRepository
import com.makay.cleaner.domain.model.AppUninstallInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppUninstallUiState(
    val isLoading: Boolean = false,
    val apps: List<AppUninstallInfo> = emptyList(),
    val totalApps: Int = 0,
    val totalSize: Long = 0L,
    val totalSizeFormatted: String = "0 B",
    val showSystemApps: Boolean = false,
    val error: String? = null
)

class AppUninstallViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppUninstallRepository(application)
    
    private val _uiState = MutableStateFlow(AppUninstallUiState())
    val uiState: StateFlow<AppUninstallUiState> = _uiState.asStateFlow()

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val apps = repository.getAllApps()
                val totalSize = apps.sumOf { it.sizeBytes }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    apps = apps,
                    totalApps = apps.size,
                    totalSize = totalSize,
                    totalSizeFormatted = formatBytes(totalSize)
                )
                val ninetyDays = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
                val unusedCount = apps.count { !it.isSystemApp && it.installDate < ninetyDays }
                com.makay.cleaner.data.CleaningRecorder.updateUnusedAppsCount(getApplication(), unusedCount)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Uygulamalar yüklenemedi: ${e.message}"
                )
            }
        }
    }

    fun toggleSystemApps() {
        _uiState.value = _uiState.value.copy(
            showSystemApps = !_uiState.value.showSystemApps
        )
    }

    fun getFilteredApps(): List<AppUninstallInfo> {
        return if (_uiState.value.showSystemApps) {
            _uiState.value.apps
        } else {
            _uiState.value.apps.filter { !it.isSystemApp }
        }
    }

    fun getUninstallIntent(packageName: String): Intent {
        return repository.getUninstallIntent(packageName)
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    // Factory sınıfı
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppUninstallViewModel::class.java)) {
                return AppUninstallViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}