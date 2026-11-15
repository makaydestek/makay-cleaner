package com.makay.cleaner.data

import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.makay.cleaner.worker.CleaningWorker
import java.util.concurrent.TimeUnit

class AutoCleanRepository(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("auto_clean_prefs", Context.MODE_PRIVATE)
    private val workManager = WorkManager.getInstance(context)

    companion object {
        const val WORK_TAG = "auto_cleaning_work"
        const val KEY_ENABLED = "auto_clean_enabled"
        const val KEY_INTERVAL_HOURS = "auto_clean_interval_hours"
        const val KEY_WIFI_ONLY = "auto_clean_wifi_only"
        const val KEY_CHARGING_ONLY = "auto_clean_charging_only"
        const val KEY_CAT_CACHE = "cat_cache"
        const val KEY_CAT_APK = "cat_apk"
        const val KEY_CAT_TMP = "cat_tmp"
        /** true = APK/indirme/artık çöpe; false = kalıcı sil (cache/tmp her zaman kalıcı). */
        const val KEY_USE_TRASH = "auto_clean_use_trash"
        const val MIN_INTERVAL_HOURS = 1L
        const val MAX_INTERVAL_HOURS = 168L
    }

    data class CategoryFlags(
        val cache: Boolean = true,
        /** Varsayılan kapalı — otomatik kalıcı APK silmeyi önler. */
        val oldApk: Boolean = false,
        val tempJunk: Boolean = false
    )

    fun saveSettings(
        enabled: Boolean,
        intervalHours: Long,
        wifiOnly: Boolean = getWifiOnly(),
        chargingOnly: Boolean = getChargingOnly(),
        categories: CategoryFlags = getCategories(),
        useTrash: Boolean = getUseTrash()
    ) {
        // WorkManager periyodik iş alt sınırı 15 dk; 0/negatif değer çökme üretir.
        val safeInterval = intervalHours.coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putLong(KEY_INTERVAL_HOURS, safeInterval)
            .putBoolean(KEY_WIFI_ONLY, wifiOnly)
            .putBoolean(KEY_CHARGING_ONLY, chargingOnly)
            .putBoolean(KEY_CAT_CACHE, categories.cache)
            .putBoolean(KEY_CAT_APK, categories.oldApk)
            .putBoolean(KEY_CAT_TMP, categories.tempJunk)
            .putBoolean(KEY_USE_TRASH, useTrash)
            .apply()

        if (enabled) {
            runCatching { scheduleAutoClean(safeInterval, wifiOnly, chargingOnly) }
                .onFailure { com.makay.cleaner.util.SoftErrorLogger.warn("AutoClean", "iş planlanamadı", it) }
        } else {
            cancelAutoClean()
        }
    }

    fun getSettings(): Pair<Boolean, Long> {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val intervalHours = prefs.getLong(KEY_INTERVAL_HOURS, 24)
            .coerceIn(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS)
        return Pair(enabled, intervalHours)
    }

    fun getWifiOnly(): Boolean = prefs.getBoolean(KEY_WIFI_ONLY, false)
    fun getChargingOnly(): Boolean = prefs.getBoolean(KEY_CHARGING_ONLY, false)

    fun getCategories(): CategoryFlags = CategoryFlags(
        cache = prefs.getBoolean(KEY_CAT_CACHE, true),
        oldApk = prefs.getBoolean(KEY_CAT_APK, false),
        tempJunk = prefs.getBoolean(KEY_CAT_TMP, false)
    )

    /** APK/indirme için çöp kutusu (önerilen: açık). */
    fun getUseTrash(): Boolean = prefs.getBoolean(KEY_USE_TRASH, true)

    private fun scheduleAutoClean(intervalHours: Long, wifiOnly: Boolean, chargingOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            // Dolu diskte temizlik çalışsın (aksi halde tam ihtiyaç anında atlanır).
            .setRequiresStorageNotLow(false)
            .apply {
                if (wifiOnly) setRequiredNetworkType(NetworkType.UNMETERED)
                if (chargingOnly) setRequiresCharging(true)
            }
            .build()

        val cats = getCategories()
        val input = Data.Builder()
            .putBoolean(CleaningWorker.KEY_DO_CACHE, cats.cache)
            .putBoolean(CleaningWorker.KEY_DO_APK, cats.oldApk)
            .putBoolean(CleaningWorker.KEY_DO_TMP, cats.tempJunk)
            .putBoolean(CleaningWorker.KEY_USE_TRASH, getUseTrash())
            .build()

        val request = PeriodicWorkRequestBuilder<CleaningWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.MINUTES
            )
            .addTag(WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancelAutoClean() {
        workManager.cancelUniqueWork(WORK_TAG)
    }

    /**
     * Ayar "açık" görünürken iş kaybolmuş olabilir (planlama hatası, süreç ölümü,
     * WorkManager temizliği). Açılışta yeniden kur.
     */
    fun ensureScheduled() {
        val (enabled, interval) = getSettings()
        if (!enabled) return
        runCatching { scheduleAutoClean(interval, getWifiOnly(), getChargingOnly()) }
            .onFailure {
                com.makay.cleaner.util.SoftErrorLogger.warn("AutoClean", "açılışta yeniden planlanamadı", it)
            }
    }

    fun runManualClean() {
        val cats = getCategories()
        val input = Data.Builder()
            .putBoolean(CleaningWorker.KEY_DO_CACHE, cats.cache)
            .putBoolean(CleaningWorker.KEY_DO_APK, cats.oldApk)
            .putBoolean(CleaningWorker.KEY_DO_TMP, cats.tempJunk)
            .putBoolean(CleaningWorker.KEY_USE_TRASH, getUseTrash())
            .build()
        val request = OneTimeWorkRequestBuilder<CleaningWorker>()
            .setInputData(input)
            .addTag("manual_clean")
            .build()
        workManager.enqueue(request)
    }
}
