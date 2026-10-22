package com.makay.cleaner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Temizlikten hariç tutulan yollar.
 * Sistem maddeleri bilgilendirme + koruma (kaldırılamaz).
 * Kullanıcı maddeleri isteğe bağlı eklenir / silinir.
 */
class CleanExclusionsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    enum class Source { SYSTEM, USER }

    data class ExclusionItem(
        val id: String,
        val path: String,
        val title: String,
        val reason: String,
        val source: Source
    )

    fun listAll(): List<ExclusionItem> = systemDefaults() + listUser()

    fun listUser(): List<ExclusionItem> {
        val raw = prefs.getString(KEY_USER, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        ExclusionItem(
                            id = o.getString("id"),
                            path = o.getString("path"),
                            title = o.optString("title").ifBlank { o.getString("path") },
                            reason = o.optString("reason").ifBlank { "Kullanıcı hariç tutması" },
                            source = Source.USER
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addUserPath(path: String, title: String? = null): ExclusionItem? {
        val normalized = normalizePath(path) ?: return null
        val existing = listUser()
        if (existing.any { it.path.equals(normalized, ignoreCase = true) }) return null
        if (systemDefaults().any { normalized.startsWith(it.path, ignoreCase = true) }) {
            // Zaten sistem korumasında — yine de kullanıcıya görünür kopya ekleme
            return null
        }
        val item = ExclusionItem(
            id = UUID.randomUUID().toString(),
            path = normalized,
            title = title?.trim()?.takeIf { it.isNotEmpty() }
                ?: normalized.substringAfterLast('/').ifBlank { normalized },
            reason = "Kullanıcı tarafından eklendi",
            source = Source.USER
        )
        saveUser(existing + item)
        return item
    }

    fun removeUser(id: String): Boolean {
        val next = listUser().filterNot { it.id == id }
        if (next.size == listUser().size) return false
        saveUser(next)
        return true
    }

    /**
     * Kullanıcı hariç tutması — taramada / silmede atlanır.
     * Sistem maddeleri [SystemFileGuard] ile zaten korunur; burada yalnızca kullanıcı yolları.
     */
    fun isUserExcluded(path: String?): Boolean {
        if (path.isNullOrBlank() || path.startsWith("content://")) return false
        val n = path.replace('\\', '/').lowercase()
        for (item in listUser()) {
            val p = item.path.replace('\\', '/').lowercase().trimEnd('/')
            if (p.isEmpty()) continue
            if (n == p || n.startsWith("$p/")) return true
        }
        return false
    }

    /** Görüntüleme: sistem koruması veya kullanıcı hariç tutması. */
    fun isExcluded(path: String?): Boolean =
        isUserExcluded(path) || com.makay.cleaner.util.SystemFileGuard.isProtectedPath(path)

    private fun saveUser(items: List<ExclusionItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("path", item.path)
                    .put("title", item.title)
                    .put("reason", item.reason)
            )
        }
        prefs.edit().putString(KEY_USER, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "clean_exclusions"
        private const val KEY_USER = "user_paths"

        fun systemDefaults(): List<ExclusionItem> = listOf(
            ExclusionItem(
                "sys_app_cache",
                "/data/data/*/cache/",
                "Uygulama iç önbelleği",
                "Sideload erişemez; Xiaomi/Samsung sistem API ile temizler",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_user_cache",
                "/data/user/0/*/cache/",
                "Kullanıcı iç önbelleği",
                "Uygulama sandbox’ı — sideload silemez",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_code_cache",
                "/data/data/*/code_cache/",
                "Kod önbelleği",
                "Sistem / uygulama kod önbelleği",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_dalvik",
                "/data/dalvik-cache/",
                "Dalvik / ART önbelleği",
                "İşletim sistemi alanı",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_data_system",
                "/data/system/",
                "Sistem veri alanı",
                "Kritik sistem klasörü",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_data_misc",
                "/data/misc/",
                "Sistem misc alanı",
                "Kritik sistem klasörü",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_recovery",
                "/cache/recovery/",
                "Recovery önbelleği",
                "Sistem recovery alanı",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_miui",
                "/Android/data/com.miui.",
                "MIUI uygulama verisi",
                "Xiaomi sistem paketleri korunur",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_samsung",
                "/Android/data/com.samsung.android.",
                "Samsung sistem verisi",
                "Samsung sistem paketleri korunur",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_sec",
                "/Android/data/com.sec.",
                "Samsung Security verisi",
                "Samsung güvenlik / servis paketleri",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_android_data",
                "/Android/data/com.android.",
                "Android sistem uygulamaları",
                "com.android.* paketleri",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_gms",
                "/Android/data/com.google.android.gms",
                "Google Play services",
                "GMS önbelleği sistem korumalı",
                Source.SYSTEM
            ),
            ExclusionItem(
                "sys_partition",
                "/system",
                "Sistem bölümü (/system, /vendor, /product, /efs)",
                "Salt okunur sistem bölümleri",
                Source.SYSTEM
            )
        )

        private fun normalizePath(raw: String): String? {
            val t = raw.trim().replace('\\', '/')
            if (t.length < 2) return null
            if (t.startsWith("content://")) return null
            return t
        }
    }
}
