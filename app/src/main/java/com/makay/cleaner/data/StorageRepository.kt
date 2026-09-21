package com.makay.cleaner.data

import android.os.Environment
import android.os.StatFs
import com.makay.cleaner.domain.model.StorageCategory
import com.makay.cleaner.domain.model.StorageInfo
import java.io.File
import java.text.DecimalFormat

class StorageRepository {

    private val decimalFormat = DecimalFormat("#.##")

    /**
     * Cihazın depolama istatistiklerini getirir
     */
    fun getStorageInfo(): StorageInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        
        val totalBytes = stat.totalBytes
        val freeBytes = stat.freeBytes
        val usedBytes = totalBytes - freeBytes
        val usagePercent = (usedBytes.toFloat() / totalBytes.toFloat()) * 100

        return StorageInfo(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            totalFormatted = formatBytes(totalBytes),
            freeFormatted = formatBytes(freeBytes),
            usedFormatted = formatBytes(usedBytes),
            usagePercent = usagePercent
        )
    }

    /**
     * Belirli klasörlerin boyutlarını hesaplar (Önbellek, İndirilenler vb.)
     */
    fun getStorageCategories(): List<StorageCategory> {
        val categories = mutableListOf<StorageCategory>()

        // Önbellek klasörü boyutu (basit versiyon)
        val cacheDir = File("/data/local/tmp")
        val cacheSize = if (cacheDir.exists()) calculateDirSize(cacheDir) else 0L

        // İndirilenler klasörü
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val downloadsSize = if (downloadsDir.exists()) calculateDirSize(downloadsDir) else 0L

        // Resimler klasörü
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val picturesSize = if (picturesDir.exists()) calculateDirSize(picturesDir) else 0L

        // Videolar klasörü
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val moviesSize = if (moviesDir.exists()) calculateDirSize(moviesDir) else 0L

        categories.add(
            StorageCategory(
                name = "Önbellek",
                sizeBytes = cacheSize,
                sizeFormatted = formatBytes(cacheSize),
                icon = "🗑️",
                color = 0xFFE53935.toInt()
            )
        )

        categories.add(
            StorageCategory(
                name = "İndirilenler",
                sizeBytes = downloadsSize,
                sizeFormatted = formatBytes(downloadsSize),
                icon = "",
                color = 0xFF1E88E5.toInt()
            )
        )

        categories.add(
            StorageCategory(
                name = "Resimler",
                sizeBytes = picturesSize,
                sizeFormatted = formatBytes(picturesSize),
                icon = "️",
                color = 0xFF43A047.toInt()
            )
        )

        categories.add(
            StorageCategory(
                name = "Videolar",
                sizeBytes = moviesSize,
                sizeFormatted = formatBytes(moviesSize),
                icon = "",
                color = 0xFF8E24AA.toInt()
            )
        )

        return categories.sortedByDescending { it.sizeBytes }
    }

    /**
     * Bir klasörün toplam boyutunu hesaplar (rekürsif)
     */
    private fun calculateDirSize(directory: File): Long {
        var size = 0L
        try {
            directory.listFiles()?.forEach { file ->
                size += if (file.isFile) {
                    file.length()
                } else if (file.isDirectory) {
                    calculateDirSize(file)
                } else {
                    0L
                }
            }
        } catch (e: SecurityException) {
            // İzin hatası, o klasörü atla
        }
        return size
    }

    /**
     * Byte cinsinden boyutu okunabilir formata çevirir (KB, MB, GB)
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}