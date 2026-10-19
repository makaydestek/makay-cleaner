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
 *
 * Optimizasyon kazancı: yalnızca kaynak dosyası gerçekten yok olan
 * kalıcı silmeler + (mümkünse) sistem önbelleği geri kazanımı.
 * Çöp kutusuna taşıma “alan kazandık” sayılmaz.
 */
object JunkScanHelper {

    /** Öncelikli güvenli varsayılanlar (önbellek + geçici). */
    val SAFE_CATEGORY_IDS: Set<String> = setOf("cache", "tmp")

    /** Kullanıcı onayı önerilen kategoriler (APK / indirme / artık). */
    val REVIEW_CATEGORY_IDS: Set<String> = setOf("apk", "unused", "residue")

    fun isSafeCategory(id: String): Boolean = id in SAFE_CATEGORY_IDS

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
        /** Gerçekten boşalan / kalıcı silinen bayt (çöp taşıması hariç). */
        val spaceSaved: Long,
        val categoriesCleaned: Int,
        /** Çöp kutusuna taşınan (diskte hâlâ yer kaplar). */
        val movedToTrashBytes: Long = 0L,
        /** StatFs ile ölçülen boş alan artışı. */
        val freeSpaceGained: Long = 0L,
        val lockedRemaining: Long = 0L
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
        val unused = unusedDownloadFiles(context, 7).filter {
            FilePurger.canRemove(context, it) && it.takeUnique()
        }

        val walkedCache = cacheFiles.sumOf { it.length() }
        val statsCache = SystemCacheReclaimer.queryCacheBytes(context)
        val lockedCache = (statsCache - walkedCache).coerceAtLeast(0L)

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

    /** Tek dokunuş Optimizasyon: kalıcı silme (çöp yok) + sistem önbelleği denemesi. */
    fun cleanAll(context: Context): CleanResult =
        cleanSelected(
            context,
            setOf("cache", "unused", "apk", "residue", "tmp"),
            recordAs = "Optimizasyon",
            useTrash = false,
            reclaimSystemCache = true
        )

