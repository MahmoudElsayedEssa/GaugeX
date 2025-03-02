package com.binissa.api

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.view.View
import com.binissa.api.annotations.GaugeXProcessor
import com.binissa.core.data.repository.EventRepositoryImpl
import com.binissa.core.di.ServiceLocator
import com.binissa.core.domain.model.Event
import com.binissa.plugin.logging.LogCollectionPlugin.LogLevel
import com.binissa.plugin.performance.PerformanceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

object GaugeX {
    /**
     * Initialize the SDK
     * @param context Application context
     * @param config Custom configuration
     */

    // Add to GaugeX.kt at the top of the object
    private const val TAG = "GaugeX"
    private val providerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    @JvmStatic
    fun initialize(context: Context, config: GaugeXConfig = GaugeXConfig.Builder().build()) {
        GaugeXProvider.initialize(context, config)
    }

    @JvmStatic
    fun purgeOldData(keepDataForDays: Int = 7) {
        if (!isInitialized()) return

        providerScope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (keepDataForDays * 24 * 60 * 60 * 1000L)
                val eventRepository = ServiceLocator.getEventRepository()
                val purgedCount = eventRepository.purgeOldEvents(cutoffTime)
                Log.i(TAG, "Purged $purgedCount events older than $keepDataForDays days")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge old data", e)
            }
        }
    }

    @JvmStatic
    fun getDatabaseStats(callback: (Map<String, Any>) -> Unit,context: Context) {
        if (!isInitialized()) {
            callback(mapOf("error" to "SDK not initialized"))
            return
        }

        providerScope.launch {
            try {
                val eventRepository = ServiceLocator.getEventRepository()
                val totalCount = eventRepository.getTotalEventCount()
                val dbSize = (eventRepository as? EventRepositoryImpl)?.getDatabaseSize( ) ?: 0L

                val stats = mapOf(
                    "totalEvents" to totalCount,
                    "databaseSizeBytes" to dbSize,
                    "databaseSizeKB" to (dbSize / 1024),
                    "databaseSizeMB" to (dbSize / (1024 * 1024))
                )

                withContext(Dispatchers.Main) {
                    callback(stats)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get database stats", e)
                withContext(Dispatchers.Main) {
                    callback(mapOf("error" to "Failed to get database stats: ${e.message}"))
                }
            }
        }
    }

    // Add to GaugeX.kt
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
//        getCrashReportingPlugin()?.reportException(exception, metadata)
    }
    /**
     * Force database optimization immediately
     */
    @JvmStatic
    fun optimizeDatabaseNow() {
        if (!isInitialized()) return

        providerScope.launch {
            try {
                val eventRepository = ServiceLocator.getEventRepository() as? EventRepositoryImpl
                eventRepository?.optimizeDatabase()
                Log.i(TAG, "Manual database optimization completed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to manually optimize database", e)
            }
        }
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
        // in their startMonitoring methods, so we just need to ensure
        // that monitoring is enabled
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
        GaugeXProvider.log(LogLevel.INFO.name, tag, message, throwable)
    }

    /**
     * Log a message at DEBUG level
     */
    @JvmStatic
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        GaugeXProvider.log(LogLevel.DEBUG.name, tag, message, throwable)
    }

    /**
     * Log a message at WARNING level
     */
    @JvmStatic
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        GaugeXProvider.log(LogLevel.WARNING.name, tag, message, throwable)
    }

    /**
     * Log a message at ERROR level
     */
    @JvmStatic
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        GaugeXProvider.log(LogLevel.ERROR.name, tag, message, throwable)
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
//        GaugeXProvider.getPerformancePlugin()?.startMeasurement(key)
    }

    /**
     * End performance measurement
     */
    @JvmStatic
    fun endPerformanceMeasurement(
        key: String, category: String, metadata: Map<String, Any> = emptyMap()
    ) {
//        GaugeXProvider.getPerformancePlugin()?.endMeasurement(key, category, metadata)
    }

    /**
     * Track screen load time
     */
    @JvmStatic
    fun trackScreenLoadTime(
        screenName: String, loadTimeMs: Long, metadata: Map<String, Any> = emptyMap()
    ) {
//        GaugeXProvider.getPerformancePlugin()?.trackScreenLoadTime(screenName, loadTimeMs, metadata)
    }

    /**
     * Monitor a specific activity manually
     * Typically not needed as automatic monitoring is enabled through 'attach()'
     */
    @JvmStatic
    fun monitorActivity(activity: Activity) {
        GaugeXProvider.getUserBehaviorPlugin()?.monitorActivity(activity)
    }
}
