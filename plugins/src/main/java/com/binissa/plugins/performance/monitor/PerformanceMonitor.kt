package com.binissa.plugin.performance.monitor

interface PerformanceMonitor {
    suspend fun initialize()
    fun start()
    fun stop()
}
