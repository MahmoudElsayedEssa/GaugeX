package com.binissa.plugins.performance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.plugins.GaugeXPlugin
import com.binissa.plugins.performance.monitor.DeviceMetricsMonitor
import com.binissa.plugins.performance.monitor.FrameMetricsMonitor
import com.binissa.plugins.performance.monitor.MemoryMonitor
import com.binissa.plugins.performance.monitor.ActivityLifecycleMonitor
import com.binissa.plugins.performance.monitor.AnrMonitor
import com.binissa.plugins.performance.monitor.PerformanceMonitor
import com.binissa.plugins.performance.monitor.StartupMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PerformanceMonitoringPlugin : GaugeXPlugin {
    private val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitors = mutableListOf<PerformanceMonitor>()
    private lateinit var appContext: Context

    override val id: String = "performance"
    private val TAG = "PerformanceMonitoringPlugin"

    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
        return try {
            appContext = context.applicationContext
            val processStartTime = estimateProcessStartTime()

            monitors.addAll(
                listOf(
                    StartupMonitor(eventFlow, processStartTime),
                    DeviceMetricsMonitor(pluginScope, eventFlow, appContext),
                    FrameMetricsMonitor(pluginScope, eventFlow, appContext),
                    AnrMonitor(pluginScope, eventFlow),
                    MemoryMonitor(pluginScope, eventFlow),
                    ActivityLifecycleMonitor(eventFlow, appContext)
                )
            )
            Log.i(TAG, "Performance monitoring plugin initialized")
            monitors.forEach { it.initialize() }
            true
        } catch (e: Exception) {
            Log.e("PerformancePlugin", "Initialization failed", e)
            false
        }
    }

    override suspend fun startMonitoring() = monitors.forEach { it.start() }
    override suspend fun stopMonitoring() = monitors.forEach { it.stop() }
    override fun getEvents(): Flow<Event> = eventFlow.asSharedFlow()
    override suspend fun shutdown() {
        pluginScope.cancel()
        monitors.clear()
    }

    private fun estimateProcessStartTime() =
        SystemClock.elapsedRealtime() - (SystemClock.uptimeMillis() / 2)
}