package com.binissa.core.domain.usecase


import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.repository.ConfigRepository
import com.binissa.core.domain.repository.EventRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Use case for collecting events with sampling
 */
class CollectEventUseCase(
    private val eventRepository: EventRepository,
    private val configRepository: ConfigRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val TAG = "CollectEventUseCase"

    // Coroutine scope for this use case
    private val coroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)


    fun executeBatch(events: List<Event>) {
        coroutineScope.launch {
            events.forEach { event ->
                try {
                    val samplingRate = configRepository.getSamplingRate(event.type.name)
                    if (shouldSampleEvent(samplingRate)) {
                        eventRepository.storeEvent(event)
                    }
                } catch (e: Exception) {
                    Log.e("CollectEventUseCase", "Error processing batch event: ${event.id}", e)
                }
            }
        }
    }

    /**
     * Execute the use case to collect an event
     * @param event The event to collect
     */
    fun execute(event: Event) {
        Log.d(TAG, "Executing for event: ${event.id}")

        coroutineScope.launch {
            try {
                // Check if this event type should be sampled
                val samplingRate = configRepository.getSamplingRate(event.type.name)

                if (shouldSampleEvent(samplingRate)) {
                    Log.d(TAG, "Event passed sampling, storing: ${event.id}")
                    eventRepository.storeEvent(event).fold(
                        onSuccess = {
                            Log.d(TAG, "Successfully stored event: ${event.id}")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Failed to store event: ${event.id}", e)
                        }
                    )
                } else {
                    Log.d(TAG, "Event sampled out by rate: $samplingRate")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting event", e)
            }
        }
    }

    /**
     * Determine if an event should be sampled based on sampling rate
     * @param samplingRate The sampling rate (0.0-1.0)
     * @return True if the event should be sampled
     */
    private fun shouldSampleEvent(samplingRate: Float): Boolean {
        // If sampling rate is 0, never sample
        if (samplingRate <= 0.0f) return false

        // If sampling rate is 1, always sample
        if (samplingRate >= 1.0f) return true

        // Otherwise, sample based on random value
        return Math.random() < samplingRate
    }
}