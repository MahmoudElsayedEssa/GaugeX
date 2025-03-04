package com.binissa.plugins.performance.collectors

import android.app.ActivityManager
import android.content.Context

class MemoryMetricsCollector(private val context: Context) : MetricsCollector {
    override suspend fun collect(): Map<String, Any> {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo().apply { am.getMemoryInfo(this) }

            mapOf(
                "total_mem" to memInfo.totalMem,
                "available_mem" to memInfo.availMem,
                "low_memory" to memInfo.lowMemory
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
