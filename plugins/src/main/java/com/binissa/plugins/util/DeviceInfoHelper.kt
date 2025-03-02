package com.binissa.plugin.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.binissa.core.domain.model.AppInfo
import com.binissa.core.domain.model.DeviceInfo
import java.io.File

/**
 * Helper class for collecting device and app information
 */
class DeviceInfoHelper(private val context: Context) {
    
    /**
     * Get comprehensive device information
     */
    fun getDeviceInfo(): DeviceInfo {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.getSystemService(WindowManager::class.java)?.currentWindowMetrics
//            displayMetrics.setTo(display?.bounds?.width() , display?.bounds?.height() ?: 0)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
        }
        // Get battery info
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        
        val batteryLevel = batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level == -1 || scale == -1) 0f else level * 100f / scale
        } ?: 0f
        
        val isCharging = batteryStatus?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } ?: false
        
        // Get memory info
        val memInfo = getMemoryInfo()
        
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            screenDensity = displayMetrics.density,
            totalRam = memInfo.first,
            availableRam = memInfo.second,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            networkType = getNetworkType(),
            isRooted = isDeviceRooted()
        )
    }
    
    /**
     * Get application information
     */
    fun getAppInfo(): AppInfo {
        val packageManager = context.packageManager
        val packageInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            throw RuntimeException("Could not get package info", e)
        }
        
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        
        val applicationInfo = context.applicationInfo
        val buildType = if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            "debug"
        } else {
            "release"
        }
        
        return AppInfo(
            packageName = context.packageName,
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = versionCode,
            buildType = buildType,
            firstInstallTime = packageInfo.firstInstallTime,
            lastUpdateTime = packageInfo.lastUpdateTime
        )
    }
    
    /**
     * Get memory information (total and available RAM)
     * @return Pair of (total RAM, available RAM) in bytes
     */
    private fun getMemoryInfo(): Pair<Long, Long> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return Pair(memoryInfo.totalMem, memoryInfo.availMem)
    }
    
    /**
     * Get the current network type (wifi, cellular, none)
     */
    private fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "none"
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "none"
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
                else -> "unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            return when (networkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> "wifi"
                ConnectivityManager.TYPE_MOBILE -> "cellular"
                ConnectivityManager.TYPE_ETHERNET -> "ethernet"
                ConnectivityManager.TYPE_BLUETOOTH -> "bluetooth"
                ConnectivityManager.TYPE_VPN -> "vpn"
                else -> if (networkInfo?.isConnected == true) "unknown" else "none"
            }
        }
    }
    
    /**
     * Check if the device is rooted
     * This is a simple check, a production implementation would be more thorough
     */
    private fun isDeviceRooted(): Boolean {
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/xbin/busybox",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su"
        )
        
        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }
        
        return false
    }
}