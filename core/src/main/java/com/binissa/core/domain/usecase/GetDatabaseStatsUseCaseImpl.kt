package com.binissa.core.domain.usecase

import android.util.Log
import com.binissa.core.domain.model.EventStatus
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.domain.usecase.GetDatabaseStatsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class GetDatabaseStatsUseCaseImpl(
    private val eventRepository: EventRepository,
    private val ioDispatcher: CoroutineContext
) : GetDatabaseStatsUseCase {

    private val TAG = "GetDatabaseStatsUseCase"

    override suspend fun execute(): Result<GetDatabaseStatsUseCase.DatabaseStats> = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Getting database statistics")
            
            // Get total event count
            val totalCountResult = eventRepository.getTotalEventCount()
            if (totalCountResult.isFailure) {
                return@withContext Result.failure(totalCountResult.exceptionOrNull() 
                    ?: Exception("Failed to get total event count"))
            }
            val totalCount = totalCountResult.getOrDefault(0)
            
            // Get database size
            val dbSizeResult = eventRepository.getDatabaseSize()
            if (dbSizeResult.isFailure) {
                return@withContext Result.failure(dbSizeResult.exceptionOrNull() 
                    ?: Exception("Failed to get database size"))
            }
            val dbSize = dbSizeResult.getOrDefault(0L)
            
            // Count pending events
            val pendingEvents = eventRepository.getEventsByStatus(EventStatus.PENDING).first().size
            
            // Count failed events
            val failedEvents = eventRepository.getEventsByStatus(EventStatus.FAILED).first().size
            
            val stats = GetDatabaseStatsUseCase.DatabaseStats(
                totalEvents = totalCount,
                databaseSizeBytes = dbSize,
                pendingEvents = pendingEvents,
                failedEvents = failedEvents
            )
            
            Log.d(TAG, "Database stats retrieved: $stats")
            Result.success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database statistics", e)
            Result.failure(e)
        }
    }
}