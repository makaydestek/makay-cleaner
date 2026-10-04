package com.makay.cleaner.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dil tercihi. Tam çeviri sonraki sürümde; kritik metinler [t] ile seçilir.
 */
object LanguageManager {
    private const val PREFS = "language_prefs"
    private const val KEY = "lang"

    enum class Lang(val displayName: String) {
        TR("Türkçe"),
        EN("English")
    }

    private val _lang = MutableStateFlow(Lang.TR)
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    fun init(context: Context) {
        val saved = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, Lang.TR.name)
        _lang.value = runCatching { Lang.valueOf(saved ?: Lang.TR.name) }.getOrDefault(Lang.TR)
    }

    fun get(context: Context): Lang {
        init(context)
        return _lang.value
    }

    fun set(context: Context, lang: Lang) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, lang.name)
            .apply()
        _lang.value = lang
    }

    fun t(tr: String, en: String, context: Context? = null): String {
        val current = if (context != null) get(context) else _lang.value
        return if (current == Lang.EN) en else tr
    }
}
