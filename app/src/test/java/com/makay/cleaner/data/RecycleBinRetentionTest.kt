package com.makay.cleaner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleBinRetentionTest {

    @Test
    fun legacyIndexSkipsIncompleteRecordsAndPreservesValidEntries() {
        val items = RecycleBinRepository.parseLegacyIndex(
            """
            [
              null,
              {"trashPath":"/trash/missing-id"},
              {"id":"null-path","trashPath":null},
              {"id":"missing-original","trashPath":"/trash/unknown"},
              {"id":"valid","trashPath":"/trash/valid","originalPath":"/downloads/file.apk",
               "sizeBytes":42,"deletedAt":1700000000000,"category":"APK"},
              {"id":"media","trashPath":"/trash/media","mediaStoreUri":"content://media/123",
               "displayName":"photo.jpg"}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("valid", "media"), items.map { it.id })
        assertEquals("file.apk", items.first().displayName)
        assertEquals(42L, items.first().sizeBytes)
        assertEquals("APK", items.first().category)
        assertEquals("content://media/123", items.last().originalPath)
        assertEquals("photo.jpg", items.last().displayName)
    }

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
