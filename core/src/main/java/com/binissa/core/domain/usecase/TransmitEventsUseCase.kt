package com.binissa.core.domain.usecase

import com.binissa.core.domain.model.EventStatus
import com.binissa.core.domain.repository.ApiClient
import com.binissa.core.domain.repository.EventRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Use case for transmitting events to the backend
 */
class TransmitEventsUseCase(
    private val eventRepository: EventRepository,
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineContext
) {
    /**
     * Transmits all pending events to the backend
     * @return Number of events successfully transmitted
     */
    suspend fun execute(): Result<Int> = withContext(ioDispatcher) {
        try {
            var successCount = 0

            // Collect all pending events
            val events = eventRepository.getEventsByStatus(EventStatus.PENDING)
                .firstOrNull() ?: emptyList()

            // Process events in batches
            events.chunked(20).forEach { batch ->
                try {
                    // Mark events as processing
                    batch.forEach { event ->
                        eventRepository.updateEventStatus(event.id, EventStatus.PROCESSING)
                    }

                    // Send to API
                    val result = apiClient.sendEvents(batch)

                    // Update status based on result
                    if (result.isSuccess) {
                        batch.forEach { event ->
                            eventRepository.updateEventStatus(event.id, EventStatus.TRANSMITTED)
                        }
                        successCount += batch.size
                    } else {
                        batch.forEach { event ->
                            eventRepository.updateEventStatus(event.id, EventStatus.FAILED)
                        }
                    }
                } catch (e: Exception) {
                    // Mark as failed in case of exception
                    batch.forEach { event ->
                        eventRepository.updateEventStatus(event.id, EventStatus.FAILED)
                    }
                }
            }

            Result.success(successCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}