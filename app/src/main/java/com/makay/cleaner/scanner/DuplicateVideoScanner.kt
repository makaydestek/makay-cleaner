package com.makay.cleaner.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.makay.cleaner.data.DuplicatePhotoPrefs
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Yinelenen video taraması — boyut + kısmi MD5 (exact).
 */
class DuplicateVideoScanner(private val context: Context) {

    data class VideoItem(
        val id: Long,
        val path: String,
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val dateAdded: Long,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val bucketName: String
    )

    data class VideoGroup(
        val id: String,
        val videos: List<VideoItem>,
        val totalSize: Long
    ) {
        val wasteBytes: Long
            get() = if (videos.size <= 1) 0L else totalSize - (videos.maxOfOrNull { it.sizeBytes } ?: 0L)
    }

    fun interface ProgressListener {
        fun onProgress(phase: String, current: Int, total: Int)
    }

    companion object {
        const val DEFAULT_LIMIT = 1200
        const val QUICK_LIMIT = 400
        const val TIMEOUT_MS = 90_000L
        const val MIN_SIZE = 100L * 1024L
    }

    fun scanExact(
        mode: String = DuplicatePhotoPrefs.MODE_FULL,
        bucketName: String? = null,
        excludedBuckets: Set<String> = emptySet(),
        allowedFormats: Set<String> = DuplicatePhotoPrefs.VIDEO_FORMATS,
        quick: Boolean = false,
        cancelled: AtomicBoolean? = null,
        progress: ProgressListener? = null
    ): List<VideoGroup> {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        fun stop() = cancelled?.get() == true || System.currentTimeMillis() > deadline

        progress?.onProgress("Videolar yükleniyor…", 0, 1)
        val limit = if (quick) QUICK_LIMIT else DEFAULT_LIMIT
        val videos = loadVideos(mode, bucketName, excludedBuckets, allowedFormats, limit, cancelled, deadline, progress)
        if (stop() || videos.isEmpty()) return emptyList()

        val bySize = videos.groupBy { it.sizeBytes }.filter { it.value.size > 1 }
        val groups = mutableListOf<VideoGroup>()
        var done = 0
        val total = bySize.values.sumOf { it.size }.coerceAtLeast(1)
        bySize.values.forEach { sameSize ->
            if (stop()) return@forEach
            val byHash = LinkedHashMap<String, MutableList<VideoItem>>()
            sameSize.forEach { v ->
                if (stop()) return@forEach
                done++
                if (done % 4 == 0) progress?.onProgress("Aynı videolar…", done, total)
                val hash = md5Partial(File(v.path)) ?: return@forEach
                byHash.getOrPut(hash) { mutableListOf() }.add(v)
            }
            byHash.forEach { (hash, list) ->
                if (list.size > 1) {
                    val sorted = list.sortedWith(
                        compareByDescending<VideoItem> { it.width.toLong() * it.height }
                            .thenByDescending { it.sizeBytes }
                            .thenByDescending { it.dateAdded }
                    )
                    groups.add(VideoGroup("vid_$hash", sorted, sorted.sumOf { it.sizeBytes }))
                }
            }
        }
        progress?.onProgress("Aynı videolar…", total, total)
        return groups.sortedByDescending { it.wasteBytes }
    }

    private fun loadVideos(
        mode: String,
        bucketName: String?,
        excluded: Set<String>,
        formats: Set<String>,
        limit: Int,
        cancelled: AtomicBoolean?,
        deadline: Long,
        progress: ProgressListener?
    ): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Video.Media.SIZE} > ?",
            arrayOf(MIN_SIZE.toString()),
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val dataI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val nameI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeI = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durI = c.getColumnIndex(MediaStore.Video.Media.DURATION)
            val wI = c.getColumnIndex(MediaStore.Video.Media.WIDTH)
            val hI = c.getColumnIndex(MediaStore.Video.Media.HEIGHT)
            val bI = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            var n = 0
            while (c.moveToNext() && out.size < limit) {
                if (cancelled?.get() == true || System.currentTimeMillis() > deadline) break
                n++
                if (n % 20 == 0) progress?.onProgress("Galeri okunuyor…", out.size, limit)
                val path = c.getString(dataI) ?: continue
                if (!SystemFileGuard.canDelete(path)) {
                    SystemFileGuard.recordSkipped()
                    continue
                }
                if (!File(path).exists()) continue
                val ext = File(path).extension.lowercase()
                if (formats.isNotEmpty() && ext.isNotEmpty() && ext !in formats) continue
                val bucket = if (bI >= 0) c.getString(bI)?.trim().orEmpty() else ""
                if (bucket.isNotEmpty() && bucket in excluded) continue
                if (!matchesMode(mode, bucketName, bucket, path)) continue
                val id = c.getLong(idI)
                out.add(
                    VideoItem(
                        id = id,
                        path = path,
                        uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        displayName = c.getString(nameI) ?: File(path).name,
                        sizeBytes = c.getLong(sizeI),
                        dateAdded = c.getLong(dateI) * 1000L,
                        durationMs = if (durI >= 0) c.getLong(durI) else 0L,
                        width = if (wI >= 0) c.getInt(wI) else 0,
                        height = if (hI >= 0) c.getInt(hI) else 0,
                        bucketName = bucket
                    )
                )
            }
        }
        return out
    }

    private fun matchesMode(mode: String, bucketName: String?, bucket: String, path: String): Boolean {
        val lower = path.lowercase()
        return when (mode) {
            DuplicatePhotoPrefs.MODE_CAMERA ->
                lower.contains("/dcim/") || lower.contains("/camera/") ||
                    bucket.contains("camera", ignoreCase = true)
            DuplicatePhotoPrefs.MODE_FOLDER -> {
                val t = bucketName?.trim().orEmpty()
                if (t.isEmpty()) true else bucket.equals(t, ignoreCase = true)
            }
            else -> true
        }
    }

    fun listBuckets(): List<DuplicatePhotoScanner.BucketInfo> {
        val counts = LinkedHashMap<String, Int>()
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media.BUCKET_DISPLAY_NAME),
            "${MediaStore.Video.Media.SIZE} > ?",
            arrayOf(MIN_SIZE.toString()),
            null
        )?.use { c ->
            val idx = c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (idx < 0) return emptyList()
            while (c.moveToNext()) {
                val name = c.getString(idx)?.trim().orEmpty()
                if (name.isEmpty()) continue
                counts[name] = (counts[name] ?: 0) + 1
            }
        }
        return counts.entries.sortedByDescending { it.value }
            .map { DuplicatePhotoScanner.BucketInfo(it.key, it.value) }
    }

    private fun md5Partial(file: File): String? {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val len = file.length()
            FileInputStream(file).use { fis ->
                val buf = ByteArray(8192)
                var read = fis.read(buf)
                if (read > 0) md.update(buf, 0, read)
                if (len > 48_000) {
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
}
