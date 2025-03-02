package com.binissa.core.data.repository

import com.binissa.core.data.datasource.local.EventDao
import kotlinx.coroutines.CoroutineScope
import kotlin.math.pow
import kotlin.math.sqrt

class EventAggregator(private val eventDao: EventDao, private val scope: CoroutineScope) {

    // Aggregate performance data by time periods
    suspend fun aggregatePerformanceByHour(
        category: String,
        startTime: Long,
        endTime: Long
    ): List<HourlyPerformance> {
        val events = eventDao.getEventsByTimeRange(category, startTime, endTime)

        // Group by hour
        return events.groupBy { it.timestamp / (60 * 60 * 1000) }
            .map { (hour, eventsInHour) ->
                HourlyPerformance(
                    hour = hour,
                    avgDuration = eventsInHour.mapNotNull { it.duration }.average(),
                    count = eventsInHour.size,
                    minDuration = eventsInHour.mapNotNull { it.duration }.minOrNull() ?: 0,
                    maxDuration = eventsInHour.mapNotNull { it.duration }.maxOrNull() ?: 0
                )
            }
    }

    // Create an anomaly detection model
    suspend fun detectPerformanceAnomalies(
        category: String,
        lookbackPeriod: Long
    ): List<PerformanceAnomaly> {
        val recentEvents = eventDao.getEventsByTimeRange(
            category,
            System.currentTimeMillis() - lookbackPeriod,
            System.currentTimeMillis()
        )

        // Group by name (e.g., method name or screen name)
        return recentEvents.groupBy { it.name }
            .mapNotNull { (name, events) ->
                // Calculate baseline statistics
                val durations = events.mapNotNull { it.duration }
                if (durations.isEmpty()) return@mapNotNull null

                val mean = durations.average()
                val stdDev = calculateStdDev(durations, mean)

                // Find outliers
                val anomalies = events.filter {
                    it.duration != null && (it.duration > mean + 2 * stdDev)
                }

                if (anomalies.isNotEmpty()) {
                    PerformanceAnomaly(
                        name = name,
                        category = category,
                        anomalies = anomalies,
                        baselineMean = mean,
                        baselineStdDev = stdDev
                    )
                } else null
            }
    }

    private fun calculateStdDev(values: List<Long>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }

}