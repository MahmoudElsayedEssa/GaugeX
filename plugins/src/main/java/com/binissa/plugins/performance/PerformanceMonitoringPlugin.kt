package com.binissa.plugin.performance

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.plugin.GaugeXPlugin
import com.binissa.plugin.performance.monitor.ActivityLifecycleMonitor
import com.binissa.plugin.performance.monitor.AnrMonitor
import com.binissa.plugin.performance.monitor.DeviceMetricsMonitor
import com.binissa.plugin.performance.monitor.FrameMetricsMonitor
import com.binissa.plugin.performance.monitor.MemoryMonitor
import com.binissa.plugin.performance.monitor.PerformanceMonitor
import com.binissa.plugin.performance.monitor.StartupMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Enhanced plugin for performance monitoring
 * Tracks app startup time, screen loading, frame rates, and device metrics
 */

class PerformanceMonitoringPlugin : GaugeXPlugin {
    private val eventFlow = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitors = mutableListOf<PerformanceMonitor>()
    private lateinit var appContext: Context

    override val id: String = "performance"
    private val TAG = "PerformanceMonitoringPlugin"

    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
        return try {
            appContext = context.applicationContext
            val processStartTime = estimateProcessStartTime()

            monitors.addAll(
                listOf(
                    StartupMonitor(eventFlow, processStartTime),
                    DeviceMetricsMonitor(pluginScope, eventFlow, appContext),
                    FrameMetricsMonitor(pluginScope, eventFlow, appContext),
                    AnrMonitor(pluginScope, eventFlow),
                    MemoryMonitor(pluginScope, eventFlow),
                    ActivityLifecycleMonitor(eventFlow, appContext)
                )
            )
            Log.i(TAG, "Performance monitoring plugin initialized")
            monitors.forEach { it.initialize() }
            true
        } catch (e: Exception) {
            Log.e("PerformancePlugin", "Initialization failed", e)
            false
        }
    }

    override suspend fun startMonitoring() = monitors.forEach { it.start() }
    override suspend fun stopMonitoring() = monitors.forEach { it.stop() }
    override fun getEvents(): Flow<Event> = eventFlow.asSharedFlow()
    override suspend fun shutdown() {
        pluginScope.cancel()
        monitors.clear()
    }

    private fun estimateProcessStartTime() =
        SystemClock.elapsedRealtime() - (SystemClock.uptimeMillis() / 2)
}

