// SessionManager.kt
package com.binissa.core.data.manager

import android.content.Context
import android.content.SharedPreferences
import com.binissa.core.data.datasource.local.EventDao
import com.binissa.core.data.datasource.remote.JsonSerializer
import com.binissa.core.domain.model.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class SessionTracker(
    private val context: Context,
//    private val eventDao: EventDao,
    private val jsonSerializer: JsonSerializer,
    private val sessionTimeout: Long = 30 * 60 * 1000 // 30 minutes
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gaugex_sessions", Context.MODE_PRIVATE)

    private var currentSessionId: String? = null
    private var lastActiveTime: Long = 0

    /**
     * Get current session ID, creating a new one if needed
     */
    fun getCurrentSessionId(): String {
        val now = System.currentTimeMillis()

        // Check if current session has timed out
        if (currentSessionId != null && now - lastActiveTime > sessionTimeout) {
            // End the current session
            endSession(currentSessionId!!, lastActiveTime)
            currentSessionId = null
        }

        // Create new session if needed
        if (currentSessionId == null) {
            currentSessionId = UUID.randomUUID().toString()
            startSession(currentSessionId!!)
        }

        // Update last active time
        lastActiveTime = now

        return currentSessionId!!
    }

    /**
     * Start a new session
     */
    private fun startSession(sessionId: String) {
        val deviceInfo = collectDeviceInfo()
        val session = Session(
            id = sessionId,
            startTime = System.currentTimeMillis(),
            deviceInfo = deviceInfo
        )

        // Persist session info
        prefs.edit()
            .putString("session_$sessionId", jsonSerializer.serialize(session))
            .putString("current_session", sessionId)
            .apply()
    }

    /**
     * End an existing session
     */
    fun endSession(sessionId: String, endTime: Long = System.currentTimeMillis()) {
        val sessionJson = prefs.getString("session_$sessionId", null) ?: return

        try {
            val session = jsonSerializer.deserialize(sessionJson, Session::class.java)
            val endedSession = session.copy(endTime = endTime)

            // Update session with end time
            prefs.edit()
                .putString("session_$sessionId", jsonSerializer.serialize(endedSession))
                .apply()
        } catch (e: Exception) {
            // Handle deserialization error
        }
    }

    /**
     * Get all sessions
     */
    suspend fun getAllSessions(): List<Session> = withContext(Dispatchers.IO) {
        val sessions = mutableListOf<Session>()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith("session_") && value is String) {
                try {
                    val session = jsonSerializer.deserialize(value, Session::class.java)
                    sessions.add(session)
                } catch (e: Exception) {
                    // Skip invalid sessions
                }
            }
        }

        return@withContext sessions
    }

    /**
     * Collect device information for session context
     */
    private fun collectDeviceInfo(): Map<String, Any> {
        return mapOf(
            "device_model" to android.os.Build.MODEL,
            "os_version" to android.os.Build.VERSION.RELEASE,
            "app_version" to getAppVersion()
        )
    }

    /**
     * Get app version name
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.toString()
        } catch (e: Exception) {
            "unknown"
        }
    }
}