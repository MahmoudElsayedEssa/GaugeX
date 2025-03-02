package com.binissa.plugin.performance.collectors

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryMetricsCollector(private val context: Context) : MetricsCollector {
    override suspend fun collect(): Map<String, Any> {
        return try {
            val batteryStatus = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            batteryStatus?.let {
                mapOf(
                    "battery_level" to it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                    "is_charging" to (it.getIntExtra(
                        BatteryManager.EXTRA_STATUS, -1
                    ) == BatteryManager.BATTERY_STATUS_CHARGING),
                    "temperature" to it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0,
                    "voltage" to it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000.0
                )
            } ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
