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
                val cats = withTimeoutOrNull(12_000L) { analyzeCategoriesFast() }
                    ?: analyzeCategoriesMinimal()
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
                    cats.sumOf { it.sizeBytes }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    storageInfo = runCatching { calculateStorageInfo() }.getOrNull(),
                    categories = analyzeCategoriesMinimal(),
                    errorMessage = null
                )
            }
        }
    }

    private fun calculateStorageInfo(): StorageInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.freeBytes
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
        val mediaSize = mediaStoreImagesVideosSize(cr)
        val audioSize = mediaStoreAudioSize(cr)
        val docsSize = maxOf(mediaStoreDocsSize(cr), estimateDocsShallow())
        val largeSize = mediaStoreLargeSize(cr)
        val otherSize = maxOf(mediaStoreDownloadSize(cr), estimateDownloadsShallow())

        return listOf(
            cat("Uygulama Önbelleği", cacheSize, 0xFFFF9800.toInt()),
            cat("Büyük Dosyalar", largeSize, 0xFFF44336.toInt()),
            cat("Medya Dosyaları", mediaSize + audioSize, 0xFF2196F3.toInt()),
            cat("Belgeler", docsSize, 0xFF4CAF50.toInt()),
            cat("Diğer", otherSize, 0xFF9E9E9E.toInt())
        )
    }

    private fun analyzeCategoriesMinimal(): List<StorageCategory> {
        val ctx = getApplication<Application>()
        val cache = safeCacheSize(ctx)
        val media = runCatching { mediaStoreImagesVideosSize(ctx.contentResolver) }.getOrDefault(0L)
        return listOf(
            cat("Uygulama Önbelleği", cache, 0xFFFF9800.toInt()),
            cat("Büyük Dosyalar", 0L, 0xFFF44336.toInt()),
            cat("Medya Dosyaları", media, 0xFF2196F3.toInt()),
            cat("Belgeler", 0L, 0xFF4CAF50.toInt()),
            cat("Diğer", 0L, 0xFF9E9E9E.toInt())
        )
    }

    private fun cat(name: String, size: Long, color: Int) = StorageCategory(
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

    private fun mediaStoreDownloadSize(cr: ContentResolver): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            querySizeSum(cr, MediaStore.Downloads.EXTERNAL_CONTENT_URI)
        } else {
            0L
        }
    }

    /** Download klasörü — en fazla 2 seviye, 400 dosya */
    private fun estimateDownloadsShallow(): Long {
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File(Environment.getExternalStorageDirectory(), "Download")
        return dirSizeBounded(root, maxDepth = 2, maxFiles = 400)
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
