package com.makay.cleaner.data

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.makay.cleaner.util.SystemFileGuard
import java.io.File

class MediaHubRepository(private val context: Context) {

    data class MediaBucket(
        val id: String,
        val title: String,
        val description: String,
        val fileCount: Int,
        val totalBytes: Long,
        val samplePaths: List<String>
    )

    fun scanBuckets(): List<MediaBucket> {
        val screenshots = queryImages(
            selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            args = arrayOf("%screenshot%", "%Screenshots%")
        )
        val camera = queryImages(
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
            args = arrayOf("%DCIM/Camera%")
        )
        val blurryish = queryImages(null, null)
            .filter { it.second < 80_000 } // küçük / düşük kalite adayı
            .take(200)

        val googlePhotos = queryImages(
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?",
            args = arrayOf("%Google%/Photos%", "%Google Photos%")
        )
        val backupCandidates = queryImages(null, null)
            .filter { (_, size) -> size > 2L * 1024 * 1024 }
            .filter { (path, _) ->
                val age = System.currentTimeMillis() - File(path).lastModified()
                age > 90L * 24 * 3600_000
            }
            .take(300)

        val downloadsMedia = File(Environment.getExternalStorageDirectory(), "Download")
            .walkTopDown()
            .maxDepth(3)
            .filter { it.isFile && isMedia(it) && SystemFileGuard.canDelete(it) }
            .toList()

        return listOf(
            MediaBucket(
                "screenshots",
                "Ekran görüntüleri",
                "Screenshots klasörü ve isim eşleşmeleri",
                screenshots.size,
                screenshots.sumOf { it.second },
                screenshots.take(5).map { it.first }
            ),
            MediaBucket(
                "camera",
                "Kamera fotoğrafları",
                "DCIM/Camera",
                camera.size,
                camera.sumOf { it.second },
                camera.take(5).map { it.first }
            ),
            MediaBucket(
                "google_photos_local",
                "Google Photos yerel kopya",
                "Cihazda Google Photos klasörü — bulut yedeğini uygulamadan doğrulayın",
                googlePhotos.size,
                googlePhotos.sumOf { it.second },
                googlePhotos.take(5).map { it.first }
            ),
            MediaBucket(
                "backup_candidates",
                "Yedek adayı (eski + büyük)",
                "90+ gün ve 2 MB üzeri — silmeden önce bulutta olduğunu kontrol edin",
                backupCandidates.size,
                backupCandidates.sumOf { it.second },
                backupCandidates.take(5).map { it.first }
            ),
            MediaBucket(
                "low_quality",
                "Düşük boyutlu görseller",
                "80 KB altı — meme / sıkıştırılmış adaylar",
                blurryish.size,
                blurryish.sumOf { it.second },
                blurryish.take(5).map { it.first }
            ),
            MediaBucket(
                "downloads_media",
                "İndirilen medya",
                "Download klasöründeki görsel/video",
                downloadsMedia.size,
                downloadsMedia.sumOf { it.length() },
                downloadsMedia.take(5).map { it.absolutePath }
            )
        ).filter { it.fileCount > 0 }
    }

    fun pathsForBucket(id: String): List<String> {
        return when (id) {
            "screenshots" -> queryImages(
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("%screenshot%", "%Screenshots%")
            ).map { it.first }
            "camera" -> queryImages(
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("%DCIM/Camera%")
            ).map { it.first }
            "google_photos_local" -> queryImages(
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DATA} LIKE ?",
                arrayOf("%Google%/Photos%", "%Google Photos%")
            ).map { it.first }
            "backup_candidates" -> queryImages(null, null)
                .filter { it.second > 2L * 1024 * 1024 }
                .filter { System.currentTimeMillis() - File(it.first).lastModified() > 90L * 24 * 3600_000 }
                .map { it.first }
            "low_quality" -> queryImages(null, null)
                .filter { it.second < 80_000 }
                .map { it.first }
            "downloads_media" -> File(Environment.getExternalStorageDirectory(), "Download")
                .walkTopDown().maxDepth(3)
                .filter { it.isFile && isMedia(it) && SystemFileGuard.canDelete(it) }
                .map { it.absolutePath }
                .toList()
            else -> emptyList()
        }
    }

    private fun queryImages(selection: String?, args: Array<String>?): List<Pair<String, Long>> {
        val out = mutableListOf<Pair<String, Long>>()
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE
        )
        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { c ->
                val di = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val si = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                while (c.moveToNext()) {
                    val path = c.getString(di) ?: continue
                    if (!SystemFileGuard.canDelete(path)) {
                        SystemFileGuard.recordSkipped()
                        continue
                    }
                    out.add(path to c.getLong(si))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun isMedia(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
            n.endsWith(".webp") || n.endsWith(".mp4") || n.endsWith(".mkv")
    }
}
