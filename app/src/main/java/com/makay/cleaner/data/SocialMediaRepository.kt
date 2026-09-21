package com.makay.cleaner.data

import android.content.Context
import android.os.Environment
import com.makay.cleaner.domain.model.SocialMediaApp
import com.makay.cleaner.domain.model.SocialMediaCategory
import com.makay.cleaner.domain.model.SocialMediaFileInfo
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SocialMediaRepository(private val context: Context) {

    private val decimalFormat = DecimalFormat("#.##")
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    /**
     * Tüm sosyal medya dosyalarını tarar
     */
    fun getAllSocialMediaFiles(): List<SocialMediaFileInfo> {
        val files = mutableListOf<SocialMediaFileInfo>()

        // WhatsApp dosyalarını tara
        files.addAll(scanWhatsAppFiles())

        // Telegram dosyalarını tara
        files.addAll(scanTelegramFiles())

        // Instagram dosyalarını tara
        files.addAll(scanInstagramFiles())

        // Facebook dosyalarını tara
        files.addAll(scanFacebookFiles())

        return files.sortedByDescending { it.sizeBytes }
    }

    /**
     * WhatsApp dosyalarını tarar (eski + Android/media yolları)
     */
    private fun scanWhatsAppFiles(): List<SocialMediaFileInfo> {
        val files = mutableListOf<SocialMediaFileInfo>()
        val candidates = listOf(
            File(Environment.getExternalStorageDirectory(), "WhatsApp"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp/WhatsApp"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.whatsapp.w4b/WhatsApp Business")
        )

        candidates.filter { it.exists() }.forEach { whatsappDir ->
            val mediaDir = File(whatsappDir, "Media")
            if (mediaDir.exists()) {
                scanWhatsAppFolder(File(mediaDir, ".WhatsAppImages"), SocialMediaApp.WHATSAPP, SocialMediaCategory.IMAGE, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Images"), SocialMediaApp.WHATSAPP, SocialMediaCategory.IMAGE, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Video"), SocialMediaApp.WHATSAPP, SocialMediaCategory.VIDEO, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Audio"), SocialMediaApp.WHATSAPP, SocialMediaCategory.AUDIO, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Documents"), SocialMediaApp.WHATSAPP, SocialMediaCategory.DOCUMENT, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Animated Gifs"), SocialMediaApp.WHATSAPP, SocialMediaCategory.ANIMATED_IMAGE, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Voice Notes"), SocialMediaApp.WHATSAPP, SocialMediaCategory.VOICE_MESSAGE, files)
                scanWhatsAppFolder(File(mediaDir, "WhatsApp Stickers"), SocialMediaApp.WHATSAPP, SocialMediaCategory.ANIMATED_IMAGE, files)
                scanWhatsAppFolder(File(mediaDir, "Sent"), SocialMediaApp.WHATSAPP, SocialMediaCategory.IMAGE, files, groupName = "Gönderilenler")
            }
            val databasesDir = File(whatsappDir, "Databases")
            if (databasesDir.exists()) {
                scanWhatsAppFolder(databasesDir, SocialMediaApp.WHATSAPP, SocialMediaCategory.DOCUMENT, files, groupName = "Yedekler")
            }
        }

        return files
    }

    /**
     * Telegram dosyalarını tarar
     */
    private fun scanTelegramFiles(): List<SocialMediaFileInfo> {
        val files = mutableListOf<SocialMediaFileInfo>()
        val candidates = listOf(
            File(Environment.getExternalStorageDirectory(), "Telegram"),
            File(Environment.getExternalStorageDirectory(), "Android/media/org.telegram.messenger/Telegram"),
            File(Environment.getExternalStorageDirectory(), "Android/media/org.telegram.messenger.web/Telegram")
        )

        candidates.filter { it.exists() }.forEach { telegramDir ->
            scanWhatsAppFolder(File(telegramDir, "Telegram Images"), SocialMediaApp.TELEGRAM, SocialMediaCategory.IMAGE, files)
            scanWhatsAppFolder(File(telegramDir, "Telegram Video"), SocialMediaApp.TELEGRAM, SocialMediaCategory.VIDEO, files)
            scanWhatsAppFolder(File(telegramDir, "Telegram Audio"), SocialMediaApp.TELEGRAM, SocialMediaCategory.AUDIO, files)
            scanWhatsAppFolder(File(telegramDir, "Telegram Documents"), SocialMediaApp.TELEGRAM, SocialMediaCategory.DOCUMENT, files)
            scanWhatsAppFolder(File(telegramDir, "Telegram Files"), SocialMediaApp.TELEGRAM, SocialMediaCategory.DOCUMENT, files)
        }

        return files
    }

    /**
     * Instagram dosyalarını tarar
     */
    private fun scanInstagramFiles(): List<SocialMediaFileInfo> {
        val files = mutableListOf<SocialMediaFileInfo>()
        val candidates = listOf(
            File(Environment.getExternalStorageDirectory(), "Instagram"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.instagram.android")
        )
        candidates.filter { it.exists() }.forEach { dir ->
            scanWhatsAppFolder(dir, SocialMediaApp.INSTAGRAM, SocialMediaCategory.IMAGE, files)
        }
        return files
    }

    /**
     * Facebook dosyalarını tarar
     */
    private fun scanFacebookFiles(): List<SocialMediaFileInfo> {
        val files = mutableListOf<SocialMediaFileInfo>()
        val candidates = listOf(
            File(Environment.getExternalStorageDirectory(), "Facebook"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.facebook.katana"),
            File(Environment.getExternalStorageDirectory(), "Android/media/com.facebook.orca")
        )
        candidates.filter { it.exists() }.forEach { dir ->
            scanWhatsAppFolder(dir, SocialMediaApp.FACEBOOK, SocialMediaCategory.IMAGE, files)
        }
        return files
    }

    /**
     * Bir klasördeki dosyaları tarar
     */
    private fun scanWhatsAppFolder(
        directory: File,
        app: SocialMediaApp,
        defaultCategory: SocialMediaCategory,
        files: MutableList<SocialMediaFileInfo>,
        groupName: String? = null
    ) {
        if (!directory.exists() || !directory.isDirectory) return

        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.length() > 0) {
                    val category = determineCategory(file.extension, defaultCategory)
                    files.add(
                        SocialMediaFileInfo(
                            id = file.hashCode().toLong(),
                            filePath = file.absolutePath,
                            fileName = file.name,
                            sizeBytes = file.length(),
                            sizeFormatted = formatBytes(file.length()),
                            category = category,
                            app = app,
                            dateAdded = file.lastModified(),
                            dateFormatted = dateFormat.format(Date(file.lastModified())),
                            groupName = groupName
                        )
                    )
                } else if (file.isDirectory) {
                    // Alt klasörleri de tara
                    scanWhatsAppFolder(file, app, defaultCategory, files, groupName)
                }
            }
        } catch (e: SecurityException) {
            // İzin hatası
        } catch (e: Exception) {
            // Diğer hatalar
        }
    }

    /**
     * Dosya uzantısına göre kategori belirler
     */
    private fun determineCategory(extension: String, defaultCategory: SocialMediaCategory): SocialMediaCategory {
        return when (extension.lowercase()) {
            in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic") -> SocialMediaCategory.IMAGE
            in listOf("mp4", "3gp", "mkv", "avi", "mov", "webm") -> SocialMediaCategory.VIDEO
            in listOf("mp3", "wav", "aac", "ogg", "m4a", "opus") -> SocialMediaCategory.AUDIO
            in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "zip", "rar") -> SocialMediaCategory.DOCUMENT
            in listOf("gif") -> SocialMediaCategory.ANIMATED_IMAGE
            else -> defaultCategory
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
    fun deleteFile(fileInfo: SocialMediaFileInfo): Boolean {
        return try {
            if (!com.makay.cleaner.util.SystemFileGuard.canDelete(fileInfo.filePath)) {
                return false
            }
            val file = File(fileInfo.filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Uygulamaya göre dosyaları filtreler
     */
    fun getFilesByApp(files: List<SocialMediaFileInfo>, app: SocialMediaApp): List<SocialMediaFileInfo> {
        return files.filter { it.app == app }
    }

    /**
     * Kategoriye göre dosyaları filtreler
     */
    fun getFilesByCategory(files: List<SocialMediaFileInfo>, category: SocialMediaCategory): List<SocialMediaFileInfo> {
        return files.filter { it.category == category }
    }
}