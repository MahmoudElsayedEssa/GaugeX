package com.binissa.plugins.performance.monitor

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.binissa.core.domain.model.Event
import com.binissa.plugins.performance.reporters.FrameReporter
import com.binissa.plugins.performance.reporters.LegacyFrameReporter
import com.binissa.plugins.performance.reporters.ModernFrameReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow

class FrameMetricsMonitor(
    private val scope: CoroutineScope,
    private val eventFlow: MutableSharedFlow<Event>,
    private val context: Context
) : PerformanceMonitor {
    private var frameReporter: FrameReporter? = null

    override suspend fun initialize() {
        frameReporter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ModernFrameReporter(eventFlow)
        } else {
            LegacyFrameReporter(eventFlow)
        }
    }

    override fun start() {
        Handler(Looper.getMainLooper()).post {
            frameReporter?.start()
        }
    }

    override fun stop() {
        Handler(Looper.getMainLooper()).post {
            frameReporter?.stop()
        }
    }
}
