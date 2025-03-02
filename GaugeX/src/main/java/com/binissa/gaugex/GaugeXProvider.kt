package com.binissa.api

import android.content.Context
import android.util.Log
import com.binissa.core.data.repository.EventRepositoryImpl
import com.binissa.core.di.ServiceLocator
import com.binissa.core.domain.model.Event
import com.binissa.plugin.PluginRegistry
import com.binissa.plugin.crash.CrashReportingPlugin
import com.binissa.plugin.logging.LogCollectionPlugin
import com.binissa.plugin.network.NetworkMonitoringPlugin
import com.binissa.plugin.performance.PerformanceEvent
import com.binissa.plugin.performance.PerformanceMonitoringPlugin
import com.binissa.plugin.user.UserBehaviorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Provider class for GaugeX that manages the lifecycle and state of the SDK
 * This class coordinates the plugins and handles event collection/transmission
 */
object GaugeXProvider {
    private const val TAG = "GaugeXProvider"

    // State flags
    private val initialized = AtomicBoolean(false)
    private val monitoringEnabled = AtomicBoolean(true)

    // Core components
    private lateinit var applicationContext: Context
    private lateinit var config: GaugeXConfig
    private lateinit var pluginRegistry: PluginRegistry

    // Provider-specific coroutine scope (each plugin has its own scope)
    private lateinit var providerScope: CoroutineScope

    // Jobs for event collection and transmission
    private var eventCollectionJob: Job? = null
    private var periodicTransmissionJob: Job? = null

    // Plugin instances
    private var crashReportingPlugin: CrashReportingPlugin? = null
    private var performancePlugin: PerformanceMonitoringPlugin? = null
    private var networkPlugin: NetworkMonitoringPlugin? = null
    private var userBehaviorPlugin: UserBehaviorPlugin? = null
    private var logCollectionPlugin: LogCollectionPlugin? = null


    private val pendingEvents = ConcurrentLinkedQueue<Event>()
    private var batchProcessingJob: Job? = null

    private val metricsAggregation = mutableMapOf<Long, MutableMap<String, Any>>()

    private fun startBatchProcessing() {
        batchProcessingJob?.cancel()
        batchProcessingJob = providerScope.launch {
            while (isActive) {
                try {
                    if (pendingEvents.isNotEmpty()) {
                        val batch = mutableListOf<Event>()
                        while (batch.size < 50 && pendingEvents.isNotEmpty()) {
                            val event = pendingEvents.poll() ?: break
                            batch.add(event)
                        }

                        if (batch.isNotEmpty()) {
                            Log.d(TAG, "Processing batch of ${batch.size} events")
                            val collectEventUseCase = ServiceLocator.getCollectEventUseCase()
                            collectEventUseCase.executeBatch(batch)
                        }
                    }

                    delay(5000) // Process batches every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in batch processing", e)
                    delay(10000) // If error, wait longer
                }
            }
        }
    }

    // Modify event collection to use batching
    private fun startEventCollection() {
        // Start batch processing
        startBatchProcessing()

        // Cancel any existing job
        eventCollectionJob?.cancel()

        // Start new collection job
        eventCollectionJob = providerScope.launch {
            try {
                // Get merged event flow from all plugins
                val pluginEventFlow = pluginRegistry.getAllPluginEventFlows()

                // Collect events from the flow
                pluginEventFlow.onEach { event ->
                    // Only collect if monitoring is enabled
                    if (monitoringEnabled.get()) {
                        // Add to pending queue for batch processing
                        pendingEvents.add(event)
                    }
                }.collect()
            } catch (e: Exception) {
                Log.e(TAG, "Error in event collection", e)
                // Try to restart event collection after a delay
                withContext(Dispatchers.IO) {
                    Thread.sleep(5000)
                    startEventCollection()
                }
            }
        }
    }


    /**
     * Check if SDK is initialized
     */
    fun isInitialized(): Boolean = initialized.get()

    /**
     * Initialize the GaugeX SDK
     * @param context Application context
     * @param config Custom configuration
     */
    fun initialize(context: Context, config: GaugeXConfig = GaugeXConfig.Builder().build()) {
        // Ensure initialization happens only once
        if (initialized.getAndSet(true)) {
            Log.w(TAG, "GaugeX SDK already initialized")
            return
        }

        this.applicationContext = context.applicationContext
        this.config = config

        // Initialize the service locator for data layer
        ServiceLocator.initialize(applicationContext)

        // Set up provider coroutine scope with error handling
        providerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Set up plugin registry
        pluginRegistry = PluginRegistry(providerScope)

        // Initialize plugins first - this must complete before we start collection
        providerScope.launch {
            initializePlugins()

            // After plugins are initialized, THEN start event collection and transmission
            providerScope.launch { startEventCollection() }
            providerScope.launch { startPeriodicTransmission() }

            providerScope.launch { monitorDatabaseSize(context) }
            providerScope.launch { scheduleDbOptimization() }

            Log.i(
                TAG,
                "GaugeX SDK fully initialized with ${pluginRegistry.getPluginCount()} plugins"
            )
        }
    }

