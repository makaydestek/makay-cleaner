package com.makay.cleaner.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object MediaCompressHelper {

    data class CompressResult(
        val originalPath: String,
        val outputPath: String,
        val originalSize: Long,
        val newSize: Long
    )

    /**
     * JPEG kalite sıkıştırması. Çıktı aynı klasörde *_compressed.jpg
     */
    fun compressImage(
        path: String,
        quality: Int = 70,
        maxSide: Int = 1920
    ): CompressResult? {
        val source = File(path)
        if (!source.exists() || !source.isFile) return null
        if (SystemFileGuard.isProtectedPath(path)) {
            SystemFileGuard.recordSkipped()
            return null
        }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > maxSide) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(path, opts) ?: return null
            val outFile = File(
                source.parentFile,
                source.nameWithoutExtension + "_compressed.jpg"
            )
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 95), fos)
            }
            bitmap.recycle()
            CompressResult(
                originalPath = path,
                outputPath = outFile.absolutePath,
                originalSize = source.length(),
                newSize = outFile.length()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun compressMany(paths: List<String>, quality: Int = 70): List<CompressResult> {
        return paths.mapNotNull { compressImage(it, quality) }
    }
}
