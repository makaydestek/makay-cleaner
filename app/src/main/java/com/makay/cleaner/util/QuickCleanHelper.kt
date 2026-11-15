package com.makay.cleaner.util

import com.makay.cleaner.R

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
                // Küçük resimler yeniden üretilebilir önbellektir; çöpe taşımak
                // yerine kalıcı silinir ve yalnızca gerçekten yok olduysa sayılır.
                val size = if (file.isFile) file.length() else 0L
                val existed = file.exists()
                if (FilePurger.remove(context, file, trash = null, category = context.getString(R.string.reason_thumbnail)) &&
                    existed && !file.exists() && size > 0L
                ) {
                    deleted++
                    saved += size
                }
            }
        }

        if (saved > 0 || deleted > 0) {
            CleaningRecorder.record(context, context.getString(R.string.record_quick_clean), saved, deleted)
        }

        return CleanResult(deleted, saved)
    }
}
