package com.binissa.plugins.performance.monitor


import android.os.SystemClock
import com.binissa.core.domain.model.Event
import com.binissa.plugins.performance.PerformanceEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StartupMonitor(
    private val eventFlow: MutableSharedFlow<Event>,
    private val processStartTime: Long
) : PerformanceMonitor {
    private val mutex = Mutex()
    private var startupPhases = mutableListOf<StartupPhase>()

    data class StartupPhase(
        val name: String,
        val startTime: Long,
        val duration: Long,
        val metadata: Map<String, Any> = emptyMap()
    )

    override suspend fun initialize() {
        // Prepare for detailed startup tracking
        recordStartupPhase(
            "process_creation",
            processStartTime,
            SystemClock.elapsedRealtime() - processStartTime
        )
    }

    private suspend fun recordStartupPhase(
        phaseName: String,
        phaseStartTime: Long,
        phaseDuration: Long,
        metadata: Map<String, Any> = emptyMap()
    ) {
        mutex.withLock {
            startupPhases.add(
                StartupPhase(
                    name = phaseName,
                    startTime = phaseStartTime,
                    duration = phaseDuration,
                    metadata = metadata
                )
            )
        }
    }

    override fun start() {
        val startupTime = SystemClock.elapsedRealtime() - processStartTime

        // Emit comprehensive startup event
        eventFlow.tryEmit(
            PerformanceEvent(
                category = "app_startup",
                name = "comprehensive_startup",
                duration = startupTime,
                metadata = mapOf(
                    "startup_type" to "cold",
                    "total_startup_time" to startupTime,
                    "startup_phases" to startupPhases
                )
            )
        )
    }

    override fun stop() {
        startupPhases.clear()
    }
}