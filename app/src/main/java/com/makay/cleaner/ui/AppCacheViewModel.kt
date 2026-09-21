package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.AppCacheRepository
import com.makay.cleaner.data.CleaningRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppCacheUiState(
    val isLoading: Boolean = false,
    val hasScanned: Boolean = false,
    val files: List<AppCacheRepository.CacheFileEntry> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val totalCacheSize: Long = 0L,
    val message: String? = null,
    val error: String? = null,
    val showSummary: Boolean = false
)

class AppCacheViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppCacheRepository(application)

    private val _uiState = MutableStateFlow(AppCacheUiState())
    val uiState: StateFlow<AppCacheUiState> = _uiState.asStateFlow()

    fun scanCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            try {
                val files = withContext(Dispatchers.IO) {
                    repository.scanAccessibleCacheFiles()
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = true,
                    files = files,
                    totalCacheSize = files.sumOf { it.file.length() },
                    selectedPaths = emptySet(),
                    message = if (files.isEmpty()) "Erişilebilir önbellek dosyası bulunamadı" else null
                )
                CleaningRecorder.recordScan(
                    getApplication(),
                    "Uygulama Önbelleği",
                    files.size,
                    files.sumOf { it.file.length() }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = true,
                    error = "Tarama başarısız: ${e.message}"
                )
            }
        }
    }

    fun toggleSelection(path: String) {
        val selected = _uiState.value.selectedPaths.toMutableSet()
        if (!selected.add(path)) selected.remove(path)
        _uiState.value = _uiState.value.copy(selectedPaths = selected)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedPaths = _uiState.value.files.map { it.file.absolutePath }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedPaths = emptySet())
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val selected = _uiState.value.selectedPaths
            val toDelete = _uiState.value.files.filter { it.file.absolutePath in selected }
            var deleted = 0
            var saved = 0L
            val trash = com.makay.cleaner.data.RecycleBinRepository(getApplication())
            com.makay.cleaner.util.SystemFileGuard.consumeSkippedCount()

            withContext(Dispatchers.IO) {
                toDelete.forEach { entry ->
                    if (!com.makay.cleaner.util.SystemFileGuard.canDelete(entry.file)) {
                        com.makay.cleaner.util.SystemFileGuard.recordSkipped()
                        return@forEach
                    }
                    if (!com.makay.cleaner.util.SystemFileGuard.canCleanPackageCache(
                            getApplication(),
                            entry.packageName
                        )
                    ) {
                        com.makay.cleaner.util.SystemFileGuard.recordSkipped()
                        return@forEach
                    }
                    val size = entry.file.length()
                    if (trash.moveToTrash(entry.file, "Önbellek")) {
                        deleted++
                        saved += size
                    }
                }
            }

            if (deleted > 0) {
                CleaningRecorder.record(getApplication(), "Uygulama Önbelleği", saved, deleted)
                com.makay.cleaner.util.CleanResultTracker.record(
                    "Uygulama Önbelleği", deleted, saved
                )
            }

            val remaining = _uiState.value.files.filter { it.file.exists() }
            _uiState.value = _uiState.value.copy(
                files = remaining,
                totalCacheSize = remaining.sumOf { it.file.length() },
                selectedPaths = emptySet(),
                message = "$deleted dosya çöp kutusuna taşındı (${formatBytes(saved)})",
                showSummary = deleted > 0
            )
        }
    }

    fun clearSummary() {
        _uiState.value = _uiState.value.copy(showSummary = false)
    }

    private fun formatBytes(bytes: Long): String = com.makay.cleaner.ui.formatBytes(bytes)

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AppCacheViewModel::class.java)) {
                return AppCacheViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
