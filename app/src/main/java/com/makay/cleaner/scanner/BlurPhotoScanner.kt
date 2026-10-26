package com.makay.cleaner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.makay.cleaner.util.SystemFileGuard
import kotlin.math.abs

data class BlurPhotoCandidate(
    val path: String,
    val sizeBytes: Long,
    val blurScore: Double,
    val displayName: String
)

/**
 * Basit Laplacian varyans ile bulanık foto adayları.
 * Düşük skor = daha bulanık. Silme zorunlu değil; inceleme listesi.
 */
object BlurPhotoScanner {

    private const val MAX_SCAN = 120
    private const val DECODE_MAX = 256
    /** Empirik eşik — altı bulanık aday */
    private const val BLUR_THRESHOLD = 80.0

    fun scan(context: Context, limit: Int = 40): List<BlurPhotoCandidate> {
        val candidates = mutableListOf<BlurPhotoCandidate>()
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        val cursor = try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
            )
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        cursor.use { c ->
            val pathIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val nameIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            var checked = 0
            while (c.moveToNext() && checked < MAX_SCAN && candidates.size < limit) {
                val path = c.getString(pathIdx) ?: continue
                if (SystemFileGuard.isProtectedPath(path)) continue
                val size = c.getLong(sizeIdx)
                if (size < 20_000L) continue
                checked++
                val score = blurVariance(path) ?: continue
                if (score < BLUR_THRESHOLD) {
                    candidates += BlurPhotoCandidate(
                        path = path,
                        sizeBytes = size,
                        blurScore = score,
                        displayName = c.getString(nameIdx) ?: path.substringAfterLast('/')
                    )
                }
            }
        }
        return candidates.sortedBy { it.blurScore }
    }

    private fun blurVariance(path: String): Double? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > DECODE_MAX) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val gray = IntArray(bmp.width * bmp.height)
            bmp.getPixels(gray, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val w = bmp.width
            val h = bmp.height
            bmp.recycle()
            if (w < 3 || h < 3) return null

            var sum = 0.0
            var sumSq = 0.0
            var n = 0
            // Basit Laplacian çekirdeği merkez örnekleri
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = luminance(gray[y * w + x])
                    val lap = abs(
                        4 * c -
                            luminance(gray[y * w + x - 1]) -
                            luminance(gray[y * w + x + 1]) -
                            luminance(gray[(y - 1) * w + x]) -
                            luminance(gray[(y + 1) * w + x])
                    )
                    sum += lap
                    sumSq += lap * lap
                    n++
                }
            }
            if (n == 0) return null
            val mean = sum / n
            sumSq / n - mean * mean
        } catch (_: Exception) {
            null
        }
    }

    private fun luminance(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }
}
