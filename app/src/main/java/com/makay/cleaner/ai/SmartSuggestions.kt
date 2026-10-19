package com.makay.cleaner.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import com.makay.cleaner.data.AnalyticsRepository
import java.io.File

class SmartSuggestions(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_suggestions", Context.MODE_PRIVATE)
    private val analyticsRepo = AnalyticsRepository(context)

    data class Suggestion(
        val id: String,
        val title: String,
        val description: String,
        val priority: Priority,
        val category: Category,
        val estimatedSpaceSavings: Long = 0
    )

    enum class Priority { LOW, MEDIUM, HIGH, URGENT }
    enum class Category { STORAGE, CACHE, LARGE_FILES, UNUSED_APPS, SOCIAL_MEDIA }

    fun generateSuggestions(): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()
        val stats = analyticsRepo.getTotalStats()
        val storageInfo = getStorageInfo()

        // Canli metrikler (prefs + anlik hesap)
        val cacheSize = maxOf(getCacheSize(), estimateOwnCache())
        val largeFiles = maxOf(getLargeFilesCount(), estimateLargeFilesQuick())
        val socialMediaSize = maxOf(getSocialMediaSize(), estimateSocialQuick())
        val unusedApps = getUnusedAppsCount()

        if (storageInfo.usagePercent > 85) {
            suggestions.add(
                Suggestion(
                    id = "urgent_storage",
                    title = "🚨 Acil: Depolama Alanı Dolmak Üzere!",
                    description = "Depolama alanınızın %${storageInfo.usagePercent.toInt()}’i dolu. Hemen temizlik yapın.",
                    priority = Priority.URGENT,
                    category = Category.STORAGE,
                    estimatedSpaceSavings = storageInfo.usedBytes / 10
                )
            )
        } else if (storageInfo.usagePercent > 60) {
            suggestions.add(
                Suggestion(
                    id = "high_storage",
                    title = "⚠️ Depolama Alanı Azalıyor",
                    description = "Depolama %${storageInfo.usagePercent.toInt()} dolu. Gereksiz dosyaları temizleyebilirsiniz.",
                    priority = Priority.HIGH,
                    category = Category.STORAGE,
                    estimatedSpaceSavings = storageInfo.usedBytes / 20
                )
            )
        }

        val lastCleaned = stats.lastCleaned
        val daysSinceLastClean = if (lastCleaned > 0) {
            ((System.currentTimeMillis() - lastCleaned) / (1000L * 60 * 60 * 24)).toInt()
        } else {
            999
        }

        if (daysSinceLastClean >= 3) {
            suggestions.add(
                Suggestion(
                    id = "cleaning_reminder",
                    title = "🧹 Temizlik Zamanı Geldi",
                    description = if (daysSinceLastClean >= 999) {
                        "Henüz temizlik kaydı yok. Hızlı temizlik ile yer açabilirsiniz."
                    } else {
                        "Son temizliğin üzerinden $daysSinceLastClean gün geçti."
                    },
                    priority = if (daysSinceLastClean > 14) Priority.HIGH else Priority.MEDIUM,
                    category = Category.CACHE
                )
            )
        }

        if (cacheSize > 20 * 1024 * 1024L) {
            suggestions.add(
                Suggestion(
                    id = "cache_cleanup",
                    title = "🗑️ Önbellek Temizliği",
                    description = "${formatBytes(cacheSize)} önbellek bulundu. Temizleyerek yer açabilirsiniz.",
                    priority = Priority.MEDIUM,
                    category = Category.CACHE,
                    estimatedSpaceSavings = cacheSize
                )
            )
        }

        if (largeFiles >= 3) {
            suggestions.add(
                Suggestion(
                    id = "large_files",
                    title = "📁 Büyük Dosyalar",
                    description = "$largeFiles adet büyük dosya tespit edildi. Gereksiz olanları silebilirsiniz.",
                    priority = Priority.MEDIUM,
                    category = Category.LARGE_FILES
                )
            )
        }

        val socialPrefs = com.makay.cleaner.data.SocialMediaPrefs(context)
        if (socialPrefs.shouldSuggestClean(socialMediaSize) || socialMediaSize > 50 * 1024 * 1024L) {
            suggestions.add(
                Suggestion(
                    id = "social_media",
                    title = "💬 Sosyal Medya Temizliği",
                    description = "${formatBytes(socialMediaSize)} sosyal medya dosyası var (eşik: ${socialPrefs.getAutoCleanThresholdMb()} MB). Eski medyayı temizleyin.",
                    priority = Priority.LOW,
                    category = Category.SOCIAL_MEDIA,
                    estimatedSpaceSavings = socialMediaSize / 2
                )
            )
        }

        if (unusedApps >= 2) {
            suggestions.add(
                Suggestion(
                    id = "unused_apps",
                    title = "📱 Kullanılmayan Uygulamalar",
                    description = "$unusedApps uygulama uzun süredir kullanılmıyor olabilir. Gözden geçirin.",
                    priority = Priority.LOW,
                    category = Category.UNUSED_APPS
                )
            )
        }

        // Her zaman en az bir pratik oneri
        if (suggestions.isEmpty()) {
            suggestions.add(
                Suggestion(
                    id = "routine_scan",
                    title = "🔍 Hızlı Kontrol Önerisi",
                    description = "Depolama ve önbelleği tarayarak gereksiz dosyaları bulabilirsiniz.",
                    priority = Priority.LOW,
                    category = Category.STORAGE
                )
            )
            suggestions.add(
                Suggestion(
                    id = "cache_tip",
                    title = "💡 Önbellek İpucu",
                    description = "Uygulama önbelleğini düzenli temizlemek cihazı hızlandırabilir.",
                    priority = Priority.LOW,
                    category = Category.CACHE
                )
            )
        }

        return suggestions.sortedByDescending { it.priority.ordinal }
    }

    fun getPersonalizedSuggestions(): List<Suggestion> {
        val allSuggestions = generateSuggestions()
        val userPreferences = getUserPreferences()
        return allSuggestions.sortedWith(
            compareByDescending<Suggestion> { it.priority.ordinal }
                .thenByDescending { userPreferences[it.category] ?: 0 }
        )
    }

    fun saveUserPreference(category: Category, weight: Int) {
        val current = prefs.getInt("pref_${category.name}", 0)
        prefs.edit().putInt("pref_${category.name}", current + weight).apply()
    }

    private fun getUserPreferences(): Map<Category, Int> {
        return Category.values().associateWith { prefs.getInt("pref_${it.name}", 0) }
    }

    private fun getStorageInfo(): StorageInfo {
        val path = try {
            Environment.getDataDirectory().path
        } catch (_: Exception) {
            "/"
        }
        val stat = StatFs(path)
        val total = stat.totalBytes
        val free = stat.freeBytes
        val used = total - free
        val percent = if (total > 0) (used.toDouble() / total.toDouble()) * 100 else 0.0
        return StorageInfo(total, free, used, percent)
    }

    private fun getCacheSize(): Long = prefs.getLong("cache_size", 0)
    private fun getLargeFilesCount(): Int = prefs.getInt("large_files_count", 0)
    private fun getSocialMediaSize(): Long = prefs.getLong("social_media_size", 0)
    private fun getUnusedAppsCount(): Int {
        val cached = prefs.getInt("unused_apps_count", 0)
        if (cached > 0) return cached
        return try {
            com.makay.cleaner.util.UnusedAppsHelper.getUnusedApps(context).size
        } catch (_: Exception) {
            0
        }
    }

    private fun estimateOwnCache(): Long {
        return try {
            dirSize(context.cacheDir) + (context.externalCacheDir?.let { dirSize(it) } ?: 0L)
        } catch (_: Exception) {
            0L
        }
    }

    private fun estimateLargeFilesQuick(): Int {
        return try {
            // Prefs yoksa Downloads'ta hizli sayim
            if (getLargeFilesCount() > 0) return getLargeFilesCount()
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var count = 0
            downloads?.listFiles()?.forEach { f ->
                if (f.isFile && f.length() >= 10L * 1024 * 1024) count++
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    private fun estimateSocialQuick(): Long {
        return try {
            if (getSocialMediaSize() > 0) return getSocialMediaSize()
            var total = 0L
            val candidates = listOf(
                File(Environment.getExternalStorageDirectory(), "WhatsApp/Media"),
                File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp/Media"),
                File(Environment.getExternalStorageDirectory(), "Telegram")
            )
            candidates.forEach { dir ->
                if (dir.exists()) total += dirSizeLimited(dir, 0, 400)
            }
            total
        } catch (_: Exception) {
            0L
        }
    }

    private fun dirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { f ->
            size += if (f.isFile) f.length() else dirSize(f)
        }
        return size
    }

    private fun dirSizeLimited(dir: File?, acc: Long, budget: Int): Long {
        if (dir == null || !dir.exists() || budget <= 0) return acc
        var size = acc
        var left = budget
        dir.listFiles()?.forEach { f ->
            if (left <= 0) return size
            if (f.isFile) {
                size += f.length()
                left--
            } else {
                size = dirSizeLimited(f, size, left / 2)
                left /= 2
            }
        }
        return size
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    data class StorageInfo(
        val totalBytes: Long,
        val freeBytes: Long,
        val usedBytes: Long,
        val usagePercent: Double
    )
}
