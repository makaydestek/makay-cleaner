package com.makay.cleaner.util

import android.content.Context
import android.os.Environment
import com.makay.cleaner.data.CleaningRecorder
import com.makay.cleaner.data.RecycleBinRepository
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
        val own = FilePurger.wipeOwnCaches(context)
        var deleted = own.filesDeleted
        var saved = own.spaceSaved

        val ext = Environment.getExternalStorageDirectory()
        val thumbs = listOf(
            File(ext, "DCIM/.thumbnails"),
            File(ext, "Pictures/.thumbnails"),
            File(ext, ".thumbnails")
        )
        thumbs.forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val size = if (file.isFile) file.length() else 0L
                if (FilePurger.remove(context, file, trash = RecycleBinRepository(context), category = "Küçük resim") && size > 0L) {
                    deleted++
                    saved += size
                }
            }
        }

        if (saved > 0 || deleted > 0) {
            CleaningRecorder.record(context, "Hızlı Temizlik", saved, deleted)
        }

        return CleanResult(deleted, saved)
    }
}
