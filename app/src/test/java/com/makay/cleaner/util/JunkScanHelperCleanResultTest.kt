package com.makay.cleaner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkScanHelperCleanResultTest {

    @Test
    fun cleanResultTracksPartialFailureFields() {
        val r = JunkScanHelper.CleanResult(
            filesDeleted = 2,
            spaceSaved = 1024L,
            categoriesCleaned = 1,
            failedCount = 3,
            skippedProtected = 1
        )
        assertEquals(3, r.failedCount)
        assertEquals(1, r.skippedProtected)
        assertTrue(JunkScanHelper.SAFE_CATEGORY_IDS.contains("cache"))
    }
}
