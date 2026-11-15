package com.makay.cleaner

import android.app.Application
import com.makay.cleaner.data.RecycleBinRepository
import com.makay.cleaner.util.CrashLogger
import com.makay.cleaner.util.PlatformManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MakayCleanerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        PlatformManager.init(this)
        scheduleRecycleBinMaintenance()
    }

    /**
     * Çöp kutusu 48 saat kuralı yalnızca ekran açıldığında değil, uygulama
     * açılışında da uygulanmalı; aksi halde süresi geçen dosyalar diskte kalır.
     */
    private fun scheduleRecycleBinMaintenance() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val repo = RecycleBinRepository(this@MakayCleanerApplication)
                repo.purgeExpired()
                repo.reconcileOrphans()
            }
            runCatching {
                com.makay.cleaner.data.AutoCleanRepository(this@MakayCleanerApplication)
                    .ensureScheduled()
            }
        }
    }

    companion object {
        lateinit var instance: MakayCleanerApplication
            private set
    }
}