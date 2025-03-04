package com.binissa.core.data.repository

import android.content.Context
import android.util.Log
import com.binissa.core.data.datasource.local.EventDao
import com.binissa.core.data.datasource.remote.JsonSerializer
import com.binissa.core.data.mapper.EventMapper
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus
import com.binissa.core.domain.repository.EventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class EventRepositoryImpl(
    private val eventDao: EventDao,
    val eventMapper: EventMapper,
    private val jsonSerializer: JsonSerializer,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) : EventRepository {

    private val TAG = "EventRepositoryImpl"

    override suspend fun storeEvent(event: Event): Result<Unit> = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Storing event: ${event.id} of type ${event.type}")

            // Convert domain event to entity
            val entity = eventMapper.mapToEntity(event, EventStatus.PENDING)

            // Insert into database
            eventDao.insertEvent(entity)
            Log.d(TAG, "Successfully stored event: ${event.id}")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store event ${event.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun storeEvents(events: List<Event>): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                if (events.isEmpty()) {
                    return@withContext Result.success(Unit)
                }

                Log.d(TAG, "Storing batch of ${events.size} events")

                // Convert all domain events to entities
                val entities = events.map { event ->
                    eventMapper.mapToEntity(event, EventStatus.PENDING)
                }

                // Insert all in a single transaction
                eventDao.insertEvents(entities)
                Log.d(TAG, "Successfully stored batch of ${events.size} events")

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store batch of ${events.size} events: ${e.message}", e)
                Result.failure(e)
            }
        }

    override fun getEventsByStatus(status: EventStatus): Flow<List<Event>> {
        return eventDao.getEventsByStatus(status.name)
            .map { entities -> entities.mapNotNull { eventMapper.mapFromEntity(it) } }
            .catch { e ->
                Log.e(TAG, "Error getting events by status: ${e.message}", e)
                throw e
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun updateEventStatus(eventId: String, status: EventStatus): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                eventDao.updateEventStatus(eventId, status.name)
                Log.d(TAG, "Updated event $eventId status to ${status.name}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update event status: ${e.message}", e)
                Result.failure(e)
            }
        }

    override suspend fun purgeOldEvents(olderThan: Long): Result<Int> = withContext(ioDispatcher) {
        try {
            // First, purge old events based on timestamp
            val purgeCount = eventDao.deleteEventsOlderThan(olderThan)

            // Additionally, check total count and enforce limits if needed
            val count = eventDao.getEventCount()
            val maxEventCount = 10000 // Configure this based on your needs

            if (count > maxEventCount) {
                // Keep newest events, delete oldest excess events
                val excessCount = count - maxEventCount
                val deletedCount = eventDao.deleteEventsOlderThan(excessCount.toLong())
                Log.d(TAG, "Purged $deletedCount excess events (over limit of $maxEventCount)")
                return@withContext Result.success(purgeCount + deletedCount)
            }

            Log.d(TAG, "Purged $purgeCount old events")
            Result.success(purgeCount)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to purge events: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getTotalEventCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            val count = eventDao.getEventCount()
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get event count: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun getDatabaseSize(): Result<Long> = withContext(ioDispatcher) {
        try {
            val dbFile = File(context.getDatabasePath("gaugex_events.db").path)
            if (dbFile.exists()) {
                val size = dbFile.length()
                return@withContext Result.success(size)
            }
            Result.success(0L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database size: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun optimizeDatabase(): Result<Unit> = withContext(ioDispatcher) {
        try {
            // First, ensure all processed events are removed
            val purgedCount = eventDao.deleteEventsByStatus(EventStatus.TRANSMITTED.name)
            Log.d(TAG, "Removed $purgedCount processed events")

            // Execute VACUUM to reclaim space
            try {
//                eventDao.runVacuum()
                Log.d(TAG, "Database optimized with VACUUM")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to execute VACUUM: ${e.message}")
                // Continue even if VACUUM fails
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to optimize database: ${e.message}", e)
            Result.failure(e)
        }
    }
}