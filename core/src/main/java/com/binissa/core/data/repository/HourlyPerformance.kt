package com.binissa.core.data.repository

import com.binissa.core.data.datasource.local.model.EventEntity

/**
 * Represents hourly aggregated performance metrics
 */
data class HourlyPerformance(
    val hour: Long,                 // Hour timestamp (epoch time / 3600000)
    val avgDuration: Double,        // Average duration in ms
    val count: Int,                 // Number of events in this hour
    val minDuration: Long,          // Minimum duration in ms
    val maxDuration: Long           // Maximum duration in ms
)

/**
 * Represents a performance anomaly detected in the data
 */
data class PerformanceAnomaly(
    val name: String,               // Name of the event (method, screen, etc.)
    val category: String,           // Category of the event
    val anomalies: List<EventEntity>, // The anomalous events
    val baselineMean: Double,       // Baseline average duration
    val baselineStdDev: Double      // Standard deviation of baseline
)

/**
 * Represents a performance trend over time
 */
data class PerformanceTrend(
    val category: String,           // Event category
    val name: String,               // Event name
    val trend: String,              // "improving", "degrading", or "stable"
    val startValue: Long?,          // First measured duration
    val currentValue: Long?,        // Most recent measured duration
    val changePercent: Double,      // Percent change (positive = worse)
    val eventCount: Int             // Number of events in the trend
)

/**
 * Represents a correlation between two events
 */
data class EventCorrelation(
    val firstEvent: EventEntity,    // First event
    val secondEvent: EventEntity,   // Second event
    val timeDifference: Long,       // Time between events in ms
    val confidence: Double = 1.0    // Confidence in the correlation (0.0-1.0)
)

/**
 * Represents an insight generated from the data
 */
data class Insight(
    val type: String,               // Type of insight (performance, memory, etc.)
    val title: String,              // Short title
    val description: String,        // Detailed description
    val data: Any,                  // Related data
    val severity: String,           // "low", "medium", "high", "critical"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Represents a user session
 */
data class Session(
    val id: String,                 // Session ID
    val startTime: Long,            // Session start time
    val endTime: Long? = null,      // Session end time (null if active)
    val deviceInfo: Map<String, Any> = emptyMap() // Device information
)