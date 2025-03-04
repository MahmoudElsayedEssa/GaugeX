package com.binissa.core.domain.repository

import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    /**
     * Stores an event for later processing
     * @param event The event to store
     * @return Success status
     */
    suspend fun storeEvent(event: Event): Result<Unit>

    /**
     * Stores multiple events in a batch operation
     * @param events List of events to store
     * @return Success status
     */
    suspend fun storeEvents(events: List<Event>): Result<Unit>

    /**
     * Get the total count of stored events
     */
    suspend fun getTotalEventCount(): Result<Int>

    /**
     * Get database size in bytes
     */
    suspend fun getDatabaseSize(): Result<Long>

    /**
     * Retrieves all events with the given status
     * @param status The status to filter by
     * @return A flow of events
     */
    fun getEventsByStatus(status: EventStatus): Flow<List<Event>>

    /**
     * Updates the status of an event
     * @param eventId The ID of the event
     * @param status The new status
     * @return Success status
     */
    suspend fun updateEventStatus(eventId: String, status: EventStatus): Result<Unit>

    /**
     * Purges old events from storage to prevent unbounded growth
     * @param olderThan Events older than this timestamp will be purged
     * @return Number of events purged
     */
    suspend fun purgeOldEvents(olderThan: Long): Result<Int>

    /**
     * Optimizes the database storage
     */
    suspend fun optimizeDatabase(): Result<Unit>
}