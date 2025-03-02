package com.binissa.core.domain.repository


import com.binissa.core.data.repository.MemoryLeakIndicator
import com.binissa.core.data.repository.PerformanceRegression
import com.binissa.core.data.repository.ScreenPerformance
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing events
 */
interface EventRepository {
    /**
     * Stores an event for later processing
     * @param event The event to store
     * @return Success status
     */
    suspend fun storeEvent(event: Event): Result<Unit>

    suspend fun getTotalEventCount(): Int


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
    suspend fun purgeOldEvents(olderThan: Long): Int


    suspend fun getSlowestScreens(limit: Int = 5): List<ScreenPerformance>
    suspend fun getPerformanceRegressions(): List<PerformanceRegression>
    suspend fun getPotentialMemoryLeaks(): List<MemoryLeakIndicator>
}
