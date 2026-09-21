package com.makay.cleaner.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AnalyticsRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("analytics_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    data class DailyUsage(
        val date: String,
        val cleanings: Int,
        val spaceSaved: Long,
        val featuresUsed: Map<String, Int>
    )

    fun recordCleaningEvent(featureName: String, spaceSaved: Long) {
        val today = dateFormat.format(Date())
        val dailyData = getDailyUsage(today)

        val updatedData = dailyData.copy(
            cleanings = dailyData.cleanings + 1,
            spaceSaved = dailyData.spaceSaved + spaceSaved,
            featuresUsed = dailyData.featuresUsed.toMutableMap().apply {
                this[featureName] = (this[featureName] ?: 0) + 1
            }
        )

        saveDailyUsage(updatedData)
        incrementTotalStats(spaceSaved)
    }

    fun getDailyUsage(date: String): DailyUsage {
        val json = prefs.getString("daily_$date", null)
        return if (json != null) {
            try {
                gson.fromJson(json, DailyUsage::class.java)
            } catch (e: Exception) {
                DailyUsage(date, 0, 0L, emptyMap())
            }
        } else {
            DailyUsage(date, 0, 0L, emptyMap())
        }
    }

    private fun saveDailyUsage(data: DailyUsage) {
        prefs.edit().putString("daily_${data.date}", gson.toJson(data)).apply()
    }

    private fun incrementTotalStats(spaceSaved: Long) {
        val totalCleanings = prefs.getInt("total_cleanings", 0) + 1
        val totalSpace = prefs.getLong("total_space", 0L) + spaceSaved
        val lastCleaned = System.currentTimeMillis()

        prefs.edit()
            .putInt("total_cleanings", totalCleanings)
            .putLong("total_space", totalSpace)
            .putLong("last_cleaned", lastCleaned)
            .apply()
    }

    fun getTotalStats(): TotalStats {
        return TotalStats(
            totalCleanings = prefs.getInt("total_cleanings", 0),
            totalSpaceSaved = prefs.getLong("total_space", 0L),
            lastCleaned = prefs.getLong("last_cleaned", 0L),
            firstUsed = prefs.getLong("first_used", 0L).also {
                if (it == 0L) prefs.edit().putLong("first_used", System.currentTimeMillis()).apply()
            }
        )
    }

    fun getLast7DaysUsage(): List<DailyUsage> {
        val result = mutableListOf<DailyUsage>()
        val calendar = Calendar.getInstance()

        repeat(7) {
            val date = dateFormat.format(calendar.time)
            result.add(0, getDailyUsage(date))
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        return result
    }

    fun getLast30DaysUsage(): List<DailyUsage> {
        val result = mutableListOf<DailyUsage>()
        val calendar = Calendar.getInstance()

        repeat(30) {
            val date = dateFormat.format(calendar.time)
            result.add(0, getDailyUsage(date))
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        return result
    }

    fun getMostUsedFeatures(): List<Pair<String, Int>> {
        val featureCounts = mutableMapOf<String, Int>()
        val calendar = Calendar.getInstance()

        repeat(30) {
            val date = dateFormat.format(calendar.time)
            val daily = getDailyUsage(date)
            daily.featuresUsed.forEach { (feature, count) ->
                featureCounts[feature] = (featureCounts[feature] ?: 0) + count
            }
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        return featureCounts.toList().sortedByDescending { it.second }
    }

    fun getAverageDailyCleanings(): Double {
        val stats = getTotalStats()
        val firstUsed = stats.firstUsed
        if (firstUsed == 0L) return 0.0

        val daysUsed = ((System.currentTimeMillis() - firstUsed) / (1000 * 60 * 60 * 24)).toInt() + 1
        return stats.totalCleanings.toDouble() / daysUsed
    }

    fun exportDataToJson(): String {
        val data = mapOf(
            "totalStats" to getTotalStats(),
            "last7Days" to getLast7DaysUsage(),
            "last30Days" to getLast30DaysUsage(),
            "mostUsedFeatures" to getMostUsedFeatures(),
            "averageDailyCleanings" to getAverageDailyCleanings()
        )
        return gson.toJson(data)
    }

    fun clearAnalytics() {
        prefs.edit().clear().apply()
    }

    data class TotalStats(
        val totalCleanings: Int,
        val totalSpaceSaved: Long,
        val lastCleaned: Long,
        val firstUsed: Long
    )
}