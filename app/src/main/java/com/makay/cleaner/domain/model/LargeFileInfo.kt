package com.makay.cleaner.domain.model

data class LargeFileInfo(
    val id: Long,
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val category: FileCategory,
    val mimeType: String,
    val dateAdded: Long,
    val dateFormatted: String
)

enum class FileCategory(val displayName: String, val color: Int) {
    VIDEO("Video", 0xFF8E24AA.toInt()),    // Mor
    IMAGE("Resim", 0xFF43A047.toInt()),    // Yeşil
    AUDIO("Ses", 0xFF1E88E5.toInt()),      // Mavi
    DOCUMENT("Belge", 0xFFF57C00.toInt()), // Turuncu
    OTHER("Diğer", 0xFF757575.toInt())     // Gri
}