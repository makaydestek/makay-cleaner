package com.makay.cleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.makay.cleaner.data.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationUiState(
    val reminderEnabled: Boolean = true,
    val reminderIntervalHours: Long = 24
)

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val notificationRepo = NotificationRepository(application)
    
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val (enabled, intervalHours) = notificationRepo.getNotificationSettings()
        _uiState.value = NotificationUiState(
            reminderEnabled = enabled,
            reminderIntervalHours = intervalHours
        )
    }

    fun setReminderEnabled(enabled: Boolean) {
        notificationRepo.saveNotificationSettings(enabled, _uiState.value.reminderIntervalHours)
        _uiState.value = _uiState.value.copy(reminderEnabled = enabled)
    }

    fun setReminderInterval(hours: Long) {
        notificationRepo.saveNotificationSettings(_uiState.value.reminderEnabled, hours)
        _uiState.value = _uiState.value.copy(reminderIntervalHours = hours)
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NotificationViewModel::class.java)) {
                return NotificationViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}