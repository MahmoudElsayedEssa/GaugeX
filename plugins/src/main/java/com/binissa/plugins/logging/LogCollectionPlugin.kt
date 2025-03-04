package com.binissa.plugin.logging

import android.content.Context
import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventType
import com.binissa.plugins.GaugeXPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enhanced plugin for log collection
 * Captures application and system logs and provides a custom logging interface
 */
class LogCollectionPlugin : GaugeXPlugin {
    private val TAG = "LogCollectionPlugin"

    // Flow of log events
    private val _eventFlow = MutableSharedFlow<Event>(replay = 0)

    // Context to access app info
    private lateinit var appContext: Context

    // Buffered logs
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private val MAX_BUFFER_SIZE = 1000

    // Plugin-specific coroutine scope
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Flag to control log collection
    private var isCollecting = false

    // System log collection job
    private val logCollectionJob = SupervisorJob()

    override val id: String = "logging"

    /**
     * Initialize the log collection plugin
     */
    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
        try {
            this.appContext = context.applicationContext
            Log.i(TAG, "Log collection plugin initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize log collection plugin", e)
            return false
        }
    }

    /**
     * Start collecting logs
     */
    override suspend fun startMonitoring() {
        try {
            isCollecting = true

            // Start collecting system logs
            collectSystemLogs()

            Log.i(TAG, "Log collection started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start log collection", e)
        }
    }

    /**
     * Stop collecting logs
     */
    override suspend fun stopMonitoring() {
        try {
            isCollecting = false

            // Cancel system log collection
            logCollectionJob.cancel()

            Log.i(TAG, "Log collection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop log collection", e)
        }
    }

    /**
     * Get the flow of log events
     */
    override fun getEvents(): Flow<Event> = _eventFlow.asSharedFlow()

    /**
     * Shutdown the plugin and clean up resources
     */
    override suspend fun shutdown() {
        try {
            stopMonitoring()
            logBuffer.clear()

            // Cancel all coroutines
            pluginScope.cancel()

            Log.i(TAG, "Log collection plugin shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown log collection plugin", e)
        }
    }

    /**
     * Log a message through GaugeX
     * @param level Log level
     * @param tag Log tag
     * @param message Log message
     * @param throwable Optional throwable
     */
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        // Log to Android Logcat first
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        // Create log entry
        val logEntry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable?.let { getStackTraceString(it) }
        )

        // Add to buffer
        addToBuffer(logEntry)

        // Create log event for significant logs (WARNING and ERROR)
        if (level == LogLevel.WARNING || level == LogLevel.ERROR) {
            val event = LogEvent(
                logLevel = level.name,
                tag = tag,
                message = message,
                stackTrace = throwable?.let { getStackTraceString(it) }
            )

            _eventFlow.tryEmit(event)
        }
    }

    /**
     * Add a log entry to the buffer
     */
    private fun addToBuffer(entry: LogEntry) {
        logBuffer.add(entry)

        // Maintain maximum buffer size
        while (logBuffer.size > MAX_BUFFER_SIZE) {
            logBuffer.poll()
        }
    }

    /**
     * Collect system logs (logcat)
     */
    private fun collectSystemLogs() {
        pluginScope.launch(logCollectionJob) {
            try {
                // Clear log buffer first
                try {
                    Runtime.getRuntime().exec("logcat -c")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear logcat buffer", e)
                }

                // Start reading logs
                val process = Runtime.getRuntime().exec("logcat -v threadtime")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

                // Read lines until stopped
                while (isActive && isCollecting) {
                    try {
                        val line = bufferedReader.readLine() ?: break
                        processLogcatLine(line)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading logcat", e)
                        delay(1000) // Wait before retrying
                    }
                }

                // Clean up
                try {
                    bufferedReader.close()
                    process.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up logcat process", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting system logs", e)
            }
        }
    }

    /**
     * Process a line from logcat
     */
    private fun processLogcatLine(line: String) {
        try {
            // Parse logcat line - Format: date time pid tid level tag: message
            val logPattern =
                """^\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s([VDIWE])\s([^:]+):\s(.+)$""".toRegex()
            val match = logPattern.find(line) ?: return

            val levelChar = match.groupValues[1]
            val tag = match.groupValues[2].trim()
            val message = match.groupValues[3]

            // Skip GaugeX logs to avoid recursion
            if (tag.contains("GaugeX") || tag == TAG) {
                return
            }

            // Convert level character to LogLevel
            val level = when (levelChar) {
                "V" -> LogLevel.VERBOSE
                "D" -> LogLevel.DEBUG
                "I" -> LogLevel.INFO
                "W" -> LogLevel.WARNING
                "E" -> LogLevel.ERROR
                else -> LogLevel.INFO
            }

            // Create log entry
            val logEntry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = message,
                throwable = null,
                source = "system"
            )

            // Add to buffer
            addToBuffer(logEntry)

            // Create event for significant logs
            if (level == LogLevel.WARNING || level == LogLevel.ERROR) {
                val event = LogEvent(
                    logLevel = level.name,
                    tag = tag,
                    message = message,
                    stackTrace = null,
                    source = "system"
                )

                _eventFlow.tryEmit(event)
            }
        } catch (e: Exception) {
            // Ignore parsing errors
        }
    }

    /**
     * Get recent logs
     * @param count Maximum number of logs to return
     * @param level Minimum log level to include
     * @return List of log entries
     */
    /**
     * Get recent logs
     * @param count Maximum number of logs to return
     * @param level Minimum log level to include
     * @return List of log entries
     */
    fun getRecentLogs(count: Int = 100, level: LogLevel = LogLevel.VERBOSE): List<LogEntry> {
        return logBuffer
            .filter { it.level.ordinal >= level.ordinal }
            .sortedByDescending { it.timestamp }
            .take(count)
    }

    /**
     * Get stack trace string from throwable
     */
    private fun getStackTraceString(throwable: Throwable): String {
        return Log.getStackTraceString(throwable)
    }

    /**
     * Log levels
     */
    enum class LogLevel {
        VERBOSE,
        DEBUG,
        INFO,
        WARNING,
        ERROR
    }

    /**
     * Data class for log entries
     */
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: String? = null,
        val source: String = "app"  // "app" or "system"
    )

    /**
     * Event class for logs
     */
    data class LogEvent(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: EventType = EventType.LOG,
        val logLevel: String,
        val tag: String,
        val message: String,
        val stackTrace: String? = null,
        val source: String = "app"  // "app" or "system"
    ) : Event

    /**
     * Convenience method to log at verbose level
     */
    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    /**
     * Convenience method to log at debug level
     */
    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    /**
     * Convenience method to log at info level
     */
    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    /**
     * Convenience method to log at warning level
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARNING, tag, message, throwable)
    }

    /**
     * Convenience method to log at error level
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }
}