package com.binissa.plugin.crash

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventType
import com.binissa.plugins.GaugeXPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.Thread.UncaughtExceptionHandler

/**
 * Enhanced plugin for crash reporting
 * Automatically captures uncaught exceptions and provides detailed context
 */
class CrashReportingPlugin : GaugeXPlugin {
    private val TAG = "CrashReportingPlugin"

    // Flow of crash events
    private val _eventFlow = MutableSharedFlow<Event>(replay = 0)

    // Original uncaught exception handler
    private var originalExceptionHandler: UncaughtExceptionHandler? = null

    // Context to access device info
    private lateinit var appContext: Context

    // Plugin-specific coroutine scope
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val id: String = "crash"


    /**
     * Initialize the crash reporting plugin
     */
    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
        try {
            this.appContext = context.applicationContext
            Log.i(TAG, "Crash reporting plugin initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize crash reporting plugin", e)
            return false
        }
    }


    /**
     * Start monitoring for crashes
     */
    override suspend fun startMonitoring() {
        try {
            // Store the original handler
            originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

            // Set our own handler
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                // Generate a crash report
                handleUncaughtException(thread, throwable)

                // Call the original handler if available
                originalExceptionHandler?.uncaughtException(thread, throwable)
            }

            Log.i(TAG, "Crash monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start crash monitoring", e)
        }
    }

    /**
     * Stop monitoring for crashes
     */
    override suspend fun stopMonitoring() {
        try {
            // Restore original handler
            if (originalExceptionHandler != null) {
                Thread.setDefaultUncaughtExceptionHandler(originalExceptionHandler)
                originalExceptionHandler = null
            }

            Log.i(TAG, "Crash monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop crash monitoring", e)
        }
    }

    /**
     * Get the flow of crash events
     */
    override fun getEvents(): Flow<Event> = _eventFlow.asSharedFlow()

    /**
     * Shutdown the plugin and clean up resources
     */
    override suspend fun shutdown() {
        try {
            stopMonitoring()

            // Cancel all coroutines
            pluginScope.cancel()

            Log.i(TAG, "Crash reporting plugin shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown crash reporting plugin", e)
        }
    }


    /**
     * Handle an uncaught exception and generate a crash report
     */
    private fun handleUncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Convert stack trace to string
            val stackTrace = getStackTraceString(throwable)

            // Collect device information

            val deviceInfo = collectDeviceInfo()

            // Collect app information
            val appInfo = collectAppInfo()

            // Create the crash event
            val crashEvent = CrashEvent(
                threadName = thread.name,
                throwableClass = throwable.javaClass.name,
                message = throwable.message ?: "No message",
                stackTrace = stackTrace,
                deviceInfo = deviceInfo,
                appInfo = appInfo
            )

            // Try to emit the event
            try {
                _eventFlow.tryEmit(crashEvent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to emit crash event", e)
            }

            // Give some time for the event to be persisted
            // before the process is terminated
            Thread.sleep(500)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling uncaught exception", e)
        }
    }

    /**
     * Collect real device information
     */
    private fun collectDeviceInfo(): DeviceInfo {
        return try {
            DeviceInfo(
                deviceModel = Build.MODEL,
                osVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                brand = Build.BRAND,
                product = Build.PRODUCT,
                device = Build.DEVICE,
                isEmulator = isEmulator(),
                availableMemory = Runtime.getRuntime().maxMemory(),
                totalMemory = Runtime.getRuntime().totalMemory(),
                freeMemory = Runtime.getRuntime().freeMemory()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting device info", e)
            DeviceInfo(
                deviceModel = "Unknown",
                osVersion = "Unknown"
            )
        }
    }

    /**
     * Check if running on an emulator
     */
    private fun isEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }

    /**
     * Collect real app information
     */
    private fun collectAppInfo(): AppInfo {
        return try {
            val packageManager = appContext.packageManager
            val packageInfo: PackageInfo =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        appContext.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    packageManager.getPackageInfo(appContext.packageName, 0)
                }

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val applicationInfo = appContext.applicationInfo
            val buildType =
                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    "debug"
                } else {
                    "release"
                }

            AppInfo(
                appVersion = packageInfo.versionName ?: "unknown",
                buildId = versionCode.toString(),
                packageName = appContext.packageName,
                buildType = buildType,
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting app info", e)
            AppInfo(
                appVersion = "Unknown",
                buildId = "Unknown"
            )
        }
    }

    /**
     * Convert a Throwable's stack trace to a string
     */
    private fun getStackTraceString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }

    /**
     * Data class for device info with comprehensive details
     */
    data class DeviceInfo(
        val deviceModel: String = "Unknown",
        val osVersion: String = "Unknown",
        val apiLevel: Int = -1,
        val manufacturer: String = "Unknown",
        val brand: String = "Unknown",
        val product: String = "Unknown",
        val device: String = "Unknown",
        val isEmulator: Boolean = false,
        val availableMemory: Long = -1,
        val totalMemory: Long = -1,
        val freeMemory: Long = -1
    )

    /**
     * Data class for app info with comprehensive details
     */
    data class AppInfo(
        val appVersion: String = "Unknown",
        val buildId: String = "Unknown",
        val packageName: String = "Unknown",
        val buildType: String = "Unknown",
        val firstInstallTime: Long = 0,
        val lastUpdateTime: Long = 0
    )

    /**
     * Event class for crashes with rich context
     */
    data class CrashEvent(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: EventType = EventType.CRASH,
        val threadName: String,
        val throwableClass: String,
        val message: String,
        val stackTrace: String,
        val deviceInfo: DeviceInfo,
        val appInfo: AppInfo
    ) : Event

    /**
     * Test method to simulate a crash (for testing)
     */
    fun simulateCrash(message: String) {
        throw RuntimeException("Simulated crash: $message")
    }
}