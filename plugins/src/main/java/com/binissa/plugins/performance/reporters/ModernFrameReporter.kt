package com.binissa.plugin.performance.reporters

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import com.binissa.core.domain.model.Event
import com.binissa.plugin.performance.PerformanceEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration.Companion.nanoseconds

class ModernFrameReporter(
    private val eventFlow: MutableSharedFlow<Event>
) : FrameReporter, Choreographer.FrameCallback {
    private var isRunning = false
    private var lastFrameTime = 0L
    private val frameTimes = mutableListOf<Long>()

    override fun start() {
        Handler(Looper.getMainLooper()).post {
            isRunning = true
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
        if (lastFrameTime > 0) {
            val duration = (frameTimeNanos - lastFrameTime).nanoseconds.inWholeMilliseconds
            frameTimes.add(duration)

            if (frameTimes.size >= 120) {
                val avg = frameTimes.average()
                val max = frameTimes.maxOrNull() ?: 0.0
                val janky = frameTimes.count { it > 16.666 }

                eventFlow.tryEmit(
                    PerformanceEvent(
                        category = "frame_metrics",
                        name = "frame_report",
                        duration = avg.toLong(),
                        metadata = mapOf(
                            "avg_frame_time" to avg,
                            "max_frame_time" to max,
                            "janky_frames" to janky
                        )
                    )
                )

                frameTimes.clear()
            }
        }

        lastFrameTime = frameTimeNanos
        if (isRunning) Choreographer.getInstance().postFrameCallback(this)
    }
}
