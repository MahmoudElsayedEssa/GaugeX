package com.binissa.api.annotations

// GaugeXProcessor.kt

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.binissa.gaugex.GaugeX
import java.lang.reflect.Method

/**
 * Handles GaugeX annotations at runtime.
 * This is a simplified implementation - in production, you would use AspectJ
 * or runtime bytecode manipulation for proper instrumentation.
 */
class GaugeXProcessor {
    private val TAG = "GaugeXProcessor"

    /**
     * Register an application for automatic activity tracking
     */
    fun registerApplication(application: Application) {
        // Set up activity lifecycle monitoring
        application.registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val trackScreen = activity.javaClass.getAnnotation(TrackScreen::class.java)
                if (trackScreen != null) {
                    val screenName =
                        if (trackScreen.screenName.isNotEmpty()) trackScreen.screenName else activity.javaClass.simpleName
                    GaugeX.trackScreenView(screenName)
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    /**
     * Process TrackPerformance annotation for a method
     */
    @Throws(Exception::class)
    fun processTrackPerformance(
        obj: Any, method: Method, args: Array<Any>, trackPerformance: TrackPerformance
    ): Any? {
        val startTime = System.currentTimeMillis()
        val perfName =
            if (trackPerformance.name.isNotEmpty()) trackPerformance.name else method.name

        try {
            return method.invoke(obj, *args)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            GaugeX.reportPerformanceMetric(
                perfName,
                trackPerformance.category,
                duration,
                mapOf("class" to obj.javaClass.simpleName)
            )
        }
    }

    /**
     * Process LogEvent annotation for a method
     */
    @Throws(Exception::class)
    fun processLogEvent(obj: Any, method: Method, args: Array<Any>, logEvent: LogEvent): Any? {
        val message = if (logEvent.message.isNotEmpty()) logEvent.message
        else "Called ${method.name}"

        val tag = obj.javaClass.simpleName

        when (logEvent.level.uppercase()) {
            "DEBUG" -> GaugeX.d(tag, message)
            "WARNING" -> GaugeX.w(tag, message)
            "ERROR" -> GaugeX.e(tag, message)
            else -> GaugeX.i(tag, message)
        }

        return method.invoke(obj, *args)
    }

    /**
     * Process MonitorExceptions annotation for a method
     */
    @Throws(Exception::class)
    fun processMonitorExceptions(
        obj: Any, method: Method, args: Array<Any>, monitorExceptions: MonitorExceptions
    ): Any? {
        try {
            return method.invoke(obj, *args)
        } catch (e: Exception) {
            // Report the exception
            GaugeX.reportException(
                e, mapOf(
                    "class" to obj.javaClass.name, "method" to method.name
                )
            )

            // Rethrow if not silent
            if (!monitorExceptions.silent) {
                throw e
            }

            // Return default value if silent
            return getDefaultReturnValue(method.returnType)
        }
    }

    /**
     * Get a default return value for a type
     */
    private fun getDefaultReturnValue(type: Class<*>): Any? {
        return when {
            type == Void.TYPE -> null
            type.isPrimitive -> when (type) {
                Boolean::class.javaPrimitiveType -> false
                Char::class.javaPrimitiveType -> 0.toChar()
                Byte::class.javaPrimitiveType -> 0.toByte()
                Short::class.javaPrimitiveType -> 0.toShort()
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                Float::class.javaPrimitiveType -> 0f
                Double::class.javaPrimitiveType -> 0.0
                else -> null
            }

            else -> null
        }
    }
}