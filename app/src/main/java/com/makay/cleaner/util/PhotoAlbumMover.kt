package com.makay.cleaner.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Fotoğrafları Pictures/&lt;albüm&gt; altına kopyalar veya taşır (MediaStore uyumlu).
 */
object PhotoAlbumMover {

    fun sanitizeAlbumName(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .take(60)
        return cleaned.ifBlank { "Makay" }
    }

    fun copyToAlbum(
        context: Context,
        sourcePath: String,
        sourceUri: Uri?,
        displayName: String,
        albumName: String
    ): Boolean {
        val album = sanitizeAlbumName(albumName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            copyViaMediaStore(context, sourcePath, sourceUri, displayName, album)
        } else {
            copyViaFile(context, sourcePath, sourceUri, displayName, album)
        }
    }

    /**
     * Hedefe kopyalar; kaynak başarıyla silinirse taşıma tamamlanır.
     * Kaynak silinemezse kopya kalır ve false döner.
     */
    fun moveToAlbum(
        context: Context,
        sourcePath: String,
        sourceUri: Uri?,
        displayName: String,
        albumName: String
    ): Boolean {
        if (!copyToAlbum(context, sourcePath, sourceUri, displayName, albumName)) return false
        val source = File(sourcePath)
        val deleted = when {
            source.isFile -> FilePurger.remove(context, source, trash = null, category = "Taşı")
            sourceUri != null -> {
                runCatching {
                    context.contentResolver.delete(sourceUri, null, null) > 0
                }.getOrDefault(false).also { ok ->
                    if (ok) MediaStoreSync.notifyRemoved(context, sourcePath)
                }
            }
            else -> false
        }
        if (!deleted && source.isFile && source.exists()) {
            // Kopya oluştu ama kaynak duruyor — taşıma yarım; yine de kopyayı bırak
            return false
        }
        return deleted || !source.exists()
    }

    private fun copyViaMediaStore(
        context: Context,
        sourcePath: String,
        sourceUri: Uri?,
        displayName: String,
        album: String
    ): Boolean {
        return try {
            val name = displayName.ifBlank { File(sourcePath).name }.ifBlank { "photo_${System.currentTimeMillis()}.jpg" }
            val mime = guessMime(name)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$album")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val cr = context.contentResolver
            val destUri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val written = cr.openOutputStream(destUri)?.use { out ->
                openSource(context, sourcePath, sourceUri)?.use { input ->
                    input.copyTo(out)
                    true
                } ?: false
            } ?: false
            if (!written) {
                runCatching { cr.delete(destUri, null, null) }
                return false
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            cr.update(destUri, values, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun copyViaFile(
        context: Context,
        sourcePath: String,
        sourceUri: Uri?,
        displayName: String,
        album: String
    ): Boolean {
        return try {
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val dir = File(pictures, album).also { it.mkdirs() }
            val name = displayName.ifBlank { File(sourcePath).name }.ifBlank { "photo_${System.currentTimeMillis()}.jpg" }
            var dest = File(dir, name)
            if (dest.exists()) {
                dest = File(dir, "${System.currentTimeMillis()}_$name")
            }
            openSource(context, sourcePath, sourceUri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return false
            if (!dest.isFile || dest.length() <= 0L) {
                dest.delete()
                return false
            }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(dest.absolutePath),
                arrayOf(guessMime(name)),
                null
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openSource(context: Context, path: String, uri: Uri?): java.io.InputStream? {
        val file = File(path)
        if (file.isFile) return FileInputStream(file)
        if (uri != null) return context.contentResolver.openInputStream(uri)
        return null
    }

    private fun guessMime(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heif"
            else -> "image/jpeg"
        }
    }
}
