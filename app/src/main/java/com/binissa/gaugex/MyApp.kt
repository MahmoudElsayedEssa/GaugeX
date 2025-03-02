package com.binissa.gaugex

import android.app.Application
import com.binissa.api.GaugeX

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        GaugeX.attach(this)
    }
}