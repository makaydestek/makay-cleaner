package com.makay.cleaner.domain.model

data class AppUninstallInfo(
    val packageName: String,
    val appName: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val isSystemApp: Boolean,
    val versionName: String,
    val installDate: Long,
    val installDateFormatted: String
)