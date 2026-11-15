package com.makay.cleaner.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.scanner.FileAnalysisScanner
import com.makay.cleaner.util.SystemFileGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class FileAnalysisUiState(
    val report: FileAnalysisScanner.AnalysisReport? = null,
    val loading: Boolean = false,
    val progressText: String = "Hazırlanıyor…",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 6
)

/** Dosya analizi taraması; ağır sonuç ekran yeniden oluşturulsa da korunur. */
class FileAnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = FileAnalysisScanner(application)

    private val _uiState = MutableStateFlow(FileAnalysisUiState())
    val uiState: StateFlow<FileAnalysisUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val cancelFlag = AtomicBoolean(false)

    fun runAnalysis() {
        scanJob?.cancel()
        cancelFlag.set(false)
        _uiState.value = _uiState.value.copy(
            loading = true,
            progressText = "Analiz başlıyor…",
            progressCurrent = 0,
            progressTotal = 6
        )
        scanJob = viewModelScope.launch {
            SystemFileGuard.consumeSkippedCount()
            val main = Handler(Looper.getMainLooper())
            val listener = FileAnalysisScanner.ProgressListener { phase, cur, tot ->
                main.post {
                    _uiState.value = _uiState.value.copy(
                        progressText = phase,
                        progressCurrent = cur,
                        progressTotal = tot.coerceAtLeast(1)
                    )
                }
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { scanner.analyze(cancelFlag, listener) }.getOrNull()
            }
            _uiState.value = _uiState.value.copy(report = result, loading = false)
            if (result != null && !result.cancelled) {
                CleaningRecorder.recordScan(
                    getApplication(),
                    "Dosya Analizi",
                    result.scannedFileEstimate,
                    result.types.sumOf { it.sizeBytes },
                    CleaningRecorder.METRIC_STORAGE
                )
            }
        }
    }

    fun cancel() {
        cancelFlag.set(true)
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = false)
    }

    override fun onCleared() {
        cancelFlag.set(true)
        scanJob?.cancel()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FileAnalysisViewModel::class.java)) {
                return FileAnalysisViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
