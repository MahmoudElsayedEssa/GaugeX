package com.binissa.core.data.repository

import com.binissa.core.domain.model.PerformanceEvent
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.repository.EventRepository

class EventStream(
    private val samplingStrategy: AdaptiveSampler,
    private val contextProvider: EventDataAggregator,
    private val eventRepository: EventRepository
) {
    
    suspend fun processEvent(event: Event) {
        // 1. Check if event should be sampled
        if (!samplingStrategy.shouldSampleEvent(event)) {
            return
        }
        
        // 2. Enrich event with context
        val enrichedEvent = enrichEvent(event)
        
        // 3. Transform event if needed
        val transformedEvent = transformEvent(enrichedEvent)
        
        // 4. Store event
        eventRepository.storeEvent(transformedEvent)
    }
    
    private fun enrichEvent(event: Event): Event {
        // Add context information to event
        val context = contextProvider.getEventContext(event)
        
        // Create enriched event with context
        return when (event) {
            is PerformanceEvent -> {
                event.copy(metadata = event.metadata + context)
            }
            // Handle other event types
            else -> event
        }
    }
    
    private fun transformEvent(event: Event): Event {
        // Apply transformations like anonymization, normalization, etc.
        return when (event) {
            is PerformanceEvent -> {
                // Normalize event names for consistent analysis
                val normalizedName = normalizeEventName(event.name)
                event.copy(name = normalizedName)
            }
            // Handle other event types
            else -> event
        }
    }
    
    private fun normalizeEventName(name: String): String {
        // Normalize event names
        // Example: "loadUserProfile:123" -> "loadUserProfile"
        return name.replace(Regex(":[0-9]+$"), "")
    }
}