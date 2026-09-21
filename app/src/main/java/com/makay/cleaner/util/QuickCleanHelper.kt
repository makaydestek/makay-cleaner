package com.makay.cleaner.util

import android.content.Context
import android.os.Environment
import com.makay.cleaner.data.CleaningRecorder
import java.io.File

/**
 * Hızlı temizlik: kendi önbellek + erişilebilir temp / thumbnail klasörleri.
 */
object QuickCleanHelper {

    data class CleanResult(
        val filesDeleted: Int,
        val spaceSaved: Long
    )

    fun cleanAppCaches(context: Context): CleanResult {
        var deleted = 0
        var saved = 0L

        fun deleteContents(dir: File?) {
            if (dir == null || !dir.exists()) return
            if (!SystemFileGuard.canDelete(dir) && dir != context.cacheDir &&
                dir != context.codeCacheDir && dir != context.externalCacheDir
            ) {
                // Kendi cache klasörlerimize her zaman izin ver
                if (!isOwnCache(context, dir)) return
            }
            dir.listFiles()?.forEach { file ->
                try {
                    if (file.isDirectory) {
                        if (!SystemFileGuard.canDelete(file) && !isOwnCache(context, file)) return@forEach
                        deleteContents(file)
                        if (file.listFiles()?.isEmpty() != false) {
                            try {
                                file.delete()
                            } catch (_: Exception) {
                            }
                        }
                    } else if (file.isFile) {
                        if (!SystemFileGuard.canDelete(file) && !isOwnCache(context, file.parentFile)) {
                            return@forEach
                        }
                        val size = file.length()
                        if (file.delete()) {
                            deleted++
                            saved += size
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Kendi uygulama önbelleği (her zaman)
        deleteContents(context.cacheDir)
        deleteContents(context.codeCacheDir)
        deleteContents(context.externalCacheDir)

        // Yaygın thumbnail / tmp (izin varsa)
        val ext = Environment.getExternalStorageDirectory()
        listOf(
            File(ext, "DCIM/.thumbnails"),
            File(ext, "Pictures/.thumbnails"),
            File(ext, ".thumbnails"),
            File(context.cacheDir, "image_manager_disk_cache"),
            File(context.cacheDir, "CoilDiskCache")
        ).forEach { deleteContents(it) }

        if (saved > 0 || deleted > 0) {
            CleaningRecorder.record(context, "Hızlı Temizlik", saved, deleted)
        }

        return CleanResult(deleted, saved)
    }

    private fun isOwnCache(context: Context, dir: File?): Boolean {
        if (dir == null) return false
        val path = dir.absolutePath
        val own = listOfNotNull(
            context.cacheDir?.absolutePath,
            context.codeCacheDir?.absolutePath,
            context.externalCacheDir?.absolutePath,
            context.filesDir?.absolutePath
        )
        return own.any { path.startsWith(it) }
    }
}
