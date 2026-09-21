package com.makay.cleaner.util

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.makay.cleaner.data.AppCacheRepository
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.ApkRemnantScanner
import com.makay.cleaner.scanner.CorpseFinder
import com.makay.cleaner.scanner.SystemCleanerScanner
import java.io.File

/**
 * Temizleyici: yalnızca gerçekten silinebilir dosyaları sayar ve temizler.
 * Gösterilen boyut = silinebilir boyut (eşleşme garantisi).
 */
object JunkScanHelper {

    data class JunkCategory(
        val id: String,
        val title: String,
        val sizeBytes: Long,
        val fileCount: Int
    )

    data class JunkReport(
        val categories: List<JunkCategory>,
        val totalBytes: Long
    )

    data class CleanResult(
        val filesDeleted: Int,
        val spaceSaved: Long,
        val categoriesCleaned: Int
    )

    fun scan(context: Context): JunkReport {
        val cache = cacheEntries(context)
        val thumbs = thumbnailFiles()
        val system = systemItems().filter { isLikelyDeletable(it.file) }
        val corpses = corpseItems(context).filter { isLikelyDeletable(File(it.path)) }
        val apks = apkItems().filter { isLikelyDeletable(it.file) }
        val unused = unusedDownloadFiles()

        val cacheBytes = cache.sumOf { it.file.length() } + thumbs.sumOf { it.length() }
        val cacheCount = cache.size + thumbs.size

        val cats = listOf(
            JunkCategory("cache", "Önbellek dosyaları", cacheBytes, cacheCount),
            JunkCategory("unused", "Kullanılmayan dosyalar", unused.sumOf { it.length() }, unused.size),
            JunkCategory("apk", "Paketler", apks.sumOf { it.sizeBytes }, apks.size),
            JunkCategory("residue", "Artık dosyalar", corpses.sumOf { it.sizeBytes }, corpses.size),
            JunkCategory("tmp", "Geçici dosyalar", system.sumOf { it.sizeBytes }, system.size)
        )
        return JunkReport(cats, cats.sumOf { it.sizeBytes })
    }

    /** Tüm kategorileri temizle (Optimizasyon tek dokunuş). */
    fun cleanAll(context: Context): CleanResult =
        cleanSelected(context, setOf("cache", "unused", "apk", "residue", "tmp"))

    fun cleanSelected(context: Context, categoryIds: Set<String>): CleanResult {
        if (categoryIds.isEmpty()) return CleanResult(0, 0, 0)
        val trash = RecycleBinRepository(context)
        var deleted = 0
        var saved = 0L
        var cats = 0

        fun purgeFile(file: File, category: String): Boolean {
            if (!file.exists()) return false
            if (file.isDirectory) {
                var ok = false
                file.listFiles()?.forEach { child ->
                    if (purgeFile(child, category)) ok = true
                }
                if (file.listFiles()?.isEmpty() != false) {
                    try {
                        file.delete()
                    } catch (_: Exception) {
                    }
                }
                return ok
            }
            if (!file.isFile) return false
            if (!SystemFileGuard.canDelete(file)) {
                SystemFileGuard.recordSkipped()
                return false
            }
            val size = file.length()
            // 1) Doğrudan sil (aynı bölüm / önbellek)
            try {
                if (file.delete()) {
                    deleted++
                    saved += size
                    return true
                }
            } catch (_: Exception) {
            }
            // 2) MediaStore üzerinden (thumbnail vb.)
            if (deleteViaMediaStore(context, file)) {
                deleted++
                saved += size
                return true
            }
            // 3) Çöp kutusu
            if (trash.moveToTrash(file, category)) {
                deleted++
                saved += size
                return true
            }
            return false
        }

        if ("cache" in categoryIds) {
            cats++
            // Hızlı kendi önbellek + thumbnail
            val quick = QuickCleanHelper.cleanAppCaches(context)
            deleted += quick.filesDeleted
            saved += quick.spaceSaved
            cacheEntries(context).forEach { purgeFile(it.file, "Önbellek") }
            thumbnailFiles().forEach { purgeFile(it, "Küçük resim") }
        }

        if ("unused" in categoryIds) {
            cats++
            unusedDownloadFiles().forEach { purgeFile(it, "Eski indirme") }
        }

        if ("apk" in categoryIds) {
            cats++
            apkItems().forEach { purgeFile(it.file, "APK") }
        }

        if ("residue" in categoryIds) {
            cats++
            val finder = CorpseFinder(context)
            corpseItems(context).forEach { corpse ->
                val (s, c) = finder.deleteCorpse(corpse)
                if (c > 0 || s > 0) {
                    saved += s
                    deleted += c
                } else {
                    purgeFile(File(corpse.path), "Artık")
                }
            }
        }

        if ("tmp" in categoryIds) {
            cats++
            systemItems().forEach { item ->
                purgeFile(item.file, item.reason)
            }
        }

        if (saved > 0 || deleted > 0) {
            CleaningRecorder.record(context, "Optimizasyon", saved, deleted)
            SystemFileGuard.consumeSkippedCount()
        }
        return CleanResult(deleted, saved, cats)
    }

