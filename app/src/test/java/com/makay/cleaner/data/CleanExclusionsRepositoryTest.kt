package com.makay.cleaner.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanExclusionsRepositoryTest {

    @Test
    fun systemDefaultsCoverOemAndInternalCaches() {
        val paths = CleanExclusionsRepository.systemDefaults().map { it.path }
        assertTrue(paths.any { it.contains("/data/data") })
        assertTrue(paths.any { it.contains("dalvik-cache") })
        assertTrue(paths.any { it.contains("com.miui") })
        assertTrue(paths.any { it.contains("com.samsung") })
        assertTrue(paths.any { it.contains("com.sec") })
        assertTrue(CleanExclusionsRepository.systemDefaults().all {
            it.source == CleanExclusionsRepository.Source.SYSTEM
        })
    }

    @Test
    fun systemItemsAreNotUserRemovableSource() {
        assertFalse(
            CleanExclusionsRepository.systemDefaults().any {
                it.source == CleanExclusionsRepository.Source.USER
            }
        )
    }
}
