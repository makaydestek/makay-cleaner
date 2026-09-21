package com.makay.cleaner.data

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

    private val prefs: SharedPreferences = context.getSharedPreferences("cleaning_stats", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val decimalFormat = DecimalFormat("#.##")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

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
                dateFormat.format(Date(lastCleaningDate))
            } else {
                "Henüz temizlik yapılmadı"
            },
            cleaningHistory = cleaningHistory
        )
    }

    fun addCleaningRecord(spaceSaved: Long, filesDeleted: Int, category: String) {
        val stats = getStats()
        val newRecord = CleaningRecord(
            id = System.currentTimeMillis(),
            date = System.currentTimeMillis(),
            dateFormatted = dateFormat.format(Date()),
            spaceSaved = spaceSaved,
            spaceSavedFormatted = formatBytes(spaceSaved),
            filesDeleted = filesDeleted,
            category = category
        )

        val updatedHistory = listOf(newRecord) + stats.cleaningHistory.take(99)
        val isScanOnly = category.startsWith("Tarama:")

        prefs.edit()
            .putInt("total_cleanings", if (isScanOnly) stats.totalCleanings else stats.totalCleanings + 1)
            .putLong(
                "total_space_saved",
                if (isScanOnly) stats.totalSpaceSaved else stats.totalSpaceSaved + spaceSaved
            )
            .putLong("last_cleaning_date", System.currentTimeMillis())
            .putString("cleaning_history", gson.toJson(updatedHistory.toTypedArray()))
            .apply()
    }

    fun addScanRecord(itemsFound: Int, bytesFound: Long, category: String) {
        addCleaningRecord(bytesFound, itemsFound, "Tarama: $category")
    }

    fun clearHistory() {
        prefs.edit().clear().apply()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }
}
