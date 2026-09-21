package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.makay.cleaner.data.CleaningStatsRepository
import com.makay.cleaner.domain.model.CleaningStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CleaningStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CleaningStatsRepository(application)
    
    private val _uiState = MutableStateFlow(CleaningStats())
    val uiState: StateFlow<CleaningStats> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = repository.getStats()
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            loadStats()
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CleaningStatsViewModel::class.java)) {
                return CleaningStatsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}