package com.makay.cleaner.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.scanner.DuplicatePhotoScanner
import com.makay.cleaner.util.SystemFileGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class DuplicatePhotosUiState(
    val exactGroups: List<DuplicatePhotoScanner.PhotoGroup> = emptyList(),
    val similarGroups: List<DuplicatePhotoScanner.PhotoGroup> = emptyList(),
    val loading: Boolean = false,
    val hasScanned: Boolean = false,
    val cancelledEarly: Boolean = false,
    val progressText: String = "Hazırlanıyor…",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 1,
    val buckets: List<DuplicatePhotoScanner.BucketInfo> = emptyList(),
    /** Her tamamlanan taramada artar; otomatik işaretleme bunu tetikler. */
    val scanId: Int = 0
) {
    val allGroups: List<DuplicatePhotoScanner.PhotoGroup>
        get() = exactGroups + similarGroups
}

/**
 * Yinelenen fotoğraf taramasının durumu. Ekran yeniden oluşturulsa
 * (döndürme/tema değişimi) tarama sonucu kaybolmaz, süren tarama iptal olmaz.
 */
class DuplicatePhotosViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = DuplicatePhotoScanner(application)

    private val _uiState = MutableStateFlow(DuplicatePhotosUiState())
    val uiState: StateFlow<DuplicatePhotosUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val cancelFlag = AtomicBoolean(false)

    fun loadBuckets() {
        if (_uiState.value.buckets.isNotEmpty()) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { scanner.listBuckets() }.getOrDefault(emptyList()) }
            _uiState.value = _uiState.value.copy(buckets = list)
        }
    }

    fun scan(options: DuplicatePhotoScanner.ScanOptions) {
        scanJob?.cancel()
        cancelFlag.set(false)
        _uiState.value = _uiState.value.copy(
            loading = true,
            hasScanned = true,
            cancelledEarly = false,
            progressText = "Tarama başlıyor…",
            progressCurrent = 0,
            progressTotal = 1
        )
        scanJob = viewModelScope.launch {
            SystemFileGuard.consumeSkippedCount()
            val mainHandler = Handler(Looper.getMainLooper())
            val listener = DuplicatePhotoScanner.ProgressListener { phase, cur, tot ->
                mainHandler.post {
                    _uiState.value = _uiState.value.copy(
                        progressText = phase,
                        progressCurrent = cur,
                        progressTotal = tot.coerceAtLeast(1)
                    )
                }
            }
            val exact = withContext(Dispatchers.IO) {
                runCatching { scanner.scanExact(options, cancelFlag, listener) }.getOrDefault(emptyList())
            }
            if (cancelFlag.get()) {
                _uiState.value = _uiState.value.copy(
                    exactGroups = exact,
                    similarGroups = emptyList(),
                    cancelledEarly = true,
                    loading = false
                )
                return@launch
            }
            val similar = withContext(Dispatchers.IO) {
                runCatching { scanner.scanSimilar(options, cancelFlag, listener) }.getOrDefault(emptyList())
            }
            _uiState.value = _uiState.value.copy(
                exactGroups = exact,
                similarGroups = similar,
                cancelledEarly = cancelFlag.get(),
                loading = false,
                scanId = _uiState.value.scanId + 1
            )
        }
    }

    fun cancel() {
        cancelFlag.set(true)
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = false, cancelledEarly = true)
    }

    /** Ayarlara dön: sonuçları temizle. */
    fun reset() {
        cancelFlag.set(true)
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(
            exactGroups = emptyList(),
            similarGroups = emptyList(),
            loading = false,
            hasScanned = false,
            cancelledEarly = false
        )
    }

    override fun onCleared() {
        cancelFlag.set(true)
        scanJob?.cancel()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DuplicatePhotosViewModel::class.java)) {
                return DuplicatePhotosViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
