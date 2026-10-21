package com.makay.cleaner.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.makay.cleaner.domain.model.StorageCategory
import com.makay.cleaner.domain.model.StorageInfo
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

/**
 * Pro tarzı dosya analizi: tür kırılımı, klasör boyutları, büyük/son dosyalar, boş klasörler.
 * MediaStore + sığ public klasör taraması; sistem yollarına dokunulmaz.
 */
class FileAnalysisScanner(private val context: Context) {

    data class TypeStat(
        val id: String,
        val name: String,
        val fileCount: Int,
        val sizeBytes: Long,
        val color: Int
    ) {
        fun toCategory(): StorageCategory = StorageCategory(
            id = id,
            name = name,
            icon = "",
            sizeBytes = sizeBytes,
            sizeFormatted = formatSize(sizeBytes),
            color = color
        )
    }

    data class FolderStat(
        val name: String,
        val pathHint: String,
        val fileCount: Int,
        val sizeBytes: Long
    )

    data class FileEntry(
        val path: String,
        val uri: Uri?,
        val displayName: String,
        val sizeBytes: Long,
        val dateModified: Long,
        val mimeType: String,
        val categoryId: String
    )

    data class AnalysisReport(
        val storage: StorageInfo,
        val types: List<TypeStat>,
        val topFolders: List<FolderStat>,
        val largestFiles: List<FileEntry>,
        val recentFiles: List<FileEntry>,
        val emptyFolders: List<String>,
        val scannedFileEstimate: Int,
        val cancelled: Boolean
    )

    fun interface ProgressListener {
        fun onProgress(phase: String, current: Int, total: Int)
    }

    companion object {
        private const val LARGE_MIN_BYTES = 20L * 1024L * 1024L
        private const val TOP_N = 25
        private const val EMPTY_MAX = 40

        fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }

    fun analyze(
        cancelled: AtomicBoolean? = null,
        progress: ProgressListener? = null
    ): AnalysisReport {
        fun stop() = cancelled?.get() == true

        progress?.onProgress("Depolama özeti…", 0, 6)
        val storage = storageInfo()
        if (stop()) return emptyReport(storage, cancelled = true)

        progress?.onProgress("Dosya türleri…", 1, 6)
        val types = analyzeTypes(cancelled)
        if (stop()) return emptyReport(storage, types, cancelled = true)

        progress?.onProgress("Klasörler…", 2, 6)
        val folders = analyzeFolders(cancelled)
        if (stop()) return emptyReport(storage, types, folders, cancelled = true)

        progress?.onProgress("Büyük dosyalar…", 3, 6)
        val largest = queryLargestFiles(cancelled)
        if (stop()) {
            return AnalysisReport(storage, types, folders, largest, emptyList(), emptyList(),
                types.sumOf { it.fileCount }, true)
        }

        progress?.onProgress("Son dosyalar…", 4, 6)
        val recent = queryRecentFiles(cancelled)
        if (stop()) {
            return AnalysisReport(storage, types, folders, largest, recent, emptyList(),
                types.sumOf { it.fileCount }, true)
        }

        progress?.onProgress("Boş klasörler…", 5, 6)
        val empty = findEmptyFolders(cancelled)
        progress?.onProgress("Tamamlandı", 6, 6)

        return AnalysisReport(
            storage = storage,
            types = types.filter { it.sizeBytes > 0 || it.fileCount > 0 },
            topFolders = folders,
            largestFiles = largest,
            recentFiles = recent,
            emptyFolders = empty,
            scannedFileEstimate = types.sumOf { it.fileCount },
            cancelled = stop()
        )
    }

    private fun emptyReport(
        storage: StorageInfo = storageInfo(),
        types: List<TypeStat> = emptyList(),
        folders: List<FolderStat> = emptyList(),
        cancelled: Boolean = false
    ) = AnalysisReport(storage, types, folders, emptyList(), emptyList(), emptyList(),
        types.sumOf { it.fileCount }, cancelled)

