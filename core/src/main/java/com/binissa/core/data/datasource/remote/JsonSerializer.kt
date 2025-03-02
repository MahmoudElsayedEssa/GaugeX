package com.binissa.core.data.datasource.remote

/**
 * Interface for JSON serialization/deserialization
 */
interface JsonSerializer {
    fun serialize(data: Any): String
    fun <T> deserialize(json: String, clazz: Class<T>): T
}