    /**
     * @param useTrash true ise APK/indirme/artık çöpe alınır (alan hemen açılmaz).
     *                 cache/tmp her zaman kalıcı silinir.
     * @param reclaimSystemCache StorageStats / freeStorage ile OEM benzeri deneme.
     */
    fun cleanSelected(
        context: Context,
        categoryIds: Set<String>,
        recordAs: String? = "Optimizasyon",
        useTrash: Boolean = true,
        reclaimSystemCache: Boolean = categoryIds.contains("cache")
    ): CleanResult {
        if (categoryIds.isEmpty()) return CleanResult(0, 0, 0)

        val freeBefore = StorageFreeSpace.snapshot()
        val lockedBefore = SystemCacheReclaimer.queryCacheBytes(context)
        val trash = if (useTrash) RecycleBinRepository(context) else null

        var deleted = 0
        var hardSaved = 0L
        var trashedBytes = 0L
        var cats = 0

        fun fileSize(file: File): Long = when {
            file.isFile -> file.length()
            file.isDirectory -> FilePurger.dirSizePublic(file)
            else -> 0L
        }

        /** Kalıcı silme — yalnızca kaynak yoksa say. */
        fun purgeHard(file: File, category: String): Boolean {
            if (!file.exists()) return false
            if (!FilePurger.canRemove(context, file)) {
                SystemFileGuard.recordSkipped()
                return false
            }
            val size = fileSize(file)
            val ok = FilePurger.remove(context, file, trash = null, category = category)
            if (ok && !file.exists() && size > 0L) {
                deleted++
                hardSaved += size
                return true
            }
            if (file.exists()) SystemFileGuard.recordSkipped()
            return false
        }

        /** Çöp: orijinal yok ama alan hemen açılmaz. */
        fun purgeTrash(file: File, category: String): Boolean {
            if (trash == null) return purgeHard(file, category)
            if (!file.exists()) return false
            if (!FilePurger.canRemove(context, file)) {
                SystemFileGuard.recordSkipped()
                return false
            }
            val size = fileSize(file)
            val ok = FilePurger.remove(context, file, trash, category)
            if (ok && !file.exists() && size > 0L) {
                deleted++
                trashedBytes += size
                return true
            }
            if (file.exists()) SystemFileGuard.recordSkipped()
            return false
        }

        if ("cache" in categoryIds) {
            cats++
            // Kendi + erişilebilir önbellek: her zaman kalıcı
            FilePurger.wipeOwnCaches(context).also {
                deleted += it.filesDeleted
                hardSaved += it.spaceSaved
            }
            cacheEntries(context).forEach { purgeHard(it.file, "Önbellek") }
            thumbnailFiles(context).forEach { purgeHard(it, "Küçük resim") }
            extraCacheFiles(context).forEach { purgeHard(it, "Uygulama önbelleği") }
        }

        if ("unused" in categoryIds) {
            cats++
            unusedDownloadFiles(context, 7).forEach { f ->
                if (useTrash) purgeTrash(f, "Eski indirme") else purgeHard(f, "Eski indirme")
            }
        }

        if ("apk" in categoryIds) {
            cats++
            apkItems(0).forEach { item ->
                if (useTrash) purgeTrash(item.file, "APK") else purgeHard(item.file, "APK")
            }
        }

        if ("residue" in categoryIds) {
            cats++
            val finder = CorpseFinder(context)
            corpseItems(context).forEach { corpse ->
                val target = File(corpse.path)
                if (!target.exists()) return@forEach
                if (useTrash) {
                    if (!purgeTrash(target, "Artık") && target.exists()) {
                        SystemFileGuard.recordSkipped()
                    }
                } else {
                    val (s, c) = finder.deleteCorpse(corpse)
                    if (!target.exists() && (c > 0 || s > 0)) {
                        hardSaved += s
                        deleted += c.coerceAtLeast(1)
                    } else if (!purgeHard(target, "Artık") && target.exists()) {
                        SystemFileGuard.recordSkipped()
                    }
                }
            }
        }

        if ("tmp" in categoryIds) {
            cats++
            systemItems(4000).forEach { item ->
                purgeHard(item.file, item.reason)
            }
        }

        if (reclaimSystemCache && "cache" in categoryIds) {
            val sys = SystemCacheReclaimer.reclaim(context)
            // Dış önbellek silmeleri hardSaved’a eklenmiş olabilir; yalnızca ek paket dokunuşu say
            if (sys.packagesTouched > 0) deleted += sys.packagesTouched
            // reclaim içindeki dosya silmeleri — yalnızca henüz sayılmayan artış
            if (sys.bytesFreed > hardSaved) {
                hardSaved = sys.bytesFreed
            }
        }

        // StatFs doğrulaması (kısa gecikme — IO iş parçacığında)
        Thread.sleep(120)
        val freeAfter = StorageFreeSpace.snapshot()
        val freeGained = StorageFreeSpace.gained(freeBefore, freeAfter)
        val lockedAfter = SystemCacheReclaimer.queryCacheBytes(context)
        val lockedDrop = (lockedBefore - lockedAfter).coerceAtLeast(0L)

        // Alternatif uygulamalar boş alan / sistem önbelleğine bakar.
        // Öncelik: ölçülen boş alan artışı; yoksa doğrulanmış kalıcı silme; sonra kilitli önbellek düşüşü.
        // Tarama farkı (beforeScan-afterScan) ASLA kullanılmaz — sahte başarı üretir.
        val spaceSaved = when {
            freeGained > 0L -> freeGained
            hardSaved > 0L -> hardSaved
            lockedDrop > 0L -> lockedDrop
            else -> 0L
        }

        if (spaceSaved > 0L && recordAs != null) {
            CleaningRecorder.record(
                context,
                recordAs,
                spaceSaved,
                deleted.coerceAtLeast(1)
            )
            SystemFileGuard.consumeSkippedCount()
        } else if (trashedBytes > 0L && recordAs != null) {
            // Çöp taşıması istatistiğe “alan kazancı” olarak yazılmaz
            SystemFileGuard.consumeSkippedCount()
        }

        return CleanResult(
            filesDeleted = deleted,
            spaceSaved = spaceSaved,
            categoriesCleaned = cats,
            movedToTrashBytes = trashedBytes,
            freeSpaceGained = freeGained,
            lockedRemaining = lockedAfter
        )
    }

    /**
     * Ana skor: yalnızca tek dokunuşla temizlenebilen junk.
     * Sistem kilitli önbellek skora girmez — ayrı gösterilir.
     */
    @Suppress("UNUSED_PARAMETER")
    fun optimizationScore(
        junkBytes: Long,
        storageUsagePercent: Float = 0f,
        unusedApps: Int = 0
    ): Int {
        val junkMb = junkBytes / (1024.0 * 1024.0)
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
