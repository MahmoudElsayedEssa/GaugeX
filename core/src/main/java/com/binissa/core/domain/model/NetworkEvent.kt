// NetworkEvent.kt
package com.binissa.core.domain.model

import java.util.UUID

data class NetworkEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val type: EventType = EventType.NETWORK,
    val method: String,           // HTTP method (GET, POST, etc.)
    val url: String?,             // Request URL
    val statusCode: Int,          // Response status code
    val duration: Long,           // Request duration in ms
    val responseSize: Long,       // Response size in bytes
    val error: String? = null,    // Error message if request failed
    val metadata: Map<String, Any> = emptyMap() // Additional context
) : Event