    private fun storageInfo(): StorageInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val total = stat.totalBytes
        val free = stat.freeBytes
        val used = (total - free).coerceAtLeast(0)
        val pct = if (total > 0) used.toFloat() / total * 100f else 0f
        return StorageInfo(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used,
            usagePercent = pct,
            totalFormatted = formatSize(total),
            freeFormatted = formatSize(free),
            usedFormatted = formatSize(used)
        )
    }

    private fun analyzeTypes(cancelled: AtomicBoolean?): List<TypeStat> {
        val cr = context.contentResolver
        val images = mediaAgg(cr, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cancelled)
        val videos = mediaAgg(cr, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cancelled)
        val audio = mediaAgg(cr, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cancelled)
        val docs = filesByMime(
            cancelled,
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv"
        )
        val apk = filesByMime(cancelled, "application/vnd.android.package-archive")
        val archives = filesByMime(
            cancelled,
            "application/zip",
            "application/x-zip-compressed",
            "application/x-rar-compressed",
            "application/x-7z-compressed",
            "application/gzip",
            "application/x-tar"
        )
        val downloads = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mediaAgg(cr, MediaStore.Downloads.EXTERNAL_CONTENT_URI, cancelled)
        } else {
            0 to 0L
        }

        return listOf(
            TypeStat("images", "Fotoğraflar", images.first, images.second, 0xFF2196F3.toInt()),
            TypeStat("videos", "Videolar", videos.first, videos.second, 0xFFE91E63.toInt()),
            TypeStat("audio", "Ses", audio.first, audio.second, 0xFF9C27B0.toInt()),
            TypeStat("docs", "Belgeler", docs.first, docs.second, 0xFF4CAF50.toInt()),
            TypeStat("apk", "APK", apk.first, apk.second, 0xFFFF9800.toInt()),
            TypeStat("archives", "Arşivler", archives.first, archives.second, 0xFF795548.toInt()),
            TypeStat("downloads", "İndirilenler", downloads.first, downloads.second, 0xFF607D8B.toInt())
        ).sortedByDescending { it.sizeBytes }
    }

    private fun mediaAgg(
        cr: android.content.ContentResolver,
        uri: Uri,
        cancelled: AtomicBoolean?
    ): Pair<Int, Long> {
        var count = 0
        var size = 0L
        try {
            cr.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA),
                null,
                null,
                null
            )?.use { c ->
                val sizeI = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dataI = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    if (cancelled?.get() == true) break
                    val path = if (dataI >= 0) c.getString(dataI) else null
                    if (path != null && !SystemFileGuard.canDelete(path)) {
                        SystemFileGuard.recordSkipped()
                        continue
                    }
                    count++
                    if (sizeI >= 0) size += c.getLong(sizeI).coerceAtLeast(0)
                }
            }
        } catch (_: Exception) {
        }
        return count to size
    }

    private fun filesByMime(cancelled: AtomicBoolean?, vararg mimes: String): Pair<Int, Long> {
        if (mimes.isEmpty()) return 0 to 0L
        val placeholders = mimes.joinToString(",") { "?" }
        var count = 0
        var size = 0L
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATA),
                "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($placeholders)",
                mimes,
                null
            )?.use { c ->
                val sizeI = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dataI = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                while (c.moveToNext()) {
                    if (cancelled?.get() == true) break
                    val path = if (dataI >= 0) c.getString(dataI) else null
                    if (path != null && !SystemFileGuard.canDelete(path)) {
                        SystemFileGuard.recordSkipped()
                        continue
                    }
                    count++
                    if (sizeI >= 0) size += c.getLong(sizeI).coerceAtLeast(0)
                }
            }
        } catch (_: Exception) {
        }
        return count to size
    }

    private fun analyzeFolders(cancelled: AtomicBoolean?): List<FolderStat> {
        val map = LinkedHashMap<String, Pair<Int, Long>>() // name -> count, size
        val projection = arrayOf(
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATA
        )
        fun consume(uri: Uri) {
            try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    val bI = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val sI = c.getColumnIndex(MediaStore.Images.Media.SIZE)
                    val dI = c.getColumnIndex(MediaStore.Images.Media.DATA)
                    while (c.moveToNext()) {
                        if (cancelled?.get() == true) return
                        val path = if (dI >= 0) c.getString(dI) else null
                        if (path != null && !SystemFileGuard.canDelete(path)) continue
                        val name = (if (bI >= 0) c.getString(bI) else null)?.trim().orEmpty()
                            .ifEmpty {
                                path?.let { File(it).parentFile?.name } ?: "Diğer"
                            }
                        val sz = if (sI >= 0) c.getLong(sI).coerceAtLeast(0) else 0L
                        val prev = map[name] ?: (0 to 0L)
                        map[name] = (prev.first + 1) to (prev.second + sz)
                    }
                }
            } catch (_: Exception) {
            }
        }
        consume(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        if (cancelled?.get() == true) return emptyList()
        consume(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)

        // Public kök klasörler (Download vb.) — sığ boyut
        listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_MUSIC
        ).forEach { dirType ->
            if (cancelled?.get() == true) return@forEach
            val root = Environment.getExternalStoragePublicDirectory(dirType) ?: return@forEach
            if (!root.exists()) return@forEach
            val name = root.name
            if (map.containsKey(name)) return@forEach
            val (n, sz) = shallowDirStats(root, maxDepth = 2, maxFiles = 500)
            if (n > 0) map[name] = n to sz
        }

        return map.entries
            .map { FolderStat(it.key, it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.sizeBytes }
            .take(TOP_N)
    }

    private fun shallowDirStats(dir: File, maxDepth: Int, maxFiles: Int): Pair<Int, Long> {
        var n = 0
        var size = 0L
        try {
            dir.walkTopDown().maxDepth(maxDepth).forEach { f ->
                if (n >= maxFiles) return@forEach
                if (!f.isFile) return@forEach
                if (!SystemFileGuard.canDelete(f.absolutePath)) return@forEach
                n++
                size += f.length()
            }
        } catch (_: Exception) {
        }
        return n to size
    }

    private fun queryLargestFiles(cancelled: AtomicBoolean?): List<FileEntry> {
        return queryFilesOrdered(
            cancelled = cancelled,
            selection = "${MediaStore.MediaColumns.SIZE} >= ?",
            args = arrayOf(LARGE_MIN_BYTES.toString()),
            order = "${MediaStore.MediaColumns.SIZE} DESC",
            limit = TOP_N
        )
    }

    private fun queryRecentFiles(cancelled: AtomicBoolean?): List<FileEntry> {
        return queryFilesOrdered(
            cancelled = cancelled,
            selection = "${MediaStore.MediaColumns.SIZE} >= ?",
            args = arrayOf((1024L * 100).toString()),
            order = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            limit = TOP_N
        )
    }

    private fun queryFilesOrdered(
        cancelled: AtomicBoolean?,
        selection: String?,
        args: Array<String>?,
        order: String,
        limit: Int
    ): List<FileEntry> {
        val out = mutableListOf<FileEntry>()
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                args,
                order
            )?.use { c ->
                val idI = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataI = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameI = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeI = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateI = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeI = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                while (c.moveToNext() && out.size < limit) {
                    if (cancelled?.get() == true) break
                    val path = (if (dataI >= 0) c.getString(dataI) else null) ?: continue
                    if (!SystemFileGuard.canDelete(path)) {
                        SystemFileGuard.recordSkipped()
                        continue
                    }
                    if (!File(path).exists()) continue
                    val mime = if (mimeI >= 0) c.getString(mimeI).orEmpty() else ""
                    val id = c.getLong(idI)
                    out.add(
                        FileEntry(
                            path = path,
                            uri = ContentUris.withAppendedId(
                                MediaStore.Files.getContentUri("external"), id
                            ),
                            displayName = if (nameI >= 0) c.getString(nameI) ?: File(path).name
                            else File(path).name,
                            sizeBytes = if (sizeI >= 0) c.getLong(sizeI) else File(path).length(),
                            dateModified = if (dateI >= 0) c.getLong(dateI) * 1000L else 0L,
                            mimeType = mime,
                            categoryId = categoryForMime(mime, path)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun findEmptyFolders(cancelled: AtomicBoolean?): List<String> {
        val found = mutableListOf<String>()
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            File(Environment.getExternalStorageDirectory(), "WhatsApp")
        )
        roots.filterNotNull().forEach { root ->
            if (cancelled?.get() == true) return@forEach
            if (!root.exists() || !root.isDirectory) return@forEach
            try {
                root.walkTopDown().maxDepth(4).forEach { dir ->
                    if (found.size >= EMPTY_MAX) return@forEach
                    if (cancelled?.get() == true) return@forEach
                    if (!dir.isDirectory) return@forEach
                    if (dir == root) return@forEach
                    if (!SystemFileGuard.canDelete(dir.absolutePath)) return@forEach
                    val children = dir.listFiles() ?: return@forEach
                    if (children.isEmpty()) {
                        found.add(dir.absolutePath)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return found
    }

    private fun categoryForMime(mime: String, path: String): String {
        val m = mime.lowercase(Locale.US)
        val ext = File(path).extension.lowercase(Locale.US)
        return when {
            m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "heic", "gif") -> "images"
            m.startsWith("video/") || ext in setOf("mp4", "mkv", "avi", "mov", "webm") -> "videos"
            m.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg") -> "audio"
            m.contains("android.package") || ext == "apk" -> "apk"
            m.contains("zip") || m.contains("rar") || m.contains("7z") ||
                ext in setOf("zip", "rar", "7z", "tar", "gz") -> "archives"
            m.startsWith("application/") || m.startsWith("text/") ||
                ext in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt") -> "docs"
            else -> "other"
        }
    }

}
