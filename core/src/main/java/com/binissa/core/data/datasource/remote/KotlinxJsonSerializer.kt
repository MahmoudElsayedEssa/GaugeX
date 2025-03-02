package com.binissa.core.data.datasource.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Implementation of JsonSerializer using Kotlinx serialization
 */
class KotlinxJsonSerializer : JsonSerializer {

    // Create a Json instance with polymorphic serialization for Event types
    private val json = Json {
        serializersModule = SerializersModule {
            // Register all Event implementations for polymorphic serialization
            // This would be expanded as new Event types are added
            polymorphic(Event::class) {
                subclass(BaseEvent::class)
                subclass(CrashEvent::class)
                // Add other event types as they are implemented
            }
        }
        // JSON configuration options
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun serialize(data: Any): String {
        return json.encodeToString(data)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(json: String, clazz: Class<T>): T {
        // Handle different types based on the class
        return when {
            Event::class.java.isAssignableFrom(clazz) -> {
                this.json.decodeFromString<Event>(json) as T
            }

            else -> {
                throw IllegalArgumentException("Unsupported class for deserialization: ${clazz.name}")
            }
        }
    }

    // Event classes with serialization annotations
    // In a real implementation, these would be in their own files

    @kotlinx.serialization.Serializable
    data class BaseEvent(
        override val id: String,
        override val timestamp: Long,
        override val type: EventType
    ) : Event

    @kotlinx.serialization.Serializable
    enum class EventType {
        CRASH,
        PERFORMANCE,
        NETWORK,
        USER_ACTION,
        LOG
    }

    @kotlinx.serialization.Serializable
    data class CrashEvent(
        override val id: String,
        override val timestamp: Long,
        override val type: EventType,
        val threadName: String,
        val throwableClass: String,
        val message: String,
        val stackTrace: String
    ) : Event

    // Interface definition needs to be inside the serializer for this example
    interface Event {
        val id: String
        val timestamp: Long
        val type: EventType
    }
}