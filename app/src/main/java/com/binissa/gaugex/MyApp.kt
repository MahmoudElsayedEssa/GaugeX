package com.binissa.gaugex

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GaugeX.attach(this)
    }
}