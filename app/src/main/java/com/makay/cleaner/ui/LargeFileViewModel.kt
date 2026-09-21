package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.LargeFileRepository
import com.makay.cleaner.domain.model.LargeFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LargeFileUiState(
    val isLoading: Boolean = false,
    val hasScanned: Boolean = false,
    val files: List<LargeFileInfo> = emptyList(),
    val totalSize: Long = 0L,
    val totalSizeFormatted: String = "0 B",
    val selectedFiles: Set<Long> = emptySet(),
    val minSizeMB: Int = 10,
    val error: String? = null,
    val message: String? = null
)

class LargeFileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LargeFileRepository(application)

    private val _uiState = MutableStateFlow(LargeFileUiState())
    val uiState: StateFlow<LargeFileUiState> = _uiState.asStateFlow()

    fun scanLargeFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, message = null)
            try {
                val minSizeBytes = _uiState.value.minSizeMB.toLong() * 1024 * 1024
                val files = withContext(Dispatchers.IO) {
                    repository.getLargeFiles(minSizeBytes)
                }
                val totalSize = files.sumOf { it.sizeBytes }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = true,
                    files = files,
                    totalSize = totalSize,
                    totalSizeFormatted = formatBytes(totalSize),
                    selectedFiles = emptySet(),
                    message = if (files.isEmpty()) "Büyük dosya bulunamadı" else null
                )
                CleaningRecorder.recordScan(
                    getApplication(),
                    "Büyük Dosya",
                    files.size,
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
            selectedFiles = _uiState.value.files.map { it.id }.toSet()
        )
    }

    fun deselectAll() {
        _uiState.value = _uiState.value.copy(selectedFiles = emptySet())
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedFiles
            val filesToDelete = _uiState.value.files.filter { it.id in selectedIds }
            var deletedCount = 0
            var saved = 0L
            val trash = com.makay.cleaner.data.RecycleBinRepository(getApplication())
            com.makay.cleaner.util.SystemFileGuard.consumeSkippedCount()

            withContext(Dispatchers.IO) {
                filesToDelete.forEach { file ->
                    val size = file.sizeBytes
                    val ok = if (file.filePath.startsWith("content://")) {
                        trash.moveUriToTrash(
                            android.net.Uri.parse(file.filePath),
                            file.displayName,
                            size,
                            "Büyük dosya"
                        )
                    } else {
                        trash.movePathToTrash(file.filePath, "Büyük dosya")
                    }
                    if (ok) {
                        deletedCount++
                        saved += size
                    }
                }
            }

            if (deletedCount > 0) {
                CleaningRecorder.record(getApplication(), "Büyük Dosya", saved, deletedCount)
                com.makay.cleaner.util.CleanResultTracker.record("Büyük Dosya", deletedCount, saved)
            }

            val remainingFiles = _uiState.value.files.filter {
                it.id !in selectedIds || (
                    if (it.filePath.startsWith("content://")) it.id !in selectedIds
                    else java.io.File(it.filePath).exists()
                )
            }.filter { file ->
                if (file.filePath.startsWith("content://")) {
                    file.id !in selectedIds
                } else {
                    java.io.File(file.filePath).exists()
                }
            }
            val totalSize = remainingFiles.sumOf { it.sizeBytes }

            _uiState.value = _uiState.value.copy(
                files = remainingFiles,
                totalSize = totalSize,
                totalSizeFormatted = formatBytes(totalSize),
                selectedFiles = emptySet(),
                message = "$deletedCount dosya çöp kutusuna taşındı (${formatBytes(saved)})"
            )
        }
    }

    fun updateMinSize(sizeMB: Int) {
        _uiState.value = _uiState.value.copy(minSizeMB = sizeMB)
        scanLargeFiles()
    }

    private fun formatBytes(bytes: Long): String = com.makay.cleaner.ui.formatBytes(bytes)

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LargeFileViewModel::class.java)) {
                return LargeFileViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
