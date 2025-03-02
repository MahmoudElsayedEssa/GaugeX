package com.binissa.plugin.performance.collectors

interface MetricsCollector {
    suspend fun collect(): Map<String, Any>
}
