package com.binissa.core.data.repository

import com.binissa.core.data.datasource.local.EventDao


data class ScreenPerformance(
    val name: String,
    val avgDuration: Double,
    val count: Int
)

/**
 * Performance regression indicator
 */
data class PerformanceRegression(
    val name: String,
    val category: String,
    val historicalAvg: Double,
    val recentAvg: Double,
    val percentChange: Double
)

/**
 * Memory leak indicator
 */
data class MemoryLeakIndicator(
    val sessionId: String,
    val startMemory: Long,
    val endMemory: Long,
    val growthRate: Double, // bytes per second
    val duration: Long      // session duration in ms
)