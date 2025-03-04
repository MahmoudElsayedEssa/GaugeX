package com.binissa.plugins.performance.monitor

import com.binissa.core.domain.model.Event
import com.binissa.plugins.performance.PerformanceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MemoryMonitor(
    private val scope: CoroutineScope, private val eventFlow: MutableSharedFlow<Event>
) : PerformanceMonitor {
    private var job: Job? = null
    override suspend fun initialize() {
    }

    override fun start() {
        job = scope.launch {
            while (isActive) {
                val runtime = Runtime.getRuntime()
                val used = runtime.totalMemory() - runtime.freeMemory()
                val max = runtime.maxMemory()

                eventFlow.emit(
                    PerformanceEvent(
                        category = "memory", name = "memory_usage", duration = 0, metadata = mapOf(
                            "used_mem" to used,
                            "max_mem" to max,
                            "usage_percent" to (used.toDouble() / max * 100)
                        )
                    )
                )

                delay(30_000)
            }
        }
    }

    override fun stop() {
        job?.cancel()
    }
}
