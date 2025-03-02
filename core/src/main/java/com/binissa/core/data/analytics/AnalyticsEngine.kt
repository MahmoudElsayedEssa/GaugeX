// EventAnalyticsService.kt
package com.binissa.core.data.analytics

import com.binissa.core.data.datasource.local.EventDao
import com.binissa.core.data.datasource.remote.JsonSerializer
import com.binissa.core.data.repository.MemoryLeakIndicator
import com.binissa.core.data.repository.PerformanceAnomaly
import com.binissa.core.data.repository.PerformanceRegression
import com.binissa.core.data.repository.ScreenPerformance
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Service for analyzing event data and generating insights
 */
class AnalyticsEngine(
    private val eventDao: EventDao,
    private val jsonSerializer: JsonSerializer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /**
     * Get slowest screens by average load time
     */
    suspend fun getSlowestScreens(limit: Int = 5): List<ScreenPerformance> =
        withContext(ioDispatcher) {
            val screenEvents = eventDao.getEventsByCategory("screen_load")

            return@withContext screenEvents.groupBy { it.name }.map { (screen, events) ->
                    ScreenPerformance(
                        name = screen,
                        avgDuration = events.mapNotNull { it.duration }.average(),
                        count = events.size
                    )
                }.sortedByDescending { it.avgDuration }.take(limit)
        }

    /**
     * Get performance regressions by comparing recent to historical
     */
    suspend fun getPerformanceRegressions(): List<PerformanceRegression> =
        withContext(ioDispatcher) {
            val now = System.currentTimeMillis()
            val oneWeekAgo = now - 7 * 24 * 60 * 60 * 1000
            val twoWeeksAgo = oneWeekAgo - 7 * 24 * 60 * 60 * 1000

            val recentEvents = eventDao.getEventsByTimeRange("performance", oneWeekAgo, now)
            val historicalEvents =
                eventDao.getEventsByTimeRange("performance", twoWeeksAgo, oneWeekAgo)

            // Group by name and category
            val recentByNameAndCategory = recentEvents.groupBy { it.category to it.name }
            val historicalByNameAndCategory = historicalEvents.groupBy { it.category to it.name }

            val regressions = mutableListOf<PerformanceRegression>()

            // Compare recent to historical
            recentByNameAndCategory.forEach { (key, recentGroupEvents) ->
                val (category, name) = key
                val historicalGroupEvents = historicalByNameAndCategory[key] ?: return@forEach

                val recentAvg = recentGroupEvents.mapNotNull { it.duration }.average()
                val historicalAvg = historicalGroupEvents.mapNotNull { it.duration }.average()

                // If recent is significantly worse than historical
                if (recentAvg > historicalAvg * 1.2) { // 20% worse
                    regressions.add(
                        PerformanceRegression(
                            name = name,
                            category = category,
                            historicalAvg = historicalAvg,
                            recentAvg = recentAvg,
                            percentChange = ((recentAvg - historicalAvg) / historicalAvg) * 100
                        )
                    )
                }
            }

            return@withContext regressions.sortedByDescending { it.percentChange }
        }

    /**
     * Detect potential memory leaks by looking for increasing memory usage
     */
    suspend fun getPotentialMemoryLeaks(): List<MemoryLeakIndicator> = withContext(ioDispatcher) {
        val memoryEvents = eventDao.getEventsByCategory("memory")

        return@withContext memoryEvents.groupBy { it.sessionId }.mapNotNull { (sessionId, events) ->
                if (events.size < 3 || sessionId == null) return@mapNotNull null

                // Sort by timestamp
                val sortedEvents = events.sortedBy { it.timestamp }

                // Extract memory usage over time
                val memoryUsages = sortedEvents.mapNotNull { event ->
                    try {
                        val metadata =
                            jsonSerializer.deserialize(event.metadataJson, Map::class.java)
                        val usedMemory = metadata["used_mem"] as? Number
                        usedMemory?.toLong()?.let { it to event.timestamp }
                    } catch (e: Exception) {
                        null
                    }
                }

                if (memoryUsages.size < 3) return@mapNotNull null

                // Check if memory consistently increases
                val increasing = memoryUsages.zipWithNext().all { (a, b) -> a.first <= b.first }

                if (increasing) {
                    val startMemory = memoryUsages.first().first
                    val endMemory = memoryUsages.last().first
                    val duration = memoryUsages.last().second - memoryUsages.first().second

                    MemoryLeakIndicator(
                        sessionId = sessionId,
                        startMemory = startMemory,
                        endMemory = endMemory,
                        growthRate = (endMemory - startMemory) / (duration / 1000.0), // bytes per second
                        duration = duration
                    )
                } else null
            }.sortedByDescending { it.growthRate }
    }

    /**
     * Detect performance anomalies
     */
    suspend fun detectPerformanceAnomalies(
        category: String, lookbackPeriod: Long
    ): List<PerformanceAnomaly> = withContext(ioDispatcher) {
        val recentEvents = eventDao.getEventsByTimeRange(
            category, System.currentTimeMillis() - lookbackPeriod, System.currentTimeMillis()
        )

        // Group by name (e.g., method name or screen name)
        return@withContext recentEvents.groupBy { it.name }.mapNotNull { (name, events) ->
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

    /**
     * Calculate standard deviation
     */
    private fun calculateStdDev(values: List<Long>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.sumOf { (it - mean).pow(2) } / values.size
        return sqrt(variance)
    }
}