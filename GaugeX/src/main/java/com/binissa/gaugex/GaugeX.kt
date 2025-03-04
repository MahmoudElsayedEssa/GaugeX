package com.binissa.gaugex

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.View
import com.binissa.api.annotations.GaugeXProcessor
import com.binissa.core.data.repository.EventRepositoryImpl
import com.binissa.core.di.ServiceLocator
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.GaugeXConfig
import com.binissa.plugin.logging.LogCollectionPlugin.LogLevel
import com.binissa.plugin.performance.PerformanceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Main entry point for the GaugeX SDK.
 * This class provides a simple API for application developers to use the SDK.
 */
object GaugeX {
    private const val TAG = "GaugeX"

    /**
     * Initialize the SDK
     * @param context Application context
     * @param config Custom configuration
     */
    @JvmStatic
    fun initialize(context: Context, config: GaugeXConfig = GaugeXConfig.Builder().build()) {
        GaugeXProvider.initialize(context, config)
    }

    /**
     * Initialize GaugeX with annotation processing
     * This is a convenience method that sets up annotation processing automatically
     */
    @JvmStatic
    fun initializeWithAnnotations(application: Application, config: GaugeXConfig = GaugeXConfig.Builder().build()) {
        // Initialize SDK
        initialize(application, config)

        // Register application for annotation processing
        val processor = GaugeXProcessor()
        processor.registerApplication(application)

        // Log that annotation processing is enabled
        Log.i(TAG, "GaugeX initialized with annotation processing")
    }

    /**
     * Check if the SDK is initialized
     * @return True if initialized
     */
    @JvmStatic
    fun isInitialized(): Boolean = GaugeXProvider.isInitialized()

    /**
     * Attach GaugeX for automatic monitoring
     * One-line integration that sets up all monitoring capabilities
     * @param application Application instance
     */
    @JvmStatic
    fun attach(application: Application) {
        // Initialize if needed
        if (!isInitialized()) {
            initialize(application)
        }

        // The plugins will automatically set up their monitoring
        GaugeXProvider.enableMonitoring()
    }

    /**
     * Manually report an event
     * @param event The event to report
     */
    @JvmStatic
    fun reportEvent(event: Event) {
        GaugeXProvider.reportEvent(event)
    }

    /**
     * Manually flush events to backend
     */
    @JvmStatic
    fun flushEvents() {
        GaugeXProvider.flushEvents()
    }

    /**
     * Disable all monitoring
     */
    @JvmStatic
    fun disableMonitoring() {
        GaugeXProvider.disableMonitoring()
    }

    /**
     * Enable monitoring after being disabled
     */
    @JvmStatic
    fun enableMonitoring() {
        GaugeXProvider.enableMonitoring()
    }

    /**
     * Purge old data to conserve space
     * @param keepDataForDays Number of days of data to keep
     */
    @JvmStatic
    fun purgeOldData(keepDataForDays: Int = 7) {
        GaugeXProvider.purgeOldData(keepDataForDays)
    }

    /**
     * Get database statistics
     * @param callback Callback to receive database stats
     * @param context Application context
     */
    @JvmStatic
    fun getDatabaseStats(callback: (Map<String, Any>) -> Unit, context: Context) {
        GaugeXProvider.getDatabaseStats(callback)
    }

    /**
     * Force database optimization immediately
     */
    @JvmStatic
    fun optimizeDatabaseNow() {
        GaugeXProvider.optimizeDatabaseNow()
    }

    /**
     * Shutdown the SDK
     */
    @JvmStatic
    fun shutdown() {
        GaugeXProvider.shutdown()
    }

    /**
     * Log a message at INFO level
     */
    @JvmStatic
    fun i(tag: String, message: String, throwable: Throwable? = null) {
//        GaugeXProvider.log(LogLevel.INFO.name, tag, message, throwable)
    }

    /**
     * Log a message at DEBUG level
     */
    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable? = null) {
//        GaugeXProvider.log(LogLevel.DEBUG.name, tag, message, throwable)
    }

    /**
     * Log a message at WARNING level
     */
    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable? = null) {
//        GaugeXProvider.log(LogLevel.WARNING.name, tag, message, throwable)
    }

    /**
     * Log a message at ERROR level
     */
    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
//        GaugeXProvider.log(LogLevel.ERROR.name, tag, message, throwable)
    }

    /**
     * Track a custom user interaction
     */
    @JvmStatic
    fun trackUserInteraction(eventName: String, properties: Map<String, Any> = emptyMap()) {
        GaugeXProvider.getUserBehaviorPlugin()?.trackUserInteraction(eventName, properties)
    }

    /**
     * Track screen view
     */
    @JvmStatic
    fun trackScreenView(screenName: String) {
        GaugeXProvider.getUserBehaviorPlugin()?.trackScreenView(screenName)
    }

    /**
     * Instrument a view for touch tracking
     */
    @JvmStatic
    fun instrumentView(view: View, eventName: String? = null) {
        GaugeXProvider.getUserBehaviorPlugin()?.instrumentView(view, eventName)
    }

    /**
     * Monitor an OkHttpClient
     */
    @JvmStatic
    fun monitorOkHttp(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return GaugeXProvider.getNetworkPlugin()?.addMonitoringToOkHttpClient(builder) ?: builder
    }

    /**
     * Measure performance of an operation
     */
    @JvmStatic
    fun startPerformanceMeasurement(key: String) {
        // Store start time for the key
        val startTimes = mutableMapOf<String, Long>()
        startTimes[key] = System.currentTimeMillis()
    }

    /**
     * End performance measurement
     */
    @JvmStatic
    fun endPerformanceMeasurement(
        key: String, category: String, metadata: Map<String, Any> = emptyMap()
    ) {
        val startTimes = mutableMapOf<String, Long>()
        val startTime = startTimes.remove(key) ?: return
        val duration = System.currentTimeMillis() - startTime

        val event = PerformanceEvent(
            category = category,
            name = key,
            duration = duration,
            metadata = metadata
        )

        reportEvent(event)
    }

    /**
     * Track screen load time
     */
    @JvmStatic
    fun trackScreenLoadTime(
        screenName: String, loadTimeMs: Long, metadata: Map<String, Any> = emptyMap()
    ) {
        val event = PerformanceEvent(
            category = "screen_load",
            name = screenName,
            duration = loadTimeMs,
            metadata = metadata
        )

        reportEvent(event)
    }

    /**
     * Monitor a specific activity manually
     * Typically not needed as automatic monitoring is enabled through 'attach()'
     */
    @JvmStatic
    fun monitorActivity(activity: Activity) {
        GaugeXProvider.getUserBehaviorPlugin()?.monitorActivity(activity)
    }

    /**
     * Report a performance metric
     * Used internally by annotation processing
     */
    @JvmStatic
    internal fun reportPerformanceMetric(name: String, category: String, durationMs: Long, metadata: Map<String, Any> = emptyMap()) {
        // Create and report a performance event
        val event = PerformanceEvent(
            category = category,
            name = name,
            duration = durationMs,
            metadata = metadata
        )
        reportEvent(event)
    }

    /**
     * Report an exception
     * Used internally by annotation processing
     */
    @JvmStatic
    internal fun reportException(exception: Throwable, metadata: Map<String, Any> = emptyMap()) {
        // Here we would create and report an exception event
        // We can delegate to the crash reporting plugin when we add it
        // getCrashReportingPlugin()?.reportException(exception, metadata)
    }
}