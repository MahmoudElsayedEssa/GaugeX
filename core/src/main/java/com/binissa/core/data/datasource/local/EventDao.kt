package com.binissa.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.binissa.core.data.datasource.local.model.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Query("SELECT * FROM events WHERE status = :status ORDER BY timestamp ASC")
    fun getEventsByStatus(status: String): Flow<List<EventEntity>>

    @Query("UPDATE events SET status = :status WHERE id = :eventId")
    suspend fun updateEventStatus(eventId: String, status: String)

    @Query("UPDATE events SET retryCount = retryCount + 1 WHERE id = :eventId")
    suspend fun incrementRetryCount(eventId: String)

    @Query("DELETE FROM events WHERE timestamp < :timestamp")
    suspend fun deleteEventsOlderThan(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM events WHERE id IN (SELECT id FROM events ORDER BY timestamp ASC LIMIT :count)")
    suspend fun deleteOldestEvents(count: Int): Int

    // Add to EventDao.kt
    @Query("DELETE FROM events WHERE status = :status")
    suspend fun deleteEventsByStatus(status: String): Int

    @Query("SELECT * FROM events WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getEventsByCategory(category: String, limit: Int = 100): List<EventEntity>

    @Query("SELECT * FROM events WHERE duration > :thresholdMs ORDER BY duration DESC")
    suspend fun getSlowEvents(thresholdMs: Long): List<EventEntity>

    @Query("SELECT AVG(duration) FROM events WHERE category = :category AND name = :name AND timestamp > :since")
    suspend fun getAverageDuration(category: String, name: String, since: Long): Double?

    @Query("SELECT * FROM events WHERE name LIKE :pattern")
    suspend fun searchEventsByName(pattern: String): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events WHERE category = :category AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getEventCountInTimeRange(category: String, startTime: Long, endTime: Long): Int

    @Query("SELECT * FROM events WHERE metadataJson LIKE :jsonPattern")
    suspend fun searchInMetadata(jsonPattern: String): List<EventEntity>


    @Query("SELECT * FROM events WHERE category = :category AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getEventsByTimeRange(category: String, startTime: Long, endTime: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE type = 'PERFORMANCE'")
    suspend fun getAllPerformanceEvents(): List<EventEntity>

    @Query("SELECT DISTINCT sessionId FROM events WHERE sessionId IS NOT NULL")
    suspend fun getAllSessionIds(): List<String>

    @Query("SELECT * FROM events WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getEventsBySession(sessionId: String): List<EventEntity>

    @Query("""
    DELETE FROM events 
    WHERE id IN (
        SELECT id FROM events 
        WHERE priority < 50 
        ORDER BY timestamp ASC 
        LIMIT :limit
    )
""")
    suspend fun deleteLowPriorityEvents(limit: Int): Int


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("SELECT * FROM events")
    suspend fun getAllEvents(): List<EventEntity>
}