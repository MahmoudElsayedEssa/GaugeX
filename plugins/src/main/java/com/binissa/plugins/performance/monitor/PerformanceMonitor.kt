package com.binissa.plugins.performance.monitor

interface PerformanceMonitor {
    suspend fun initialize()
    fun start()
    fun stop()
}
