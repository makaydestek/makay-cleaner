package com.makay.cleaner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkScanHelperTest {

    @Test
    fun scoreIs100WhenJunkIsTiny() {
        assertEquals(100, JunkScanHelper.optimizationScore(0L))
        assertEquals(100, JunkScanHelper.optimizationScore(512 * 1024))
    }

    @Test
    fun scoreDropsWithXiaomiLikeJunk() {
        val twoGb = 2L * 1024 * 1024 * 1024
        val score = JunkScanHelper.optimizationScore(twoGb)
        assertTrue(score in 80..90)
    }

    @Test
    fun unusedAppsDoNotChangeScore() {
        val junk = 100L * 1024 * 1024
        assertEquals(
            JunkScanHelper.optimizationScore(junk, unusedApps = 0),
            JunkScanHelper.optimizationScore(junk, unusedApps = 12)
        )
    }

    @Test
    fun emptyReportDefaultsAreHonest() {
        val report = JunkScanHelper.JunkReport(emptyList(), 0)
        assertEquals(0L, report.totalBytes)
        assertEquals(0L, report.cleanableBytes)
        assertEquals(0L, report.lockedBytes)
    }
}