    /**
     * Cihaz skoru (Xiaomi tarzı): junk, kullanılmayan uygulama ve depolama doluluğu düşürür.
     * Junk temizlenince o ceza kalkar. Fotoğraf/video kütüphanesi junk sayılmaz.
     * Kalan ceza (uygulama / dolu disk) kullanıcı aksiyonu olmadan 100 olmaz.
     */
    fun optimizationScore(
        junkBytes: Long,
        storageUsagePercent: Float = 0f,
        unusedApps: Int = 0
    ): Int {
        var score = 100
        val junkMb = junkBytes / (1024.0 * 1024.0)
        score -= when {
            junkMb <= 0.0 -> 0
            junkMb < 5 -> 5
            junkMb < 20 -> 10
            junkMb < 50 -> 15
            junkMb < 150 -> 25
            else -> 35
        }
        score -= when {
            unusedApps <= 0 -> 0
            unusedApps < 8 -> 5
            unusedApps < 20 -> 10
            unusedApps < 35 -> 15
            else -> 25
        }
        score -= when {
            storageUsagePercent >= 92f -> 15
            storageUsagePercent >= 85f -> 10
            storageUsagePercent >= 75f -> 5
            else -> 0
        }
        return score.coerceIn(40, 100)
    }

    private fun isLikelyDeletable(file: File): Boolean {
        if (!file.exists()) return false
        if (!SystemFileGuard.canDelete(file)) return false
        // Yazılamayan / silinemeyen dosyaları tarama sonucuna alma
        return try {
            file.canWrite() || file.parentFile?.canWrite() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteViaMediaStore(context: Context, file: File): Boolean {
        return try {
            val path = file.absolutePath
            val cr = context.contentResolver
            val uris = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri("external")
            )
            for (uri in uris) {
                val n = cr.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(path))
                if (n > 0) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun cacheEntries(context: Context): List<AppCacheRepository.CacheFileEntry> = try {
        AppCacheRepository(context).scanAccessibleCacheFiles()
            .filter { isLikelyDeletable(it.file) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun thumbnailFiles(): List<File> {
        val ext = Environment.getExternalStorageDirectory() ?: return emptyList()
        val roots = listOf(
            File(ext, "DCIM/.thumbnails"),
            File(ext, "Pictures/.thumbnails"),
            File(ext, ".thumbnails")
        )
        val out = mutableListOf<File>()
        roots.forEach { dir ->
            if (!dir.exists()) return@forEach
            try {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && isLikelyDeletable(f)) out.add(f)
                    else if (f.isDirectory) {
                        f.listFiles()?.forEach { child ->
                            if (child.isFile && isLikelyDeletable(child)) out.add(child)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun systemItems() = try {
        SystemCleanerScanner().scan(maxItems = 500)
    } catch (_: Exception) {
        emptyList()
    }

    private fun corpseItems(context: Context) = try {
        CorpseFinder(context).scan()
    } catch (_: Exception) {
        emptyList()
    }

    private fun apkItems() = try {
        ApkRemnantScanner().scan(minAgeDays = 1, limit = 200)
    } catch (_: Exception) {
        emptyList()
    }

    private fun unusedDownloadFiles(): List<File> {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download")
        ).distinctBy { it.absolutePath }
        val threshold = System.currentTimeMillis() - 30L * 24 * 3600_000
        val out = mutableListOf<File>()
        roots.forEach { dir ->
            if (!dir.exists()) return@forEach
            try {
                dir.listFiles()?.forEach { f ->
                    if (out.size >= 250) return@forEach
                    if (!f.isFile) return@forEach
                    if (!isLikelyDeletable(f)) return@forEach
                    val old = f.lastModified() < threshold
                    val junkExt = f.extension.lowercase() in setOf("apk", "tmp", "temp", "crdownload", "part")
                    if ((old && f.length() > 30 * 1024) || junkExt) out.add(f)
                }
            } catch (_: Exception) {
            }
        }
        return out
    }
}
