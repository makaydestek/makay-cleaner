package com.makay.cleaner.util

import android.util.Log

/**
 * Sessizce yutulan hatalar yerine ortak uyarı günlüğü.
 * Kullanıcıya Toast göstermek çağıranın sorumluluğundadır.
 */
object SoftErrorLogger {
    private const val DEFAULT_TAG = "MakayCleaner"

    fun warn(tag: String = DEFAULT_TAG, message: String, error: Throwable? = null) {
        if (error != null) {
            Log.w(tag, message, error)
        } else {
            Log.w(tag, message)
        }
    }

    fun error(tag: String = DEFAULT_TAG, message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(tag, message, error)
        } else {
            Log.e(tag, message)
        }
    }
}
