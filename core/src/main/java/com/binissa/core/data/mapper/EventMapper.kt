package com.binissa.core.data.mapper

import com.binissa.core.data.datasource.local.model.EventEntity
import com.binissa.core.data.datasource.remote.JsonSerializer
import com.binissa.core.data.manager.SessionTracker
import com.binissa.core.domain.model.NetworkEvent
import com.binissa.core.domain.model.PerformanceEvent
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus

/**
 * Mapper for converting between domain Event and data EventEntity
 */
class EventMapper(
    private val jsonSerializer: JsonSerializer,
    private val sessionTracker: SessionTracker
) {

    /**
     * Maps a domain Event to an EventEntity for persistence
     */
    fun mapToEntity(event: Event, status: EventStatus): EventEntity {
        // Extract common metadata
        val metadata = mutableMapOf<String, Any>()
        val category: String
        val name: String
        var duration: Long? = null

        // Process based on event type
        when (event) {
            is PerformanceEvent -> {
                category = event.category
                name = event.name
                duration = event.duration
                metadata.putAll(event.metadata)

                // For performance events, extract key metrics
                if (event.category == "memory") {
                    // Store memory snapshot separately in a structured field
                    event.metadata["used_mem"]?.let { metadata["used_memory"] = it }
                    event.metadata["max_mem"]?.let { metadata["max_memory"] = it }
                } else if (event.category == "cpu") {
                    // Extract CPU metrics for easier querying
                    event.metadata["cpu_usage"]?.let { metadata["cpu_percentage"] = it }
                }
            }
            is NetworkEvent -> {
                category = "network"
                name = event.url?.let { "${event.method} " } ?: event.method
                duration = event.duration
                // Extract network data in a standardized form
                metadata["status_code"] = event.statusCode
                metadata["response_size"] = event.responseSize
                metadata["error"] = event.error != null
            }
            // Handle other event types...
            else -> {
                category = "other"
                name = event.id
            }
        }

        // Capture device state for context
//        val deviceState = getDeviceState()

        return EventEntity(
            id = event.id,
            timestamp = event.timestamp,
            type = event.type.name,
            status = status.name,
            payload = jsonSerializer.serialize(event),
            category = category,
            name = name,
            duration = duration,
            metadataJson = jsonSerializer.serialize(metadata),
            sessionId = sessionTracker.getCurrentSessionId(),
            deviceState = jsonSerializer.serialize(5),
            priority = calculateEventPriority(event),
            retryCount = 0
        )
    }

    private fun calculateEventPriority(event: Event): Int {
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
    /**
     * Maps an EventEntity to a domain Event
     */
    fun mapFromEntity(entity: EventEntity): Event? {
        return try {
            jsonSerializer.deserialize(entity.payload, Event::class.java)
        } catch (e: Exception) {
            // Log error but don't crash
            null
        }
    }
}
