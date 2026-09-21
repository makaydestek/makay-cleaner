package com.makay.cleaner.scanner

import android.os.Environment
import com.makay.cleaner.util.SystemFileGuard
import java.io.File

data class ApkRemnant(
    val file: File,
    val sizeBytes: Long,
    val ageDays: Long
)

/**
 * Download ve yaygın klasörlerde eski / yinelenen APK kalıntıları.
 */
class ApkRemnantScanner {

    fun scan(minAgeDays: Int = 7, limit: Int = 200): List<ApkRemnant> {
        val roots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "apk"),
            File(Environment.getExternalStorageDirectory(), "APKs")
        )
        val cutoff = System.currentTimeMillis() - minAgeDays * 24L * 3600_000
        val now = System.currentTimeMillis()
        val out = mutableListOf<ApkRemnant>()
        roots.distinctBy { it.absolutePath }.forEach { root ->
            if (!root.exists()) return@forEach
            walk(root, 4) { file ->
                if (out.size >= limit) return@walk
                if (!file.isFile) return@walk
                if (!file.extension.equals("apk", true)) return@walk
                if (!SystemFileGuard.canDelete(file)) {
                    SystemFileGuard.recordSkipped()
                    return@walk
                }
                if (file.lastModified() >= cutoff) return@walk
                val age = ((now - file.lastModified()) / (24L * 3600_000)).coerceAtLeast(0)
                out.add(ApkRemnant(file, file.length(), age))
            }
        }
        return out.sortedByDescending { it.sizeBytes }
    }

    private fun walk(dir: File, depth: Int, onFile: (File) -> Unit) {
        if (depth < 0 || SystemFileGuard.isProtectedPath(dir.absolutePath)) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) walk(child, depth - 1, onFile) else onFile(child)
        }
    }
}
