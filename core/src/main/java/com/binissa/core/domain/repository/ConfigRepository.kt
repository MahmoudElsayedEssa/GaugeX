package com.binissa.core.domain.repository

/**
 * Repository interface for managing SDK configuration
 */
interface ConfigRepository {
    /**
     * Retrieves the API key for GaugeX
     * @return The API key
     */
    suspend fun getApiKey(): String?

    /**
     * Checks if a specific feature is enabled
     * @param feature The feature to check
     * @return True if the feature is enabled
     */
    suspend fun isFeatureEnabled(feature: String): Boolean

    /**
     * Gets the sampling rate for a specific event type
     * @param eventType The event type
     * @return The sampling rate (0.0-1.0)
     */
    suspend fun getSamplingRate(eventType: String): Float
}