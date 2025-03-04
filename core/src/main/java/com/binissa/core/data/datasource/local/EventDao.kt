package com.binissa.core.data.datasource.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.binissa.core.data.datasource.local.model.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE status = :status ORDER BY timestamp ASC")
    fun getEventsByStatus(status: String): Flow<List<EventEntity>>

    @Query("UPDATE events SET status = :status WHERE id = :eventId")
    suspend fun updateEventStatus(eventId: String, status: String)

    @Query("DELETE FROM events WHERE timestamp < :timestamp")
    suspend fun deleteEventsOlderThan(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM events WHERE status = :status")
    suspend fun deleteEventsByStatus(status: String): Int

    @Query("SELECT COUNT(*) FROM events WHERE status = :status")
    suspend fun getEventCountByStatus(status: String): Int

    @Query("SELECT * FROM events ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getOldestEvents(limit: Int): List<EventEntity>

    @Query("DELETE FROM events WHERE id IN (:ids)")
    suspend fun deleteEventsByIds(ids: List<String>): Int
}