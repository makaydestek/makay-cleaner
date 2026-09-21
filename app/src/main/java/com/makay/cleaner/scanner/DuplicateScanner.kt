package com.makay.cleaner.scanner

import android.content.Context
import android.os.Environment
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class DuplicateScanner(private val context: Context) {

    data class DuplicateGroup(
        val hash: String,
        val sizeBytes: Long,
        val files: List<File>
    )

    fun scan(
        roots: List<File> = defaultRoots(),
        minSizeBytes: Long = 50 * 1024L,
        maxFiles: Int = 4000
    ): List<DuplicateGroup> {
        val bySize = LinkedHashMap<Long, MutableList<File>>()
        var scanned = 0
        roots.forEach { root ->
            if (!root.exists()) return@forEach
            walk(root) { file ->
                if (scanned >= maxFiles) return@walk
                if (!file.isFile || file.length() < minSizeBytes) return@walk
                if (!SystemFileGuard.canDelete(file)) {
                    SystemFileGuard.recordSkipped()
                    return@walk
                }
                scanned++
                bySize.getOrPut(file.length()) { mutableListOf() }.add(file)
            }
        }

        val groups = mutableListOf<DuplicateGroup>()
        bySize.values.filter { it.size > 1 }.forEach { sameSize ->
            val byHash = LinkedHashMap<String, MutableList<File>>()
            sameSize.forEach { file ->
                val hash = md5Partial(file) ?: return@forEach
                byHash.getOrPut(hash) { mutableListOf() }.add(file)
            }
            byHash.forEach { (hash, files) ->
                if (files.size > 1) {
                    groups.add(DuplicateGroup(hash, files.first().length(), files))
                }
            }
        }
        return groups.sortedByDescending { it.sizeBytes * (it.files.size - 1) }
    }

    private fun defaultRoots(): List<File> {
        val ext = Environment.getExternalStorageDirectory()
        return listOf(
            File(ext, "Download"),
            File(ext, "DCIM"),
            File(ext, "Pictures"),
            File(ext, "Documents"),
            context.getExternalFilesDir(null)?.parentFile?.parentFile // Android/data sibling skip
        ).filterNotNull().filter { it.exists() }
    }

    private fun walk(dir: File, onFile: (File) -> Unit) {
        if (SystemFileGuard.isProtectedPath(dir.absolutePath)) return
        val children = dir.listFiles() ?: return
        children.forEach { child ->
            if (child.isDirectory) walk(child, onFile) else onFile(child)
        }
    }

    /** Hızlı kısmi MD5: ilk + orta + son 8KB */
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
}
