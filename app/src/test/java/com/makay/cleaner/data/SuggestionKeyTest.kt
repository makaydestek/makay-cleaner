package com.makay.cleaner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SuggestionKeyTest {

    @Test
    fun metricIdWinsEvenWhenTheLabelChanges() {
        assertEquals("cache_size", CleaningRecorder.suggestionKey(CleaningRecorder.METRIC_CACHE, "Cache"))
        assertEquals("large_files_count", CleaningRecorder.suggestionKey(CleaningRecorder.METRIC_LARGE, "Dosyalar"))
        assertEquals("social_media_size", CleaningRecorder.suggestionKey(CleaningRecorder.METRIC_SOCIAL, "Social"))
        assertEquals("storage_scan_bytes", CleaningRecorder.suggestionKey(CleaningRecorder.METRIC_STORAGE, "Disk"))
    }

    @Test
    fun turkishFallbackStillMapsOldNames() {
        assertEquals("cache_size", CleaningRecorder.suggestionKey(null, "Uygulama \u00D6nbelle\u011Fi"))
        assertEquals("large_files_count", CleaningRecorder.suggestionKey(null, "B\u00FCy\u00FCk Dosya"))
        assertNull(CleaningRecorder.suggestionKey(null, "Güvenlik özeti"))
    }
}
