package com.binissa.core.data.repository

import com.binissa.core.domain.model.PerformanceEvent
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.repository.ConfigRepository

class AdaptiveSampler(private val configRepository: ConfigRepository) {

    suspend fun shouldSampleEvent(event: Event): Boolean {
        // Get base sampling rate from config
        val baseSamplingRate = configRepository.getSamplingRate(event.type.name)

        // Adjust sampling based on event characteristics
        return when (event) {
            is PerformanceEvent -> {
                // Sample based on performance characteristics
                val importanceMultiplier = when {
                    // Always capture slow events
                    event.duration > 1000 -> 2.0f
                    // Sample fewer fast events
                    event.duration < 10 -> 0.5f
                    // Standard sampling for normal events
                    else -> 1.0f
                }

                // Sample based on event category
                val categoryMultiplier = when (event.category) {
                    "network" -> 1.5f  // Network events are important
                    "memory" -> 0.8f   // Memory events less so
                    else -> 1.0f
                }

                // Calculate final sampling rate
                val finalRate =
                    (baseSamplingRate * importanceMultiplier * categoryMultiplier).coerceIn(
                        0.0f, 1.0f
                    )

                // Sample based on the calculated rate
                Math.random() < finalRate
            }

            else -> {
                // Default sampling for other event types
                Math.random() < baseSamplingRate
            }
        }
    }

    fun calculateEventPriority(event: Event): Int {
        // Assign priority (0-100) based on importance
        return when (event) {
            is PerformanceEvent -> {
                when {
                    // High priority for very slow operations
                    event.duration > 5000 -> 90
                    // Medium-high priority for slow operations
                    event.duration > 1000 -> 70
                    // Medium priority for moderately slow operations
                    event.duration > 100 -> 50
                    // Lower priority for fast operations
                    else -> 30
                }
            }

            else -> 50 // Default priority
        }
    }
}