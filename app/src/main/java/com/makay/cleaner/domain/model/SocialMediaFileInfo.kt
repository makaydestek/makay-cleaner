package com.makay.cleaner.domain.model

data class SocialMediaFileInfo(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val category: SocialMediaCategory,
    val app: SocialMediaApp,
    val dateAdded: Long,
    val dateFormatted: String,
    val groupName: String? = null
)

enum class SocialMediaApp(val displayName: String, val color: Int) {
    WHATSAPP("WhatsApp", 0xFF25D366.toInt()),
    TELEGRAM("Telegram", 0xFF0088CC.toInt()),
    INSTAGRAM("Instagram", 0xFFE1306C.toInt()),
    FACEBOOK("Facebook", 0xFF1877F2.toInt()),
    OTHER("Diğer", 0xFF757575.toInt())
}

enum class SocialMediaCategory(val displayName: String, val icon: String) {
    IMAGE("Resimler", "🖼️"),
    VIDEO("Videolar", "🎬"),
    AUDIO("Ses Kayıtları", "🎵"),
    DOCUMENT("Belgeler", "📄"),
    ANIMATED_IMAGE("GIF/Sticker", "🎭"),
    VOICE_MESSAGE("Ses Mesajı", ""),
    PROFILE_PHOTO("Profil Fotoğrafı", "👤")
}