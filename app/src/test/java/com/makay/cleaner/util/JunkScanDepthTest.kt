package com.makay.cleaner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkScanDepthTest {

    @Test
    fun deepTimeoutLongerThanQuick() {
        assertTrue(JunkScanHelper.DEEP_SCAN_TIMEOUT_MS > JunkScanHelper.DEFAULT_SCAN_TIMEOUT_MS)
    }

    @Test
    fun scanDepthValuesDistinct() {
        assertNotEquals(JunkScanHelper.ScanDepth.QUICK, JunkScanHelper.ScanDepth.DEEP)
        assertEquals(2, JunkScanHelper.ScanDepth.entries.size)
    }
}
