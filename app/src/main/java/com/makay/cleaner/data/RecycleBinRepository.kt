package com.makay.cleaner.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Soft-delete: dosyaları uygulama özel çöp kutusuna taşır (varsayılan 48 saat).
 * MediaStore content:// URI'leri de destekler.
 */
class RecycleBinRepository(private val context: Context) {

    data class TrashItem(
        val id: String,
        val originalPath: String,
        val trashPath: String,
        val displayName: String,
        val sizeBytes: Long,
        val deletedAt: Long,
        val category: String,
        val mediaStoreUri: String? = null,
        val mimeType: String? = null
    )

    private val gson = Gson()
    private val prefs = context.getSharedPreferences("recycle_bin_index", Context.MODE_PRIVATE)
    private val trashRoot: File =
        File(context.filesDir, "recycle_bin").also { if (!it.exists()) it.mkdirs() }

    companion object {
        const val RETENTION_MS = 48L * 60 * 60 * 1000
    }

    fun listItems(): List<TrashItem> {
        purgeExpired()
        return loadIndex().sortedByDescending { it.deletedAt }
    }

    fun getTotalSize(): Long = listItems().sumOf { it.sizeBytes }

    fun getCount(): Int = listItems().size

    fun moveToTrash(source: File, category: String = "Genel"): Boolean {
        if (!SystemFileGuard.canDelete(source)) {
            SystemFileGuard.recordSkipped()
            return false
        }
        if (!source.exists() || !source.isFile) return false
        return try {
            val id = UUID.randomUUID().toString()
            val dest = File(trashRoot, id)
            val size = source.length()
            val name = source.name
            val original = source.absolutePath
            val moved = source.renameTo(dest) || try {
                source.copyTo(dest, overwrite = true)
                if (dest.length() == size && source.delete()) true
                else {
                    dest.delete()
                    false
                }
            } catch (_: Exception) {
                dest.delete()
                false
            }
            if (!moved) return false
            // Orijinal duruyorsa temizlik başarısız — kopyayı geri al
            if (source.exists()) {
                if (dest.exists()) dest.delete()
                return false
            }
            if (!dest.exists()) return false
            addIndex(
                TrashItem(
                    id = id,
                    originalPath = original,
                    trashPath = dest.absolutePath,
                    displayName = name,
                    sizeBytes = size,
                    deletedAt = System.currentTimeMillis(),
                    category = category
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun movePathToTrash(path: String, category: String = "Genel"): Boolean {
        if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            // Çöp kutusunda gerçek dosya adı görünsün; her şey "media" olmasın
            val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "dosya"
            return moveUriToTrash(uri, name, 0L, category)
        }
        return moveToTrash(File(path), category)
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun moveUriToTrash(
        uri: Uri,
        displayName: String,
        sizeBytes: Long,
        category: String = "Medya",
        mimeType: String? = null
    ): Boolean {
        return try {
            val id = UUID.randomUUID().toString()
            val dest = File(trashRoot, id)
            var size = sizeBytes
            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return false
            if (sizeBytes > 0L && copied != sizeBytes) {
                dest.delete()
                return false
            }
            if (size <= 0) size = dest.length()
            if (!dest.exists() || dest.length() != copied || copied <= 0L) {
                dest.delete()
                return false
            }
            val deleted = try {
                context.contentResolver.delete(uri, null, null) > 0
            } catch (_: Exception) {
                false
            }
            if (!deleted) {
                if (dest.exists()) dest.delete()
                return false
            }
            addIndex(
                TrashItem(
                    id = id,
                    originalPath = uri.toString(),
                    trashPath = dest.absolutePath,
                    displayName = displayName.ifBlank { "media_$id" },
                    sizeBytes = size,
                    deletedAt = System.currentTimeMillis(),
                    category = category,
                    mediaStoreUri = uri.toString(),
                    mimeType = mimeType ?: context.contentResolver.getType(uri)
                )
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restore(id: String): Boolean {
        val index = loadIndex().toMutableList()
        val item = index.find { it.id == id } ?: return false
        val trashFile = File(item.trashPath)
        if (!trashFile.exists()) {
            index.removeAll { it.id == id }
            saveIndex(index)
            return false
        }
        return try {
            val expectedLength = trashFile.length()
            val ok = if (item.mediaStoreUri != null || item.originalPath.startsWith("content://")) {
                restoreToMediaStore(item, trashFile)
            } else {
                val original = File(item.originalPath)
                original.parentFile?.mkdirs()
                val target = if (original.exists()) {
                    File(original.parentFile, "restored_${System.currentTimeMillis()}_${item.displayName}")
                } else original
                val moved = trashFile.renameTo(target) || run {
                    trashFile.copyTo(target, overwrite = true)
                    trashFile.delete()
                }
                moved && target.exists() && target.length() == expectedLength
            }
            if (ok) {
                if (trashFile.exists()) trashFile.delete()
                index.removeAll { it.id == id }
                saveIndex(index)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun restoreToMediaStore(item: TrashItem, trashFile: File): Boolean {
        val mime = item.mimeType ?: guessMime(item.displayName)
        val collection = when {
            mime.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            mime.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "restored_${item.displayName}")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    when {
                        mime.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/MakayRestored"
                        mime.startsWith("audio") -> Environment.DIRECTORY_MUSIC + "/MakayRestored"
                        else -> Environment.DIRECTORY_PICTURES + "/MakayRestored"
                    }
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(collection, values) ?: return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                trashFile.inputStream().use { it.copyTo(out) }
            } ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            }
            true
        } catch (_: Exception) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {
            }
            false
        }
    }

    private fun guessMime(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") -> "video/mp4"
            n.endsWith(".mp3") || n.endsWith(".m4a") -> "audio/mpeg"
            n.endsWith(".png") -> "image/png"
            n.endsWith(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }

    fun purgePermanently(id: String): Boolean {
        val index = loadIndex().toMutableList()
        val item = index.find { it.id == id } ?: return false
        val file = File(item.trashPath)
        if (file.exists() && !file.delete() && file.exists()) return false
        index.removeAll { it.id == id }
        saveIndex(index)
        return true
    }

    fun emptyBin(): Pair<Long, Int> {
        val index = loadIndex()
        var saved = 0L
        var count = 0
        val keep = mutableListOf<TrashItem>()
        index.forEach { item ->
            val file = File(item.trashPath)
            if (!file.exists() || (file.delete() && !file.exists())) {
                saved += item.sizeBytes
                count++
            } else keep.add(item)
        }
        saveIndex(keep)
        return saved to count
    }

    fun hoursRemaining(item: TrashItem, now: Long = System.currentTimeMillis()): Long {
        val left = RETENTION_MS - (now - item.deletedAt)
        return (left.coerceAtLeast(0L) / (60L * 60L * 1000L))
    }

    @Volatile
    private var lastBatchIds: List<String> = emptyList()

    fun beginBatch() {
        lastBatchIds = emptyList()
    }

    fun canUndoLastBatch(): Boolean = lastBatchIds.isNotEmpty()

    fun undoLastBatch(): Pair<Int, Long> {
        val ids = lastBatchIds
        lastBatchIds = emptyList()
        var count = 0
        var bytes = 0L
        ids.forEach { id ->
            val item = loadIndex().find { it.id == id } ?: return@forEach
            val size = item.sizeBytes
            if (restore(id)) {
                count++
                bytes += size
            }
        }
        return count to bytes
    }

    fun purgeExpired() {
        val now = System.currentTimeMillis()
        val index = loadIndex().toMutableList()
        val keep = mutableListOf<TrashItem>()
        index.forEach { item ->
            if (now - item.deletedAt > RETENTION_MS) {
                val file = File(item.trashPath)
                if (file.exists() && !file.delete() && file.exists()) keep.add(item)
            } else {
                keep.add(item)
            }
        }
        if (keep.size != index.size) saveIndex(keep)
    }

    private fun addIndex(item: TrashItem) {
        val index = loadIndex().toMutableList()
        index.add(item)
        saveIndex(index)
        lastBatchIds = lastBatchIds + item.id
    }

    private fun loadIndex(): List<TrashItem> {
        val json = prefs.getString("items", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<Array<TrashItem>>() {}.type
            (gson.fromJson<Array<TrashItem>>(json, type) ?: emptyArray()).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveIndex(items: List<TrashItem>) {
        prefs.edit().putString("items", gson.toJson(items)).apply()
    }
}
