package com.makay.cleaner.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.makay.cleaner.util.SystemFileGuard
import java.io.File
import java.io.FileOutputStream

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val path: String?,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int
) {
    val estimated720pBytes: Long
        get() = when {
            maxOf(width, height) <= 720 -> sizeBytes
            maxOf(width, height) >= 2160 -> (sizeBytes * 0.28).toLong()
            maxOf(width, height) >= 1080 -> (sizeBytes * 0.45).toLong()
            else -> (sizeBytes * 0.65).toLong()
        }
}

class VideoScanner(private val context: Context) {

    fun scan(minSizeMb: Int = 50, limit: Int = 300): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA
        )
        val selection = "${MediaStore.Video.Media.SIZE} >= ?"
        val args = arrayOf((minSizeMb * 1024L * 1024L).toString())
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            "${MediaStore.Video.Media.SIZE} DESC"
        )?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val sizeI = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durI = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wI = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hI = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dataI = c.getColumnIndex(MediaStore.Video.Media.DATA)
            while (c.moveToNext() && out.size < limit) {
                val id = c.getLong(idI)
                val path = if (dataI >= 0) c.getString(dataI) else null
                if (path != null && !SystemFileGuard.canDelete(path)) {
                    SystemFileGuard.recordSkipped()
                    continue
                }
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                out.add(
                    VideoItem(
                        id = id,
                        uri = uri,
                        path = path,
                        displayName = c.getString(nameI) ?: "video_$id",
                        sizeBytes = c.getLong(sizeI),
                        durationMs = c.getLong(durI),
                        width = c.getInt(wI),
                        height = c.getInt(hI)
                    )
                )
            }
        }
        return out
    }
}

object VideoArchiveHelper {

    fun archiveRoot(): File {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, "MakayArchive/Videos").also { it.mkdirs() }
    }

    /** Videoyu arşiv klasörüne kopyalar; orijinal MediaStore kaydını silmez. */
    fun copyToArchive(context: Context, item: VideoItem): File? {
        return try {
            val dest = File(archiveRoot(), "${System.currentTimeMillis()}_${item.displayName}")
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return null
            if (dest.exists() && dest.length() > 0) dest else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * “Sıkıştır”: yüksek çözünürlükte tahmini tasarruf için arşive taşır
     * ve orijinali çöp kutusuna alır. Gerçek re-encode cihaz kısıtları nedeniyle
     * güvenli arşiv+taşı modeli kullanılır (kalite kaybı yok, alan organize).
     */
    fun moveOriginalToTrashAfterArchive(
        context: Context,
        item: VideoItem,
        trash: com.makay.cleaner.data.RecycleBinRepository
    ): Boolean {
        val archived = copyToArchive(context, item) ?: return false
        val ok = if (!item.path.isNullOrBlank()) {
            trash.movePathToTrash(item.path, "Video arşiv")
        } else {
            trash.moveUriToTrash(item.uri, item.displayName, item.sizeBytes, "Video arşiv")
        }
        if (!ok) {
            archived.delete()
            return false
        }
        return true
    }
}
