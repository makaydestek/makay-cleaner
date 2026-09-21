package com.makay.cleaner.scanner

import android.os.Environment
import com.makay.cleaner.util.SystemFileGuard
import java.io.File

class SystemCleanerScanner {

    data class JunkItem(
        val file: File,
        val reason: String,
        val sizeBytes: Long
    )

    fun scan(maxItems: Int = 2000): List<JunkItem> {
        val results = mutableListOf<JunkItem>()
        val ext = Environment.getExternalStorageDirectory() ?: return emptyList()
        val roots = mutableListOf(
            File(ext, "Download"),
            File(ext, "DCIM/.thumbnails"),
            File(ext, "Pictures/.thumbnails"),
            File(ext, ".thumbnails")
        )
        // Android/data yalnızca tüm dosya erişimi varsa (aksi halde silinemez)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
        ) {
            roots.add(File(ext, "Android/data"))
        }

        roots.forEach { root ->
            if (!root.exists()) return@forEach
            walk(root, results, maxItems)
        }

        // Boş klasörler (Download altında)
        findEmptyDirs(File(ext, "Download"), results, maxItems)
        return results.distinctBy { it.file.absolutePath }
            .filter { it.file.canWrite() || it.file.parentFile?.canWrite() == true || it.sizeBytes == 0L }
            .sortedByDescending { it.sizeBytes }
    }

    private fun walk(dir: File, out: MutableList<JunkItem>, max: Int) {
        if (out.size >= max) return
        if (SystemFileGuard.isProtectedPath(dir.absolutePath)) return
        val children = dir.listFiles() ?: return
        children.forEach { child ->
            if (out.size >= max) return
            if (child.isDirectory) {
                // Android/data altında yalnızca cache/temp benzeri
                if (dir.absolutePath.contains("/Android/data", ignoreCase = true)) {
                    val name = child.name.lowercase()
                    if (name == "cache" || name == "code_cache" || name.contains("temp") || name.contains("tmp")) {
                        collectFiles(child, "Önbellek / geçici", out, max)
                    } else {
                        walk(child, out, max)
                    }
                } else {
                    walk(child, out, max)
                }
            } else {
                val reason = classify(child) ?: return@forEach
                if (!SystemFileGuard.canDelete(child)) {
                    SystemFileGuard.recordSkipped()
                    return@forEach
                }
                out.add(JunkItem(child, reason, child.length()))
            }
        }
    }

    private fun collectFiles(dir: File, reason: String, out: MutableList<JunkItem>, max: Int) {
        if (out.size >= max) return
        if (SystemFileGuard.isProtectedPath(dir.absolutePath)) return
        dir.listFiles()?.forEach { child ->
            if (out.size >= max) return
            if (child.isDirectory) collectFiles(child, reason, out, max)
            else if (SystemFileGuard.canDelete(child)) {
                out.add(JunkItem(child, reason, child.length()))
            } else SystemFileGuard.recordSkipped()
        }
    }

    private fun findEmptyDirs(dir: File, out: MutableList<JunkItem>, max: Int) {
        if (out.size >= max || !dir.exists()) return
        if (SystemFileGuard.isProtectedPath(dir.absolutePath)) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                findEmptyDirs(child, out, max)
                val kids = child.listFiles()
                if (kids != null && kids.isEmpty() && SystemFileGuard.canDelete(child)) {
                    out.add(JunkItem(child, "Boş klasör", 0))
                }
            }
        }
    }

    private fun classify(file: File): String? {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".tmp") || name.endsWith(".temp") -> "Geçici dosya"
            name.endsWith(".log") && file.length() > 100_000 -> "Log dosyası"
            name.endsWith(".apk") && file.lastModified() < System.currentTimeMillis() - 7L * 24 * 3600_000 -> "Eski APK"
            name.contains("thumb") || file.parent?.contains("thumbnail", true) == true -> "Küçük resim"
            name.endsWith(".nomedia") -> null
            else -> null
        }
    }
}
