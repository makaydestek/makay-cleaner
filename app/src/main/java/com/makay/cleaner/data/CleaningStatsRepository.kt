package com.makay.cleaner.data

import com.makay.cleaner.R
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.makay.cleaner.domain.model.CleaningRecord
import com.makay.cleaner.domain.model.CleaningStats
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CleaningStatsRepository(private val context: Context) {

    companion object {
        /** Worker + UI eşzamanlı yazabildiği için süreç-içi kilit. */
        private val LOCK = Any()
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("cleaning_stats", Context.MODE_PRIVATE)
    private val gson = Gson()

    // SimpleDateFormat/DecimalFormat thread-safe değil; her kullanımda yerel üretilir.
    private fun newDateFormat() = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private fun newDecimalFormat() = DecimalFormat("#.##")

    fun getStats(): CleaningStats {
        val totalCleanings = prefs.getInt("total_cleanings", 0)
        val totalSpaceSaved = prefs.getLong("total_space_saved", 0L)
        val lastCleaningDate = prefs.getLong("last_cleaning_date", 0L)
        val historyJson = prefs.getString("cleaning_history", "[]")

        // Array::class.java kullan — R8 altında TypeToken kırılıyor
        val cleaningHistory: List<CleaningRecord> = try {
            gson.fromJson(historyJson, Array<CleaningRecord>::class.java)?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        return CleaningStats(
            totalCleanings = totalCleanings,
            totalSpaceSaved = totalSpaceSaved,
            totalSpaceSavedFormatted = formatBytes(totalSpaceSaved),
            lastCleaningDate = lastCleaningDate,
            lastCleaningDateFormatted = if (lastCleaningDate > 0) {
                newDateFormat().format(Date(lastCleaningDate))
            } else {
                context.getString(R.string.stats_never_cleaned)
            },
            cleaningHistory = cleaningHistory
        )
    }

    fun addCleaningRecord(spaceSaved: Long, filesDeleted: Int, category: String) = synchronized(LOCK) {
        val stats = getStats()
        val now = System.currentTimeMillis()
        val newRecord = CleaningRecord(
            id = now,
            date = now,
            dateFormatted = newDateFormat().format(Date(now)),
            spaceSaved = spaceSaved,
            spaceSavedFormatted = formatBytes(spaceSaved),
            filesDeleted = filesDeleted,
            category = category
        )

        val updatedHistory = listOf(newRecord) + stats.cleaningHistory.take(99)
        val isScanOnly = category.startsWith("Tarama:")

        val editor = prefs.edit()
            .putInt("total_cleanings", if (isScanOnly) stats.totalCleanings else stats.totalCleanings + 1)
            .putLong(
                "total_space_saved",
                if (isScanOnly) stats.totalSpaceSaved else stats.totalSpaceSaved + spaceSaved
            )
            .putString("cleaning_history", gson.toJson(updatedHistory.toTypedArray()))
        // "Son temizlik" yalnızca gerçek temizlikte güncellenir; tarama sayılmaz.
        if (!isScanOnly) {
            editor.putLong("last_cleaning_date", now)
        }
        editor.apply()
    }

    fun addScanRecord(itemsFound: Int, bytesFound: Long, category: String) {
        addCleaningRecord(bytesFound, itemsFound, "Tarama: $category")
    }

    fun clearHistory() {
        // Yalnızca geçmiş kayıtlarını sil; toplam istatistikler korunur.
        prefs.edit().remove("cleaning_history").apply()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${newDecimalFormat().format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${newDecimalFormat().format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${newDecimalFormat().format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
