package com.binissa.core.domain.usecase

import android.content.Context
import android.util.Log
import com.binissa.core.data.datasource.local.EventDao
import com.binissa.core.data.datasource.local.EventDatabase
import com.binissa.core.data.datasource.remote.JsonSerializerImpl
import com.binissa.core.data.datasource.remote.ObservaXApiClient
import com.binissa.core.data.manager.SessionTracker
import com.binissa.core.data.mapper.EventMapper
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventStatus
import com.binissa.core.domain.model.GaugeXConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class CoreModuleImpl(
    private val context: Context, private val config: GaugeXConfig
) : CoreModule {
    // Database
    private val database: EventDatabase by lazy { EventDatabase.getInstance(context) }
    private val eventDao: EventDao by lazy { database.eventDao() }

    // Dependencies
    private val jsonSerializer = JsonSerializerImpl()
    private val sessionTracker = SessionTracker(context, jsonSerializer)
    private val eventMapper = EventMapper(jsonSerializer, sessionTracker)
    private val apiClient =
        ObservaXApiClient(config.endpointUrl, config.apiKey ?: "", jsonSerializer)

    // Collect event
    override suspend fun storeEvent(event: Event): Boolean = withContext(Dispatchers.IO) {
        try {
            // Apply sampling
            val samplingRate = config.samplingRates[event.type.name] ?: 1.0f
            if (Math.random() > samplingRate) {
                return@withContext true // Sampled out, but not an error
            }

            // Map and store event
            val entity = eventMapper.mapToEntity(event, EventStatus.PENDING)
            eventDao.insertEvent(entity)
            true
        } catch (e: Exception) {
            Log.e("CoreModule", "Error storing event: ${e.message}")
            false
        }
    }

    // Batch collection
    override suspend fun storeEvents(events: List<Event>): Int = withContext(Dispatchers.IO) {
        try {
            // Apply sampling to each event
            val sampledEvents = events.filter {
                val samplingRate = config.samplingRates[it.type.name] ?: 1.0f
                Math.random() <= samplingRate
            }

            if (sampledEvents.isEmpty()) {
                return@withContext 0
            }

            // Map and store events
            val entities = sampledEvents.map {
                eventMapper.mapToEntity(it, EventStatus.PENDING)
            }

            eventDao.insertEvents(entities)
            sampledEvents.size
        } catch (e: Exception) {
            Log.e("CoreModule", "Error storing events: ${e.message}")
            0
        }
    }

    // Get events by status
    override fun getEventsByStatus(status: EventStatus): Flow<List<Event>> {
        return eventDao.getEventsByStatus(status.name)
            .map { entities -> entities.mapNotNull { eventMapper.mapFromEntity(it) } }
            .catch { e -> Log.e("CoreModule", "Error getting events: ${e.message}") }
            .flowOn(Dispatchers.IO)
    }

    // Update event status
    override suspend fun updateEventStatus(eventId: String, status: EventStatus): Boolean =
        withContext(Dispatchers.IO) {
            try {
                eventDao.updateEventStatus(eventId, status.name)
                true
            } catch (e: Exception) {
                Log.e("CoreModule", "Error updating status: ${e.message}")
                false
            }
        }

    // Purge old events
    override suspend fun purgeOldEvents(olderThan: Long): Int = withContext(Dispatchers.IO) {
        try {
            // Delete old events
            val purgeCount = eventDao.deleteEventsOlderThan(olderThan)

            // Check if we need more aggressive cleanup
            val totalCount = eventDao.getEventCount()
            val maxEvents = 10000 // Maximum number of events to keep

            if (totalCount > maxEvents) {
                // Delete oldest events to maintain size limit
                val excessCount = totalCount - maxEvents
                val deletedExcess = deleteOldestEvents(excessCount)
                return@withContext purgeCount + deletedExcess
            }

            purgeCount
        } catch (e: Exception) {
            Log.e("CoreModule", "Error purging events: ${e.message}")
            0
        }
    }

    // Helper for deleting oldest events
    private suspend fun deleteOldestEvents(count: Int): Int {
        return try {
            // This would be a custom query in your DAO
            val oldestEvents = eventDao.getOldestEvents(count)
            val ids = oldestEvents.map { it.id }
            eventDao.deleteEventsByIds(ids)
            ids.size
        } catch (e: Exception) {
            Log.e("CoreModule", "Error deleting oldest events: ${e.message}")
            0
        }
    }

    // Get database statistics
    override suspend fun getDatabaseStats(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            // Get basic stats
            val totalCount = eventDao.getEventCount()
            val dbFile = File(context.getDatabasePath("gaugex_events.db").path)
            val dbSize = if (dbFile.exists()) dbFile.length() else 0L

            // Get counts by status
            val pendingCount = eventDao.getEventCountByStatus(EventStatus.PENDING.name)
            val failedCount = eventDao.getEventCountByStatus(EventStatus.FAILED.name)

            // Return comprehensive stats
            mapOf(
                "totalEvents" to totalCount,
                "pendingEvents" to pendingCount,
                "failedEvents" to failedCount,
                "databaseSizeBytes" to dbSize,
                "databaseSizeKB" to (dbSize / 1024),
                "databaseSizeMB" to (dbSize / (1024 * 1024))
            )
        } catch (e: Exception) {
            Log.e("CoreModule", "Error getting DB stats: ${e.message}")
            mapOf("error" to "Failed to get database stats")
        }
    }

    // Optimize database
    override suspend fun optimizeDatabase(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Delete transmitted events to free space
            val deletedCount = eventDao.deleteEventsByStatus(EventStatus.TRANSMITTED.name)
            Log.d("CoreModule", "Deleted $deletedCount transmitted events")

            try {
                // Use raw SQL for VACUUM
                database.openHelper.writableDatabase.execSQL("VACUUM")
                Log.d("CoreModule", "VACUUM completed successfully")

                // Create indices if they don't exist
                database.openHelper.writableDatabase.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_events_status ON events(status)"
                )
                database.openHelper.writableDatabase.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(timestamp)"
                )
                Log.d("CoreModule", "Database indices created/verified")
            } catch (e: Exception) {
                // Non-critical error
                Log.w("CoreModule", "Error during optimization: ${e.message}")
            }

            true
        } catch (e: Exception) {
            Log.e("CoreModule", "Error optimizing database: ${e.message}")
            false
        }
    }

    // Transmit events
    override suspend fun transmitEvents(): CoreModule.TransmissionResult =
        withContext(Dispatchers.IO) {
            try {
                // Get pending events
                val events = getEventsByStatus(EventStatus.PENDING).first()

                if (events.isEmpty()) {
                    return@withContext CoreModule.TransmissionResult(0, 0)
                }

                var sent = 0
                var failed = 0

                // Process in batches of 50
                events.chunked(50).forEach { batch ->
                    try {
                        // Mark as processing
                        batch.forEach { updateEventStatus(it.id, EventStatus.PROCESSING) }

                        // Send to API
                        try {
                            apiClient.sendEvents(batch)

                            // Mark as transmitted on success
                            batch.forEach { updateEventStatus(it.id, EventStatus.TRANSMITTED) }
                            sent += batch.size
                        } catch (e: Exception) {
                            // Handle different errors
                            val newStatus = when (e) {
                                // For temporary errors, mark as pending for retry
                                is IOException -> EventStatus.PENDING
                                // For server errors, mark as pending for retry
                                // For other errors, mark as failed
                                else -> EventStatus.FAILED
                            }

                            // Update status
                            batch.forEach { updateEventStatus(it.id, newStatus) }
                            failed += batch.size

                            // Log error
                            Log.e("CoreModule", "Error sending batch: ${e.message}")
                        }
                    } catch (e: Exception) {
                        // For processing errors, mark as failed
                        batch.forEach { updateEventStatus(it.id, EventStatus.FAILED) }
                        failed += batch.size
                        Log.e("CoreModule", "Error processing batch: ${e.message}")
                    }
                }

                // Return results
                CoreModule.TransmissionResult(sent, failed)
            } catch (e: Exception) {
                Log.e("CoreModule", "Error transmitting events: ${e.message}")
                CoreModule.TransmissionResult(0, 0)
            }
        }
}