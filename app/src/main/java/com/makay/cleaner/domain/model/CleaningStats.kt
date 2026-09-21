package com.makay.cleaner.domain.model

data class CleaningStats(
    val totalCleanings: Int = 0,
    val totalSpaceSaved: Long = 0L,
    val totalSpaceSavedFormatted: String = "0 B",
    val lastCleaningDate: Long = 0L,
    val lastCleaningDateFormatted: String = "Henüz temizlik yapılmadı",
    val cleaningHistory: List<CleaningRecord> = emptyList()
)

data class CleaningRecord(
    val id: Long,
    val date: Long,
    val dateFormatted: String,
    val spaceSaved: Long,
    val spaceSavedFormatted: String,
    val filesDeleted: Int,
    val category: String
)