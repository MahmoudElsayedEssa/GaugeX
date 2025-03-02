package com.binissa.plugin.performance.collectors

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

class NetworkMetricsCollector(private val context: Context) : MetricsCollector {
    @Suppress("DEPRECATION")
    override suspend fun collect(): Map<String, Any> {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork ?: return mapOf("network_available" to false)
                val caps = cm.getNetworkCapabilities(activeNetwork)
                    ?: return mapOf("network_available" to false)

                mapOf(
                    "network_available" to true,
                    "wifi" to caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    "cellular" to caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                    "metered" to cm.isActiveNetworkMetered
                )
            } else {
                val activeNetworkInfo =
                    cm.activeNetworkInfo ?: return mapOf("network_available" to false)

                mapOf(
                    "network_available" to activeNetworkInfo.isConnected,
                    "wifi" to (activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI),
                    "cellular" to (activeNetworkInfo.type == ConnectivityManager.TYPE_MOBILE),
                    "metered" to cm.isActiveNetworkMetered
                )
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
