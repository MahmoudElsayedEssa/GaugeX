package com.binissa.plugins.performance.collectors

interface MetricsCollector {
    suspend fun collect(): Map<String, Any>
}
