package com.binissa.core.data.datasource.local.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [
        Index(value = ["status"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)

data class EventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val type: String,
    val status: String,
    val payload: String,
    val category: String,
    val name: String,
    val duration: Long? = null,
    val metadataJson: String,
    val sessionId: String? = null,
    val deviceState: String? = null,
    val priority: Int = 0,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val lastAttemptTime: Long? = null
)