package com.makay.cleaner.scanner

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Duplicate Photos Fixer tarzı: aynı ve benzer fotoğraf grupları.
 * Tarama modları, benzerlik seviyesi, zaman/GPS, döndürme ve ilerleme desteği.
 */
class DuplicatePhotoScanner(private val context: Context) {

    data class PhotoItem(
        val id: Long,
        val path: String,
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val dateAdded: Long,
        val dateTaken: Long,
        val width: Int,
        val height: Int,
        val bucketName: String,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val hasMetadata: Boolean = false,
        val mimeHint: String = ""
    ) {
        val pixelCount: Long get() = width.toLong().coerceAtLeast(1) * height.toLong().coerceAtLeast(1)

        /** En iyi kare skoru: çözünürlük > boyut > yenilik */
        fun qualityScore(sharpness: Double = 0.0): Long {
            val sharpBoost = (sharpness * 50_000).toLong()
            return pixelCount * 1_000L + sizeBytes / 1_024L + dateAdded / 1_000_000L + sharpBoost
        }
    }

    data class PhotoGroup(
        val id: String,
        val type: GroupType,
        val photos: List<PhotoItem>,
        val totalSize: Long
    ) {
        val wasteBytes: Long
            get() = if (photos.size <= 1) 0L else totalSize - (photos.maxOfOrNull { it.sizeBytes } ?: 0L)
    }

    data class BucketInfo(val name: String, val count: Int)

    data class ScanOptions(
        val mode: String = DuplicatePhotoPrefs.MODE_FULL,
        val bucketName: String? = null,
        val matchingLevel: Int = 1,
        val timeIntervalSec: Int = 30,
        val gpsRadiusMeters: Int = 0,
        val includeRotatedFlipped: Boolean = true,
        val excludedBuckets: Set<String> = emptySet(),
        val allowedFormats: Set<String> = DuplicatePhotoPrefs.DEFAULT_FORMATS,
        val exactLimit: Int = DEFAULT_EXACT_LIMIT,
        val similarLimit: Int = DEFAULT_SIMILAR_LIMIT,
        val timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ) {
        val hammingThreshold: Int
            get() = when (matchingLevel.coerceIn(0, 2)) {
                0 -> 10 // düşük eşik → daha geniş benzerlik
                2 -> 3  // yüksek → neredeyse aynı
                else -> 6
            }

        companion object {
            fun fromPrefs(prefs: DuplicatePhotoPrefs): ScanOptions {
                val mode = prefs.scanMode
                val quick = mode == DuplicatePhotoPrefs.MODE_QUICK
                val rotated = prefs.includeRotatedFlipped
                return ScanOptions(
                    mode = mode,
                    bucketName = if (mode == DuplicatePhotoPrefs.MODE_FOLDER) prefs.selectedBucket else null,
                    matchingLevel = prefs.matchingLevel,
                    timeIntervalSec = prefs.timeIntervalSec,
                    gpsRadiusMeters = prefs.gpsRadiusMeters,
                    includeRotatedFlipped = rotated,
                    excludedBuckets = prefs.excludedBuckets,
                    allowedFormats = prefs.allowedFormats.ifEmpty { DuplicatePhotoPrefs.DEFAULT_FORMATS },
                    exactLimit = if (quick) QUICK_EXACT_LIMIT else DEFAULT_EXACT_LIMIT,
                    // Döndürme varyantları bellek maliyetli; limiti düşürerek kararlılık korunur
                    similarLimit = when {
                        quick -> QUICK_SIMILAR_LIMIT
                        rotated -> DEFAULT_SIMILAR_LIMIT / 2
                        else -> DEFAULT_SIMILAR_LIMIT
                    },
                    timeoutMs = if (quick) QUICK_TIMEOUT_MS else DEFAULT_TIMEOUT_MS
                )
            }
        }
    }

    fun interface ProgressListener {
        fun onProgress(phase: String, current: Int, total: Int)
    }

    enum class GroupType { EXACT, SIMILAR }

    companion object {
        const val DEFAULT_EXACT_LIMIT = 2500
        const val DEFAULT_SIMILAR_LIMIT = 800
        const val QUICK_EXACT_LIMIT = 800
        const val QUICK_SIMILAR_LIMIT = 300
        const val DEFAULT_TIMEOUT_MS = 90_000L
        const val QUICK_TIMEOUT_MS = 35_000L
        const val MIN_FREE_HEAP_BYTES = 24L * 1024L * 1024L
        const val MAX_SIMILAR_DECODE_BYTES = 8L * 1024L * 1024L
        private val CAMERA_BUCKETS = setOf("camera", "dcim", "100andro", "100media")
    }

