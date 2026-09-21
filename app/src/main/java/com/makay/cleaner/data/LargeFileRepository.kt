package com.makay.cleaner.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.makay.cleaner.domain.model.FileCategory
import com.makay.cleaner.domain.model.LargeFileInfo
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LargeFileRepository(private val context: Context) {

    private val decimalFormat = DecimalFormat("#.##")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    /**
     * Belirtilen boyutun üzerindeki dosyaları tarar (MediaStore API)
     * @param minSizeBytes Minimum boyut (byte cinsinden) - varsayılan 50 MB
     */
    fun getLargeFiles(minSizeBytes: Long = 50 * 1024 * 1024): List<LargeFileInfo> {
        val largeFiles = mutableListOf<LargeFileInfo>()

        // Video dosyaları
        largeFiles.addAll(scanMediaStore(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            minSizeBytes,
            FileCategory.VIDEO
        ))

        // Resim dosyaları
        largeFiles.addAll(scanMediaStore(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            minSizeBytes,
            FileCategory.IMAGE
        ))

        // Ses dosyaları
        largeFiles.addAll(scanMediaStore(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            minSizeBytes,
            FileCategory.AUDIO
        ))

        // Belge dosyaları (Downloads, Documents vb.)
        largeFiles.addAll(scanDocuments(minSizeBytes))

        // Eski APK dosyaları
        largeFiles.addAll(scanApkFiles(minSizeBytes))

        return largeFiles
            .filter { com.makay.cleaner.util.SystemFileGuard.canDelete(it.filePath) }
            .distinctBy { it.filePath }
            .sortedByDescending { it.sizeBytes }
    }

    private fun scanApkFiles(minSizeBytes: Long): List<LargeFileInfo> {
        val files = mutableListOf<LargeFileInfo>()
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads.exists()) {
                downloads.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.equals("apk", true) && file.length() >= minSizeBytes) {
                        files.add(
                            LargeFileInfo(
                                id = file.hashCode().toLong(),
                                displayName = file.name,
                                filePath = file.absolutePath,
                                sizeBytes = file.length(),
                                sizeFormatted = formatBytes(file.length()),
                                category = FileCategory.OTHER,
                                mimeType = "application/vnd.android.package-archive",
                                dateAdded = file.lastModified() / 1000,
                                dateFormatted = dateFormat.format(Date(file.lastModified()))
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }
        return files
    }

    /**
     * MediaStore'dan medya dosyalarını tarar
     */
    private fun scanMediaStore(
        uri: Uri,
        minSizeBytes: Long,
        category: FileCategory
    ): List<LargeFileInfo> {
        val files = mutableListOf<LargeFileInfo>()

        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATA
            )

            val selection = "${MediaStore.MediaColumns.SIZE} >= ?"
            val selectionArgs = arrayOf(minSizeBytes.toString())

            val sortOrder = "${MediaStore.MediaColumns.SIZE} DESC"

            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Bilinmeyen"
                        val size = cursor.getLong(sizeColumn)
                        val mimeType = cursor.getString(mimeColumn) ?: "application/octet-stream"
                        val dateAdded = cursor.getLong(dateColumn)
                        val dataPath = cursor.getString(dataColumn)

                        val filePath = if (dataPath != null) {
                            dataPath
                        } else {
                            // Android 10+ için URI'dan yol oluştur
                            val contentUri = ContentUris.withAppendedId(uri, id)
                            contentUri.toString()
                        }

                        files.add(
                            LargeFileInfo(
                                id = id,
                                displayName = name,
                                filePath = filePath,
                                sizeBytes = size,
                                sizeFormatted = formatBytes(size),
                                category = category,
                                mimeType = mimeType,
                                dateAdded = dateAdded,
                                dateFormatted = dateFormat.format(Date(dateAdded * 1000))
                            )
                        )
                    } catch (e: Exception) {
                        // Satır okuma hatası, atla
                    }
                }
            }
        } catch (e: SecurityException) {
            // İzin hatası
        } catch (e: Exception) {
            // Genel hata
        }

        return files
    }

    /**
     * Belge dosyalarını tarar (Downloads, Documents vb.)
     */
    private fun scanDocuments(minSizeBytes: Long): List<LargeFileInfo> {
        val files = mutableListOf<LargeFileInfo>()

        val documentDirs = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_DCIM
        )

        documentDirs.forEach { dirName ->
            try {
                val dir = Environment.getExternalStoragePublicDirectory(dirName)
                if (dir.exists() && dir.isDirectory) {
                    scanDirectoryRecursive(dir, minSizeBytes, files)
                }
            } catch (e: SecurityException) {
                // İzin hatası
            } catch (e: Exception) {
                // Genel hata
            }
        }

        return files
    }

    /**
     * Klasörü rekürsif olarak tarar
     */
    private fun scanDirectoryRecursive(
        directory: File,
        minSizeBytes: Long,
        files: MutableList<LargeFileInfo>
    ) {
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.length() >= minSizeBytes) {
                    val category = categorizeByExtension(file.extension)
                    files.add(
                        LargeFileInfo(
                            id = file.hashCode().toLong(),
                            displayName = file.name,
                            filePath = file.absolutePath,
                            sizeBytes = file.length(),
                            sizeFormatted = formatBytes(file.length()),
                            category = category,
                            mimeType = getMimeType(file.extension),
                            dateAdded = file.lastModified() / 1000,
                            dateFormatted = dateFormat.format(Date(file.lastModified()))
                        )
                    )
                } else if (file.isDirectory) {
                    scanDirectoryRecursive(file, minSizeBytes, files)
                }
            }
        } catch (e: SecurityException) {
            // İzin hatası
        }
    }

    /**
     * Dosya uzantısına göre kategori belirler
     */
    private fun categorizeByExtension(extension: String): FileCategory {
        return when (extension.lowercase()) {
            in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp") -> FileCategory.VIDEO
            in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic") -> FileCategory.IMAGE
            in listOf("mp3", "wav", "flac", "aac", "ogg", "wma", "m4a") -> FileCategory.AUDIO
            in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar") -> FileCategory.DOCUMENT
            else -> FileCategory.OTHER
        }
    }

    /**
     * Dosya uzantısına göre MIME type belirler
     */
    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            in listOf("mp4", "avi", "mkv") -> "video/*"
            in listOf("jpg", "jpeg", "png") -> "image/*"
            in listOf("mp3", "wav", "flac") -> "audio/*"
            in listOf("pdf", "doc", "docx") -> "application/*"
            else -> "application/octet-stream"
        }
    }

    /**
     * Byte cinsinden boyutu okunabilir formata çevirir
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${decimalFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${decimalFormat.format(bytes / (1024.0 * 1024.0))} MB"
            else -> "${decimalFormat.format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    /**
     * Belirli bir dosyayı siler
     */
    fun deleteFile(fileInfo: LargeFileInfo): Boolean {
        return try {
            if (!com.makay.cleaner.util.SystemFileGuard.canDelete(fileInfo.filePath)) {
                return false
            }
            if (fileInfo.filePath.startsWith("content://")) {
                val uri = Uri.parse(fileInfo.filePath)
                context.contentResolver.delete(uri, null, null) > 0
            } else {
                val file = File(fileInfo.filePath)
                if (file.exists()) {
                    file.delete()
                } else {
                    false
                }
            }
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}