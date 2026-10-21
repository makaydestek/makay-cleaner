package com.makay.cleaner.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleaningWorkerPolicyTest {

    @Test
    fun retryLimitIsFinite() {
        assertTrue(CleaningWorker.MAX_ATTEMPTS in 1..5)
        assertEquals(3, CleaningWorker.MAX_ATTEMPTS)
    }

    @Test
    fun inputKeysStable() {
        assertEquals("use_trash", CleaningWorker.KEY_USE_TRASH)
        assertEquals("do_cache", CleaningWorker.KEY_DO_CACHE)
        assertEquals("do_apk", CleaningWorker.KEY_DO_APK)
        assertEquals("do_tmp", CleaningWorker.KEY_DO_TMP)
    }
}
