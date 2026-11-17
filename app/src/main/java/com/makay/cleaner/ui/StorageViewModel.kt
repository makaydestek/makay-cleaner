package com.makay.cleaner.ui

import android.app.Application
import android.content.ContentResolver
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.R
import com.makay.cleaner.data.AppCacheRepository
import com.makay.cleaner.domain.model.StorageCategory
import com.makay.cleaner.domain.model.StorageInfo
import com.makay.cleaner.util.JunkScanHelper
import com.makay.cleaner.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class StorageUiState(
    val isLoading: Boolean = false,
    val storageInfo: StorageInfo? = null,
    val categories: List<StorageCategory> = emptyList(),
    val errorMessage: String? = null,
    val needsPermission: Boolean = false
)

/**
 * Hızlı depolama özeti: StatFs + MediaStore + kendi önbellek.
 * Tüm SD kartı recursive gezmez (önceki sürüm takılıyor / 0 B kalıyordu).
 */
class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(StorageUiState(isLoading = true))
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    init {
        scanStorage()
    }

    fun scanStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val info = calculateStorageInfo()
                val hasStorageAccess = PermissionHelper.hasStoragePermission(getApplication())
                val cats = if (hasStorageAccess) {
                    withTimeoutOrNull(12_000L) { analyzeCategoriesFast() }
                        ?: analyzeCategoriesMinimal()
                } else {
                    analyzeCategoriesWithoutPermission()
                }
                _uiState.value = StorageUiState(
                    isLoading = false,
                    storageInfo = info,
                    categories = cats,
                    needsPermission = !PermissionHelper.hasStoragePermission(getApplication())
                )
                com.makay.cleaner.data.CleaningRecorder.recordScan(
                    getApplication(),
                    "Depolama Analizi",
                    cats.count { it.sizeBytes > 0 },
                    cats.sumOf { it.sizeBytes },
                    com.makay.cleaner.data.CleaningRecorder.METRIC_STORAGE
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    storageInfo = runCatching { calculateStorageInfo() }.getOrNull(),
                    categories = if (PermissionHelper.hasStoragePermission(getApplication())) {
                        analyzeCategoriesMinimal()
                    } else {
                        analyzeCategoriesWithoutPermission()
                    },
                    errorMessage = null
                )
            }
        }
    }

    private fun calculateStorageInfo(): StorageInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalBytes = stat.totalBytes
        // availableBytes: sistem/Xiaomi ile uyumlu kullanılabilir boş alan
        val freeBytes = stat.availableBytes
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0)
        val usagePercent =
            if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) * 100f else 0f
        return StorageInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            usagePercent = usagePercent,
            totalFormatted = formatBytes(totalBytes),
            freeFormatted = formatBytes(freeBytes),
            usedFormatted = formatBytes(usedBytes)
        )
    }

    private fun analyzeCategoriesFast(): List<StorageCategory> {
        val ctx = getApplication<Application>()
        val cr = ctx.contentResolver
        val cacheSize = maxOf(
            safeCacheSize(ctx),
            runCatching {
                JunkScanHelper.scan(ctx).categories.firstOrNull { it.id == "cache" }?.sizeBytes ?: 0L
            }.getOrDefault(0L)
        )
        val allMediaSize = mediaStoreImagesVideosSize(cr) + mediaStoreAudioSize(cr)
        val largeSize = mediaStoreLargeSize(cr).coerceAtMost(allMediaSize)
        val docsSize = maxOf(mediaStoreDocsSize(cr), estimateDocsShallow())
        val downloads = downloadBreakdown(cr)

        return listOf(
            cat("cache", "Uygulama Önbelleği", cacheSize, 0xFFFF9800.toInt()),
            cat("large", ctx.getString(R.string.storage_category_large_media), largeSize, 0xFFF44336.toInt()),
            cat("media", ctx.getString(R.string.storage_category_other_media), (allMediaSize - largeSize).coerceAtLeast(0L), 0xFF2196F3.toInt()),
            cat("docs", "Belgeler", docsSize, 0xFF4CAF50.toInt()),
            cat("other", ctx.getString(R.string.storage_category_other_downloads), downloads.otherBytes, 0xFF9E9E9E.toInt())
        )
    }

    private fun analyzeCategoriesMinimal(): List<StorageCategory> {
        val ctx = getApplication<Application>()
        val cache = safeCacheSize(ctx)
        val media = runCatching { mediaStoreImagesVideosSize(ctx.contentResolver) }.getOrDefault(0L)
        return listOf(
            cat("cache", "Uygulama Önbelleği", cache, 0xFFFF9800.toInt()),
            cat("large", getApplication<Application>().getString(R.string.storage_category_large_media), 0L, 0xFFF44336.toInt()),
            cat("media", "Medya Dosyaları", media, 0xFF2196F3.toInt()),
            cat("docs", "Belgeler", 0L, 0xFF4CAF50.toInt()),
            cat("other", "Diğer", 0L, 0xFF9E9E9E.toInt())
        )
    }

    private fun analyzeCategoriesWithoutPermission(): List<StorageCategory> {
        val cache = safeCacheSize(getApplication())
        return listOf(
            cat("cache", "Uygulama Önbelleği", cache, 0xFFFF9800.toInt()),
            cat("large", getApplication<Application>().getString(R.string.storage_category_large_media), 0L, 0xFFF44336.toInt()),
            cat("media", "Medya Dosyaları", 0L, 0xFF2196F3.toInt()),
            cat("docs", "Belgeler", 0L, 0xFF4CAF50.toInt()),
            cat("other", "Diğer", 0L, 0xFF9E9E9E.toInt())
        )
    }

    private fun cat(id: String, name: String, size: Long, color: Int) = StorageCategory(
        id = id,
        name = name,
        icon = "",
        sizeBytes = size.coerceAtLeast(0),
        sizeFormatted = formatBytes(size.coerceAtLeast(0)),
        color = color
    )

    private fun safeCacheSize(ctx: Application): Long {
        return try {
            AppCacheRepository(ctx).scanAccessibleCacheFiles()
                .sumOf { it.file.length() }
                .coerceAtLeast(
                    dirSizeBounded(ctx.cacheDir) + (ctx.externalCacheDir?.let { dirSizeBounded(it) } ?: 0L)
                )
        } catch (_: Exception) {
            dirSizeBounded(ctx.cacheDir) + (ctx.externalCacheDir?.let { dirSizeBounded(it) } ?: 0L)
        }
    }

    private fun mediaStoreImagesVideosSize(cr: ContentResolver): Long {
        var total = 0L
        total += querySizeSum(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        total += querySizeSum(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return total
    }

    private fun mediaStoreAudioSize(cr: ContentResolver): Long {
        return querySizeSum(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }

    private fun querySizeSum(
        cr: ContentResolver,
        uri: android.net.Uri,
        selection: String? = null,
        args: Array<String>? = null
    ): Long {
        return try {
            var sum = 0L
            cr.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), selection, args, null)?.use { c ->
                val idx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (idx < 0) return 0L
                while (c.moveToNext()) {
                    sum += c.getLong(idx).coerceAtLeast(0)
                }
            }
            sum
        } catch (_: Exception) {
            0L
        }
    }

    /** 50 MB ve üzeri medya — sahte yüzde değil, MediaStore toplamı. */
    private fun mediaStoreLargeSize(cr: ContentResolver): Long {
        val sel = "${MediaStore.MediaColumns.SIZE} >= ?"
        val args = arrayOf((50L * 1024 * 1024).toString())
        return querySizeSum(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, sel, args) +
            querySizeSum(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, sel, args) +
            querySizeSum(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, sel, args)
    }

    private fun mediaStoreDocsSize(cr: ContentResolver): Long {
        val mimes = arrayOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv"
        )
        val placeholders = mimes.joinToString(",") { "?" }
        return querySizeSum(
            cr,
            MediaStore.Files.getContentUri("external"),
            "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($placeholders)",
            mimes
        )
    }

    private data class DownloadBreakdown(val otherBytes: Long)

    /** İndirilenlerde belge ve medya olarak sınıflandırılmayan baytlar. */
    private fun downloadBreakdown(cr: ContentResolver): DownloadBreakdown {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return DownloadBreakdown(estimateOtherDownloadsShallow())
        }
        return try {
            var other = 0L
            cr.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.MIME_TYPE),
                null, null, null
            )?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                if (sizeIndex >= 0) while (cursor.moveToNext()) {
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    val classified = mime.startsWith("image/") || mime.startsWith("video/") ||
                        mime.startsWith("audio/") || isDocumentMime(mime)
                    if (!classified) other += cursor.getLong(sizeIndex).coerceAtLeast(0L)
                }
            }
            DownloadBreakdown(other)
        } catch (_: Exception) {
            // Hata halinde toplamı tahmin olarak sunmamak, kategori çakışmasını önler.
            DownloadBreakdown(0L)
        }
    }

    private fun isDocumentMime(mime: String): Boolean = mime in setOf(
        "application/pdf", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain", "text/csv"
    )

    /** Sınıflandırılmamış Download dosyaları — belge ve medya hariç. */
    private fun estimateOtherDownloadsShallow(): Long {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File(Environment.getExternalStorageDirectory(), "Download")
        if (!root.exists()) return 0L
        val mediaExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "mp4", "mkv", "mov", "avi", "webm", "mp3", "m4a", "wav", "aac", "ogg", "opus")
        val documentExtensions = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
        var total = 0L
        var count = 0
        try {
            root.walkTopDown().maxDepth(2).forEach { file ->
                if (count >= 400) return@forEach
                if (!file.isFile) return@forEach
                count++
                val ext = file.extension.lowercase()
                if (ext !in mediaExtensions && ext !in documentExtensions) total += file.length()
            }
        } catch (_: Exception) {
        }
        return total
    }

    private fun estimateDocsShallow(): Long {
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )
        val exts = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv")
        var total = 0L
        var count = 0
        roots.filterNotNull().forEach { root ->
            if (!root.exists()) return@forEach
            try {
                root.walkTopDown().maxDepth(3).forEach { f ->
                    if (count > 300) return@forEach
                    if (f.isFile && f.extension.lowercase() in exts) {
                        total += f.length()
                        count++
                    }
                }
            } catch (_: Exception) {
            }
        }
        return total
    }

    private fun dirSizeBounded(dir: File?, maxDepth: Int = 4, maxFiles: Int = 800): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        var files = 0
        try {
            dir.walkTopDown().maxDepth(maxDepth).forEach { f ->
                if (files >= maxFiles) return@forEach
                if (f.isFile) {
                    size += f.length()
                    files++
                }
            }
        } catch (_: Exception) {
        }
        return size
    }

    fun refresh() = scanStorage()

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StorageViewModel::class.java)) {
                return StorageViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
