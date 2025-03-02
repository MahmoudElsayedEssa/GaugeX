package com.binissa.plugin.performance

import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventType
import java.util.UUID

data class PerformanceEvent(
    override val id: String = UUID.randomUUID().toString(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val type: EventType = EventType.PERFORMANCE,
    val category: String,  // e.g., app_startup, screen_load, network, etc.
    val name: String,      // Identifier for what was measured
    val duration: Long,    // Duration in milliseconds
    val metadata: Map<String, Any> = emptyMap() // Additional context
) : Event
