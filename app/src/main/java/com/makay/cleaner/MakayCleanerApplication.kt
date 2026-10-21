package com.makay.cleaner

import android.app.Application
import com.makay.cleaner.util.CrashLogger
import com.makay.cleaner.util.PlatformManager

class MakayCleanerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        CrashLogger.install(this)
        PlatformManager.init(this)
    }

    companion object {
        lateinit var instance: MakayCleanerApplication
            private set
    }
}