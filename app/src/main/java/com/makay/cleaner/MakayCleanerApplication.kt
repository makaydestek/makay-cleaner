package com.makay.cleaner

import android.app.Application

class MakayCleanerApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: MakayCleanerApplication
            private set
    }
}