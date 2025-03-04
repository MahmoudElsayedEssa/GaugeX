package com.binissa.core.domain.usecase

import android.util.Log
import com.binissa.core.domain.model.EventStatus
import com.binissa.core.domain.repository.ApiClient
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.domain.usecase.TransmitEventsUseCase.TransmissionResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.CoroutineContext

interface TransmitEventsUseCase {
    suspend operator fun invoke(): Result<TransmissionResult>

    data class TransmissionResult(
        val totalProcessed: Int,
        val successCount: Int,
        val failureCount: Int,
    )
}


class TransmitEventsUseCaseImpl(
    private val eventRepository: EventRepository,
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineContext
) : TransmitEventsUseCase {

    private val TAG = "TransmitEvents"
    private val MAX_BATCH_SIZE = 50

    override suspend fun invoke(): Result<TransmissionResult> = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Starting event transmission")

            // Get events with pending status
            val events = eventRepository.getEventsByStatus(EventStatus.PENDING).first()

            if (events.isEmpty()) {
                Log.d(TAG, "No pending events to transmit")
                return@withContext Result.success(
                    TransmissionResult(0, 0, 0)
                )
            }

            Log.d(TAG, "Found ${events.size} pending events to transmit")

            var successCount = 0
            var failureCount = 0

            // Process events in batches for better network efficiency
            events.chunked(MAX_BATCH_SIZE).forEach { batch ->
                try {
                    // Mark events as processing
                    batch.forEach { event ->
                        eventRepository.updateEventStatus(event.id, EventStatus.PROCESSING)
                    }

                    // Send the batch to the API
                    apiClient.sendEvents(batch).fold(
                        onSuccess = {
                            // On success, mark all events as transmitted
                            batch.forEach { event ->
                                eventRepository.updateEventStatus(event.id, EventStatus.TRANSMITTED)
                            }
                            successCount += batch.size
                            Log.d(TAG, "Successfully transmitted ${batch.size} events")
                        },
                        onFailure = { error ->
                            // Handle different types of failures with appropriate status updates
                            val newStatus = determineStatusFromError(error)

                            batch.forEach { event ->
                                eventRepository.updateEventStatus(event.id, newStatus)
                            }
                            failureCount += batch.size

                            Log.e(
                                TAG,
                                "Failed to transmit batch of ${batch.size} events: ${error.message}"
                            )
                        }
                    )
                } catch (e: Exception) {
                    // Handle unexpected exceptions during batch processing
                    Log.e(TAG, "Unexpected error processing batch", e)

                    // Mark events as failed
                    batch.forEach { event ->
                        eventRepository.updateEventStatus(event.id, EventStatus.FAILED)
                    }
                    failureCount += batch.size
                }
            }

            val result = TransmissionResult(
                totalProcessed = events.size,
                successCount = successCount,
                failureCount = failureCount
            )

            Log.d(TAG, "Transmission completed: $result")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error during transmission process", e)
            Result.failure(e)
        }
    }

    /**
     * Determines the appropriate status based on the type of error
     * - Transient network errors will be marked as PENDING to retry later
     * - Server errors (5xx) will be marked as PENDING to retry later
     * - Client errors (4xx) will be marked as FAILED since they're unlikely to succeed on retry
     * - Unknown errors will be marked as FAILED
     */
    private fun determineStatusFromError(error: Throwable): EventStatus {
        return when (error) {
            // Network connectivity issues - retry later
            is UnknownHostException,
            is SocketTimeoutException,
            is IOException -> {
                Log.w(TAG, "Transient network error, will retry: ${error.message}")
                EventStatus.PENDING
            }

            // API errors
            is ApiClient.NetworkError -> {
                when (error) {
                    is ApiClient.NetworkError.ConnectionError,
                    is ApiClient.NetworkError.Timeout -> {
                        Log.w(TAG, "Connection error, will retry: ${error.message}")
                        EventStatus.PENDING
                    }

                    is ApiClient.NetworkError.ServerError -> {
                        if (error.statusCode >= 500) {
                            // Server errors - retry later
                            Log.w(
                                TAG,
                                "Server error ${error.statusCode}, will retry: ${error.message}"
                            )
                            EventStatus.PENDING
                        } else {
                            // Client errors - don't retry
                            Log.e(
                                TAG,
                                "Client error ${error.statusCode}, won't retry: ${error.message}"
                            )
                            EventStatus.FAILED
                        }
                    }

                    else -> {
                        Log.e(TAG, "Unknown API error, won't retry: ${error.message}")
                        EventStatus.FAILED
                    }
                }
            }

            // Any other exceptions
            else -> {
                Log.e(
                    TAG,
                    "Unhandled error type, won't retry: ${error.javaClass.simpleName} - ${error.message}"
                )
                EventStatus.FAILED
            }
        }
    }
}