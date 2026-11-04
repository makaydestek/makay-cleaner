package com.makay.cleaner.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Tek back stack ile ekran gezinmesi.
 * Eski `xxxReturn` state'lerinin yerine geçer; geri her zaman stack'ten pop eder.
 */
class AppNavigator(start: AppScreen = AppScreen.Storage) {
    private val stack: SnapshotStateList<AppScreen> = mutableStateListOf(start)

    val current: AppScreen
        get() = stack.lastOrNull() ?: AppScreen.Storage

    val canPop: Boolean
        get() = stack.size > 1

    /** Compose recomposition tetiklemek için stack boyutu. */
    val depth: Int
        get() = stack.size

    fun navigate(screen: AppScreen) {
        if (stack.lastOrNull() == screen) return
        stack.add(screen)
    }

    /** Aynı ekranı yeniden basmadan tek örneğe indirger (kök / kilit). */
    fun replace(screen: AppScreen) {
        if (stack.isEmpty()) {
            stack.add(screen)
            return
        }
        stack[stack.lastIndex] = screen
    }

    /** Kök ekrana dön (Storage) ve isteğe bağlı hedefe git. */
    fun resetTo(screen: AppScreen) {
        stack.clear()
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun popTo(screen: AppScreen): Boolean {
        val idx = stack.indexOfLast { it == screen }
        if (idx < 0) return false
        while (stack.lastIndex > idx) {
            stack.removeAt(stack.lastIndex)
        }
        return true
    }
}