//class PerformanceMonitoringPlugin : GaugeXPlugin {
//    private val TAG = "PerformancePlugin"
//
//    // Flow of performance events
//    private val _eventFlow = MutableSharedFlow<Event>(replay = 0)
//
//    // Context to access app info
//    private lateinit var appContext: Context
//
//    // Store timestamps for different metrics
//    private val timestamps = ConcurrentHashMap<String, Long>()
//
//    // Store process start time - important for cold start measurement
//    private var processStartTime: Long = 0
//
//    // Plugin-specific coroutine scope
//    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
//
//    // Jobs for various monitoring tasks
//    private var metricsJob = SupervisorJob()
//    private var frameMetricsJob = SupervisorJob()
//    private var anrDetectionJob = SupervisorJob()
//    private var memoryMonitoringJob = SupervisorJob()
//
//    // Flag to control monitoring state
//    private var isMonitoring = false
//
//    override val id: String = "performance"
//
//    private lateinit var deviceInfoHelper: DeviceInfoHelper
//
//    /**
//     * Initialize the performance monitoring plugin
//     */
//    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
//        try {
//            this.appContext = context.applicationContext
//
//            // Record process start time estimation
//            // Note: This is approximate since we're not running at the actual process start
//            processStartTime = SystemClock.elapsedRealtime() - (SystemClock.uptimeMillis() / 2)
//            deviceInfoHelper = DeviceInfoHelper(appContext)
//
//            Log.i(TAG, "Performance monitoring plugin initialized")
//            return true
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to initialize performance monitoring plugin", e)
//            return false
//        }
//    }
//
//    /**
//     * Collects CPU usage information
//     * @return Map of CPU metrics
//     */
//    private fun collectCpuInfo(): Map<String, Any> {
//        val metrics = mutableMapOf<String, Any>()
//
//        try {
//            // 1. Get process-level CPU info
//            val pid = android.os.Process.myPid()
//            val procStatFile = File("/proc/$pid/stat")
//            if (procStatFile.exists()) {
//                val startTime = System.currentTimeMillis()
//                val startStatString = procStatFile.readText()
//                val startStatParts = startStatString.split(" ")
//
//                // User and system CPU time in clock ticks
//                val startUserTime = startStatParts[13].toLong()
//                val startSysTime = startStatParts[14].toLong()
//
//                // Wait a short period to measure delta
//                Thread.sleep(500)
//
//                val endTime = System.currentTimeMillis()
//                val endStatString = procStatFile.readText()
//                val endStatParts = endStatString.split(" ")
//
//                // Updated times
//                val endUserTime = endStatParts[13].toLong()
//                val endSysTime = endStatParts[14].toLong()
//
//                // Calculate CPU usage
//                val cpuTimeUsed = (endUserTime - startUserTime) + (endSysTime - startSysTime)
//                val timeElapsed = endTime - startTime
//
//                // CPU usage as percentage (this is an approximation)
//                val cpuUsagePercent = cpuTimeUsed * 100.0 / timeElapsed
//
//                metrics["app_cpu_usage_percent"] = cpuUsagePercent
//            }
//
//            // 2. Get device-level CPU info
//            val cpuInfoFile = File("/proc/cpuinfo")
//            if (cpuInfoFile.exists()) {
//                val cpuInfo = cpuInfoFile.readText()
//                val processors = cpuInfo.split("processor").size - 1 // Count processors
//                metrics["cpu_cores"] = processors
//
//                // Extract CPU frequency if available
//                val cpuFreq = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
//                if (cpuFreq.exists()) {
//                    val freqKhz = cpuFreq.readText().trim().toLongOrNull() ?: 0
//                    metrics["cpu_frequency_mhz"] = freqKhz / 1000.0
//                }
//            }
//
//            // 3. Get CPU load averages
//            val loadAvgFile = File("/proc/loadavg")
//            if (loadAvgFile.exists()) {
//                val loadAvg = loadAvgFile.readText().split(" ")
//                if (loadAvg.size >= 3) {
//                    metrics["load_avg_1min"] = loadAvg[0].toDoubleOrNull() ?: 0.0
//                    metrics["load_avg_5min"] = loadAvg[1].toDoubleOrNull() ?: 0.0
//                    metrics["load_avg_15min"] = loadAvg[2].toDoubleOrNull() ?: 0.0
//                }
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error collecting CPU info", e)
//            metrics["cpu_error"] = "Failed to collect CPU metrics: ${e.message}"
//        }
//
//        return metrics
//    }
//
//    /**
//     * Start monitoring performance metrics
//     */
//    override suspend fun startMonitoring() {
//        try {
//            isMonitoring = true
//
//            // 1. For Phase 1, emit a startup time event
//            val startupTime = calculateAppStartupTime()
//            reportAppStartupTime(startupTime)
//
//            // 2. Start device metrics collection
//            startDeviceMetricsCollection()
//
//            // 3. Start frame metrics collection
//            startFrameMetricsCollection()
//
//            // 4. Start ANR detection
//            setupAnrDetection()
//
//            // 5. Start memory monitoring
//            setupMemoryMonitoring()
//
//            // 6. Set up activity monitoring if possible
//            if (appContext is Application) {
//                setupActivityPerformanceTracking(appContext as Application)
//            }
//
//            Log.i(TAG, "Performance monitoring started")
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to start performance monitoring", e)
//        }
//    }
//
//    /**
//     * Stop monitoring performance metrics
//     */
//    override suspend fun stopMonitoring() {
//        try {
//            isMonitoring = false
//
//            // Cancel all monitoring jobs
//            metricsJob.cancel()
//            frameMetricsJob.cancel()
//            anrDetectionJob.cancel()
//            memoryMonitoringJob.cancel()
//
//            // Clear any stored timestamps
//            timestamps.clear()
//
//            Log.i(TAG, "Performance monitoring stopped")
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to stop performance monitoring", e)
//        }
//    }
//
//    /**
//     * Get the flow of performance events
//     */
//    override fun getEvents(): Flow<Event> = _eventFlow.asSharedFlow()
//
//    /**
//     * Shutdown the plugin and clean up resources
//     */
//    override suspend fun shutdown() {
//        try {
//            stopMonitoring()
//
//            // Cancel all coroutines
//            pluginScope.cancel()
//
//            Log.i(TAG, "Performance monitoring plugin shutdown")
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to shutdown performance monitoring plugin", e)
//        }
//    }
//
//    /**
//     * Mark the start of a performance measurement
//     * @param key Identifier for the measurement
//     */
//    fun startMeasurement(key: String) {
//        timestamps[key] = SystemClock.elapsedRealtime()
//    }
//
//    /**
//     * End a performance measurement and report it
//     * @param key Identifier for the measurement
//     * @param category Type of measurement (e.g., "screen_load", "network")
//     * @param metadata Additional information about the measurement
//     */
//    fun endMeasurement(key: String, category: String, metadata: Map<String, Any> = emptyMap()) {
//        val startTime = timestamps.remove(key) ?: return
//        val endTime = SystemClock.elapsedRealtime()
//        val duration = endTime - startTime
//
//        // Create and emit performance event
//        val event = PerformanceEvent(
//            category = category, duration = duration, name = key, metadata = metadata
//        )
//
//        _eventFlow.tryEmit(event)
//
//        Log.d(TAG, "Performance measurement: $key, duration: $duration ms")
//    }
//
//    /**
//     * Calculate app startup time
//     * In Phase 1, this is an approximation
//     */
//    private fun calculateAppStartupTime(): Long {
//        val now = SystemClock.elapsedRealtime()
//        return now - processStartTime
//    }
//
//    /**
//     * Report app startup time as a performance event
//     */
//    private fun reportAppStartupTime(startupTimeMs: Long) {
//        val event = PerformanceEvent(
//            category = "app_startup",
//            duration = startupTimeMs,
//            name = "cold_start",
//            metadata = mapOf(
//                "startup_type" to "cold"
//            )
//        )
//
//        _eventFlow.tryEmit(event)
//
//        Log.i(TAG, "App startup time: $startupTimeMs ms")
//    }
//
//    /**
//     * Track screen load time
//     * @param screenName Name of the screen
//     * @param loadTimeMs Time taken to load the screen
//     * @param metadata Additional information about the screen
//     */
//    fun trackScreenLoadTime(
//        screenName: String, loadTimeMs: Long, metadata: Map<String, Any> = emptyMap()
//    ) {
//        val event = PerformanceEvent(
//            category = "screen_load", duration = loadTimeMs, name = screenName, metadata = metadata
//        )
//
//        _eventFlow.tryEmit(event)
//
//        Log.d(TAG, "Screen load time: $screenName, duration: $loadTimeMs ms")
//    }
//
//    /**
//     * Start collecting device metrics (battery, CPU, memory, etc.)
//     */
//    private fun startDeviceMetricsCollection() {
//        pluginScope.launch(metricsJob) {
//            try {
//                val metricsInterval = 60_000L // 1 minute
//
//                while (isActive && isMonitoring) {
//                    try {
//
//                        val deviceInfo = deviceInfoHelper.getDeviceInfo()
//                        val appInfo = deviceInfoHelper.getAppInfo()
//
//                        // Create metrics from this information
//                        val metrics = mapOf(
//                            "battery_level" to deviceInfo.batteryLevel,
//                            "is_charging" to deviceInfo.isCharging,
//                            "total_memory" to deviceInfo.totalRam,
//                            "available_memory" to deviceInfo.availableRam,
//                            "screen_density" to deviceInfo.screenDensity,
//                            "network_type" to deviceInfo.networkType,
//                            "is_rooted" to deviceInfo.isRooted,
//                            // Add other metrics
//                        )
//
//                        // Also collect CPU info separately (since DeviceInfoHelper doesn't have it)
//                        val cpuInfo = collectCpuInfo()
//                        val allMetrics = metrics + cpuInfo
//
//                        // Collect battery information
//                        val batteryInfo = collectBatteryInfo()
//
//                        // Collect network connectivity info
//                        val networkInfo = collectNetworkInfo()
//
//                        // Collect CPU and memory info
//                        val resourceInfo = collectDeviceResourceInfo() + allMetrics
//
//                        // Create and emit device metrics event
//                        val event = createDeviceMetricsEvent(batteryInfo, networkInfo, resourceInfo)
//                        _eventFlow.tryEmit(event)
//
//                        // Wait for next collection interval
//                        delay(metricsInterval)
//                    } catch (e: CancellationException) {
//                        // Job was cancelled, exit loop
//                        break
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error collecting device metrics", e)
//                        delay(10_000) // Retry after a delay
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Device metrics collection stopped unexpectedly", e)
//            }
//        }
//    }
//
//    /**
//     * Collects battery information from the device
//     */
//    private fun collectBatteryInfo(): Map<String, Any> {
//        try {
//            val batteryIntent = appContext.registerReceiver(
//                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
//            )
//
//            val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
//            val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
//            val batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1
//
//            val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
//            val isCharging =
//                status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
//
//            val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
//            val voltage = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
//
//            return mapOf(
//                "battery_level" to batteryPct,
//                "is_charging" to isCharging,
//                "temperature" to temperature / 10.0, // Convert to Celsius
//                "voltage" to voltage / 1000.0 // Convert to Volts
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error collecting battery info", e)
//            return mapOf("error" to "Failed to collect battery info")
//        }
//    }
//
//    /**
//     * Collects network connectivity information
//     */
//    private fun collectNetworkInfo(): Map<String, Any> {
//        try {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                val connectivityManager =
//                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
//                        ?: return mapOf("network_available" to false)
//
//                val network =
//                    connectivityManager.activeNetwork ?: return mapOf("network_available" to false)
//                val capabilities = connectivityManager.getNetworkCapabilities(network)
//                    ?: return mapOf("network_available" to false)
//
//                val hasWifi =
//                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
//                val hasCellular =
//                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
//                val hasEthernet =
//                    capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
//
//                return mapOf(
//                    "network_available" to true,
//                    "wifi" to hasWifi,
//                    "cellular" to hasCellular,
//                    "ethernet" to hasEthernet,
//                    "metered" to connectivityManager.isActiveNetworkMetered
//                )
//            } else {
//                val connectivityManager =
//                    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
//                        ?: return mapOf("network_available" to false)
//
//                val networkInfo = connectivityManager.activeNetworkInfo
//                val isConnected = networkInfo?.isConnected == true
//
//                return mapOf(
//                    "network_available" to isConnected,
//                    "network_type" to (networkInfo?.typeName ?: "none")
//                )
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error collecting network info", e)
//            return mapOf("error" to "Failed to collect network info")
//        }
//    }
//
//    /**
//     * Collects device CPU and memory resource information
//     */
//    private fun collectDeviceResourceInfo(): Map<String, Any> {
//        try {
//            val activityManager =
//                appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
//                    ?: return mapOf("error" to "ActivityManager not available")
//
//            val memoryInfo = android.app.ActivityManager.MemoryInfo()
//            activityManager.getMemoryInfo(memoryInfo)
//
//            // Get CPU usage (simplified approach)
//            val cpuInfo = try {
//                val process = Runtime.getRuntime().exec("top -n 1")
//                val reader = BufferedReader(InputStreamReader(process.inputStream))
//                val cpuLine = reader.lineSequence().drop(2) // Skip header lines
//                    .firstOrNull { it.contains("CPU") } ?: ""
//
//                val cpuIdle = cpuLine.split("\\s+".toRegex()).find { it.contains("%idle") }
//                    ?.replace("%idle", "")?.toDoubleOrNull() ?: 0.0
//
//                100.0 - cpuIdle // Convert idle percentage to usage percentage
//            } catch (e: Exception) {
//                -1.0 // Error or not available
//            }
//
//            // Get processor info
//            val coreCount = Runtime.getRuntime().availableProcessors()
//
//            return mapOf(
//                "total_memory" to memoryInfo.totalMem,
//                "available_memory" to memoryInfo.availMem,
//                "memory_usage_percent" to ((memoryInfo.totalMem - memoryInfo.availMem) * 100.0 / memoryInfo.totalMem),
//                "low_memory" to memoryInfo.lowMemory,
//                "cpu_usage_percent" to cpuInfo,
//                "cpu_cores" to coreCount,
//                "max_heap_size" to Runtime.getRuntime().maxMemory(),
//                "allocated_heap" to Runtime.getRuntime().totalMemory(),
//                "free_heap" to Runtime.getRuntime().freeMemory()
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Error collecting device resource info", e)
//            return mapOf("error" to "Failed to collect resource info")
//        }
//    }
//
//    /**
//     * Creates device metrics event with all collected information
//     */
//    private fun createDeviceMetricsEvent(
//        batteryInfo: Map<String, Any>, networkInfo: Map<String, Any>, resourceInfo: Map<String, Any>
//    ): PerformanceEvent {
//        // Create a combined metrics map with all the collected data
//        val metrics = mutableMapOf<String, Any>()
//        metrics.putAll(batteryInfo)
//        metrics.putAll(networkInfo)
//        metrics.putAll(resourceInfo)
//
//        // Add device identification info
//        metrics["device_model"] = Build.MODEL
//        metrics["device_manufacturer"] = Build.MANUFACTURER
//        metrics["android_version"] = Build.VERSION.RELEASE
//        metrics["api_level"] = Build.VERSION.SDK_INT
//
//        // Create a custom performance event for device metrics
//        return PerformanceEvent(
//            category = "device_metrics",
//            name = "device_snapshot",
//            duration = 0, // Not a duration-based metric
//            metadata = metrics
//        )
//    }
//
//    /**
//     * Start collecting frame metrics for UI performance
//     */
//    private fun startFrameMetricsCollection() {
//        pluginScope.launch(frameMetricsJob) {
//            try {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    setupFrameMetricsWithChoreographer()
//                } else {
//                    setupLegacyFrameMetrics()
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Error setting up frame metrics collection", e)
//            }
//        }
//    }
//
//    /**
//     * Set up frame metrics collection for Android N and above
//     */
//    @RequiresApi(Build.VERSION_CODES.N)
//    private suspend fun setupFrameMetricsWithChoreographer() {
//        withContext(Dispatchers.Main) {
//            // For Android 7.0 (API 24) and higher, use the Choreographer FrameCallback
//            val frameCallback = object : Choreographer.FrameCallback {
//                private var lastFrameTimeNanos = 0L
//                private var frameCount = 0
//                private val frameTimeList = mutableListOf<Long>()
//                private val MAX_FRAME_COUNT =
//                    120 // Report every 120 frames (about 2 seconds at 60fps)
//
//                override fun doFrame(frameTimeNanos: Long) {
//                    try {
//                        if (lastFrameTimeNanos > 0) {
//                            val frameDurationNanos = frameTimeNanos - lastFrameTimeNanos
//                            frameTimeList.add(frameDurationNanos)
//                            frameCount++
//
//                            if (frameCount >= MAX_FRAME_COUNT) {
//                                // Calculate metrics over the collected frames
//                                val avgFrameTimeMs = frameTimeList.average() / 1_000_000.0
//                                val maxFrameTimeMs =
//                                    frameTimeList.maxOrNull()?.div(1_000_000.0) ?: 0.0
//                                val jankyFrames =
//                                    frameTimeList.count { it > 16_666_666 } // >16.6ms (60fps)
//
//                                // Create and emit performance event
//                                val event = PerformanceEvent(
//                                    category = "frame_rate",
//                                    duration = avgFrameTimeMs.toLong(),
//                                    name = "frame_rate_summary",
//                                    metadata = mapOf(
//                                        "avg_frame_time_ms" to avgFrameTimeMs,
//                                        "max_frame_time_ms" to maxFrameTimeMs,
//                                        "janky_frames" to jankyFrames,
//                                        "total_frames" to frameCount,
//                                        "fps" to (1000.0 / avgFrameTimeMs),
//                                        "dropped_frames_percent" to (jankyFrames * 100.0 / frameCount)
//                                    )
//                                )
//
//                                _eventFlow.tryEmit(event)
//
//                                // Reset for next collection
//                                frameCount = 0
//                                frameTimeList.clear()
//                            }
//                        }
//
//                        lastFrameTimeNanos = frameTimeNanos
//
//                        // Continue monitoring if still active
//                        if (isMonitoring) {
//                            Choreographer.getInstance().postFrameCallback(this)
//                        }
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error in frame callback", e)
//                        // Try to continue monitoring
//                        if (isMonitoring) {
//                            Choreographer.getInstance().postFrameCallback(this)
//                        }
//                    }
//                }
//            }
//
//            // Start frame monitoring
//            Choreographer.getInstance().postFrameCallback(frameCallback)
//        }
//    }
//
//    /**
//     * Set up legacy frame metrics for older Android versions
//     */
//    private suspend fun setupLegacyFrameMetrics() {
//        withContext(Dispatchers.Main) {
//            // For older Android versions, use a simpler approach with Choreographer
//            val frameCallback = object : Choreographer.FrameCallback {
//                private var lastFrameTime = 0L
//                private var frameCount = 0
//
//                override fun doFrame(frameTimeNanos: Long) {
//                    try {
//                        val currentTime = System.currentTimeMillis()
//
//                        if (lastFrameTime > 0) {
//                            frameCount++
//
//                            // Report FPS every 2 seconds
//                            if (currentTime - lastFrameTime >= 2000) {
//                                val fps = frameCount / ((currentTime - lastFrameTime) / 1000.0)
//
//                                val event = PerformanceEvent(
//                                    category = "frame_rate",
//                                    duration = 0,
//                                    name = "fps_measurement",
//                                    metadata = mapOf(
//                                        "fps" to fps,
//                                        "frame_count" to frameCount,
//                                        "measurement_duration_ms" to (currentTime - lastFrameTime)
//                                    )
//                                )
//
//                                _eventFlow.tryEmit(event)
//
//                                // Reset counters
//                                frameCount = 0
//                                lastFrameTime = currentTime
//                            }
//                        } else {
//                            // First frame
//                            lastFrameTime = currentTime
//                        }
//
//                        // Continue monitoring if still active
//                        if (isMonitoring) {
//                            Choreographer.getInstance().postFrameCallback(this)
//                        }
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error in legacy frame callback", e)
//                        // Try to continue monitoring
//                        if (isMonitoring) {
//                            Choreographer.getInstance().postFrameCallback(this)
//                        }
//                    }
//                }
//            }
//
//            // Start frame monitoring
//            Choreographer.getInstance().postFrameCallback(frameCallback)
//        }
//    }
//
//    /**
//     * Set up ANR (Application Not Responding) detection
//     */
//    private fun setupAnrDetection() {
//        pluginScope.launch(anrDetectionJob) {
//            try {
//                // Create a watchdog thread to detect main thread blocking
//                val mainThreadHandler = Handler(Looper.getMainLooper())
//
//                while (isActive && isMonitoring) {
//                    try {
//                        val pingTime = System.currentTimeMillis()
//                        val signal = CountDownLatch(1)
//
//                        // Post a task to the main thread
//                        mainThreadHandler.post { signal.countDown() }
//
//                        // Wait for the task to complete with timeout
//                        val completed = signal.await(5, TimeUnit.SECONDS)
//
//                        if (!completed) {
//                            // Main thread is blocked (potential ANR)
//                            val blockDuration = System.currentTimeMillis() - pingTime
//
//                            // Create ANR event
//                            val event = PerformanceEvent(
//                                category = "anr",
//                                duration = blockDuration,
//                                name = "main_thread_blocked",
//                                metadata = mapOf(
//                                    "block_duration_ms" to blockDuration,
//                                    "stack_trace" to getMainThreadStackTrace()
//                                )
//                            )
//
//                            _eventFlow.tryEmit(event)
//
//                            Log.w(
//                                TAG,
//                                "Potential ANR detected: Main thread blocked for $blockDuration ms"
//                            )
//                        }
//
//                        // Check every 2 seconds
//                        delay(2000)
//                    } catch (e: CancellationException) {
//                        // Job was cancelled, exit loop
//                        break
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error in ANR detection", e)
//                        delay(5000) // Wait before retrying
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "ANR detection stopped unexpectedly", e)
//            }
//        }
//    }
//
//    /**
//     * Get the stack trace of the main thread for ANR reporting
//     */
//    private fun getMainThreadStackTrace(): String {
//        val mainThread = Looper.getMainLooper().thread
//        val stackTraceElements = mainThread.stackTrace
//
//        return stackTraceElements.joinToString("\n") { element ->
//            "\tat ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})"
//        }
//    }
//
//    /**
//     * Set up periodic memory usage monitoring
//     */
//    private fun setupMemoryMonitoring() {
//        pluginScope.launch(memoryMonitoringJob) {
//            try {
//                val memoryCheckInterval = 30_000L // 30 seconds
//
//                while (isActive && isMonitoring) {
//                    try {
//                        val runtime = Runtime.getRuntime()
//                        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
//                        val maxMemory = runtime.maxMemory()
//                        val memoryUsagePercent = usedMemory * 100.0 / maxMemory
//
//                        // Report memory metrics
//                        val event = PerformanceEvent(
//                            category = "memory",
//                            duration = 0,
//                            name = "app_memory_usage",
//                            metadata = mapOf(
//                                "used_memory_bytes" to usedMemory,
//                                "max_memory_bytes" to maxMemory,
//                                "usage_percent" to memoryUsagePercent,
//                                "free_memory_bytes" to runtime.freeMemory()
//                            )
//                        )
//
//                        _eventFlow.tryEmit(event)
//
//                        // Check for low memory conditions
//                        if (memoryUsagePercent > 80.0) {
//                            Log.w(TAG, "High memory usage detected: $memoryUsagePercent%")
//
//                            // Create critical memory event if approaching limits
//                            if (memoryUsagePercent > 90.0) {
//                                val criticalEvent = PerformanceEvent(
//                                    category = "memory",
//                                    duration = 0,
//                                    name = "critical_memory_warning",
//                                    metadata = mapOf(
//                                        "used_memory_bytes" to usedMemory,
//                                        "max_memory_bytes" to maxMemory,
//                                        "usage_percent" to memoryUsagePercent
//                                    )
//                                )
//
//                                _eventFlow.tryEmit(criticalEvent)
//                            }
//                        }
//
//                        delay(memoryCheckInterval)
//                    } catch (e: CancellationException) {
//                        // Job was cancelled, exit loop
//                        break
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error in memory monitoring", e)
//                        delay(10_000) // Retry after a delay
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e(TAG, "Memory monitoring stopped unexpectedly", e)
//            }
//        }
//    }
//
//    /**
//     * Set up automatic activity performance tracking
//     */
//    private fun setupActivityPerformanceTracking(application: Application) {
//        val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
//            private val activityCreationTimes = ConcurrentHashMap<String, Long>()
//
//            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
//                val activityName = activity.javaClass.simpleName
//                activityCreationTimes[activityName] = SystemClock.elapsedRealtime()
//                startMeasurement("activity_create_$activityName")
//            }
//
//            override fun onActivityStarted(activity: Activity) {
//                // Nothing to do
//            }
//
//            override fun onActivityResumed(activity: Activity) {
//                val activityName = activity.javaClass.simpleName
//
//                // Measure time from creation to resume (first display)
//                activityCreationTimes[activityName]?.let { creationTime ->
//                    val resumeTime = SystemClock.elapsedRealtime()
//                    val displayTime = resumeTime - creationTime
//
//                    // Track as screen load time
//                    trackScreenLoadTime(
//                        screenName = activityName, loadTimeMs = displayTime, metadata = mapOf(
//                            "from_created_to_resumed" to true
//                        )
//                    )
//
//                    // Clear creation time
//                    activityCreationTimes.remove(activityName)
//                }
//
//                // End the creation measurement
//                endMeasurement(
//                    key = "activity_create_$activityName",
//                    category = "activity_lifecycle",
//                    metadata = mapOf(
//                        "stage" to "created_to_resumed"
//                    )
//                )
//
//                // Start measuring the visible time
//                startMeasurement("activity_visible_$activityName")
//
//                // If API level supports it, track frame metrics for this activity
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                    setupActivityFrameMetrics(activity)
//                }
//            }
//
//            override fun onActivityPaused(activity: Activity) {
//                val activityName = activity.javaClass.simpleName
//
//                // End the visibility measurement
//                endMeasurement(
//                    key = "activity_visible_$activityName",
//                    category = "activity_visibility",
//                    metadata = mapOf(
//                        "activity" to activityName
//                    )
//                )
//            }
//
//            override fun onActivityStopped(activity: Activity) {
//                // Nothing to do
//            }
//
//            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
//                // Nothing to do
//            }
//
//            override fun onActivityDestroyed(activity: Activity) {
//                // Nothing to do
//            }
//        }
//
//        // Register the callback
//        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
//    }
//
//    /**
//     * Set up frame metrics tracking for a specific activity
//     */
//    @RequiresApi(Build.VERSION_CODES.N)
//    private fun setupActivityFrameMetrics(activity: Activity) {
//        try {
//            val window = activity.window
//            val activityName = activity.javaClass.simpleName
//
//            // Create frame metrics listener
//            window.addOnFrameMetricsAvailableListener(
//                object : Window.OnFrameMetricsAvailableListener {
//                    private var frameCount = 0
//                    private var totalLayoutDuration = 0L
//                    private var totalDrawDuration = 0L
//                    private var totalFrameDuration = 0L
//                    private var jankyFrames = 0
//
//                    override fun onFrameMetricsAvailable(
//                        window: Window,
//                        frameMetrics: FrameMetrics,
//                        dropCountSinceLastInvocation: Int
//                    ) {
//                        try {
//                            frameCount++
//
//                            // Get durations in nanoseconds
//                            val layoutDuration =
//                                frameMetrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
//                            val drawDuration = frameMetrics.getMetric(FrameMetrics.DRAW_DURATION)
//                            val totalDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
//
//                            // Accumulate values
//                            totalLayoutDuration += layoutDuration
//                            totalDrawDuration += drawDuration
//                            totalFrameDuration += totalDuration
//
//                            // Check for janky frames (>16ms or 16,666,666ns)
//                            if (totalDuration > 16_666_666) {
//                                jankyFrames++
//                            }
//
//                            // Report metrics every 60 frames or if there were dropped frames
//                            if (frameCount >= 60 || dropCountSinceLastInvocation > 0) {
//                                // Convert to milliseconds for readability
//                                val avgLayoutMs = (totalLayoutDuration / frameCount) / 1_000_000.0
//                                val avgDrawMs = (totalDrawDuration / frameCount) / 1_000_000.0
//                                val avgFrameMs = (totalFrameDuration / frameCount) / 1_000_000.0
//
//                                val event = PerformanceEvent(
//                                    category = "frame_metrics",
//                                    duration = avgFrameMs.toLong(),
//                                    name = "${activityName}_frame_metrics",
//                                    metadata = mapOf(
//                                        "avg_layout_duration_ms" to avgLayoutMs,
//                                        "avg_draw_duration_ms" to avgDrawMs,
//                                        "avg_frame_duration_ms" to avgFrameMs,
//                                        "janky_frames" to jankyFrames,
//                                        "drop_count" to dropCountSinceLastInvocation,
//                                        "frame_count" to frameCount
//                                    )
//                                )
//
//                                _eventFlow.tryEmit(event)
//
//                                // Reset counters
//                                frameCount = 0
//                                totalLayoutDuration = 0
//                                totalDrawDuration = 0
//                                totalFrameDuration = 0
//                                jankyFrames = 0
//                            }
//                        } catch (e: Exception) {
//                            Log.e(TAG, "Error processing frame metrics", e)
//                        }
//                    }
//                }, Handler(Looper.getMainLooper())
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to set up activity frame metrics", e)
//        }
//    }
//
//    /**
//     * Event class for performance metrics
//     */
//}