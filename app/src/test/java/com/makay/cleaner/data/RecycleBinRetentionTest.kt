package com.makay.cleaner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleBinRetentionTest {

    @Test
    fun retentionIs48Hours() {
        assertEquals(48L * 60 * 60 * 1000, RecycleBinRepository.RETENTION_MS)
    }

    @Test
    fun hoursRemainingUsesRetentionWindow() {
        val now = 1_700_000_000_000L
        val item = RecycleBinRepository.TrashItem(
            id = "1",
            originalPath = "/tmp/a",
            trashPath = "/tmp/b",
            displayName = "a",
            sizeBytes = 10,
            deletedAt = now - (12L * 60 * 60 * 1000),
            category = "Test"
        )
        val hours = (RecycleBinRepository.RETENTION_MS - (now - item.deletedAt)) / (60L * 60L * 1000L)
        assertEquals(36L, hours)
        assertTrue(hours in 1..48)
    }
}
