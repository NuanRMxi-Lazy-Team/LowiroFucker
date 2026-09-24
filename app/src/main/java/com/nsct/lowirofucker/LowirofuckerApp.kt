package com.nsct.lowirofucker

import android.app.Application

class LowirofuckerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceManager.register()
    }
}
