package com.makay.cleaner.util

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Dosya FS'ten kalktıktan sonra MediaStore / galeri / dosya yöneticisi
 * indekslerinin tutarlı kalması için senkron.
 */
object MediaStoreSync {

    /**
     * Path için MediaStore kaydını siler ve tarayıcıya “yok” bildirir.
     * Soft-delete sonrası hayalet girdileri önler.
     */
    fun notifyRemoved(context: Context, absolutePath: String?) {
        if (absolutePath.isNullOrBlank()) return
        if (absolutePath.startsWith("content://")) return
        val path = absolutePath
        purgeByPath(context, path)
        // Boş path scan: indeksin dosyanın kalktığını görmesi için
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                null
            ) { _, _ -> }
        }
        // Üst klasörü de yenile (bazı dosya yöneticileri için)
        runCatching {
            val parent = File(path).parent ?: return@runCatching
            MediaScannerConnection.scanFile(context, arrayOf(parent), null, null)
        }
    }

    fun notifyRemoved(context: Context, file: File) {
        notifyRemoved(context, file.absolutePath)
    }

    fun purgeByPath(context: Context, path: String): Boolean {
        return try {
            val cr = context.contentResolver
            var removed = false
            // Önce ID ile silmeyi dene (DATA kolonu Android 10+'da güvenilmez)
            findContentUri(context, path)?.let { uri ->
                try {
                    if (cr.delete(uri, null, null) > 0) removed = true
                } catch (_: SecurityException) {
                    // RecoverableSecurityException — kullanıcı onayı gerekir; yine de scan dene
                } catch (_: Exception) {
                }
            }
            val collections = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri("external")
            )
            for (collection in collections) {
                try {
                    val n = cr.delete(
                        collection,
                        "${MediaStore.MediaColumns.DATA}=?",
                        arrayOf(path)
                    )
                    if (n > 0) removed = true
                } catch (_: Exception) {
                }
            }
            // DISPLAY_NAME + RELATIVE_PATH fallback (Q+)
            if (!removed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val file = File(path)
                val name = file.name
                val relative = guessRelativePath(file)
                if (relative != null) {
                    for (collection in collections) {
                        try {
                            val n = cr.delete(
                                collection,
                                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                                arrayOf(name, relative)
                            )
                            if (n > 0) removed = true
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            removed
        } catch (_: Exception) {
            false
        }
    }

    fun findContentUri(context: Context, path: String): Uri? {
        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Files.getContentUri("external")
        )
        for (collection in collections) {
            try {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA}=?",
                    arrayOf(path),
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        return android.content.ContentUris.withAppendedId(collection, id)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun guessRelativePath(file: File): String? {
        val path = file.absolutePath.replace('\\', '/')
        val markers = listOf(
            "/DCIM/", "/Pictures/", "/Download/", "/Downloads/",
            "/Movies/", "/Music/", "/Documents/", "/WhatsApp/", "/Telegram/"
        )
        for (m in markers) {
            val idx = path.indexOf(m, ignoreCase = true)
            if (idx >= 0) {
                val rel = path.substring(idx + 1)
                val lastSlash = rel.lastIndexOf('/')
                return if (lastSlash > 0) rel.substring(0, lastSlash + 1) else null
            }
        }
        return file.parentFile?.let { parent ->
            val name = parent.name
            if (name.isNotBlank()) "$name/" else null
        }
    }
}
