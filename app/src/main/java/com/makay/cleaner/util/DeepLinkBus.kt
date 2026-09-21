package com.makay.cleaner.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeepLinkBus {
    private val _link = MutableStateFlow<String?>(null)
    val link: StateFlow<String?> = _link.asStateFlow()

    fun offer(deepLink: String?) {
        if (!deepLink.isNullOrBlank()) _link.value = deepLink
    }

    fun consume(): String? {
        val v = _link.value
        _link.value = null
        return v
    }
}
