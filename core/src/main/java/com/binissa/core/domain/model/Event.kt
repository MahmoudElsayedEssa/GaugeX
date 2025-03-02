package com.binissa.core.domain.model

interface Event {
    val id: String
    val timestamp: Long
    val type: EventType
}

enum class EventType {
    CRASH,
    PERFORMANCE,
    NETWORK,
    USER_ACTION,
    LOG
}

enum class EventStatus {
    PENDING,
    PROCESSING,
    TRANSMITTED,
    FAILED
}