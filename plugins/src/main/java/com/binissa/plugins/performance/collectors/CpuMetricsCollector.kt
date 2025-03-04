package com.binissa.plugins.performance.collectors

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class CpuMetricsCollector : MetricsCollector {

    companion object {
        private const val TAG = "CpuMetricsCollector"
    }

    override suspend fun collect() = withContext(Dispatchers.IO) {
        val metrics = mutableMapOf<String, Any>()

        try {
            // CPU Usage
            val pid = android.os.Process.myPid()
            val statFile = File("/proc/$pid/stat").readText().split(" ")
            val utime = statFile[13].toLong()
            val stime = statFile[14].toLong()
            val totalTime = utime + stime

            delay(500)

            val statFile2 = File("/proc/$pid/stat").readText().split(" ")
            val utime2 = statFile2[13].toLong()
            val stime2 = statFile2[14].toLong()
            val totalTime2 = utime2 + stime2

            metrics["cpu_usage"] = ((totalTime2 - totalTime) * 100 / 500).toDouble()

            // CPU Cores
            metrics["cpu_cores"] = Runtime.getRuntime().availableProcessors()

            // CPU Frequency
            val freqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")
            if (freqFile.exists()) {
                metrics["cpu_freq_mhz"] = freqFile.readText().trim().toLong() / 1000
            }
        } catch (e: Exception) {
            metrics["cpu_error"] = e.message.toString()
        }

        Log.d(TAG, "collect:metrics:$metrics ")
        return@withContext metrics
    }
}
