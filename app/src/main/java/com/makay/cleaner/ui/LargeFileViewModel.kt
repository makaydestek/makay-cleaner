package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.LargeFileRepository
import com.makay.cleaner.domain.model.FileCategory
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
    val categoryFilter: FileCategory? = null,
    val maxAgeDays: Int? = null,
    val visibleFiles: List<LargeFileInfo> = emptyList(),
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
                val visible = applyFilters(files, _uiState.value.categoryFilter, _uiState.value.maxAgeDays)
                val totalSize = visible.sumOf { it.sizeBytes }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    hasScanned = true,
                    files = files,
                    visibleFiles = visible,
                    totalSize = totalSize,
                    totalSizeFormatted = formatBytes(totalSize),
                    selectedFiles = emptySet(),
                    message = if (visible.isEmpty()) "Büyük dosya bulunamadı" else null
                )
                CleaningRecorder.recordScan(
                    getApplication(),
                    "Büyük Dosya",
                    files.size,
                    totalSize,
                    CleaningRecorder.METRIC_LARGE
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
            selectedFiles = _uiState.value.visibleFiles.map { it.id }.toSet()
        )
    }

    fun setCategoryFilter(category: FileCategory?) {
        _uiState.value = _uiState.value.copy(categoryFilter = category)
        refreshVisible()
    }

    fun setMaxAgeDays(days: Int?) {
        _uiState.value = _uiState.value.copy(maxAgeDays = days)
        refreshVisible()
    }

    private fun refreshVisible() {
        val s = _uiState.value
        val visible = applyFilters(s.files, s.categoryFilter, s.maxAgeDays)
        _uiState.value = s.copy(
            visibleFiles = visible,
            totalSize = visible.sumOf { it.sizeBytes },
            totalSizeFormatted = formatBytes(visible.sumOf { it.sizeBytes }),
            selectedFiles = s.selectedFiles.filter { id -> visible.any { it.id == id } }.toSet()
        )
    }

    private fun applyFilters(
        files: List<LargeFileInfo>,
        category: FileCategory?,
        maxAgeDays: Int?
    ): List<LargeFileInfo> {
        val cutoff = maxAgeDays?.let { System.currentTimeMillis() - it * 24L * 3600_000 }
        return files.filter { file ->
            val catOk = category == null || file.category == category
            val ageOk = cutoff == null || file.dateAdded >= cutoff
            catOk && ageOk
        }
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
            val deletedIds = mutableSetOf<Long>()
            val trash = com.makay.cleaner.data.RecycleBinRepository(getApplication())
            com.makay.cleaner.util.SystemFileGuard.consumeSkippedCount()

            withContext(Dispatchers.IO) {
                trash.beginBatch()
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
                        deletedIds.add(file.id)
                    }
                }
            }

            if (deletedCount > 0) {
                CleaningRecorder.record(getApplication(), "Büyük Dosya", saved, deletedCount)
                com.makay.cleaner.util.CleanResultTracker.record("Büyük Dosya", deletedCount, saved)
            }

            val allRemaining = _uiState.value.files.filter { it.id !in deletedIds }
            val remainingFiles = applyFilters(
                allRemaining,
                _uiState.value.categoryFilter,
                _uiState.value.maxAgeDays
            )
            val totalSize = remainingFiles.sumOf { it.sizeBytes }
            val failed = selectedIds.size - deletedIds.size

            _uiState.value = _uiState.value.copy(
                files = allRemaining,
                visibleFiles = remainingFiles,
                totalSize = totalSize,
                totalSizeFormatted = formatBytes(totalSize),
                selectedFiles = emptySet(),
                message = when {
                    deletedCount == 0 && failed > 0 ->
                        "Hiçbir dosya taşınamadı (izin veya sistem koruması)"
                    failed > 0 ->
                        "$deletedCount taşındı, $failed taşınamadı (${formatBytes(saved)})"
                    else ->
                        "$deletedCount dosya çöp kutusuna taşındı (${formatBytes(saved)})"
                }
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
