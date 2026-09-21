package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.SocialMediaRepository
import com.makay.cleaner.domain.model.SocialMediaApp
import com.makay.cleaner.domain.model.SocialMediaCategory
import com.makay.cleaner.domain.model.SocialMediaFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SocialMediaUiState(
    val isLoading: Boolean = false,
    val hasScanned: Boolean = false,
    val allFiles: List<SocialMediaFileInfo> = emptyList(),
    val filteredFiles: List<SocialMediaFileInfo> = emptyList(),
    val selectedFiles: Set<Long> = emptySet(),
    val totalSize: Long = 0L,
    val totalSizeFormatted: String = "0 B",
    val selectedApp: SocialMediaApp? = null,
    val selectedCategory: SocialMediaCategory? = null,
    val error: String? = null,
    val message: String? = null
)

class SocialMediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SocialMediaRepository(application)

    private val _uiState = MutableStateFlow(SocialMediaUiState())
    val uiState: StateFlow<SocialMediaUiState> = _uiState.asStateFlow()

    fun scanSocialMediaFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            try {
                val allFiles = withContext(Dispatchers.IO) {
                    repository.getAllSocialMediaFiles()
                }
                val totalSize = allFiles.sumOf { it.sizeBytes }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = true,
                    allFiles = allFiles,
                    filteredFiles = allFiles,
                    totalSize = totalSize,
                    totalSizeFormatted = formatBytes(totalSize),
                    selectedFiles = emptySet(),
                    message = if (allFiles.isEmpty()) "Sosyal medya dosyası bulunamadı" else null
                )
                CleaningRecorder.recordScan(
                    getApplication(),
                    "Sosyal Medya",
                    allFiles.size,
                    totalSize
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = true,
                    error = "Dosyalar taranamadı: ${e.message}"
                )
            }
        }
    }

    fun toggleFileSelection(fileId: Long) {
        val currentSelected = _uiState.value.selectedFiles.toMutableSet()
        if (!currentSelected.add(fileId)) currentSelected.remove(fileId)
        _uiState.value = _uiState.value.copy(selectedFiles = currentSelected)
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedFiles = _uiState.value.filteredFiles.map { it.id }.toSet()
        )
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptySet())
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedFiles
            val filesToDelete = _uiState.value.filteredFiles.filter { it.id in selectedIds }
            var deletedCount = 0
            var saved = 0L
            val trash = com.makay.cleaner.data.RecycleBinRepository(getApplication())
            com.makay.cleaner.util.SystemFileGuard.consumeSkippedCount()

            withContext(Dispatchers.IO) {
                filesToDelete.forEach { file ->
                    val size = file.sizeBytes
                    if (trash.movePathToTrash(file.filePath, "Sosyal medya")) {
                        deletedCount++
                        saved += size
                    }
                }
            }

            if (deletedCount > 0) {
                CleaningRecorder.record(getApplication(), "Sosyal Medya", saved, deletedCount)
                com.makay.cleaner.util.CleanResultTracker.record("Sosyal Medya", deletedCount, saved)
            }

            val remainingFiles = _uiState.value.allFiles.filterNot { it.id in selectedIds }
            val filteredRemaining = remainingFiles.filter { file ->
                (_uiState.value.selectedApp == null || file.app == _uiState.value.selectedApp) &&
                    (_uiState.value.selectedCategory == null || file.category == _uiState.value.selectedCategory)
            }
            val totalSize = remainingFiles.sumOf { it.sizeBytes }

            _uiState.value = _uiState.value.copy(
                allFiles = remainingFiles,
                filteredFiles = filteredRemaining,
                totalSize = totalSize,
                totalSizeFormatted = formatBytes(totalSize),
                selectedFiles = emptySet(),
                message = "$deletedCount dosya çöp kutusuna taşındı (${formatBytes(saved)})"
            )
        }
    }

    fun filterByApp(app: SocialMediaApp?) {
        _uiState.value = _uiState.value.copy(selectedApp = app)
        applyFilters()
    }

    fun filterByCategory(category: SocialMediaCategory?) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        applyFilters()
    }

    private fun applyFilters() {
        val allFiles = _uiState.value.allFiles
        val selectedApp = _uiState.value.selectedApp
        val selectedCategory = _uiState.value.selectedCategory

        val filtered = allFiles.filter { file ->
            (selectedApp == null || file.app == selectedApp) &&
                (selectedCategory == null || file.category == selectedCategory)
        }

        _uiState.value = _uiState.value.copy(
            filteredFiles = filtered,
            selectedFiles = emptySet()
        )
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            selectedApp = null,
            selectedCategory = null,
            filteredFiles = _uiState.value.allFiles,
            selectedFiles = emptySet()
        )
    }

    private fun formatBytes(bytes: Long): String = com.makay.cleaner.ui.formatBytes(bytes)

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SocialMediaViewModel::class.java)) {
                return SocialMediaViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
