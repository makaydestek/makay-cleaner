package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.makay.cleaner.data.AnalyticsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AnalyticsUiState(
    val totalCleanings: Int = 0,
    val totalSpaceSaved: Long = 0L,
    val totalSpaceSavedFormatted: String = "0 MB",
    val lastCleanedFormatted: String = "Henüz temizlik yapılmadı",
    val averageDailyCleanings: Double = 0.0,
    val last7DaysUsage: List<AnalyticsRepository.DailyUsage> = emptyList(),
    val last30DaysUsage: List<AnalyticsRepository.DailyUsage> = emptyList(),
    val mostUsedFeatures: List<Pair<String, Int>> = emptyList(),
    val maxDailyCleanings: Int = 1,
    val max30DayCleanings: Int = 1
)

class AnalyticsViewModel(application: Application) : AndroidViewModel(application) {

    private val analyticsRepo = AnalyticsRepository(application)
    
    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    fun loadAnalytics() {
        val stats = analyticsRepo.getTotalStats()
        val last7Days = analyticsRepo.getLast7DaysUsage()
        val last30Days = analyticsRepo.getLast30DaysUsage()
        val mostUsed = analyticsRepo.getMostUsedFeatures()
        val avgDaily = analyticsRepo.getAverageDailyCleanings()

        val maxCleanings = last7Days.maxOfOrNull { it.cleanings } ?: 1
        val max30 = last30Days.maxOfOrNull { it.cleanings } ?: 1

        _uiState.value = AnalyticsUiState(
            totalCleanings = stats.totalCleanings,
            totalSpaceSaved = stats.totalSpaceSaved,
            totalSpaceSavedFormatted = formatBytes(stats.totalSpaceSaved),
            lastCleanedFormatted = if (stats.lastCleaned > 0) {
                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(stats.lastCleaned))
            } else "Henüz temizlik yapılmadı",
            averageDailyCleanings = avgDaily,
            last7DaysUsage = last7Days,
            last30DaysUsage = last30Days,
            mostUsedFeatures = mostUsed,
            maxDailyCleanings = if (maxCleanings > 0) maxCleanings else 1,
            max30DayCleanings = if (max30 > 0) max30 else 1
        )
    }

    fun exportData(): String {
        return analyticsRepo.exportDataToJson()
    }

    fun clearAnalytics() {
        analyticsRepo.clearAnalytics()
        loadAnalytics()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.2f".format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.2f".format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(AnalyticsViewModel::class.java)) {
                return AnalyticsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}