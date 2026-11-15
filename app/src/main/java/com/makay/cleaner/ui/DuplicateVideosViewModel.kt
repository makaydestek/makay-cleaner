package com.makay.cleaner.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.scanner.DuplicatePhotoScanner
import com.makay.cleaner.scanner.DuplicateVideoScanner
import com.makay.cleaner.util.SystemFileGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class DuplicateVideosUiState(
    val groups: List<DuplicateVideoScanner.VideoGroup> = emptyList(),
    val loading: Boolean = false,
    val hasScanned: Boolean = false,
    val progressText: String = "Hazırlanıyor…",
    val progressCurrent: Int = 0,
    val progressTotal: Int = 1,
    val buckets: List<DuplicatePhotoScanner.BucketInfo> = emptyList(),
    val scanId: Int = 0
)

/** Yinelenen video taraması; ekran yeniden oluşturulsa da sonuç korunur. */
class DuplicateVideosViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = DuplicateVideoScanner(application)
    private val prefs = DuplicatePhotoPrefs(application)

    private val _uiState = MutableStateFlow(DuplicateVideosUiState())
    val uiState: StateFlow<DuplicateVideosUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private val cancelFlag = AtomicBoolean(false)

    fun loadBuckets() {
        if (_uiState.value.buckets.isNotEmpty()) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { scanner.listBuckets() }.getOrDefault(emptyList()) }
            _uiState.value = _uiState.value.copy(buckets = list)
        }
    }

    fun scan(mode: String, bucket: String?) {
        scanJob?.cancel()
        cancelFlag.set(false)
        _uiState.value = _uiState.value.copy(
            loading = true,
            hasScanned = true,
            progressText = "Tarama başlıyor…",
            progressCurrent = 0,
            progressTotal = 1
        )
        scanJob = viewModelScope.launch {
            SystemFileGuard.consumeSkippedCount()
            val main = Handler(Looper.getMainLooper())
            val listener = DuplicateVideoScanner.ProgressListener { phase, cur, tot ->
                main.post {
                    _uiState.value = _uiState.value.copy(
                        progressText = phase,
                        progressCurrent = cur,
                        progressTotal = tot.coerceAtLeast(1)
                    )
                }
            }
            val groups = withContext(Dispatchers.IO) {
                runCatching {
                    scanner.scanExact(
                        mode = mode,
                        bucketName = if (mode == DuplicatePhotoPrefs.MODE_FOLDER) bucket else null,
                        excludedBuckets = prefs.excludedBuckets,
                        allowedFormats = DuplicatePhotoPrefs.VIDEO_FORMATS,
                        quick = mode == DuplicatePhotoPrefs.MODE_QUICK,
                        cancelled = cancelFlag,
                        progress = listener
                    )
                }.getOrDefault(emptyList())
            }
            _uiState.value = _uiState.value.copy(
                groups = groups,
                loading = false,
                scanId = _uiState.value.scanId + 1
            )
        }
    }

    fun cancel() {
        cancelFlag.set(true)
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(loading = false, hasScanned = false)
    }

    override fun onCleared() {
        cancelFlag.set(true)
        scanJob?.cancel()
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DuplicateVideosViewModel::class.java)) {
                return DuplicateVideosViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
