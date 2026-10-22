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
    fun unusedAppsDoNotLowerScore() {
        val junk = 100L * 1024 * 1024
        val base = JunkScanHelper.optimizationScore(junk, unusedApps = 0)
        val withUnused = JunkScanHelper.optimizationScore(junk, unusedApps = 12)
        assertEquals(base, withUnused)
    }

    @Test
    fun highStorageUsageDoesNotLowerScore() {
        val base = JunkScanHelper.optimizationScore(0L, storageUsagePercent = 50f)
        val full = JunkScanHelper.optimizationScore(0L, storageUsagePercent = 96f)
        assertEquals(100, base)
        assertEquals(100, full)
    }

    @Test
    fun emptyReportDefaultsAreHonest() {
        val report = JunkScanHelper.JunkReport(emptyList(), 0)
        assertEquals(0L, report.totalBytes)
        assertEquals(0L, report.cleanableBytes)
        assertEquals(0L, report.lockedBytes)
    }

    @Test
    fun cleanResultDefaultsDoNotInflateSavings() {
        val empty = JunkScanHelper.CleanResult(0, 0, 0)
        assertEquals(0L, empty.spaceSaved)
        assertEquals(0L, empty.movedToTrashBytes)
        assertEquals(0L, empty.freeSpaceGained)
    }

    @Test
    fun safeCategoriesAreCacheAndTmpOnly() {
        assertTrue(JunkScanHelper.isSafeCategory("cache"))
        assertTrue(JunkScanHelper.isSafeCategory("tmp"))
        assertTrue("apk" in JunkScanHelper.REVIEW_CATEGORY_IDS)
    }
}
