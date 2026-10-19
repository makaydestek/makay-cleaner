package com.makay.cleaner.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.makay.cleaner.data.RecycleBinRepository
import java.io.File

/**
 * Silme yalnızca kaynak dosya gerçekten yoksa başarılı sayılır.
 * Çöp kutusuna kopyalayıp orijinali bırakmak “temizlendi” diye raporlanmaz.
 */
object FilePurger {

    data class Result(val filesDeleted: Int, val spaceSaved: Long) {
        operator fun plus(other: Result) =
            Result(filesDeleted + other.filesDeleted, spaceSaved + other.spaceSaved)
    }

    fun canRemove(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        if (!SystemFileGuard.canDelete(file)) return false
        if (isOwnAppFile(context, file)) return true
        if (isOtherAppSandboxPath(file.absolutePath)) {
            // Android 13+: Android/data / obb üçüncü parti uygulamaya kapalı
            if (Build.VERSION.SDK_INT >= 33) return false
            // Android 11–12: yalnızca tüm dosya erişimi + yazılabilir üst klasör
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return Environment.isExternalStorageManager() &&
                    (file.canWrite() || file.parentFile?.canWrite() == true)
            }
        }
        return true
    }

    /** İzin kontrolü olmadan silmeyi dene (Android/data listelenebiliyorsa). */
    fun tryDelete(file: File): Boolean {
        if (!file.exists()) return true
        if (!SystemFileGuard.canDelete(file)) return false
        runCatching {
            file.setWritable(true)
            file.setReadable(true)
            if (file.delete() && !file.exists()) return true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                java.nio.file.Files.deleteIfExists(file.toPath())
                if (!file.exists()) return true
            }
        }
        return !file.exists()
    }

    fun remove(
        context: Context,
        file: File,
        trash: RecycleBinRepository? = null,
        category: String = "Junk"
    ): Boolean {
        if (!file.exists()) return true
        if (!canRemove(context, file)) {
            SystemFileGuard.recordSkipped()
            return false
        }
        return if (file.isDirectory) {
            removeDirectory(context, file, trash, category)
        } else {
            removeFile(context, file, trash, category)
        }
    }

    fun removeAll(
        context: Context,
        files: Iterable<File>,
        trash: RecycleBinRepository? = null,
        category: String = "Junk"
    ): Result {
        var count = 0
        var saved = 0L
        files.forEach { file ->
            val size = if (file.isFile) file.length() else dirSize(file)
            if (remove(context, file, trash, category)) {
                count++
                saved += size
            }
        }
        return Result(count, saved)
    }

    fun wipeOwnCaches(context: Context): Result {
        var count = 0
        var saved = 0L
        listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir).forEach { dir ->
            dir.listFiles()?.forEach { child ->
                val size = if (child.isFile) child.length() else dirSize(child)
                if (remove(context, child, trash = null, category = "Önbellek")) {
                    count++
                    saved += size
                }
            }
        }
        return Result(count, saved)
    }

    private fun removeFile(
        context: Context,
        file: File,
        trash: RecycleBinRepository?,
        category: String
    ): Boolean {
        if (!file.isFile) return false
        if (trash != null) return trash.moveToTrash(file, category) && !file.exists()
        runCatching { if (file.delete() && !file.exists()) return true }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { java.nio.file.Files.deleteIfExists(file.toPath()) }
        }
        return (!file.exists()) || (deleteViaMediaStore(context, file) && !file.exists())
    }

    private fun removeDirectory(
        context: Context,
        dir: File,
        trash: RecycleBinRepository?,
        category: String
    ): Boolean {
        dir.listFiles()?.forEach { child ->
            remove(context, child, trash, category)
        }
        runCatching {
            dir.setWritable(true)
            dir.delete()
        }
        val leftover = dir.listFiles()
        return !dir.exists() || leftover?.isEmpty() == true
    }

    private fun deleteViaMediaStore(context: Context, file: File): Boolean {
        return try {
            val path = file.absolutePath
            val cr = context.contentResolver
            val uris = listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Files.getContentUri("external")
            )
            var removed = false
            for (uri in uris) {
                val n = cr.delete(uri, "${MediaStore.MediaColumns.DATA}=?", arrayOf(path))
                if (n > 0) removed = true
            }
            removed && !file.exists()
        } catch (_: Exception) {
            false
        }
    }

    private fun isOwnAppFile(context: Context, file: File): Boolean {
        val path = file.absolutePath
        return listOfNotNull(
            context.cacheDir?.absolutePath,
            context.codeCacheDir?.absolutePath,
            context.externalCacheDir?.absolutePath,
            context.filesDir?.absolutePath,
            context.getExternalFilesDir(null)?.absolutePath
        ).any { path.startsWith(it) }
    }

    fun isOtherAppSandboxPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.contains("/Android/data/", ignoreCase = true) ||
            normalized.contains("/Android/obb/", ignoreCase = true)
    }

    fun dirSizePublic(dir: File): Long = dirSize(dir)

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        try {
            dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
        } catch (_: Exception) {
        }
        return size
    }
}
