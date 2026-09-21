package com.makay.cleaner.domain.model

data class AppCacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSizeBytes: Long,
    val cacheSizeFormatted: String,
    val iconData: ByteArray? = null,
    val isSystemApp: Boolean = false
)