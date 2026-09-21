package com.makay.cleaner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.makay.cleaner.util.SystemFileGuard
import kotlin.math.abs

/**
 * Difference-hash (dHash) + Hamming mesafesi ile benzer fotoğraf grupları.
 * ML Kit bağımlılığı olmadan perceptual benzerlik.
 */
class SimilarPhotoScanner(private val context: Context) {

    data class SimilarGroup(
        val hash: String,
        val paths: List<String>,
        val totalSize: Long
    )

    fun scan(limit: Int = 800): List<SimilarGroup> {
        val entries = mutableListOf<Triple<String, String, Long>>() // hash, path, size
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            var n = 0
            while (cursor.moveToNext() && n < limit) {
                val path = cursor.getString(dataIdx) ?: continue
                if (!SystemFileGuard.canDelete(path)) {
                    SystemFileGuard.recordSkipped()
                    continue
                }
                val hash = differenceHash(path) ?: continue
                entries.add(Triple(hash, path, cursor.getLong(sizeIdx)))
                n++
            }
        }

        val used = BooleanArray(entries.size)
        val groups = mutableListOf<SimilarGroup>()
        for (i in entries.indices) {
            if (used[i]) continue
            val merged = mutableListOf(entries[i].second to entries[i].third)
            used[i] = true
            for (j in i + 1 until entries.size) {
                if (used[j]) continue
                if (hamming(entries[i].first, entries[j].first) <= 8) {
                    merged.add(entries[j].second to entries[j].third)
                    used[j] = true
                }
            }
            if (merged.size > 1) {
                groups.add(
                    SimilarGroup(
                        hash = entries[i].first,
                        paths = merged.map { it.first },
                        totalSize = merged.sumOf { it.second }
                    )
                )
            }
        }
        return groups.sortedByDescending { it.totalSize }
    }

    /** 9x8 gri ölçek fark hash → 64 bit string */
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
