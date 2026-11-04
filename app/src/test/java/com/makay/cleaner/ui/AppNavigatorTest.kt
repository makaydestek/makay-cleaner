package com.makay.cleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigatorTest {

    @Test
    fun navigateAndPopRestoresPrevious() {
        val nav = AppNavigator(AppScreen.Storage)
        nav.navigate(AppScreen.Toolbox)
        nav.navigate(AppScreen.RecycleBin)
        assertEquals(AppScreen.RecycleBin, nav.current)
        assertTrue(nav.pop())
        assertEquals(AppScreen.Toolbox, nav.current)
        assertTrue(nav.pop())
        assertEquals(AppScreen.Storage, nav.current)
        assertFalse(nav.pop())
    }

    @Test
    fun resetToClearsStack() {
        val nav = AppNavigator(AppScreen.Storage)
        nav.navigate(AppScreen.Settings)
        nav.navigate(AppScreen.Pro)
        nav.resetTo(AppScreen.Lock)
        assertEquals(AppScreen.Lock, nav.current)
        assertEquals(1, nav.depth)
    }
}
