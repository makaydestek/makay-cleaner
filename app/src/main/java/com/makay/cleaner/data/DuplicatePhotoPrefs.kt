package com.makay.cleaner.data

import android.content.Context
import com.makay.cleaner.scanner.SelectionAssistant

/**
 * Yinelenen foto/video tarama tercihleri — kullanıcı güveni için ayarlar kalıcı tutulur.
 */
class DuplicatePhotoPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var scanMode: String
        get() = prefs.getString(KEY_MODE, MODE_FULL) ?: MODE_FULL
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    /** photo | video */
    var mediaKind: String
        get() = prefs.getString(KEY_MEDIA_KIND, KIND_PHOTO) ?: KIND_PHOTO
        set(value) = prefs.edit().putString(KEY_MEDIA_KIND, value).apply()

    /** 0=düşük(geniş), 1=orta, 2=yüksek(dar) */
    var matchingLevel: Int
        get() = prefs.getInt(KEY_MATCH, 1).coerceIn(0, 2)
        set(value) = prefs.edit().putInt(KEY_MATCH, value.coerceIn(0, 2)).apply()

    /** Benzer fotoğraf için max zaman farkı (saniye). 0 = kapalı */
    var timeIntervalSec: Int
        get() = prefs.getInt(KEY_TIME, 30).coerceIn(0, 86_400)
        set(value) = prefs.edit().putInt(KEY_TIME, value.coerceIn(0, 86_400)).apply()

    /** GPS yarıçapı (metre). 0 = kapalı */
    var gpsRadiusMeters: Int
        get() = prefs.getInt(KEY_GPS, 0).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_GPS, value.coerceIn(0, 100)).apply()

    var includeRotatedFlipped: Boolean
        get() = prefs.getBoolean(KEY_ROTATE, true)
        set(value) = prefs.edit().putBoolean(KEY_ROTATE, value).apply()

    var selectedBucket: String?
        get() = prefs.getString(KEY_BUCKET, null)
        set(value) = prefs.edit().putString(KEY_BUCKET, value).apply()

    var excludedBuckets: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDE, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDE, value).apply()

    /** Auto-mark bu klasörlere dokunmaz */
    var protectedBuckets: Set<String>
        get() = prefs.getStringSet(KEY_PROTECT, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PROTECT, value).apply()

    /** Klasör önceliği (önce gelen tutulur) */
    var folderPriorityCsv: String
        get() = prefs.getString(KEY_FOLDER_PRIO, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FOLDER_PRIO, value).apply()

    val folderPriority: List<String>
        get() = folderPriorityCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    var selectionRulesCsv: String
        get() = prefs.getString(KEY_RULES, SelectionAssistant.serializeRules(SelectionAssistant.defaultOrder))
            ?: SelectionAssistant.serializeRules(SelectionAssistant.defaultOrder)
        set(value) = prefs.edit().putString(KEY_RULES, value).apply()

    val selectionRules: List<SelectionAssistant.Rule>
        get() = SelectionAssistant.parseRules(selectionRulesCsv)

    /**
     * İzin verilen uzantılar (küçük harf). Boş set = tüm varsayılan formatlar.
     */
    var allowedFormats: Set<String>
        get() {
            val raw = prefs.getStringSet(KEY_FORMATS, null)
            return if (raw.isNullOrEmpty()) DEFAULT_FORMATS else raw.map { it.lowercase() }.toSet()
        }
        set(value) = prefs.edit().putStringSet(KEY_FORMATS, value.map { it.lowercase() }.toSet()).apply()

    companion object {
        private const val PREFS = "dup_photo_prefs"
        private const val KEY_MODE = "scan_mode"
        private const val KEY_MEDIA_KIND = "media_kind"
        private const val KEY_MATCH = "matching_level"
        private const val KEY_TIME = "time_interval_sec"
        private const val KEY_GPS = "gps_radius_m"
        private const val KEY_ROTATE = "include_rotated"
        private const val KEY_BUCKET = "selected_bucket"
        private const val KEY_EXCLUDE = "excluded_buckets"
        private const val KEY_PROTECT = "protected_buckets"
        private const val KEY_FOLDER_PRIO = "folder_priority"
        private const val KEY_RULES = "selection_rules"
        private const val KEY_FORMATS = "allowed_formats"

        const val MODE_QUICK = "quick"
        const val MODE_CAMERA = "camera"
        const val MODE_FOLDER = "folder"
        const val MODE_FULL = "full"

        const val KIND_PHOTO = "photo"
        const val KIND_VIDEO = "video"

        val DEFAULT_FORMATS = setOf(
            "jpg", "jpeg", "png", "webp", "heic", "heif", "gif", "bmp",
            "dng", "raw", "cr2", "nef", "arw", "orf", "rw2", "tif", "tiff"
        )

        val ALL_FORMAT_OPTIONS = listOf(
            "jpg" to "JPEG",
            "png" to "PNG",
            "webp" to "WebP",
            "heic" to "HEIC",
            "heif" to "HEIF",
            "gif" to "GIF",
            "bmp" to "BMP",
            "dng" to "DNG",
            "raw" to "RAW",
            "cr2" to "CR2",
            "nef" to "NEF",
            "arw" to "ARW",
            "tif" to "TIFF"
        )

        val VIDEO_FORMATS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
    }
}
