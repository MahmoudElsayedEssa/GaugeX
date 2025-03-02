package com.binissa.plugin.performance.monitor

import android.os.Debug
import android.os.Handler
import android.os.Looper
import com.binissa.core.domain.model.Event
import com.binissa.plugin.performance.PerformanceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class AnrMonitor(
    private val scope: CoroutineScope,
    private val eventFlow: MutableSharedFlow<Event>,
    private val config: Config = Config()
) : PerformanceMonitor {

    // Configuration data class
    data class Config(
        var timeout: Duration = 5000.milliseconds, var ignoreDebugger: Boolean = false
    )

    @Volatile
    private var lastTick: Long = 0L

    @Volatile
    private var reported = false

    private var watchdogJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override suspend fun initialize() {
        // Initialization logic if needed
    }

    override fun start() {
        watchdogJob = scope.launch {
            while (isActive) {
                if (lastTick == 0L) {
                    // Start the tick and schedule a reset after the timeout period
                    lastTick = System.currentTimeMillis()
                    mainHandler.postDelayed({ resetTick() }, config.timeout.inWholeMilliseconds)
                }

                // Post a ping to the main thread
                mainHandler.post {
                    if (lastTick != 0L) {
                        // Update the tick to current time
                        lastTick = System.currentTimeMillis()
                        reported = false
                    }
                }

                // Delay half the timeout duration before checking again
                delay(config.timeout.inWholeMilliseconds / 2)

                // Check if an ANR has occurred
                if (lastTick != 0L && !reported) {
                    // If debugger is attached and not ignored, skip the ANR detection
                    if (!config.ignoreDebugger && (Debug.isDebuggerConnected() || Debug.waitingForDebugger())) {
                        resetTick()
                        continue
                    }

                    val elapsed = System.currentTimeMillis() - lastTick
                    if (elapsed >= config.timeout.inWholeMilliseconds) {
                        // Create an event with the ANR details
                        val stackTrace = getMainThreadStackTrace()
                        val event = PerformanceEvent(
                            category = "anr",
                            name = "main_thread_blocked",
                            duration = elapsed,
                            metadata = mapOf("stack_trace" to stackTrace)
                        )
                        // Emit the event via eventFlow
                        eventFlow.emit(event)
                        reported = true
                        resetTick()
                    }
                }
            }
        }
    }

    override fun stop() {
        watchdogJob?.cancel()
    }

    // Resets the tick and reported flag
    private fun resetTick() {
        lastTick = 0L
        reported = false
    }

    companion object {
        // Utility to get the main thread's stack trace
        fun getMainThreadStackTrace(): String {
            return Looper.getMainLooper().thread.stackTrace.joinToString("\n") {
                "${it.className}.${it.methodName} (${it.fileName}:${it.lineNumber})"
            }
        }
    }
}
