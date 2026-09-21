package com.makay.cleaner.domain.model

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val totalFormatted: String,
    val freeFormatted: String,
    val usedFormatted: String,
    val usagePercent: Float
)

data class StorageCategory(
    val name: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val icon: String,
    val color: Int
)