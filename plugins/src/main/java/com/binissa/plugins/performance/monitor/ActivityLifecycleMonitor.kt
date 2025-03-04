package com.binissa.plugins.performance.monitor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import com.binissa.core.domain.model.Event
import com.binissa.plugins.performance.PerformanceEvent
import kotlinx.coroutines.flow.MutableSharedFlow

class ActivityLifecycleMonitor(
    private val eventFlow: MutableSharedFlow<Event>, private val context: Context
) : PerformanceMonitor {
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        private val createTimes = mutableMapOf<String, Long>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            val name = activity.javaClass.simpleName
            createTimes[name] = SystemClock.elapsedRealtime()
            eventFlow.tryEmit(
                PerformanceEvent(
                    category = "activity",
                    name = "create_start",
                    duration = 0,
                    metadata = mapOf("activity" to name)
                )
            )
        }

        override fun onActivityResumed(activity: Activity) {
            val name = activity.javaClass.simpleName
            createTimes.remove(name)?.let { startTime ->
                val duration = SystemClock.elapsedRealtime() - startTime
                eventFlow.tryEmit(
                    PerformanceEvent(
                        category = "activity",
                        name = "create_to_resume",
                        duration = duration,
                        metadata = mapOf("activity" to name)
                    )
                )
            }
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    override suspend fun initialize() {
        if (context is Application) {
            context.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    override fun start() {
    }

    override fun stop() {
    }
}
