package com.binissa.core.domain.usecase

import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.repository.ConfigRepository
import com.binissa.core.domain.repository.EventRepository
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface CollectEventUseCase {
    suspend operator fun invoke(event: Event): Result<Unit>
    suspend fun executeBatch(events: List<Event>): Result<Int>
}

//class CollectEventUseCaseImpl(
//    private val eventRepository: EventRepository,
//    private val configRepository: ConfigRepository,
//    private val ioDispatcher: CoroutineContext
//) : CollectEventUseCase {
//
//    private val TAG = "CollectEventUseCase"
//
//
//    data class BatchParams(val events: List<Event>)
//
//    override suspend fun invoke(event: Event): Result<Unit> = withContext(ioDispatcher) {
//        try {
//            Log.d(TAG, "Processing event: ${event.id}")
//
//            // Check sampling rate
//            val samplingRate = configRepository.getSamplingRate(event.type.name)
//            if (!shouldSampleEvent(samplingRate)) {
//                Log.d(TAG, "Event skipped due to sampling: ${event.id}")
//                return@withContext Result.success(Unit)
//            }
//
//            // Process the event
//            eventRepository.storeEvent(event)
//                .onSuccess { Log.d(TAG, "Successfully stored event: ${event.id}") }
//                .onFailure { e -> Log.e(TAG, "Failed to store event: ${event.id}", e) }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error collecting event", e)
//            Result.failure(e)
//        }
//    }
//
//
//    class SamplingSkippedException : Exception("Events were skipped due to sampling rules")
//
//    override suspend fun executeBatch(events: List<Event>): Result<Int> =
//        withContext(ioDispatcher) {
//            try {
//                Log.d(TAG, "Processing batch of ${events.size} events")
//
//                val sampledEvents = events.filter { event ->
//                    val samplingRate = configRepository.getSamplingRate(event.type.name)
//                    shouldSampleEvent(samplingRate)
//                }
//
//                if (sampledEvents.isEmpty()) {
//                    Log.d(TAG, "All events in batch were sampled out")
//                    return@withContext Result.failure(SamplingSkippedException())
//                }
//
//                // Process events in batch
//                eventRepository.storeEvents(sampledEvents).fold(onSuccess = {
//                    Log.d(TAG, "Successfully stored batch of ${sampledEvents.size} events")
//                    Result.success(sampledEvents.size)
//                }, onFailure = { e ->
//                    Log.e(TAG, "Failed to store batch of ${sampledEvents.size} events", e)
//                    Result.failure(e)
//                })
//            } catch (e: Exception) {
//                Log.e(TAG, "Error processing event batch", e)
//                Result.failure(e)
//            }
//        }
//
//    private fun shouldSampleEvent(samplingRate: Float): Boolean {
//        if (samplingRate <= 0.0f) return false
//        if (samplingRate >= 1.0f) return true
//        return Math.random() < samplingRate
//    }
//}
