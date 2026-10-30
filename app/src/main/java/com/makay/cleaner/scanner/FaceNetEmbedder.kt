package com.makay.cleaner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * MobileFaceNet TFLite: 112×112 RGB → 192-boyutlu yüz embedding.
 * Model: assets/mobilefacenet.tflite
 */
class FaceNetEmbedder(context: Context) : AutoCloseable {

    private val interpreter: Interpreter
    private val embeddingSize: Int
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1 * INPUT * INPUT * 3 * 4).order(ByteOrder.nativeOrder())
    private val outputBuffer: Array<FloatArray>

    init {
        val model = loadModel(context, MODEL_ASSET)
        val opts = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, opts)
        val outShape = interpreter.getOutputTensor(0).shape()
        embeddingSize = outShape.last().coerceAtLeast(64)
        outputBuffer = Array(1) { FloatArray(embeddingSize) }
    }

    /**
     * Göz landmark’ları varsa hizalayarak, yoksa bbox kırpımıyla embedding üretir.
     */
    fun embed(
        fullImage: Bitmap,
        faceBox: Rect,
        leftEye: PointF?,
        rightEye: PointF?
    ): FloatArray {
        val aligned = if (leftEye != null && rightEye != null) {
            alignFace(fullImage, leftEye, rightEye, faceBox)
        } else {
            cropSquare(fullImage, faceBox)
        }
        return try {
            runModel(aligned)
        } finally {
            if (!aligned.isRecycled && aligned !== fullImage) aligned.recycle()
        }
    }

    private fun runModel(face112: Bitmap): FloatArray {
        val scaled = if (face112.width == INPUT && face112.height == INPUT) {
            face112
        } else {
            Bitmap.createScaledBitmap(face112, INPUT, INPUT, true)
        }
        try {
            inputBuffer.rewind()
            val pixels = IntArray(INPUT * INPUT)
            scaled.getPixels(pixels, 0, INPUT, 0, 0, INPUT, INPUT)
            var i = 0
            while (i < pixels.size) {
                val p = pixels[i++]
                // (pixel - 128) / 128 — MobileFaceNet standart ön işleme
                inputBuffer.putFloat((((p shr 16) and 0xFF) - 128f) / 128f)
                inputBuffer.putFloat((((p shr 8) and 0xFF) - 128f) / 128f)
                inputBuffer.putFloat(((p and 0xFF) - 128f) / 128f)
            }
            outputBuffer[0].fill(0f)
            interpreter.run(inputBuffer, outputBuffer)
            return l2Normalize(outputBuffer[0].copyOf())
        } finally {
            if (scaled !== face112 && !scaled.isRecycled) scaled.recycle()
        }
    }

    private fun cropSquare(bitmap: Bitmap, box: Rect): Bitmap {
        val pad = (max(box.width(), box.height()) * 0.18f).toInt()
        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        val half = max(box.width(), box.height()) / 2f + pad
        val src = RectF(cx - half, cy - half, cx + half, cy + half)
        src.intersect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val left = src.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = src.top.toInt().coerceIn(0, bitmap.height - 1)
        val w = (src.width().toInt()).coerceAtLeast(1).coerceAtMost(bitmap.width - left)
        val h = (src.height().toInt()).coerceAtLeast(1).coerceAtMost(bitmap.height - top)
        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
        val out = Bitmap.createScaledBitmap(cropped, INPUT, INPUT, true)
        if (cropped !== out && cropped !== bitmap) cropped.recycle()
        return out
    }

    /** Göz hizasını yatay yapıp 112×112 yüz üretir. */
    private fun alignFace(
        bitmap: Bitmap,
        leftEye: PointF,
        rightEye: PointF,
        faceBox: Rect
    ): Bitmap {
        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val eyesCenterX = (leftEye.x + rightEye.x) / 2f
        val eyesCenterY = (leftEye.y + rightEye.y) / 2f
        val eyeDist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

        // Tipik MobileFaceNet: gözler üst-orta; kırpım göz mesafesine göre
        val desiredFaceSize = eyeDist * 3.2f
        val scale = INPUT / desiredFaceSize

        val matrix = Matrix()
        matrix.postTranslate(-eyesCenterX, -eyesCenterY)
        matrix.postRotate(-angleDeg)
        // Gözler çıktıda yaklaşık y=0.38
        matrix.postScale(scale, scale)
        matrix.postTranslate(INPUT / 2f, INPUT * 0.38f)

        val out = Bitmap.createBitmap(INPUT, INPUT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

        // Hizasız kırpım yedek: tamamen siyah çıktı olmasın
        if (isMostlyEmpty(out)) {
            out.recycle()
            return cropSquare(bitmap, faceBox)
        }
        return out
    }

    private fun isMostlyEmpty(bmp: Bitmap): Boolean {
        val sample = IntArray(64)
        val stepX = (bmp.width / 8).coerceAtLeast(1)
        val stepY = (bmp.height / 8).coerceAtLeast(1)
        var idx = 0
        var y = 0
        while (y < bmp.height && idx < sample.size) {
            var x = 0
            while (x < bmp.width && idx < sample.size) {
                sample[idx++] = bmp.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        var dark = 0
        for (i in 0 until idx) {
            val p = sample[i]
            val lum = ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
            if (lum < 24) dark++
        }
        return dark > idx * 0.85f
    }

    override fun close() {
        runCatching { interpreter.close() }
    }

    companion object {
        private const val MODEL_ASSET = "mobilefacenet.tflite"
        const val INPUT = 112

        /** L2-normalize sonrası kosinüs benzerliği (1 = aynı, 0 = dik). */
        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            val n = minOf(a.size, b.size)
            var dot = 0f
            for (i in 0 until n) dot += a[i] * b[i]
            return dot.coerceIn(-1f, 1f)
        }

        /**
         * L2-normalize vektörlerde Öklid mesafesi.
         * Aynı kişi genelde &lt; 1.0; Dart referans uygulaması ~0.5–0.8 eşik kullanır.
         */
        fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
            val n = minOf(a.size, b.size)
            var sum = 0f
            for (i in 0 until n) {
                val d = a[i] - b[i]
                sum += d * d
            }
            return sqrt(sum)
        }

        private fun l2Normalize(v: FloatArray): FloatArray {
            var sum = 0f
            for (x in v) sum += x * x
            val norm = sqrt(sum)
            if (norm < 1e-8f) return v
            for (i in v.indices) v[i] /= norm
            return v
        }

        private fun loadModel(context: Context, assetName: String): MappedByteBuffer {
            context.assets.openFd(assetName).use { fd ->
                return fd.createInputStream().channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        }
    }
}
