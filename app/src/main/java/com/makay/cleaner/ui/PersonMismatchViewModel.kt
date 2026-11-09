package com.makay.cleaner.ui

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.PersonMismatchScanArchive
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.PersonMismatchScanner
import com.makay.cleaner.util.CleanResultTracker
import com.makay.cleaner.util.FilePurger
import com.makay.cleaner.util.PhotoAlbumMover
import com.makay.cleaner.util.SoftErrorLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

data class PersonMismatchUiState(
    val buckets: List<PersonMismatchScanner.BucketInfo> = emptyList(),
    val selectedBucket: String? = null,
    val referenceUris: List<Uri> = emptyList(),
    val candidates: List<PersonMismatchScanner.MismatchCandidate> = emptyList(),
    val selected: Set<String> = emptySet(),
    val loadingBuckets: Boolean = false,
    val scanning: Boolean = false,
    val busy: Boolean = false,
    val hasScanned: Boolean = false,
    val progressText: String = "",
    val progressCur: Int = 0,
    val progressTot: Int = 1,
    val threshold: Float = PersonMismatchScanner.DEFAULT_MATCH_THRESHOLD,
    val archiveSavedAt: Long = 0L,
    val archiveRestored: Boolean = false,
    val toastMessage: String? = null,
    val errorMessage: String? = null
)

class PersonMismatchViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = PersonMismatchScanner(application)
    private val archive = PersonMismatchScanArchive(application)
    private val trash = RecycleBinRepository(application)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cancelFlag = AtomicBoolean(false)
    private var scanJob: Job? = null

    private val _uiState = MutableStateFlow(PersonMismatchUiState())
    val uiState: StateFlow<PersonMismatchUiState> = _uiState.asStateFlow()

    init {
        restoreArchive()
    }

    fun consumeToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun refreshBuckets() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingBuckets = true) }
            val buckets = withContext(Dispatchers.IO) {
                runCatching { scanner.listBuckets() }.getOrElse {
                    SoftErrorLogger.warn(TAG, "Klasör listesi alınamadı", it)
                    emptyList()
                }
            }
            _uiState.update { state ->
                val selected = state.selectedBucket
                    ?: buckets.firstOrNull()?.name
                state.copy(
                    buckets = buckets,
                    selectedBucket = selected,
                    loadingBuckets = false
                )
            }
        }
    }

    fun selectBucket(name: String) {
        _uiState.update {
            it.copy(
                selectedBucket = name,
                candidates = emptyList(),
                selected = emptySet(),
                hasScanned = false,
                archiveRestored = false
            )
        }
    }

    fun setThreshold(value: Float) {
        _uiState.update { it.copy(threshold = value.coerceIn(0.30f, 0.70f)) }
    }

    fun addReferences(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { state ->
            state.copy(
                referenceUris = (state.referenceUris + uris)
                    .distinct()
                    .take(PersonMismatchScanner.MAX_REFERENCES)
            )
        }
    }

    fun removeReference(uri: Uri) {
        _uiState.update { state ->
            state.copy(referenceUris = state.referenceUris.filterNot { it == uri })
        }
    }

    fun toggleSelected(path: String) {
        _uiState.update { state ->
            val next = state.selected.toMutableSet()
            if (!next.add(path)) next.remove(path)
            state.copy(selected = next)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selected = state.candidates.map { it.path }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selected = emptySet()) }
    }

    fun cancelScan() {
        cancelFlag.set(true)
        // İş parçacığı iptal bayrağını görünce kısmi sonucu arşivleyip bitecek.
    }

    fun runScan() {
        val state = _uiState.value
        val bucket = state.selectedBucket ?: return
        if (state.referenceUris.isEmpty()) {
            _uiState.update { it.copy(toastMessage = "Önce asıl kişi için örnek foto seçin") }
            return
        }
        if (state.scanning) return

        scanJob?.cancel()
        cancelFlag.set(false)
        scanJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    scanning = true,
                    hasScanned = true,
                    archiveRestored = false,
                    candidates = emptyList(),
                    selected = emptySet(),
                    progressText = "Referans yüzler çıkarılıyor…",
                    progressCur = 0,
                    progressTot = 1,
                    errorMessage = null
                )
            }
            try {
                val refs = withContext(Dispatchers.Default) {
                    scanner.buildReferences(state.referenceUris)
                }
                if (refs.embeddings.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            scanning = false,
                            toastMessage = "Referanslarda yüz bulunamadı " +
                                "(yüz yok: ${refs.skippedNoFace}, hata: ${refs.errors})"
                        )
                    }
                    return@launch
                }

                val found = withContext(Dispatchers.Default) {
                    scanner.scan(
                        bucketName = bucket,
                        references = refs.embeddings,
                        matchThreshold = state.threshold,
                        cancelled = cancelFlag,
                        progress = PersonMismatchScanner.ProgressListener { phase, cur, tot ->
                            mainHandler.post {
                                _uiState.update { ui ->
                                    if (!ui.scanning) ui
                                    else ui.copy(
                                        progressText = phase,
                                        progressCur = cur,
                                        progressTot = tot.coerceAtLeast(1)
                                    )
                                }
                            }
                        }
                    )
                }

                val completed = !cancelFlag.get()
                val snapshot = archive.fromCandidates(
                    bucketName = bucket,
                    threshold = state.threshold,
                    referenceUris = state.referenceUris,
                    candidates = found,
                    completed = completed,
                    scannedCount = _uiState.value.progressCur,
                    totalCount = _uiState.value.progressTot
                )
                withContext(Dispatchers.IO) { archive.save(snapshot) }

                _uiState.update {
                    it.copy(
                        scanning = false,
                        candidates = found,
                        hasScanned = true,
                        archiveSavedAt = snapshot.savedAt,
                        toastMessage = when {
                            !completed && found.isNotEmpty() ->
                                "Tarama durduruldu · ${found.size} aday arşivlendi"
                            refs.skippedNoFace > 0 ->
                                "${refs.embeddings.size} yüz kaydı · ${refs.skippedNoFace} örnekte yüz yoktu"
                            else -> null
                        }
                    )
                }
            } catch (oom: OutOfMemoryError) {
                SoftErrorLogger.error(TAG, "Tarama OOM", oom)
                // Kısmi sonuç varsa koru
                persistCurrentIfAny(bucket, state, completed = false)
                _uiState.update {
                    it.copy(
                        scanning = false,
                        errorMessage = "Bellek yetersiz — tarama yarıda kesildi. " +
                            "Daha az örnek veya daha küçük klasör deneyin."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SoftErrorLogger.error(TAG, "Tarama hatası", e)
                persistCurrentIfAny(bucket, state, completed = false)
                _uiState.update {
                    it.copy(
                        scanning = false,
                        errorMessage = "Tarama tamamlanamadı: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    /** Sonuçları disk arşivine yazar (geri çıkmadan önce / silme sonrası). */
    fun persistArchiveNow() {
        val snapshot = buildSnapshotFromState() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            archive.save(snapshot)
            _uiState.update { it.copy(archiveSavedAt = snapshot.savedAt) }
        }
    }

    /** Navigasyon / onCleared için senkron arşiv — coroutine iptaline güvenmez. */
    fun persistArchiveBlocking() {
        val snapshot = buildSnapshotFromState() ?: return
        runCatching { archive.save(snapshot) }
        _uiState.update { it.copy(archiveSavedAt = snapshot.savedAt) }
    }

    private fun buildSnapshotFromState(): PersonMismatchScanArchive.Snapshot? {
        val state = _uiState.value
        val bucket = state.selectedBucket ?: return null
        if (!state.hasScanned && state.candidates.isEmpty()) return null
        return archive.fromCandidates(
            bucketName = bucket,
            threshold = state.threshold,
            referenceUris = state.referenceUris,
            candidates = state.candidates,
            completed = !state.scanning,
            scannedCount = state.progressCur,
            totalCount = state.progressTot
        )
    }

    fun applyTrash(paths: List<PersonMismatchScanner.MismatchCandidate>) {
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            var n = 0
            var saved = 0L
            withContext(Dispatchers.IO) {
                trash.beginBatch()
                paths.forEach { item ->
                    val f = File(item.path)
                    val size = item.sizeBytes
                    val ok = if (f.isFile) {
                        FilePurger.remove(
                            getApplication(),
                            f,
                            trash = trash,
                            category = "Farklı kişi"
                        )
                    } else {
                        trash.moveUriToTrash(item.uri, item.displayName, size, "Farklı kişi")
                    }
                    if (ok) {
                        n++
                        saved += size
                    }
                }
            }
            if (n > 0) {
                CleaningRecorder.record(getApplication(), "Farklı kişi", saved, n)
                CleanResultTracker.record("Farklı kişi", n, saved)
            }
            val done = paths.map { it.path }.toSet()
            _uiState.update { state ->
                state.copy(
                    busy = false,
                    candidates = state.candidates.filterNot { it.path in done },
                    selected = emptySet(),
                    toastMessage = if (n > 0) "$n foto çöp kutusuna taşındı" else "Silinemedi"
                )
            }
            persistArchiveNow()
        }
    }

    fun applyCopyOrMove(
        paths: List<PersonMismatchScanner.MismatchCandidate>,
        move: Boolean,
        album: String
    ) {
        if (paths.isEmpty() || album.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val donePaths = mutableSetOf<String>()
            withContext(Dispatchers.IO) {
                paths.forEach { item ->
                    val ok = if (move) {
                        PhotoAlbumMover.moveToAlbum(
                            getApplication(), item.path, item.uri, item.displayName, album
                        )
                    } else {
                        PhotoAlbumMover.copyToAlbum(
                            getApplication(), item.path, item.uri, item.displayName, album
                        )
                    }
                    if (ok) donePaths += item.path
                }
            }
            val verb = if (move) "taşındı" else "kopyalandı"
            _uiState.update { state ->
                val nextCandidates = if (move && donePaths.isNotEmpty()) {
                    state.candidates.filterNot { it.path in donePaths }
                } else state.candidates
                val nextSelected = if (move) state.selected - donePaths else state.selected
                state.copy(
                    busy = false,
                    candidates = nextCandidates,
                    selected = nextSelected,
                    toastMessage = if (donePaths.isNotEmpty()) {
                        "${donePaths.size} foto «$album» albümüne $verb"
                    } else {
                        "İşlem başarısız"
                    }
                )
            }
            if (move && donePaths.isNotEmpty()) persistArchiveNow()
        }
    }

    private fun restoreArchive() {
        viewModelScope.launch(Dispatchers.IO) {
            val snap = archive.load() ?: return@launch
            val candidates = archive.toCandidates(snap).filter { item ->
                val f = File(item.path)
                f.isFile || item.uri.scheme.equals("content", ignoreCase = true)
            }
            val refs = snap.referenceUris.mapNotNull { raw ->
                runCatching { Uri.parse(raw) }.getOrNull()
            }
            _uiState.update { state ->
                // Canlı tarama varsa arşivi üzerine yazma
                if (state.scanning || state.candidates.isNotEmpty()) state
                else state.copy(
                    selectedBucket = snap.bucketName.ifBlank { state.selectedBucket },
                    threshold = snap.threshold.coerceIn(0.30f, 0.70f),
                    referenceUris = refs.ifEmpty { state.referenceUris },
                    candidates = candidates,
                    hasScanned = candidates.isNotEmpty() || snap.completed,
                    archiveSavedAt = snap.savedAt,
                    archiveRestored = candidates.isNotEmpty(),
                    progressCur = snap.scannedCount,
                    progressTot = snap.totalCount.coerceAtLeast(1)
                )
            }
        }
    }

    private fun persistCurrentIfAny(
        bucket: String,
        state: PersonMismatchUiState,
        completed: Boolean
    ) {
        val current = _uiState.value.candidates
        if (current.isEmpty() && state.candidates.isEmpty()) return
        val items = current.ifEmpty { state.candidates }
        val snapshot = archive.fromCandidates(
            bucketName = bucket,
            threshold = state.threshold,
            referenceUris = state.referenceUris,
            candidates = items,
            completed = completed
        )
        runCatching { archive.save(snapshot) }
    }

    override fun onCleared() {
        persistArchiveBlocking()
        runCatching { scanner.close() }
        super.onCleared()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PersonMismatchViewModel::class.java)) {
                return PersonMismatchViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "PersonMismatchVM"
    }
}
