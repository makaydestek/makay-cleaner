package com.makay.cleaner.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import com.makay.cleaner.util.SystemFileGuard
import java.io.File

/**
 * Kaldırılmış uygulamaların Android/data|obb|media kalıntılarını bulur.
 */
class CorpseFinder(private val context: Context) {

    data class Corpse(
        val packageName: String,
        val path: String,
        val sizeBytes: Long,
        val fileCount: Int
    )

    fun scan(): List<Corpse> {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toHashSet()

        val ext = Environment.getExternalStorageDirectory() ?: return emptyList()
        val bases = listOf(
            File(ext, "Android/data"),
            File(ext, "Android/obb"),
            File(ext, "Android/media")
        )

        val corpses = mutableListOf<Corpse>()
        bases.forEach { base ->
            if (!base.exists()) return@forEach
            base.listFiles()?.forEach { dir ->
                if (!dir.isDirectory) return@forEach
                val pkg = dir.name
                if (installed.contains(pkg)) return@forEach
                if (SystemFileGuard.isProtectedPackageName(pkg)) return@forEach
                if (SystemFileGuard.isProtectedPath(dir.absolutePath)) return@forEach
                val (size, count) = measure(dir)
                if (count > 0 || size > 0) {
                    corpses.add(Corpse(pkg, dir.absolutePath, size, count))
                }
            }
        }
        return corpses.sortedByDescending { it.sizeBytes }
    }

    fun deleteCorpse(corpse: Corpse): Pair<Long, Int> {
        if (!SystemFileGuard.canDelete(corpse.path)) {
            SystemFileGuard.recordSkipped()
            return 0L to 0
        }
        val root = File(corpse.path)
        return deleteTree(root)
    }

    private fun measure(file: File): Pair<Long, Int> {
        if (!file.exists()) return 0L to 0
        if (file.isFile) return file.length() to 1
        var size = 0L
        var count = 0
        file.walkTopDown().forEach {
            if (it.isFile) {
                size += it.length()
                count++
            }
        }
        return size to count
    }

    private fun deleteTree(file: File): Pair<Long, Int> {
        if (!file.exists()) return 0L to 0
        if (!SystemFileGuard.canDelete(file)) {
            SystemFileGuard.recordSkipped()
            return 0L to 0
        }
        var saved = 0L
        var count = 0
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                val (s, c) = deleteTree(child)
                saved += s
                count += c
            }
        } else {
            saved = file.length()
        }
        if (file.delete() && file.isFile) count++
        else if (!file.exists() && !file.isDirectory) { /* ok */ }
        return saved to count
    }
}
