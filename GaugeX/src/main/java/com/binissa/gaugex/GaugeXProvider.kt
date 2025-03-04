package com.binissa.gaugex

import android.content.Context
import android.util.Log
import com.binissa.core.di.ServiceLocator
import com.binissa.core.domain.model.GaugeXConfig
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.usecase.CoreModule
import com.binissa.core.domain.usecase.CoreModuleImpl
import com.binissa.plugin.PluginRegistry
import com.binissa.plugin.crash.CrashReportingPlugin
import com.binissa.plugin.logging.LogCollectionPlugin
import com.binissa.plugin.network.NetworkMonitoringPlugin
import com.binissa.plugin.performance.PerformanceMonitoringPlugin
import com.binissa.plugin.user.UserBehaviorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private lateinit var coreModule: CoreModule
    private lateinit var pluginRegistry: PluginRegistry

    // Provider-specific coroutine scope
    private lateinit var providerScope: CoroutineScope

    // Jobs for event collection and transmission
    private var eventCollectionJob: Job? = null
    private var periodicTransmissionJob: Job? = null
    private var maintenanceJob: Job? = null

    // Plugin instances
    private var crashReportingPlugin: CrashReportingPlugin? = null
    private var performancePlugin: PerformanceMonitoringPlugin? = null
    private var networkPlugin: NetworkMonitoringPlugin? = null
    private var userBehaviorPlugin: UserBehaviorPlugin? = null
    private var logCollectionPlugin: LogCollectionPlugin? = null

    // Event queue for batch processing
    private val pendingEvents = ConcurrentLinkedQueue<Event>()

    /**
     * Initialize the GaugeX SDK
     */
    fun initialize(context: Context, config: GaugeXConfig = GaugeXConfig.Builder().build()) {
        if (initialized.getAndSet(true)) {
            Log.w(TAG, "GaugeX SDK already initialized")
            return
        }

        this.applicationContext = context.applicationContext
        this.config = config

        // Initialize the service locator
        ServiceLocator.initialize(context, config)

        // Get the core module
        coreModule = ServiceLocator.getCoreModule()

        // Set up provider coroutine scope
        providerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Set up plugin registry
        pluginRegistry = PluginRegistry(providerScope)

        // Initialize plugins
        initializePlugins()

        // Start collection, transmission, and maintenance
        startEventCollection()
        startPeriodicTransmission()
        startDatabaseMaintenance()

        Log.i(TAG, "GaugeX SDK initialized")
    }

    /**
     * Initialize plugins based on configuration
     */
    private fun initializePlugins() {
        try {
            // Create and register plugins based on config
            if (config.enableCrashReporting) {
                crashReportingPlugin = CrashReportingPlugin()
                pluginRegistry.registerPlugin(crashReportingPlugin!!)
            }

            if (config.enablePerformanceMonitoring) {
                performancePlugin = PerformanceMonitoringPlugin()
                pluginRegistry.registerPlugin(performancePlugin!!)
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
            providerScope.launch {
                pluginRegistry.initializePlugins(applicationContext, pluginConfigs)

                // Start monitoring if enabled
                if (monitoringEnabled.get()) {
                    pluginRegistry.startMonitoring()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize plugins", e)
        }
    }

    /**
     * Create plugin configurations
     */
    private fun createPluginConfigs(): Map<String, Map<String, Any>> {
        // Same implementation as before
        val result = mutableMapOf<String, MutableMap<String, Any>>()
        val commonConfig = mutableMapOf<String, Any>()
        config.apiKey?.let { commonConfig["apiKey"] = it }

        if (config.enableCrashReporting) result["crash"] = commonConfig.toMutableMap()
        if (config.enablePerformanceMonitoring) result["performance"] = commonConfig.toMutableMap()
        if (config.enableNetworkMonitoring) result["network"] = commonConfig.toMutableMap()
        if (config.enableUserBehaviorTracking) result["user"] = commonConfig.toMutableMap()
        if (config.enableLogCollection) result["logging"] = commonConfig.toMutableMap()

        return result
    }

    /**
     * Start collecting events from plugins
     */
    private fun startEventCollection() {
        // Cancel any existing job
        eventCollectionJob?.cancel()

        // Start new collection job
        eventCollectionJob = providerScope.launch {
            try {
                // Get merged event flow from all plugins
                pluginRegistry.getAllPluginEventFlows().collect { event ->
                    // Only collect if monitoring is enabled
                    if (monitoringEnabled.get()) {
                        // Add to pending queue for batch processing
                        pendingEvents.add(event)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in event collection", e)
                delay(5000)
                startEventCollection()
            }
        }

        // Start batch processing job
        providerScope.launch {
            while (isActive) {
                try {
                    if (pendingEvents.isNotEmpty()) {
                        val batch = mutableListOf<Event>()
                        while (batch.size < 50 && pendingEvents.isNotEmpty()) {
                            pendingEvents.poll()?.let { batch.add(it) }
                        }

                        if (batch.isNotEmpty()) {
                            val processedCount = coreModule.storeEvents(batch)
                            Log.d(TAG, "Processed $processedCount/${batch.size} events")
                        }
                    }

                    delay(5000) // Process batches every 5 seconds
                } catch (e: Exception) {
                    Log.e(TAG, "Error in batch processing", e)
                    delay(10000) // Wait longer on error
                }
            }
        }
    }

    /**
     * Start periodic transmission of events
     */
    private fun startPeriodicTransmission() {
        // Cancel any existing job
        periodicTransmissionJob?.cancel()

        // Start new transmission job
        periodicTransmissionJob = providerScope.launch {
            try {
                while (isActive) {
                    // Wait between transmissions
                    delay(15 * 60 * 1000) // 15 minutes

                    // Transmit events
                    val result = coreModule.transmitEvents()
                    if (result.sent > 0 || result.failed > 0) {
                        Log.i(TAG, "Transmitted ${result.sent} events, ${result.failed} failed")
                    }

                    // Purge old events
                    val cutoffTime = System.currentTimeMillis() - config.maxEventAge
                    val purgedCount = coreModule.purgeOldEvents(cutoffTime)
                    if (purgedCount > 0) {
                        Log.i(TAG, "Purged $purgedCount old events")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in periodic transmission", e)
                delay(10 * 60 * 1000) // 10 minutes on error
                startPeriodicTransmission()
            }
        }
    }

    /**
     * Start database maintenance job
     */
    private fun startDatabaseMaintenance() {
        // Cancel any existing job
        maintenanceJob?.cancel()

        // Start new maintenance job
        maintenanceJob = providerScope.launch {
            try {
                while (isActive) {
                    // Run maintenance during likely idle times (e.g., 3 AM)
                    val calendar = Calendar.getInstance()
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

                    // Optimize database
                    if (coreModule.optimizeDatabase()) {
                        Log.i(TAG, "Database optimization completed")
                    }

                    // Check database size
                    val stats = coreModule.getDatabaseStats()
                    val sizeBytes = stats["databaseSizeBytes"] as? Long ?: 0L

                    // If approaching size limit, purge more aggressively
                    if (sizeBytes > config.maxStorageSize * 0.9) {
                        Log.w(TAG, "Database approaching size limit, purging aggressively")
                        val currentTime = System.currentTimeMillis()
                        // Keep only last 24 hours of data
                        val purgedCount = coreModule.purgeOldEvents(currentTime - 24 * 60 * 60 * 1000)
                        Log.i(TAG, "Aggressively purged $purgedCount events")
                    }

                    // Wait until next day
                    delay(24 * 60 * 60 * 1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in database maintenance", e)
                delay(6 * 60 * 60 * 1000) // Retry in 6 hours
                startDatabaseMaintenance()
            }
        }
    }

    /**
     * Public methods for the GaugeX API
     */

    /**
     * Check if SDK is initialized
     */
    fun isInitialized(): Boolean = initialized.get()

    /**
     * Report an event manually
     */
    fun reportEvent(event: Event) {
        if (!initialized.get() || !monitoringEnabled.get()) return

        providerScope.launch {
            try {
                if (coreModule.storeEvent(event)) {
                    Log.d(TAG, "Successfully reported event: ${event.id}")
                } else {
                    Log.e(TAG, "Failed to report event: ${event.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reporting event", e)
            }
        }
    }

    /**
     * Flush events to backend immediately
     */
    fun flushEvents() {
        if (!initialized.get()) return

        providerScope.launch {
            try {
                val result = coreModule.transmitEvents()
                Log.i(TAG, "Manual flush: ${result.sent} sent, ${result.failed} failed")
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing events", e)
            }
        }
    }

    /**
     * Get database statistics
     */
    fun getDatabaseStats(callback: (Map<String, Any>) -> Unit) {
        if (!initialized.get()) {
            callback(mapOf("error" to "SDK not initialized"))
            return
        }

        providerScope.launch {
            try {
                val stats = coreModule.getDatabaseStats()
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

    /**
     * Force database optimization immediately
     */
    fun optimizeDatabaseNow() {
        if (!initialized.get()) return

        providerScope.launch {
            try {
                if (coreModule.optimizeDatabase()) {
                    Log.i(TAG, "Manual database optimization completed")
                } else {
                    Log.e(TAG, "Failed to manually optimize database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to manually optimize database", e)
            }
        }
    }

    /**
     * Purge old data
     */
    fun purgeOldData(keepDataForDays: Int = 7) {
        if (!initialized.get()) return

        providerScope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - (keepDataForDays * 24 * 60 * 60 * 1000L)
                val purgedCount = coreModule.purgeOldEvents(cutoffTime)
                Log.i(TAG, "Purged $purgedCount events older than $keepDataForDays days")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to purge old data", e)
            }
        }
    }

    /**
     * Enable/disable monitoring
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
     * Plugin getters
     */
    fun getNetworkPlugin(): NetworkMonitoringPlugin? = networkPlugin
    fun getUserBehaviorPlugin(): UserBehaviorPlugin? = userBehaviorPlugin
    fun getLogCollectionPlugin(): LogCollectionPlugin? = logCollectionPlugin
    fun getPerformancePlugin(): PerformanceMonitoringPlugin? = performancePlugin
    fun getCrashReportingPlugin(): CrashReportingPlugin? = crashReportingPlugin

    /**
     * Shutdown the SDK and clean up resources
     */
    fun shutdown() {
        if (!initialized.get()) return

        try {
            // Cancel all provider jobs
            eventCollectionJob?.cancel()
            periodicTransmissionJob?.cancel()
            maintenanceJob?.cancel()

            // Shutdown plugins
            providerScope.launch {
                try {
                    // Flush any pending events before shutdown
                    flushEvents()

                    // Shutdown all plugins
                    pluginRegistry.shutdown()

                    // Clear plugin references
                    crashReportingPlugin = null
                    performancePlugin = null
                    networkPlugin = null
                    userBehaviorPlugin = null
                    logCollectionPlugin = null

                    // Shutdown core module
                    ServiceLocator.shutdown()

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