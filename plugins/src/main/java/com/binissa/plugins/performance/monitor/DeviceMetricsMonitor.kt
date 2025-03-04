package com.binissa.plugins.performance.monitor

import android.content.Context
import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.plugin.util.DeviceInfoHelper
import com.binissa.plugins.performance.PerformanceEvent
import com.binissa.plugins.performance.collectors.BatteryMetricsCollector
import com.binissa.plugins.performance.collectors.CpuMetricsCollector
import com.binissa.plugins.performance.collectors.DeviceInfoCollector
import com.binissa.plugins.performance.collectors.MemoryMetricsCollector
import com.binissa.plugins.performance.collectors.NetworkMetricsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceMetricsMonitor(
    private val scope: CoroutineScope,
    private val eventFlow: MutableSharedFlow<Event>,
    private val context: Context
) : PerformanceMonitor {
    private var job: Job? = null
    private val deviceInfoHelper = DeviceInfoHelper(context)
    private val metricsCollectors = listOf(
        CpuMetricsCollector(),
        BatteryMetricsCollector(context),
        NetworkMetricsCollector(context),
        MemoryMetricsCollector(context),
        DeviceInfoCollector(deviceInfoHelper)
    )

    override suspend fun initialize() {

    }

    override fun start() {
        job = scope.launch {
            // Emit an initial event quickly for testing
            emitDeviceMetricsEvent()

            while (isActive) {
                delay(60_000) // Keep the regular interval at 60 seconds
                emitDeviceMetricsEvent()
            }
        }
    }

    private suspend fun emitDeviceMetricsEvent() {
        try {
            val metrics =
                metricsCollectors.flatMap { it.collect().entries }.associate { it.toPair() }
            Log.d("DeviceMetricsMonitor", "Collected metrics: ${metrics.size} data points, metrics:$metrics")

            eventFlow.emit(
                PerformanceEvent(
                    category = "device_metrics",
                    name = "device_snapshot",
                    duration = 0,
                    metadata = metrics
                )
            )
            Log.d("DeviceMetricsMonitor", "Emitted device metrics event")
        } catch (e: Exception) {
            Log.e("DeviceMetricsMonitor", "Error emitting metrics", e)
        }
    }

    override fun stop() {
        job?.cancel()
    }
}
