package com.makay.cleaner.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "makay_app_settings"
)

/**
 * Merkezi ayar katmanı (DataStore). Yeni tercihler buraya eklenmeli;
 * eski SharedPreferences dosyaları kademeli olarak göç ettirilir.
 */
class AppSettingsStore private constructor(private val context: Context) {

    private val store = context.applicationContext.appSettingsDataStore

    val firstLaunchDone: Flow<Boolean> = store.data.map { it[KEY_FIRST_LAUNCH] ?: false }
    val platformMode: Flow<String?> = store.data.map { it[KEY_PLATFORM] }
    val aggressiveCacheReclaim: Flow<Boolean> =
        store.data.map { it[KEY_AGGRESSIVE_RECLAIM] ?: true }

    suspend fun setFirstLaunchDone(done: Boolean = true) {
        store.edit { it[KEY_FIRST_LAUNCH] = done }
    }

    suspend fun setPlatformMode(mode: String) {
        store.edit { it[KEY_PLATFORM] = mode }
    }

    suspend fun setAggressiveCacheReclaim(enabled: Boolean) {
        store.edit { it[KEY_AGGRESSIVE_RECLAIM] = enabled }
        SystemCacheReclaimerBridge.setAggressive(context, enabled)
    }

    companion object {
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("first_launch_done")
        private val KEY_PLATFORM = stringPreferencesKey("platform_mode")
        private val KEY_AGGRESSIVE_RECLAIM = booleanPreferencesKey("aggressive_cache_reclaim")

        @Volatile
        private var instance: AppSettingsStore? = null

        fun getInstance(context: Context): AppSettingsStore =
            instance ?: synchronized(this) {
                instance ?: AppSettingsStore(context.applicationContext).also { instance = it }
            }
    }
}

/** util ↔ data döngüsünü kırmak için ince köprü. */
internal object SystemCacheReclaimerBridge {
    fun setAggressive(context: Context, enabled: Boolean) {
        com.makay.cleaner.util.SystemCacheReclaimer.setAggressiveReclaimEnabled(context, enabled)
    }
}
