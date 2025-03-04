package com.binissa.core.domain.usecase

import android.util.Log
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.domain.usecase.PurgeEventsUseCase
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class PurgeEventsUseCaseImpl(
    private val eventRepository: EventRepository,
    private val ioDispatcher: CoroutineContext
) : PurgeEventsUseCase {

    private val TAG = "PurgeEventsUseCase"

    override suspend fun execute(olderThan: Long): Result<Int> = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Purging events older than timestamp: $olderThan")
            eventRepository.purgeOldEvents(olderThan)
        } catch (e: Exception) {
            Log.e(TAG, "Error purging old events", e)
            Result.failure(e)
        }
    }
}