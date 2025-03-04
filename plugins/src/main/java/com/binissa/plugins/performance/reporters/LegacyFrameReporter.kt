package com.binissa.plugins.performance.reporters

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.binissa.core.domain.model.Event
import com.binissa.plugins.performance.PerformanceEvent
import kotlinx.coroutines.flow.MutableSharedFlow

class LegacyFrameReporter(
    private val eventFlow: MutableSharedFlow<Event>
) : FrameReporter, Choreographer.FrameCallback {
    private var isRunning = false
    private var frameCount = 0
    private var lastReportTime = 0L

    override fun start() {
        Handler(Looper.getMainLooper()).post {
            isRunning = true
            lastReportTime = System.currentTimeMillis()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun stop() {
        Handler(Looper.getMainLooper()).post {
            isRunning = false
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        frameCount++
        val now = System.currentTimeMillis()

        if (now - lastReportTime >= 2000) {
            val fps = frameCount / ((now - lastReportTime) / 1000.0)
            eventFlow.tryEmit(
                PerformanceEvent(
                    category = "frame_metrics",
                    name = "fps_report",
                    duration = 0,
                    metadata = mapOf("fps" to fps)
                )
            )

            frameCount = 0
            lastReportTime = now
        }

        if (isRunning) Choreographer.getInstance().postFrameCallback(this)
    }
}
