package com.binissa.core.domain.model

data class Session(
    val id: String,                 // Session ID
    val startTime: Long,            // Session start time
    val endTime: Long? = null,      // Session end time (null if active)
    val deviceInfo: Map<String, Any> = emptyMap() // Device information
)