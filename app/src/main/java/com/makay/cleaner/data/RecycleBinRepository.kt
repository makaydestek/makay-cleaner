package com.makay.cleaner.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.Keep
import com.google.gson.Gson
import com.makay.cleaner.util.MediaStoreSync
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Soft-delete: dosyaları uygulama özel çöp kutusuna taşır (varsayılan 48 saat).
 * MediaStore content:// URI'leri de destekler.
 *
 * İndeks Room'da tutulur; eşzamanlı silmeler birbirinin kaydını ezmez.
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
        val mimeType: String? = null,
        /** Orijinal albüm/klasör (MediaStore RELATIVE_PATH biçiminde). */
        val relativePath: String? = null
    )

    @Keep
    private data class LegacyTrashItem(
        val id: String?,
        val originalPath: String?,
        val trashPath: String?,
        val displayName: String?,
        val sizeBytes: Long,
        val deletedAt: Long,
        val category: String?,
        val mediaStoreUri: String?,
        val mimeType: String?,
        val relativePath: String?
    )

    private val dao: TrashItemDao = RecycleBinDatabase.get(context).trashDao()
    private val prefs = context.getSharedPreferences("recycle_bin_index", Context.MODE_PRIVATE)
    private val trashRoot: File =
        File(context.filesDir, "recycle_bin").also { if (!it.exists()) it.mkdirs() }

    /** Dosya işlemi + indeks yazımı tutarlı olsun diye süreç-içi kilit. */
    private val binLock = Any()

    init {
        migrateLegacyIndexIfNeeded()
    }

    fun listItems(): List<TrashItem> {
        purgeExpired()
        return dao.all().map { it.toModel() }
    }

    fun getTotalSize(): Long = dao.all().sumOf { it.sizeBytes }

    fun getCount(): Int = dao.count()

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
            val relative = relativePathOf(original)
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
            // F Manager / Galeri hayalet kaydı bırakmasın
            MediaStoreSync.notifyRemoved(context, original)
            addIndex(
                TrashItem(
                    id = id,
                    originalPath = original,
                    trashPath = dest.absolutePath,
                    displayName = name,
                    sizeBytes = size,
                    deletedAt = System.currentTimeMillis(),
                    category = category,
                    relativePath = relative
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
            val resolvedMime = mimeType ?: context.contentResolver.getType(uri)
            val relative = relativePathOf(uri)
            val display = displayName.ifBlank { "media_$id" }
            // Önce indekse yaz: delete(uri) başarılı olup indeks yazımı
            // kaybolursa dosya hem orijinalden gitmiş hem izsiz kalırdı.
            addIndex(
                TrashItem(
                    id = id,
                    originalPath = uri.toString(),
                    trashPath = dest.absolutePath,
                    displayName = display,
                    sizeBytes = size,
                    deletedAt = System.currentTimeMillis(),
                    category = category,
                    mediaStoreUri = uri.toString(),
                    mimeType = resolvedMime,
                    relativePath = relative
                )
            )
            val deleted = try {
                context.contentResolver.delete(uri, null, null) > 0
            } catch (_: Exception) {
                false
            }
            if (!deleted) {
                // Geri al: indeks + kopya temizlenir, orijinal yerinde kalır.
                dao.deleteById(id)
                if (dest.exists()) dest.delete()
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun restore(id: String): Boolean = synchronized(binLock) {
        val item = dao.byId(id)?.toModel() ?: return false
        val trashFile = File(item.trashPath)
        if (!trashFile.exists()) {
            dao.deleteById(id)
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
                val restored = moved && target.exists() && target.length() == expectedLength
                if (restored) {
                    // Galeri/dosya yöneticisi yeni dosyayı hemen görsün.
                    runCatching {
                        android.media.MediaScannerConnection.scanFile(
                            context, arrayOf(target.absolutePath), null, null
                        )
                    }
                }
                restored
            }
            if (ok) {
                if (trashFile.exists()) trashFile.delete()
                dao.deleteById(id)
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
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Orijinal albüm varsa oraya, yoksa güvenli varsayılan klasöre.
                val relative = item.relativePath?.takeIf { it.isNotBlank() } ?: when {
                    mime.startsWith("video") -> Environment.DIRECTORY_MOVIES + "/MakayRestored"
                    mime.startsWith("audio") -> Environment.DIRECTORY_MUSIC + "/MakayRestored"
                    else -> Environment.DIRECTORY_PICTURES + "/MakayRestored"
                }
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
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
                val updated = context.contentResolver.update(uri, values, null, null)
                if (updated <= 0) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    return false
                }
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
        val ext = name.substringAfterLast('.', "").lowercase()
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(ext)
            ?: when {
                ext == "mkv" || ext == "webm" -> "video/x-matroska"
                ext == "m4a" -> "audio/mp4"
                else -> "image/jpeg"
            }
    }

    fun purgePermanently(id: String): Boolean = synchronized(binLock) {
        val item = dao.byId(id) ?: return false
        val file = File(item.trashPath)
        if (file.exists() && !file.delete() && file.exists()) return false
        dao.deleteById(id)
        true
    }

    fun emptyBin(): Pair<Long, Int> = synchronized(binLock) {
        var saved = 0L
        var count = 0
        dao.all().forEach { entity ->
            val file = File(entity.trashPath)
            if (!file.exists()) {
                // Zaten yok: kazanç sayma, indeksten düş.
                dao.deleteById(entity.id)
            } else if (file.delete() && !file.exists()) {
                saved += entity.sizeBytes
                count++
                dao.deleteById(entity.id)
            }
        }
        saved to count
    }

    fun hoursRemaining(item: TrashItem, now: Long = System.currentTimeMillis()): Long {
        val left = RETENTION_MS - (now - item.deletedAt)
        return (left.coerceAtLeast(0L) / (60L * 60L * 1000L))
    }

    @Volatile
    private var lastBatchIds: List<String> = emptyList()

    fun beginBatch() {
        lastBatchIds = emptyList()
        prefs.edit().putString(KEY_LAST_BATCH, "").apply()
    }

    fun canUndoLastBatch(): Boolean =
        lastBatchIds.isNotEmpty() ||
            !prefs.getString(KEY_LAST_BATCH, "").isNullOrBlank()

    fun undoLastBatch(): Pair<Int, Long> = synchronized(binLock) {
        val ids = if (lastBatchIds.isNotEmpty()) {
            lastBatchIds
        } else {
            prefs.getString(KEY_LAST_BATCH, "").orEmpty()
                .split(',')
                .filter { it.isNotBlank() }
        }
        lastBatchIds = emptyList()
        prefs.edit().putString(KEY_LAST_BATCH, "").apply()
        var count = 0
        var bytes = 0L
        ids.forEach { id ->
            val item = dao.byId(id) ?: return@forEach
            val size = item.sizeBytes
            if (restore(id)) {
                count++
                bytes += size
            }
        }
        count to bytes
    }

    fun purgeExpired() = synchronized(binLock) {
        val now = System.currentTimeMillis()
        dao.expired(now - RETENTION_MS).forEach { entity ->
            val file = File(entity.trashPath)
            if (!file.exists() || (file.delete() && !file.exists())) {
                dao.deleteById(entity.id)
            }
        }
    }

    /**
     * İndekste olmayan yetim çöp dosyalarını temizle.
     * (Eski sürüm/yarış nedeniyle indeksten düşmüş dosyalar diski şişirmesin.)
     */
    fun reconcileOrphans(now: Long = System.currentTimeMillis()) {
        val known = dao.allTrashPaths().toHashSet()
        trashRoot.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            if (f.absolutePath in known) return@forEach
            // Yeni oluşturulmuş olabilecek dosyaları yanlışlıkla silme.
            if (now - f.lastModified() < 60L * 60L * 1000L) return@forEach
            runCatching { f.delete() }
        }
    }

    private fun addIndex(item: TrashItem) = synchronized(binLock) {
        dao.insert(item.toEntity())
        lastBatchIds = lastBatchIds + item.id
        prefs.edit().putString(KEY_LAST_BATCH, lastBatchIds.joinToString(",")).apply()
    }

    /** Eski SharedPreferences JSON indeksini Room'a taşır (tek sefer). */
    private fun migrateLegacyIndexIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        synchronized(binLock) {
            if (prefs.getBoolean(KEY_MIGRATED, false)) return
            val raw = prefs.getString("items", null)
            if (!raw.isNullOrBlank() && dao.count() == 0) {
                val valid = parseLegacyIndex(raw)
                if (valid.isNotEmpty()) {
                    dao.insertAll(valid.map { it.toEntity() })
                }
            }
            prefs.edit()
                .putBoolean(KEY_MIGRATED, true)
                .remove("items")
                .apply()
        }
    }

    private fun relativePathOf(absolutePath: String): String? {
        val normalized = absolutePath.replace('\\', '/')
        val root = Environment.getExternalStorageDirectory()?.absolutePath
            ?.replace('\\', '/')?.trimEnd('/')
        if (!root.isNullOrBlank() && normalized.startsWith("$root/")) {
            val rel = normalized.removePrefix("$root/")
            val dir = rel.substringBeforeLast('/', "")
            if (dir.isNotBlank()) return "$dir/"
        }
        return null
    }

    private fun relativePathOf(uri: Uri): String? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@runCatching null
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    private fun TrashItem.toEntity() = TrashItemEntity(
        id = id,
        originalPath = originalPath,
        trashPath = trashPath,
        displayName = displayName,
        sizeBytes = sizeBytes,
        deletedAt = deletedAt,
        category = category,
        mediaStoreUri = mediaStoreUri,
        mimeType = mimeType,
        relativePath = relativePath
    )

    private fun TrashItemEntity.toModel() = TrashItem(
        id = id,
        originalPath = originalPath,
        trashPath = trashPath,
        displayName = displayName,
        sizeBytes = sizeBytes,
        deletedAt = deletedAt,
        category = category,
        mediaStoreUri = mediaStoreUri,
        mimeType = mimeType,
        relativePath = relativePath
    )

    companion object {
        internal fun parseLegacyIndex(raw: String): List<TrashItem> {
            val entries = runCatching {
                Gson().fromJson(raw, Array<LegacyTrashItem?>::class.java)
            }.getOrNull().orEmpty()

            return entries.filterNotNull().mapNotNull { entry ->
                val id = entry.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val trashPath = entry.trashPath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val mediaStoreUri = entry.mediaStoreUri?.takeIf { it.isNotBlank() }
                val originalPath = entry.originalPath?.takeIf { it.isNotBlank() }
                    ?: mediaStoreUri ?: return@mapNotNull null
                TrashItem(
                    id = id,
                    originalPath = originalPath,
                    trashPath = trashPath,
                    displayName = entry.displayName?.takeIf { it.isNotBlank() }
                        ?: File(originalPath).name.ifBlank { File(trashPath).name },
                    sizeBytes = entry.sizeBytes,
                    deletedAt = entry.deletedAt,
                    category = entry.category.orEmpty(),
                    mediaStoreUri = mediaStoreUri,
                    mimeType = entry.mimeType,
                    relativePath = entry.relativePath
                )
            }
        }
        const val RETENTION_MS = 48L * 60 * 60 * 1000
        private const val KEY_LAST_BATCH = "last_batch_ids"
        private const val KEY_MIGRATED = "room_migrated"
    }
}
