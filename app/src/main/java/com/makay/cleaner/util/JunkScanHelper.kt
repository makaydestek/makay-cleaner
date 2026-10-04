package com.makay.cleaner.util

import android.content.Context
import android.os.Environment
import com.makay.cleaner.data.AppCacheRepository
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.scanner.ApkRemnantScanner
import com.makay.cleaner.scanner.CorpseFinder
import com.makay.cleaner.scanner.SystemCleanerScanner
import java.io.File

/**
 * Skor yalnızca silinebilen dosya junk’ıdır. RAM tahmini skora girmez;
 * kilitli sistem önbelleği ayrı tutulur.
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
        val totalBytes: Long,
        val cleanableBytes: Long = totalBytes,
        val lockedBytes: Long = 0L
    )

    data class CleanResult(
        val filesDeleted: Int,
        val spaceSaved: Long,
        val categoriesCleaned: Int
    )

    fun scan(context: Context): JunkReport = scanInternal(context)

    /** Ayrıntılı temizleyici ile aynı kapsam (Xiaomi tarzı tam tarama). */
    fun deepScan(context: Context): JunkReport = scanInternal(context)

    private fun scanInternal(context: Context): JunkReport {
        val cache = cacheEntries(context)
        val thumbs = thumbnailFiles(context)
        val extra = extraCacheFiles(context)
        val seen = mutableSetOf<String>()
        fun File.takeUnique(): Boolean = seen.add(absolutePath)

        val cacheFiles = (cache.map { it.file } + thumbs + extra).filter { it.takeUnique() }
        val system = systemItems(4000)
            .filter { FilePurger.canRemove(context, it.file) && it.file.takeUnique() }
        val corpses = corpseItems(context)
            .filter { FilePurger.canRemove(context, File(it.path)) && File(it.path).takeUnique() }
        val apks = apkItems(0)
            .filter { FilePurger.canRemove(context, it.file) && it.file.takeUnique() }
        val unused = unusedDownloadFiles(context, 7).filter { it.takeUnique() }

        val walkedCache = cacheFiles.sumOf { it.length() }
        val statsCache = SystemCacheReclaimer.queryCacheBytes(context)
        val lockedCache = (statsCache - walkedCache).coerceAtLeast(0L)
        // RAM tahmini skora girmez: trim sonrası süreç ölünce tahmin geri gelir,
        // skor %100 iken yeniden açılışta tekrar düşer.

        val cats = listOf(
            JunkCategory("cache", "Önbellek dosyaları", walkedCache, cacheFiles.size),
            JunkCategory("unused", "Kullanılmayan dosyalar", unused.sumOf { it.length() }, unused.size),
            JunkCategory("apk", "Paketler", apks.sumOf { it.sizeBytes }, apks.size),
            JunkCategory("residue", "Artık dosyalar", corpses.sumOf { it.sizeBytes }, corpses.size),
            JunkCategory("tmp", "Geçici dosyalar", system.sumOf { it.sizeBytes }, system.size)
        )
        val cleanable = cats.sumOf { it.sizeBytes }
        return JunkReport(
            categories = cats,
            totalBytes = cleanable,
            cleanableBytes = cleanable,
            lockedBytes = lockedCache
        )
    }

    /** Tüm kategorileri temizle (Optimizasyon tek dokunuş). */
    fun cleanAll(context: Context): CleanResult =
        cleanSelected(context, setOf("cache", "unused", "apk", "residue", "tmp"))

    fun cleanSelected(
        context: Context,
        categoryIds: Set<String>,
        recordAs: String? = "Optimizasyon"
    ): CleanResult {
        if (categoryIds.isEmpty()) return CleanResult(0, 0, 0)
        val beforeBytes = scanInternal(context).cleanableBytes
        val trash = RecycleBinRepository(context)
        var deleted = 0
        var saved = 0L
        var cats = 0

        fun purgeFile(file: File, category: String): Boolean {
            val size = if (file.isFile) file.length() else 0L
            val existed = file.exists()
            val ok = FilePurger.remove(context, file, trash, category)
            if (ok && existed && !file.exists()) {
                deleted++
                saved += size
                return true
            }
            return false
        }

        if ("cache" in categoryIds) {
            cats++
            FilePurger.wipeOwnCaches(context)
            cacheEntries(context).forEach { purgeFile(it.file, "Önbellek") }
            thumbnailFiles(context).forEach { purgeFile(it, "Küçük resim") }
            extraCacheFiles(context).forEach { purgeFile(it, "Uygulama önbelleği") }
        }

        if ("unused" in categoryIds) {
            cats++
            unusedDownloadFiles(context, 7).forEach { purgeFile(it, "Eski indirme") }
        }

        if ("apk" in categoryIds) {
            cats++
            apkItems(0).forEach { purgeFile(it.file, "APK") }
        }

        if ("residue" in categoryIds) {
            cats++
            val finder = CorpseFinder(context)
            corpseItems(context).forEach { corpse ->
                val target = File(corpse.path)
                val (s, c) = finder.deleteCorpse(corpse)
                if (!target.exists() && (c > 0 || s > 0)) {
                    saved += s
                    deleted += c
                } else if (purgeFile(target, "Artık")) {
                    // purgeFile saydı
                } else if (target.exists()) {
                    SystemFileGuard.recordSkipped()
                }
            }
        }

        if ("tmp" in categoryIds) {
            cats++
            systemItems(4000).forEach { item ->
                purgeFile(item.file, item.reason)
            }
        }

        val afterBytes = scanInternal(context).cleanableBytes
        val verified = (beforeBytes - afterBytes).coerceAtLeast(0L)
        if (verified > 0L && recordAs != null) {
            CleaningRecorder.record(context, recordAs, verified, deleted.coerceAtLeast(1))
            SystemFileGuard.consumeSkippedCount()
        }
        return CleanResult(deleted, verified, cats)
    }

    /**
     * Ana skor (Xiaomi Güvenlik tarzı): yalnızca tek dokunuşla temizlenebilen junk düşürür.
     * Optimizasyon junk’ı temizleyince skor %100 olur.
     * Kullanılmayan uygulama / dolu disk skor düşürmez — bunlar ana sayfada “öneri”dir.
     * [storageUsagePercent] / [unusedApps] geriye uyumluluk için tutulur, skora etki etmez.
     */
    @Suppress("UNUSED_PARAMETER")
    fun optimizationScore(
        junkBytes: Long,
        storageUsagePercent: Float = 0f,
        unusedApps: Int = 0
    ): Int {
        val junkMb = junkBytes / (1024.0 * 1024.0)
        // Xiaomi: ~2.8 GB junk → ~%85
        val penalty = when {
            junkMb <= 1.0 -> 0
            junkMb < 50 -> 5
            junkMb < 200 -> 8
            junkMb < 500 -> 10
            junkMb < 1024 -> 12
            junkMb < 4096 -> 15
            else -> 20
        }
        return (100 - penalty).coerceIn(40, 100)
    }

    private fun cacheEntries(context: Context): List<AppCacheRepository.CacheFileEntry> = try {
        AppCacheRepository(context).scanAccessibleCacheFiles()
            .filter { FilePurger.canRemove(context, it.file) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun thumbnailFiles(context: Context): List<File> {
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
                    if (f.isFile && FilePurger.canRemove(context, f)) out.add(f)
                    else if (f.isDirectory) {
                        f.listFiles()?.forEach { child ->
                            if (child.isFile && FilePurger.canRemove(context, child)) out.add(child)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun systemItems(maxItems: Int = 500) = try {
        SystemCleanerScanner().scan(maxItems = maxItems)
    } catch (_: Exception) {
        emptyList()
    }

    private fun corpseItems(context: Context) = try {
        CorpseFinder(context).scan()
    } catch (_: Exception) {
        emptyList()
    }

    private fun apkItems(minAgeDays: Int = 1) = try {
        ApkRemnantScanner().scan(minAgeDays = minAgeDays, limit = if (minAgeDays <= 0) 400 else 200)
    } catch (_: Exception) {
        emptyList()
    }

    private fun unusedDownloadFiles(context: Context, minAgeDays: Int = 30): List<File> {
        val roots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download")
        ).distinctBy { it.absolutePath }
        val threshold = System.currentTimeMillis() - minAgeDays.coerceAtLeast(1) * 24L * 3600_000
        val out = mutableListOf<File>()
        roots.forEach { dir ->
            if (!dir.exists()) return@forEach
            try {
                dir.listFiles()?.forEach { f ->
                    if (out.size >= 400) return@forEach
                    if (!f.isFile) return@forEach
                    if (!FilePurger.canRemove(context, f)) return@forEach
                    val old = f.lastModified() < threshold
                    val junkExt = f.extension.lowercase() in setOf(
                        "apk", "tmp", "temp", "crdownload", "part", "log", "bak"
                    )
                    if ((old && f.length() > 20 * 1024) || junkExt) out.add(f)
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun extraCacheFiles(context: Context): List<File> {
        val ext = Environment.getExternalStorageDirectory() ?: return emptyList()
        val roots = mutableListOf(
            File(ext, "Android/data/com.whatsapp/cache"),
            File(ext, "Android/data/org.telegram.messenger/cache"),
            File(ext, "Android/data/com.instagram.android/cache"),
            File(ext, "Android/data/com.twitter.android/cache"),
            File(ext, "Android/data/com.facebook.katana/cache"),
            File(ext, "Android/data/com.google.android.youtube/cache"),
            File(ext, "Android/data/com.google.android.youtube.tv/cache"),
            File(ext, "Android/data/com.netflix.ninja/cache"),
            File(ext, "Android/data/org.videolan.vlc/cache")
        )
        roots.addAll(TvHelper.getTvStreamingCachePaths(context))
        val out = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 5 || out.size >= 2000) return
            if (!dir.exists()) return
            try {
                dir.listFiles()?.forEach { child ->
                    if (out.size >= 2000) return
                    if (child.isDirectory) walk(child, depth + 1)
                    else if (child.isFile && FilePurger.canRemove(context, child) && child.length() > 0) {
                        out.add(child)
                    }
                }
            } catch (_: Exception) {
            }
        }
        roots.distinctBy { it.absolutePath }.forEach { walk(it, 0) }
        return out
    }
}
