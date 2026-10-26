package com.makay.cleaner.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build

/**
 * Pano temizliği — yalnızca ClipboardManager; sistem geçmişine root gerekmez.
 */
object ClipboardCleanHelper {

    fun clear(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cm.clearPrimaryClip()
            } else {
                cm.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasContent(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return false
            cm.hasPrimaryClip() && (cm.primaryClip?.itemCount ?: 0) > 0
        } catch (_: Exception) {
            false
        }
    }
}
