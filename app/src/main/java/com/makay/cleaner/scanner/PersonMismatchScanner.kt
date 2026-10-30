package com.makay.cleaner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Klasördeki fotoğrafları referans “asıl kişi” yüz(ler)iyle karşılaştırır.
 * Eşleşmeyen (veya hiç eşleşen yüzü olmayan) adayları listeler.
 */
class PersonMismatchScanner(private val context: Context) {

    data class BucketInfo(val name: String, val count: Int)

    data class MismatchCandidate(
        val path: String,
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val bestSimilarity: Float,
        val faceCount: Int,
        val reason: String
    )

    data class ReferenceBuildResult(
        val embeddings: List<FloatArray>,
        val skippedNoFace: Int,
        val errors: Int
    )

    fun interface ProgressListener {
        fun onProgress(phase: String, current: Int, total: Int)
    }

    private val detector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.12f)
                .build()
        )
    }

    fun listBuckets(): List<BucketInfo> {
        val counts = LinkedHashMap<String, Int>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
            "${MediaStore.Images.Media.SIZE} > ?",
            arrayOf("8192"),
            null
        )?.use { c ->
            val idx = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            if (idx < 0) return emptyList()
            while (c.moveToNext()) {
                val name = c.getString(idx)?.trim().orEmpty()
                if (name.isEmpty()) continue
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .map { BucketInfo(it.key, it.value) }
    }

    fun buildReferences(uris: List<Uri>): ReferenceBuildResult {
        val embeddings = mutableListOf<FloatArray>()
        var skipped = 0
        var errors = 0
        for (uri in uris) {
            try {
                val bmp = decodeBitmap(uri, null)
                if (bmp == null) {
                    errors++
                    continue
                }
                try {
                    val faces = detectFaces(bmp)
                    if (faces.isEmpty()) {
                        skipped++
                    } else {
                        // En büyük yüz = asıl kişi varsayımı
                        val primary = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }
                        embeddings += faceEmbedding(bmp, primary)
                    }
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            } catch (_: Exception) {
                errors++
            }
        }
        return ReferenceBuildResult(embeddings, skipped, errors)
    }

    fun scan(
        bucketName: String,
        references: List<FloatArray>,
        matchThreshold: Float = DEFAULT_MATCH_THRESHOLD,
        cancelled: AtomicBoolean? = null,
        progress: ProgressListener? = null
    ): List<MismatchCandidate> {
        if (references.isEmpty()) return emptyList()
        val photos = loadBucketPhotos(bucketName)
        if (photos.isEmpty()) return emptyList()

        val out = mutableListOf<MismatchCandidate>()
        photos.forEachIndexed { index, photo ->
            if (cancelled?.get() == true) return out
            progress?.onProgress("Yüzler karşılaştırılıyor…", index + 1, photos.size)
            try {
                val bmp = decodeBitmap(photo.uri, photo.path) ?: return@forEachIndexed
                try {
                    val faces = detectFaces(bmp)
                    if (faces.isEmpty()) {
                        // Yüz yok → farklı kişi sayılmaz (manzara vb.)
                        return@forEachIndexed
                    }
                    var best = 0f
                    for (face in faces) {
                        val emb = faceEmbedding(bmp, face)
                        for (ref in references) {
                            best = maxOf(best, cosineSimilarity(emb, ref))
                        }
                    }
                    if (best < matchThreshold) {
                        out += MismatchCandidate(
                            path = photo.path,
                            uri = photo.uri,
                            displayName = photo.displayName,
                            sizeBytes = photo.sizeBytes,
                            bestSimilarity = best,
                            faceCount = faces.size,
                            reason = if (best <= 0.01f) {
                                "Yüz var, asıl kişiyle eşleşme yok"
                            } else {
                                "Benzerlik düşük (%${(best * 100).toInt()})"
                            }
                        )
                    }
                } finally {
                    if (!bmp.isRecycled) bmp.recycle()
                }
            } catch (_: Exception) {
                // tek dosya hatası taramayı düşürmez
            }
        }
        return out.sortedBy { it.bestSimilarity }
    }

    fun close() {
        runCatching { detector.close() }
    }

    private data class PhotoRow(
        val path: String,
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long
    )

    private fun loadBucketPhotos(bucketName: String): List<PhotoRow> {
        val out = mutableListOf<PhotoRow>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME}=? AND ${MediaStore.Images.Media.SIZE} > ?",
            arrayOf(bucketName, "8192"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dataI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val nameI = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeI = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            while (c.moveToNext()) {
                val path = c.getString(dataI).orEmpty()
                if (path.isNotBlank() && SystemFileGuard.isProtectedPath(path)) continue
                val id = c.getLong(idI)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                out += PhotoRow(
                    path = path.ifBlank { uri.toString() },
                    uri = uri,
                    displayName = c.getString(nameI) ?: "foto_$id",
                    sizeBytes = c.getLong(sizeI)
                )
                if (out.size >= MAX_SCAN) break
            }
        }
        return out
    }

    private fun detectFaces(bitmap: Bitmap): List<Face> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return Tasks.await(detector.process(image))
    }

    private fun decodeBitmap(uri: Uri?, path: String?): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (!path.isNullOrBlank() && File(path).isFile) {
                BitmapFactory.decodeFile(path, bounds)
            } else if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
            } else return null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > DECODE_MAX) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            when {
                !path.isNullOrBlank() && File(path).isFile ->
                    BitmapFactory.decodeFile(path, opts)
                uri != null ->
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Landmark oranları + hizalanmış yüz kırpımının düşük çözünürlük gri vektörü.
     * Tam FaceNet değil; belirgin farklı kişilerde yüksek isabet hedeflenir.
     */
    private fun faceEmbedding(bitmap: Bitmap, face: Face): FloatArray {
        val landmarkParts = listOf(
            landmarkNorm(face, FaceLandmark.LEFT_EYE, bitmap),
            landmarkNorm(face, FaceLandmark.RIGHT_EYE, bitmap),
            landmarkNorm(face, FaceLandmark.NOSE_BASE, bitmap),
            landmarkNorm(face, FaceLandmark.MOUTH_LEFT, bitmap),
            landmarkNorm(face, FaceLandmark.MOUTH_RIGHT, bitmap),
            landmarkNorm(face, FaceLandmark.LEFT_CHEEK, bitmap),
            landmarkNorm(face, FaceLandmark.RIGHT_CHEEK, bitmap)
        )
        val ratios = landmarkRatios(face)
        val crop = faceCropGray(bitmap, face.boundingBox)
        val combined = FloatArray(
            landmarkParts.sumOf { it.size } + ratios.size + crop.size
        )
        var i = 0
        for (part in landmarkParts) {
            for (v in part) combined[i++] = v
        }
        for (v in ratios) combined[i++] = v
        for (v in crop) combined[i++] = v
        return normalize(combined)
    }

    private fun landmarkNorm(face: Face, type: Int, bitmap: Bitmap): FloatArray {
        val lm = face.getLandmark(type)?.position
        val box = face.boundingBox
        if (lm == null || box.width() <= 0 || box.height() <= 0) {
            return floatArrayOf(0.5f, 0.5f)
        }
        val x = ((lm.x - box.left) / box.width().toFloat()).coerceIn(0f, 1f)
        val y = ((lm.y - box.top) / box.height().toFloat()).coerceIn(0f, 1f)
        // bitmap boyutuna göre hafif ağırlık (farklı kırpımlarda kararlılık)
        val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        return floatArrayOf(x, y * (0.85f + 0.15f * aspect.coerceIn(0.5f, 1.5f)))
    }

    private fun landmarkRatios(face: Face): FloatArray {
        fun pt(type: Int) = face.getLandmark(type)?.position
        val le = pt(FaceLandmark.LEFT_EYE)
        val re = pt(FaceLandmark.RIGHT_EYE)
        val nose = pt(FaceLandmark.NOSE_BASE)
        val ml = pt(FaceLandmark.MOUTH_LEFT)
        val mr = pt(FaceLandmark.MOUTH_RIGHT)
        if (le == null || re == null) return FloatArray(6)
        val iod = dist(le.x, le.y, re.x, re.y).coerceAtLeast(1f)
        val mouthW = if (ml != null && mr != null) dist(ml.x, ml.y, mr.x, mr.y) / iod else 0f
        val eyeMouth = if (ml != null && mr != null) {
            val mx = (ml.x + mr.x) / 2f
            val my = (ml.y + mr.y) / 2f
            val ex = (le.x + re.x) / 2f
            val ey = (le.y + re.y) / 2f
            dist(ex, ey, mx, my) / iod
        } else 0f
        val noseEye = if (nose != null) {
            val ex = (le.x + re.x) / 2f
            val ey = (le.y + re.y) / 2f
            dist(ex, ey, nose.x, nose.y) / iod
        } else 0f
        val noseMouth = if (nose != null && ml != null && mr != null) {
            val mx = (ml.x + mr.x) / 2f
            val my = (ml.y + mr.y) / 2f
            dist(nose.x, nose.y, mx, my) / iod
        } else 0f
        return floatArrayOf(
            mouthW,
            eyeMouth,
            noseEye,
            noseMouth,
            face.headEulerAngleY / 90f,
            face.headEulerAngleZ / 90f
        )
    }

    private fun faceCropGray(bitmap: Bitmap, box: Rect): FloatArray {
        val padX = (box.width() * 0.12f).toInt()
        val padY = (box.height() * 0.12f).toInt()
        val left = (box.left - padX).coerceAtLeast(0)
        val top = (box.top - padY).coerceAtLeast(0)
        val right = (box.right + padX).coerceAtMost(bitmap.width)
        val bottom = (box.bottom + padY).coerceAtMost(bitmap.height)
        if (right <= left || bottom <= top) return FloatArray(CROP_SIDE * CROP_SIDE)

        val cropped = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        val scaled = Bitmap.createScaledBitmap(cropped, CROP_SIDE, CROP_SIDE, true)
        if (cropped !== bitmap && cropped !== scaled) cropped.recycle()

        val gray = Bitmap.createBitmap(CROP_SIDE, CROP_SIDE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(gray)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        if (scaled !== gray) scaled.recycle()

        val pixels = IntArray(CROP_SIDE * CROP_SIDE)
        gray.getPixels(pixels, 0, CROP_SIDE, 0, 0, CROP_SIDE, CROP_SIDE)
        gray.recycle()
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        }
        return out
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0f
        var na = 0f
        var nb = 0f
        for (i in 0 until n) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-8f) 0f else (dot / denom).coerceIn(-1f, 1f)
    }

    private fun normalize(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) sumSq += x * x
        val norm = sqrt(sumSq)
        if (norm < 1e-8f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        const val DEFAULT_MATCH_THRESHOLD = 0.78f
        private const val DECODE_MAX = 960
        private const val CROP_SIDE = 24
        private const val MAX_SCAN = 800
    }
}