    /**
     * Initialize plugins based on configuration
     */
    private fun initializePlugins() {
        providerScope.launch {
            try {
                Log.d(TAG, "Starting plugin initialization")

                // Create and register plugins based on config
                if (config.enableCrashReporting) {
                    crashReportingPlugin = CrashReportingPlugin()
                    pluginRegistry.registerPlugin(crashReportingPlugin!!)
                    Log.d(TAG, "Registered CrashReportingPlugin")
                }

                if (config.enablePerformanceMonitoring) {
                    performancePlugin = PerformanceMonitoringPlugin()
                    pluginRegistry.registerPlugin(performancePlugin!!)
                    Log.d(TAG, "Registered PerformanceMonitoringPlugin")
                }


                if (config.enableNetworkMonitoring) {
                    networkPlugin = NetworkMonitoringPlugin()
                    pluginRegistry.registerPlugin(networkPlugin!!)
                }

                if (config.enableUserBehaviorTracking) {
                    userBehaviorPlugin = UserBehaviorPlugin()
                    pluginRegistry.registerPlugin(userBehaviorPlugin!!)
                }

                if (config.enableLogCollection) {
                    logCollectionPlugin = LogCollectionPlugin()
                    pluginRegistry.registerPlugin(logCollectionPlugin!!)
                }

                // Create configuration map for plugins
                val pluginConfigs = createPluginConfigs()

                // Initialize all registered plugins
                Log.d(TAG, "Initializing ${pluginRegistry.getPluginCount()} registered plugins")
                pluginRegistry.initializePlugins(applicationContext, pluginConfigs)

                // Start monitoring if enabled
                if (monitoringEnabled.get()) {
                    Log.d(TAG, "Starting monitoring for all plugins")
                    pluginRegistry.startMonitoring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize plugins", e)
            }
        }
    }

    /**
     * Create configuration maps for each plugin
     */
    private fun createPluginConfigs(): Map<String, Map<String, Any>> {
        val result = mutableMapOf<String, MutableMap<String, Any>>()

        // Add common configuration
        val commonConfig = mutableMapOf<String, Any>()
        config.apiKey?.let { commonConfig["apiKey"] = it }

        // Create configs for each plugin type
        if (config.enableCrashReporting) {
            result["crash"] = commonConfig.toMutableMap()
        }

        if (config.enablePerformanceMonitoring) {
            result["performance"] = commonConfig.toMutableMap()
        }

        if (config.enableNetworkMonitoring) {
            result["network"] = commonConfig.toMutableMap()
        }

        if (config.enableUserBehaviorTracking) {
            result["user"] = commonConfig.toMutableMap()
        }

        if (config.enableLogCollection) {
            result["logging"] = commonConfig.toMutableMap()
        }

        return result
    }

    /**
     * Start collecting events from plugins
     */
//    private fun startEventCollection() {
//        // Cancel any existing job
//        eventCollectionJob?.cancel()
//
//        // Start new collection job
//        eventCollectionJob = providerScope.launch {
//            try {
//                // Wait until we have plugins registered
//                while (pluginRegistry.getPluginCount() == 0) {
//                    Log.d(TAG, "Waiting for plugins to be registered...")
//                    delay(100)
//                }
//
//                Log.d(
//                    TAG,
//                    "Starting event collection with ${pluginRegistry.getPluginCount()} plugins"
//                )
//
//                // Get merged event flow from all plugins
//                val pluginEventFlow = pluginRegistry.getAllPluginEventFlows()
//
//                // Collect events from the flow
//                pluginEventFlow.onEach { event ->
//                    Log.d(TAG, "Received event: ${event.id} of type ${event.type}")
//
//                    // Only collect if monitoring is enabled
//                    if (monitoringEnabled.get()) {
//                        try {
//                            val collectEventUseCase = ServiceLocator.getCollectEventUseCase()
//                            Log.d(TAG, "Executing CollectEventUseCase for event: ${event.id}")
//                            collectEventUseCase.execute(event)
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Error in collectEventUseCase.execute", e)
//                        }
//                    } else {
//                        Log.d(TAG, "Monitoring disabled, event not collected")
//                    }
//                }.collect()
//            } catch (e: Exception) {
//                Log.e(TAG, "Error in event collection", e)
//            }
//        }
//    }

    /**
     * Start periodic transmission of events to backend
     */
    private fun startPeriodicTransmission() {
        // Cancel any existing job
        periodicTransmissionJob?.cancel()

        // Start new transmission job
        periodicTransmissionJob = providerScope.launch {
            try {
                // For a real implementation, use a more sophisticated approach with retries
                while (true) {
                    // Wait between transmissions (configurable)
                    val transmissionInterval = 15 * 60 * 1000L // 15 minutes default
                    withContext(Dispatchers.IO) {
                        Thread.sleep(transmissionInterval)
                    }

                    // Transmit events
                    flushEventsInternal()

                    // Cleanup old events
                    purgeOldEvents()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in periodic transmission", e)

                // Try to restart transmission after a delay
                withContext(Dispatchers.IO) {
                    Thread.sleep(10000)
                    startPeriodicTransmission()
                }
            }
        }
    }

    /**
     * Flush events to backend immediately
     */
    private suspend fun flushEventsInternal() {
        try {
            val transmitEventsUseCase = ServiceLocator.getTransmitEventsUseCase()
            val result = transmitEventsUseCase.execute()

            if (result.isSuccess) {
                Log.i(TAG, "Successfully transmitted ${result.getOrDefault(0)} events")
            } else {
                Log.e(TAG, "Failed to transmit events", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error flushing events", e)
        }
    }

    /**
     * Purge old events to prevent unbounded growth
     */
    private suspend fun purgeOldEvents() {
        try {
            val eventRepository = ServiceLocator.getEventRepository()
            val cutoffTime = System.currentTimeMillis() - config.maxEventAge
            val purgedCount = eventRepository.purgeOldEvents(cutoffTime)

            if (purgedCount > 0) {
                Log.i(TAG, "Purged $purgedCount old events")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error purging old events", e)
        }
    }


    // Add a method to aggregate metrics events rather than storing each one
    private fun aggregateMetricsEvent(event: Event): Event? {
        // Only aggregate specific types of events
        if (event !is PerformanceEvent || event.category != "device_metrics") {
            return event
        }

        // Use a time window for aggregation (e.g., 5 minutes)
        val aggregationWindow = 5 * 60 * 1000L // 5 minutes
        val windowKey = event.timestamp / aggregationWindow

        // Store in memory for aggregation
        val metrics = event.metadata.toMutableMap()

        // If we already have metrics for this window, aggregate them
        synchronized(metricsAggregation) {
            val existingMetrics = metricsAggregation[windowKey]
            if (existingMetrics != null) {
                // Aggregate numeric values, keep latest for non-numeric
                existingMetrics.forEach { (key, value) ->
                    if (value is Number && metrics[key] is Number) {
                        // For numeric values, calculate average
                        val newValue =
                            ((value.toDouble() + (metrics[key] as Number).toDouble()) / 2)
                        metrics[key] = newValue
                    }
                }
                // Skip storing this event since we've aggregated it
                return null
            } else {
                // Store for future aggregation
                metricsAggregation[windowKey] = metrics
                // Clean up old aggregation data
                val oldKeys = metricsAggregation.keys.filter { it < windowKey - 10 }
                oldKeys.forEach { metricsAggregation.remove(it) }
            }
        }

        // Return the original event for the first in each window
        return event
    }

    private fun monitorDatabaseSize(context: Context) {
        providerScope.launch {
            while (isActive) {
                try {
                    val eventRepository = ServiceLocator.getEventRepository()
                    val size =
                        (eventRepository as? EventRepositoryImpl)?.getDatabaseSize() ?: 0L

                    Log.d(TAG, "Current database size: ${size / 1024}KB")

                    // If database exceeds threshold, trigger aggressive purging
                    val maxSizeBytes = config.maxStorageSize
                    if (size > maxSizeBytes * 0.9) { // At 90% of max
                        Log.w(TAG, "Database approaching size limit, purging aggressively")
                        val currentTime = System.currentTimeMillis()
                        // Keep only last 24 hours of data in this case
                        val purgedCount =
                            eventRepository.purgeOldEvents(currentTime - 24 * 60 * 60 * 1000)
                        Log.i(TAG, "Aggressively purged $purgedCount events")
                    }

                    delay(30 * 60 * 1000) // Check every 30 minutes
                } catch (e: Exception) {
                    Log.e(TAG, "Error monitoring database size", e)
                    delay(60 * 60 * 1000) // If error, check again in 1 hour
                }
            }
        }
    }

    private suspend fun scheduleDbOptimization() {
        providerScope.launch {
            while (isActive) {
                try {
                    // Run optimization during likely idle times (e.g., 3 AM)
                    val currentTimeMillis = System.currentTimeMillis()
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = currentTimeMillis

                    val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                    val targetHour = 3 // 3 AM

                    // Calculate time until next optimization window
                    val hoursToWait = if (currentHour < targetHour) {
                        targetHour - currentHour
                    } else {
                        24 - currentHour + targetHour
                    }

                    val delayMs = hoursToWait * 60 * 60 * 1000L
                    delay(delayMs)

                    // Run optimization
                    val eventRepository =
                        ServiceLocator.getEventRepository() as? EventRepositoryImpl
                    eventRepository?.optimizeDatabase()

                    delay(24 * 60 * 60 * 1000) // Wait 24 hours until next check
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling database optimization", e)
                    delay(6 * 60 * 60 * 1000) // If error, retry in 6 hours
                }
            }
        }
    }

    /**
     * Public methods for the GaugeX API to use
     */

    /**
     * Get the network monitoring plugin
     */
    fun getNetworkPlugin(): NetworkMonitoringPlugin? {
        return networkPlugin
    }

    /**
     * Get the user behavior plugin
     */
    fun getUserBehaviorPlugin(): UserBehaviorPlugin? {
        return userBehaviorPlugin
    }

    /**
     * Get the log collection plugin
     */
    fun getLogCollectionPlugin(): LogCollectionPlugin? {
        return logCollectionPlugin
    }

    /**
     * Get the performance monitoring plugin
     */
    fun getPerformancePlugin(): PerformanceMonitoringPlugin? {
        return performancePlugin
    }


    fun getCrashReportingPlugin(): CrashReportingPlugin? {
        return crashReportingPlugin
    }


    /**
     * Report an event manually
     * @param event The event to report
     */
    fun reportEvent(event: Event) {
        if (!initialized.get() || !monitoringEnabled.get()) return

        providerScope.launch {
            try {
                val collectEventUseCase = ServiceLocator.getCollectEventUseCase()
                collectEventUseCase.execute(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting event", e)
            }
        }
    }

    /**
     * Log a message through the log collection plugin
     * @param level Log level (from LogCollectionPlugin.LogLevel)
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!initialized.get() || !monitoringEnabled.get()) return

        try {
            logCollectionPlugin?.let { plugin ->
                val logLevel = try {
                    LogCollectionPlugin.LogLevel.valueOf(level.uppercase())
                } catch (e: Exception) {
                    LogCollectionPlugin.LogLevel.INFO
                }

                plugin.log(logLevel, tag, message, throwable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging message", e)
        }
    }

    /**
     * Flush events to backend immediately
     */
    fun flushEvents() {
        if (!initialized.get()) return

        providerScope.launch {
            flushEventsInternal()
        }
    }

    /**
     * Disable all monitoring
     */
    fun disableMonitoring() {
        if (!initialized.get() || !monitoringEnabled.getAndSet(false)) return

        providerScope.launch {
            try {
                pluginRegistry.stopMonitoring()
                Log.i(TAG, "Monitoring disabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error disabling monitoring", e)
            }
        }
    }

    /**
     * Enable all monitoring after it has been disabled
     */
    fun enableMonitoring() {
        if (!initialized.get() || monitoringEnabled.getAndSet(true)) return

        providerScope.launch {
            try {
                pluginRegistry.startMonitoring()
                Log.i(TAG, "Monitoring enabled")
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling monitoring", e)
            }
        }
    }

    /**
     * Shutdown the SDK and clean up resources
     */
    fun shutdown() {
        if (!initialized.get()) return

        try {
            // Cancel all provider jobs
            eventCollectionJob?.cancel()
            periodicTransmissionJob?.cancel()

            // Shutdown plugins
            providerScope.launch {
                try {
                    // Flush any pending events before shutdown
                    flushEventsInternal()

                    // Shutdown all plugins
                    pluginRegistry.shutdown()

                    // Clear plugin references
                    crashReportingPlugin = null
                    performancePlugin = null
                    networkPlugin = null
                    userBehaviorPlugin = null
                    logCollectionPlugin = null

                    Log.i(TAG, "GaugeX SDK shutdown complete")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during shutdown", e)
                } finally {
                    // Cancel the provider scope
                    providerScope.cancel()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down GaugeX SDK", e)
        }

        initialized.set(false)
    }
}