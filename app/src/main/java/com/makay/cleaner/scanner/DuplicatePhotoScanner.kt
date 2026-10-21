package com.makay.cleaner.scanner

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Duplicate Photos Fixer tarzı: aynı ve benzer fotoğraf grupları.
 * Zaman aşımı, iptal ve düşük bellek durumunda erken çıkar.
 */
class DuplicatePhotoScanner(private val context: Context) {

    data class PhotoItem(
        val id: Long,
        val path: String,
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val dateAdded: Long
    )

    data class PhotoGroup(
        val id: String,
        val type: GroupType,
        val photos: List<PhotoItem>,
        val totalSize: Long
    ) {
        val wasteBytes: Long
            get() = if (photos.size <= 1) 0L else totalSize - (photos.maxOfOrNull { it.sizeBytes } ?: 0L)
    }

    enum class GroupType { EXACT, SIMILAR }

    companion object {
        const val DEFAULT_EXACT_LIMIT = 2500
        const val DEFAULT_SIMILAR_LIMIT = 800
        const val DEFAULT_TIMEOUT_MS = 60_000L
        /** Benzer tarama için üst bellek eşiği (yaklaşık). */
        const val MIN_FREE_HEAP_BYTES = 24L * 1024L * 1024L
        const val MAX_SIMILAR_DECODE_BYTES = 8L * 1024L * 1024L
    }

    fun scanExact(
        limit: Int = DEFAULT_EXACT_LIMIT,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        cancelled: AtomicBoolean? = null
    ): List<PhotoGroup> {
        val deadline = System.currentTimeMillis() + timeoutMs
        fun stop(): Boolean =
            cancelled?.get() == true || System.currentTimeMillis() > deadline || lowMemory()

        val photos = loadPhotos(limit, cancelled, deadline)
        if (stop()) return emptyList()
        val bySize = photos.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
        val groups = mutableListOf<PhotoGroup>()
        bySize.values.forEach { sameSize ->
            if (stop()) return@forEach
            val byHash = LinkedHashMap<String, MutableList<PhotoItem>>()
            sameSize.forEach { photo ->
                if (stop()) return@forEach
                val hash = md5Partial(File(photo.path)) ?: return@forEach
                byHash.getOrPut(hash) { mutableListOf() }.add(photo)
            }
            byHash.forEach { (hash, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedByDescending { it.dateAdded }
                    groups.add(
                        PhotoGroup(
                            id = "exact_$hash",
                            type = GroupType.EXACT,
                            photos = sorted,
                            totalSize = sorted.sumOf { it.sizeBytes }
                        )
                    )
                }
            }
        }
        return groups.sortedByDescending { it.wasteBytes }
    }

    fun scanSimilar(
        limit: Int = DEFAULT_SIMILAR_LIMIT,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        cancelled: AtomicBoolean? = null
    ): List<PhotoGroup> {
        val deadline = System.currentTimeMillis() + timeoutMs
        fun stop(): Boolean =
            cancelled?.get() == true || System.currentTimeMillis() > deadline || lowMemory()

        val photos = loadPhotos(limit, cancelled, deadline)
        if (stop()) return emptyList()
        val entries = mutableListOf<Pair<String, PhotoItem>>()
        photos.forEach { photo ->
            if (stop()) return@forEach
            if (photo.sizeBytes > MAX_SIMILAR_DECODE_BYTES) return@forEach
            val hash = differenceHash(photo.path) ?: return@forEach
            entries.add(hash to photo)
        }
        val used = BooleanArray(entries.size)
        val groups = mutableListOf<PhotoGroup>()
        for (i in entries.indices) {
            if (stop()) break
            if (used[i]) continue
            val merged = mutableListOf(entries[i].second)
            used[i] = true
            for (j in i + 1 until entries.size) {
                if (stop()) break
                if (used[j]) continue
                if (hamming(entries[i].first, entries[j].first) <= 6) {
                    merged.add(entries[j].second)
                    used[j] = true
                }
            }
            if (merged.size > 1) {
                val sorted = merged.sortedByDescending { it.dateAdded }
                groups.add(
                    PhotoGroup(
                        id = "sim_${entries[i].first}",
                        type = GroupType.SIMILAR,
                        photos = sorted,
                        totalSize = sorted.sumOf { it.sizeBytes }
                    )
                )
            }
        }
        return groups.sortedByDescending { it.wasteBytes }
    }

    private fun lowMemory(): Boolean {
        val rt = Runtime.getRuntime()
        val free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        return free < MIN_FREE_HEAP_BYTES
    }

    private fun loadPhotos(
        limit: Int,
        cancelled: AtomicBoolean?,
        deadlineMs: Long
    ): List<PhotoItem> {
        val out = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.SIZE} > ?",
            arrayOf("10240"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeI = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext() && out.size < limit) {
                if (cancelled?.get() == true || System.currentTimeMillis() > deadlineMs) break
                val path = c.getString(dataI) ?: continue
                if (!SystemFileGuard.canDelete(path)) {
                    SystemFileGuard.recordSkipped()
                    continue
                }
                if (!File(path).exists()) continue
                val id = c.getLong(idI)
                out.add(
                    PhotoItem(
                        id = id,
                        path = path,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        displayName = c.getString(nameI) ?: File(path).name,
                        sizeBytes = c.getLong(sizeI),
                        dateAdded = c.getLong(dateI) * 1000L
                    )
                )
            }
        }
        return out
    }

    private fun md5Partial(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val len = file.length()
            FileInputStream(file).use { fis ->
                val buf = ByteArray(8192)
                var read = fis.read(buf)
                if (read > 0) md.update(buf, 0, read)
                if (len > 24_000) {
                    fis.channel.position(len / 2)
                    read = fis.read(buf)
                    if (read > 0) md.update(buf, 0, read)
                    fis.channel.position((len - 8192).coerceAtLeast(0))
                    read = fis.read(buf)
                    if (read > 0) md.update(buf, 0, read)
                }
                md.update(len.toString().toByteArray())
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun differenceHash(path: String): String? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val w = 9
            val h = 8
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            if (bmp !== scaled) bmp.recycle()
            val sb = StringBuilder(64)
            for (y in 0 until h) {
                for (x in 0 until w - 1) {
                    val left = gray(scaled.getPixel(x, y))
                    val right = gray(scaled.getPixel(x + 1, y))
                    sb.append(if (left < right) '1' else '0')
                }
            }
            scaled.recycle()
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    private fun gray(p: Int): Int =
        ((p shr 16 and 0xFF) + (p shr 8 and 0xFF) + (p and 0xFF)) / 3

    private fun hamming(a: String, b: String): Int {
        if (a.length != b.length) return 64
        var d = 0
        for (i in a.indices) if (a[i] != b[i]) d++
        return d
    }
}