    fun listBuckets(): List<BucketInfo> {
        val counts = LinkedHashMap<String, Int>()
        val projection = arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.SIZE} > ?",
            arrayOf("10240"),
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

    fun scanExact(
        options: ScanOptions = ScanOptions(),
        cancelled: AtomicBoolean? = null,
        progress: ProgressListener? = null
    ): List<PhotoGroup> {
        val deadline = System.currentTimeMillis() + options.timeoutMs
        fun stop(): Boolean =
            cancelled?.get() == true || System.currentTimeMillis() > deadline || lowMemory()

        progress?.onProgress("Fotoğraflar yükleniyor…", 0, 1)
        val photos = loadPhotos(options, options.exactLimit, cancelled, deadline, progress)
        if (stop() || photos.isEmpty()) return emptyList()

        val bySize = photos.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
        val groups = mutableListOf<PhotoGroup>()
        var done = 0
        val total = bySize.values.sumOf { it.size }.coerceAtLeast(1)
        bySize.values.forEach { sameSize ->
            if (stop()) return@forEach
            val byHash = LinkedHashMap<String, MutableList<PhotoItem>>()
            sameSize.forEach { photo ->
                if (stop()) return@forEach
                done++
                if (done % 8 == 0) progress?.onProgress("Aynı fotoğraflar…", done, total)
                val hash = md5Partial(File(photo.path)) ?: return@forEach
                byHash.getOrPut(hash) { mutableListOf() }.add(photo)
            }
            byHash.forEach { (hash, list) ->
                if (list.size > 1) {
                    val sorted = sortByQuality(list)
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
        progress?.onProgress("Aynı fotoğraflar…", total, total)
        return groups.sortedByDescending { it.wasteBytes }
    }

    fun scanSimilar(
        options: ScanOptions = ScanOptions(),
        cancelled: AtomicBoolean? = null,
        progress: ProgressListener? = null
    ): List<PhotoGroup> {
        val deadline = System.currentTimeMillis() + options.timeoutMs
        fun stop(): Boolean =
            cancelled?.get() == true || System.currentTimeMillis() > deadline || lowMemory()

        progress?.onProgress("Benzer fotoğraflar yükleniyor…", 0, 1)
        val photos = loadPhotos(options, options.similarLimit, cancelled, deadline, progress)
        if (stop() || photos.isEmpty()) return emptyList()

        val threshold = options.hammingThreshold
        val timeMs = if (options.timeIntervalSec > 0) options.timeIntervalSec * 1000L else 0L
        val gpsM = options.gpsRadiusMeters.toFloat()
        val entries = mutableListOf<Pair<List<String>, PhotoItem>>()
        var i = 0
        photos.forEach { photo ->
            if (stop()) return@forEach
            i++
            if (i % 5 == 0) progress?.onProgress("Benzerlik analizi…", i, photos.size)
            if (photo.sizeBytes > MAX_SIMILAR_DECODE_BYTES) return@forEach
            val hashes = differenceHashes(photo.path, options.includeRotatedFlipped) ?: return@forEach
            entries.add(hashes to photo)
        }
        if (entries.isEmpty()) return emptyList()

        val used = BooleanArray(entries.size)
        val groups = mutableListOf<PhotoGroup>()
        for (a in entries.indices) {
            if (stop()) break
            if (used[a]) continue
            val merged = mutableListOf(entries[a].second)
            used[a] = true
            for (b in a + 1 until entries.size) {
                if (stop()) break
                if (used[b]) continue
                val pa = entries[a].second
                val pb = entries[b].second
                if (timeMs > 0 && !withinTime(pa, pb, timeMs)) continue
                if (gpsM > 0f && !withinGps(pa, pb, gpsM)) continue
                if (minHamming(entries[a].first, entries[b].first) <= threshold) {
                    merged.add(pb)
                    used[b] = true
                }
            }
            if (merged.size > 1) {
                val sorted = sortByQuality(merged)
                groups.add(
                    PhotoGroup(
                        id = "sim_${entries[a].first.firstOrNull() ?: a}",
                        type = GroupType.SIMILAR,
                        photos = sorted,
                        totalSize = sorted.sumOf { it.sizeBytes }
                    )
                )
            }
            if (a % 10 == 0) progress?.onProgress("Gruplanıyor…", a + 1, entries.size)
        }
        progress?.onProgress("Gruplanıyor…", entries.size, entries.size)
        return groups.sortedByDescending { it.wasteBytes }
    }

    /** Grup içinde en iyi kareyi başa alır (keskinlik örneklemeli). */
    fun keepBestOrder(photos: List<PhotoItem>): List<PhotoItem> {
        if (photos.size <= 1) return photos
        return photos
            .map { it to estimateSharpness(it.path) }
            .sortedByDescending { (p, sharp) -> p.qualityScore(sharp) }
            .map { it.first }
    }

    fun pickBest(photos: List<PhotoItem>): PhotoItem? =
        keepBestOrder(photos).firstOrNull()

    private fun sortByQuality(photos: List<PhotoItem>): List<PhotoItem> =
        photos.sortedWith(
            compareByDescending<PhotoItem> { it.pixelCount }
                .thenByDescending { it.sizeBytes }
                .thenByDescending { it.dateAdded }
        )

    private fun withinTime(a: PhotoItem, b: PhotoItem, maxDiffMs: Long): Boolean {
        val ta = a.dateTaken.takeIf { it > 0 } ?: a.dateAdded
        val tb = b.dateTaken.takeIf { it > 0 } ?: b.dateAdded
        return abs(ta - tb) <= maxDiffMs
    }

    private fun withinGps(a: PhotoItem, b: PhotoItem, maxMeters: Float): Boolean {
        val la = a.latitude ?: return true
        val loa = a.longitude ?: return true
        val lb = b.latitude ?: return true
        val lob = b.longitude ?: return true
        val out = FloatArray(1)
        Location.distanceBetween(la, loa, lb, lob, out)
        return out[0] <= maxMeters
    }

    private fun minHamming(hashesA: List<String>, hashesB: List<String>): Int {
        var best = 64
        for (ha in hashesA) {
            for (hb in hashesB) {
                best = minOf(best, hamming(ha, hb))
                if (best == 0) return 0
            }
        }
        return best
    }

    private fun lowMemory(): Boolean {
        val rt = Runtime.getRuntime()
        val free = rt.maxMemory() - (rt.totalMemory() - rt.freeMemory())
        return free < MIN_FREE_HEAP_BYTES
    }

    private fun loadPhotos(
        options: ScanOptions,
        limit: Int,
        cancelled: AtomicBoolean?,
        deadlineMs: Long,
        progress: ProgressListener?
    ): List<PhotoItem> {
        val out = mutableListOf<PhotoItem>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val needGps = options.gpsRadiusMeters > 0
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
            val takenI = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val wI = c.getColumnIndex(MediaStore.Images.Media.WIDTH)
            val hI = c.getColumnIndex(MediaStore.Images.Media.HEIGHT)
            val bucketI = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            var scanned = 0
            while (c.moveToNext() && out.size < limit) {
                if (cancelled?.get() == true || System.currentTimeMillis() > deadlineMs) break
                scanned++
                if (scanned % 40 == 0) {
                    progress?.onProgress("Galeri okunuyor…", out.size, limit)
                }
                val path = c.getString(dataI) ?: continue
                if (!SystemFileGuard.canDelete(path)) {
                    SystemFileGuard.recordSkipped()
                    continue
                }
                if (!File(path).exists()) continue
                val ext = File(path).extension.lowercase()
                if (ext.isNotEmpty() && options.allowedFormats.isNotEmpty() &&
                    ext !in options.allowedFormats
                ) {
                    // jpeg alias
                    if (!(ext == "jpeg" && "jpg" in options.allowedFormats) &&
                        !(ext == "jpg" && "jpeg" in options.allowedFormats) &&
                        !(ext == "tiff" && "tif" in options.allowedFormats) &&
                        !(ext == "tif" && "tiff" in options.allowedFormats) &&
                        !(ext == "heif" && "heic" in options.allowedFormats) &&
                        !(ext == "heic" && "heif" in options.allowedFormats)
                    ) continue
                }
                val bucket = if (bucketI >= 0) c.getString(bucketI)?.trim().orEmpty() else ""
                if (bucket.isNotEmpty() && bucket in options.excludedBuckets) continue
                if (!matchesMode(options, bucket, path)) continue

                val id = c.getLong(idI)
                var w = if (wI >= 0) c.getInt(wI) else 0
                var h = if (hI >= 0) c.getInt(hI) else 0
                if (w <= 0 || h <= 0) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    w = bounds.outWidth.coerceAtLeast(0)
                    h = bounds.outHeight.coerceAtLeast(0)
                }
                val taken = if (takenI >= 0) c.getLong(takenI) else 0L
                var lat: Double? = null
                var lon: Double? = null
                val meta = readLightMetadata(path, needGps)
                if (needGps) {
                    lat = meta.latitude
                    lon = meta.longitude
                }
                out.add(
                    PhotoItem(
                        id = id,
                        path = path,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        displayName = c.getString(nameI) ?: File(path).name,
                        sizeBytes = c.getLong(sizeI),
                        dateAdded = c.getLong(dateI) * 1000L,
                        dateTaken = taken,
                        width = w,
                        height = h,
                        bucketName = bucket,
                        latitude = lat,
                        longitude = lon,
                        hasMetadata = meta.hasMetadata || taken > 0,
                        mimeHint = ext
                    )
                )
            }
        }
        return out
    }

    private fun matchesMode(options: ScanOptions, bucket: String, path: String): Boolean {
        val lowerBucket = bucket.lowercase()
        val lowerPath = path.lowercase()
        return when (options.mode) {
            DuplicatePhotoPrefs.MODE_CAMERA ->
                lowerBucket in CAMERA_BUCKETS ||
                    lowerBucket.contains("camera") ||
                    lowerPath.contains("/dcim/") ||
                    lowerPath.contains("/camera/")
            DuplicatePhotoPrefs.MODE_FOLDER -> {
                val target = options.bucketName?.trim().orEmpty()
                if (target.isEmpty()) true else bucket.equals(target, ignoreCase = true)
            }
            else -> true // quick + full
        }
    }

    private data class LightMeta(
        val hasMetadata: Boolean,
        val latitude: Double? = null,
        val longitude: Double? = null
    )

    private fun readLightMetadata(path: String, needGps: Boolean): LightMeta {
        return try {
            val exif = ExifInterface(path)
            val has = !exif.getAttribute(ExifInterface.TAG_DATETIME).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_MAKE).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_MODEL).isNullOrBlank() ||
                !exif.getAttribute(ExifInterface.TAG_ORIENTATION).isNullOrBlank()
            var lat: Double? = null
            var lon: Double? = null
            if (needGps) {
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    lat = latLong[0].toDouble()
                    lon = latLong[1].toDouble()
                }
            }
            LightMeta(hasMetadata = has || lat != null, latitude = lat, longitude = lon)
        } catch (_: Exception) {
            LightMeta(hasMetadata = false)
        }
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

    /** Temel dHash + isteğe bağlı döndürme/ayna varyantları. */
    private fun differenceHashes(path: String, includeRotated: Boolean): List<String>? {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
            val variants = mutableListOf(bmp)
            if (includeRotated) {
                variants += rotate(bmp, 90f)
                variants += rotate(bmp, 180f)
                variants += rotate(bmp, 270f)
                variants += flip(bmp, horizontal = true)
                variants += flip(bmp, horizontal = false)
            }
            val hashes = variants.mapNotNull { hashBitmap(it) }.distinct()
            variants.forEach { if (it !== bmp) it.recycle() }
            bmp.recycle()
            hashes.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun flip(src: Bitmap, horizontal: Boolean): Bitmap {
        val m = Matrix().apply {
            if (horizontal) preScale(-1f, 1f) else preScale(1f, -1f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun hashBitmap(bmp: Bitmap): String? {
        return try {
            val w = 9
            val h = 8
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            val sb = StringBuilder(64)
            for (y in 0 until h) {
                for (x in 0 until w - 1) {
                    val left = gray(scaled.getPixel(x, y))
                    val right = gray(scaled.getPixel(x + 1, y))
                    sb.append(if (left < right) '1' else '0')
                }
            }
            if (scaled !== bmp) scaled.recycle()
            sb.toString()
        } catch (_: Exception) {
            null
        }
    }

    /** Küçük örneklemle keskinlik (varyans); Keep Best için. */
    fun estimateSharpness(path: String): Double {
        return try {
            val opts = BitmapFactory.Options().apply { inSampleSize = 16 }
            val bmp = BitmapFactory.decodeFile(path, opts) ?: return 0.0
            val w = minOf(32, bmp.width)
            val h = minOf(32, bmp.height)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            if (bmp !== scaled) bmp.recycle()
            var sum = 0.0
            var sumSq = 0.0
            var n = 0
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val c = gray(scaled.getPixel(x, y)).toDouble()
                    val lap = abs(
                        4 * c -
                            gray(scaled.getPixel(x - 1, y)) -
                            gray(scaled.getPixel(x + 1, y)) -
                            gray(scaled.getPixel(x, y - 1)) -
                            gray(scaled.getPixel(x, y + 1))
                    )
                    sum += lap
                    sumSq += lap * lap
                    n++
                }
            }
            scaled.recycle()
            if (n == 0) 0.0 else {
                val mean = sum / n
                sqrt((sumSq / n) - mean * mean)
            }
        } catch (_: Exception) {
            0.0
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
