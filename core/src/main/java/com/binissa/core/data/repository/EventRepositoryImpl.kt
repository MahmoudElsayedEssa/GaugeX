package com.binissa.core.data.repository

import android.content.Context
import android.util.Log
import androidx.room.RoomDatabase
import com.binissa.core.data.analytics.AnalyticsEngine
import com.binissa.core.data.datasource.local.EventDao
import com.binissa.core.data.datasource.remote.JsonSerializer
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.data.mapper.EventMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Implementation of EventRepository using Room for local storage
 */
class EventRepositoryImpl(
    private val eventDao: EventDao,
    private val eventMapper: EventMapper,
    private val jsonSerializer: JsonSerializer,
    private val context: Context,
    private val analyticsService: AnalyticsEngine,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : EventRepository {

    override suspend fun storeEvent(event: Event): Result<Unit> = withContext(ioDispatcher) {
        try {
            Log.d("EventRepository", "About to store event: ${event.id} of type ${event.type}")

            // Convert domain event to entity
            val entity = eventMapper.mapToEntity(event, EventStatus.PENDING)

            // Insert into database
            eventDao.insertEvent(entity)
            Log.d("EventRepository", "Successfully stored event: ${event.id}")

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Failed to store event ${event.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun getEventsByStatus(status: EventStatus): Flow<List<Event>> = flow {
        eventDao.getEventsByStatus(status.name)
            .map { entities -> entities.mapNotNull { eventMapper.mapFromEntity(it) } }
            .collect { emit(it) }
    }.flowOn(ioDispatcher)

    override suspend fun updateEventStatus(eventId: String, status: EventStatus): Result<Unit> =
        withContext(ioDispatcher) {
            try {
                eventDao.updateEventStatus(eventId, status.name)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun purgeOldEvents(olderThan: Long): Int = withContext(ioDispatcher) {
        try {
            // First, purge old events based on timestamp
            val purgeCount = eventDao.deleteEventsOlderThan(olderThan)

            // Additionally, check total count and enforce limits if needed
            val count = eventDao.getEventCount()
            val maxEventCount = 10000 // Configure this based on your needs

            if (count > maxEventCount) {
                // Keep newest events, delete oldest excess events
                val excessCount = count - maxEventCount
                val deletedCount = eventDao.deleteOldestEvents(excessCount)
                Log.d(
                    "EventRepository",
                    "Purged $deletedCount excess events (over limit of $maxEventCount)"
                )
                return@withContext purgeCount + deletedCount
            }

            Log.d("EventRepository", "Purged $purgeCount old events")
            purgeCount
        } catch (e: Exception) {
            Log.e("EventRepository", "Failed to purge events: ${e.message}", e)
            0
        }
    }

    override suspend fun getTotalEventCount(): Int = withContext(ioDispatcher) {
        try {
            eventDao.getEventCount()
        } catch (e: Exception) {
            Log.e("EventRepository", "Failed to get event count: ${e.message}")
            0
        }
    }

    suspend fun getDatabaseSize(): Long = withContext(ioDispatcher) {
        try {
            val dbFile = File(context.getDatabasePath("gaugex_events.db").path)
            if (dbFile.exists()) {
                return@withContext dbFile.length()
            }
            0L
        } catch (e: Exception) {
            Log.e("EventRepository", "Failed to get database size: ${e.message}")
            0L
        }
    }

    suspend fun optimizeDatabase(): Result<Unit> = withContext(ioDispatcher) {
        try {
            // First, ensure all processed events are removed
            val purgedCount = eventDao.deleteEventsByStatus(EventStatus.TRANSMITTED.name)
            Log.d("EventRepository", "Removed $purgedCount processed events")

            // Execute VACUUM to reclaim space
            val db = (eventDao as? RoomDatabase)?.openHelper?.writableDatabase
            db?.execSQL("VACUUM")

            Log.d("EventRepository", "Database optimized with VACUUM")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("EventRepository", "Failed to optimize database: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Analytics methods delegated to AnalyticsService
    override suspend fun getSlowestScreens(limit: Int): List<ScreenPerformance> {
        return analyticsService.getSlowestScreens(limit)
    }

    override suspend fun getPerformanceRegressions(): List<PerformanceRegression> {
        return analyticsService.getPerformanceRegressions()
    }

    override suspend fun getPotentialMemoryLeaks(): List<MemoryLeakIndicator> {
        return analyticsService.getPotentialMemoryLeaks()
    }
}