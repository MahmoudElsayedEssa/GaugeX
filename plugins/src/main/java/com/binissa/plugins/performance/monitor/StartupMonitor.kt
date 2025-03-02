package com.binissa.plugin.performance.monitor

import android.os.SystemClock
import com.binissa.core.domain.model.Event
import com.binissa.plugin.performance.PerformanceEvent
import kotlinx.coroutines.flow.MutableSharedFlow

class StartupMonitor(
    private val eventFlow: MutableSharedFlow<Event>, private val processStartTime: Long
) : PerformanceMonitor {
    override suspend fun initialize() {

    }

    override fun start() {
        val startupTime = SystemClock.elapsedRealtime() - processStartTime
        eventFlow.tryEmit(
            PerformanceEvent(
                category = "app_startup",
                name = "cold_start",
                duration = startupTime,
                metadata = mapOf("startup_type" to "cold")
            )
        )
    }

    override fun stop() {

    }
}
