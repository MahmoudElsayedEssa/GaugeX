package com.binissa.plugins.performance.monitor

import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AdaptiveMonitoringStrategy(
    private val baseInterval: Duration = 60.seconds,
    private val minInterval: Duration = 5.seconds,
    private val maxInterval: Duration = 5.minutes
) {
    // Performance load indicators
    private var cpuLoad: Double = 0.0
    private var memoryUsage: Double = 0.0
    private var networkActivity: Double = 0.0

    // Adaptive interval calculation
    fun calculateInterval(): Duration {
        val loadFactor = calculateLoadFactor()
        return (baseInterval * (1 + loadFactor)).coerceIn(minInterval, maxInterval)
    }

    private fun calculateLoadFactor(): Double {
        // Combine various system load indicators
        val combinedLoad = (cpuLoad + memoryUsage + networkActivity) / 3
        return when {
            combinedLoad > 0.8 -> 2.0  // High load, increase interval
            combinedLoad > 0.5 -> 1.0  // Moderate load, slight increase
            combinedLoad < 0.2 -> -0.5 // Low load, decrease interval
            else -> 0.0
        }
    }

    // Update methods for various system metrics
    fun updateCpuLoad(load: Double) {
        cpuLoad = load.coerceIn(0.0, 1.0)
    }

    fun updateMemoryUsage(usage: Double) {
        memoryUsage = usage.coerceIn(0.0, 1.0)
    }

    fun updateNetworkActivity(activity: Double) {
        networkActivity = activity.coerceIn(0.0, 1.0)
    }
}