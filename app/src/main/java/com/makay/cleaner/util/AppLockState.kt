package com.makay.cleaner.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kilit durumunun süreç-geneli yayını.
 *
 * OTA gibi kilit ekranının dışında çalışan bileşenler, uygulama kilitliyken
 * indirme/kurulum başlatmamak için bunu okur.
 */
object AppLockState {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun setLocked(value: Boolean) {
        _locked.value = value
    }

    fun isLocked(): Boolean = _locked.value
}
