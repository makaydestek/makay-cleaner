package com.makay.cleaner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
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
import kotlin.math.abs

/**
 * Klasördeki fotoğrafları referans “asıl kişi” ile MobileFaceNet embedding’leriyle karşılaştırır.
 * ML Kit: yüz tespiti + landmark; TFLite MobileFaceNet: kimlik vektörü.
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
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.08f)
                .build()
        )
    }

    private val embedder: FaceNetEmbedder by lazy { FaceNetEmbedder(context.applicationContext) }

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
                    val usable = faces.filter { isUsableIdentityFace(it) }
                    if (usable.isEmpty()) {
                        skipped++
                    } else {
                        val primary = usable.maxBy {
                            it.boundingBox.width() * it.boundingBox.height()
                        }
                        embeddings += embedFace(bmp, primary)
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
        progress?.onProgress("Klasör listeleniyor…", 0, 1)
        val photos = loadBucketPhotos(bucketName)
        if (photos.isEmpty()) return emptyList()

        val out = mutableListOf<MismatchCandidate>()
        photos.forEachIndexed { index, photo ->
            if (cancelled?.get() == true) return out
            progress?.onProgress("YZ yüz analizi…", index + 1, photos.size)
            // Büyük klasörlerde bellek baskısını azalt
            if (index % 40 == 0) {
                System.gc()
            }
            try {
                val bmp = decodeBitmap(photo.uri, photo.path) ?: return@forEachIndexed
                try {
                    val faces = detectFaces(bmp)
                    val identityFaces = faces.filter { isUsableIdentityFace(it) }
                    if (identityFaces.isEmpty()) {
                        // Profil / arkası dönük / yüz yok → aday değil
                        return@forEachIndexed
                    }
                    var best = -1f
                    for (face in identityFaces) {
                        val emb = embedFace(bmp, face)
                        for (ref in references) {
                            best = maxOf(best, FaceNetEmbedder.cosineSimilarity(emb, ref))
                        }
                    }
                    if (best < matchThreshold) {
                        out += MismatchCandidate(
                            path = photo.path,
                            uri = photo.uri,
                            displayName = photo.displayName,
                            sizeBytes = photo.sizeBytes,
                            bestSimilarity = best.coerceAtLeast(0f),
                            faceCount = identityFaces.size,
                            reason = when {
                                best < 0f -> "Yüz bulundu, kimlik eşleşmedi"
                                else -> "YZ benzerlik %${(best * 100).toInt()} (eşik %${(matchThreshold * 100).toInt()})"
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
        runCatching { embedder.close() }
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
            }
        }
        return out
    }

    /**
     * Kimlik karşılaştırması için yeterince cepheden / görünür yüz.
     * Aşırı profil ve çok küçük yüzler atlanır (yanlış negatif/pozitif azaltır).
     */
    private fun isUsableIdentityFace(face: Face): Boolean {
        val box = face.boundingBox
        if (box.width() < 48 || box.height() < 48) return false
        if (abs(face.headEulerAngleY) > 42f) return false
        if (abs(face.headEulerAngleZ) > 35f) return false
        return true
    }

    private fun embedFace(bitmap: Bitmap, face: Face): FloatArray {
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let { PointF(it.x, it.y) }
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let { PointF(it.x, it.y) }
        return embedder.embed(bitmap, face.boundingBox, left, right)
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
            val raw = when {
                !path.isNullOrBlank() && File(path).isFile ->
                    BitmapFactory.decodeFile(path, opts)
                uri != null ->
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, opts)
                    }
                else -> null
            } ?: return null

            val rotation = readExifRotation(path, uri)
            if (rotation == 0) raw
            else {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                if (rotated !== raw) raw.recycle()
                rotated
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun readExifRotation(path: String?, uri: Uri?): Int {
        return try {
            val exif = when {
                !path.isNullOrBlank() && File(path).isFile ->
                    ExifInterface(path)
                uri != null ->
                    context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                else -> null
            } ?: return 0
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        /**
         * MobileFaceNet + L2 kosinüs: aynı kişi genelde ≥ 0.55–0.75.
         * Varsayılan 0.52 — aynı kişiyi yanlış “farklı” işaretlemeyi azaltır.
         * Kaydırıcıyla yükseltmek = daha katı (daha çok aday).
         */
        const val DEFAULT_MATCH_THRESHOLD = 0.52f
        private const val DECODE_MAX = 1280
    }
}
