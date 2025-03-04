package com.binissa.core.domain.usecase

import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus
import kotlinx.coroutines.flow.Flow

interface CoreModule {
    // Store events
    suspend fun storeEvent(event: Event): Boolean
    suspend fun storeEvents(events: List<Event>): Int
    
    // Retrieve events
    fun getEventsByStatus(status: EventStatus): Flow<List<Event>>
    
    // Update status
    suspend fun updateEventStatus(eventId: String, status: EventStatus): Boolean
    
    // Maintenance
    suspend fun purgeOldEvents(olderThan: Long): Int
    suspend fun getDatabaseStats(): Map<String, Any>
    suspend fun optimizeDatabase(): Boolean
    
    // Transmission
    suspend fun transmitEvents(): TransmissionResult
    
    data class TransmissionResult(
        val sent: Int,
        val failed: Int
    )
}