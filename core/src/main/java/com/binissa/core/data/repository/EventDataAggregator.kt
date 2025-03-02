package com.binissa.core.data.repository

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.binissa.core.data.manager.SessionTracker
import com.binissa.core.domain.model.Event

class EventDataAggregator(
    private val context: Context,
    private val sessionTracker: SessionTracker
) {
    // Track current screen
    private var currentScreenName: String = "unknown"
    private var sessionStartTime: Long = System.currentTimeMillis()

    fun setCurrentScreen(screenName: String) {
        currentScreenName = screenName
    }

    fun getDeviceState(): Map<String, Any> {
        val deviceState = mutableMapOf<String, Any>()

        // Battery info
        deviceState["battery_level"] = getBatteryLevel()

        // Memory info
        deviceState["memory_available"] = getAvailableMemory()

        // Network info
        deviceState["network_type"] = getNetworkType()

        // Power mode
        deviceState["is_low_power_mode"] = isLowPowerMode()

        // Thermal status
        deviceState["thermal_status"] = getThermalStatus()

        return deviceState
    }

    fun getAppState(): Map<String, Any> {
        val appState = mutableMapOf<String, Any>()

        // App state
        appState["foreground"] = isAppInForeground()
        appState["screen_name"] = getCurrentScreenName()
        appState["session_duration"] = getSessionDuration()

        return appState
    }

    fun getUserContext(): Map<String, Any> {
        val userContext = mutableMapOf<String, Any>()

        // Session info
        userContext["session_id"] = getCurrentSessionId()
        userContext["user_flow"] = getCurrentUserFlow()

        return userContext
    }

    fun getEventContext(event: Event): Map<String, Any> {
        val context = mutableMapOf<String, Any>()

        // Add device state
        context.putAll(getDeviceState())

        // Add app state
        context.putAll(getAppState())

        // Add user context
        context.putAll(getUserContext())

        return context
    }

    // Helper methods
    private fun getBatteryLevel(): Int {
        val batteryIntent =
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            -1
        }
    }

    private fun getAvailableMemory(): Long {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.availMem
    }

    private fun getNetworkType(): String {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "none"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "none"

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo?.typeName?.lowercase() ?: "none"
        }
    }

    private fun isLowPowerMode(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isPowerSaveMode
        }
        return false
    }

    private fun getThermalStatus(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "none"
                PowerManager.THERMAL_STATUS_LIGHT -> "light"
                PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
                PowerManager.THERMAL_STATUS_SEVERE -> "severe"
                PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
                else -> "unknown"
            }
        }
        return "unknown"
    }

    private fun isAppInForeground(): Boolean {
        // This is a simplification - in a real implementation, you would use
        // ProcessLifecycleOwner or a custom ActivityLifecycleCallbacks
        return true
    }

    private fun getCurrentScreenName(): String {
        return currentScreenName
    }

    private fun getSessionDuration(): Long {
        return System.currentTimeMillis() - sessionStartTime
    }

    private fun getCurrentSessionId(): String {
        return sessionTracker.getCurrentSessionId()
    }

    private fun getCurrentUserFlow(): String {
        // This would track the sequence of screens in a user flow
        return "default"
    }
}