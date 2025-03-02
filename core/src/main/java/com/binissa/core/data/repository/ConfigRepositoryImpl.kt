package com.binissa.core.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.binissa.core.domain.repository.ConfigRepository

class ConfigRepositoryImpl(private val context: Context) : ConfigRepository {

    companion object {
        private const val META_DATA_PREFIX = "com.gaugex."
        private const val API_KEY = "API_KEY"
        private const val DEFAULT_SAMPLING_RATE = 1.0f
    }

    /**
     * Cache for manifest metadata to avoid repeated reads
     */
    private val metaDataCache: MutableMap<String, Any?> = mutableMapOf()

    /**
     * Gets the API key from AndroidManifest.xml
     */
    override suspend fun getApiKey(): String? {
        return getMetaDataString(API_KEY)
    }

    /**
     * Checks if a feature is enabled based on AndroidManifest.xml configuration
     */
    override suspend fun isFeatureEnabled(feature: String): Boolean {
        val key = "ENABLE_$feature"
        return getMetaDataBoolean(key) ?: true // Default to enabled
    }

    /**
     * Gets the sampling rate for an event type
     */
    override suspend fun getSamplingRate(eventType: String): Float {
        val key = "SAMPLING_${eventType}"
        return getMetaDataFloat(key) ?: DEFAULT_SAMPLING_RATE
    }

    /**
     * Gets a string value from metadata
     */
    private fun getMetaDataString(key: String): String? {
        val fullKey = META_DATA_PREFIX + key
        return if (metaDataCache.containsKey(fullKey)) {
            metaDataCache[fullKey] as? String
        } else {
            try {
                val ai: ApplicationInfo = context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                )
                val value = ai.metaData?.getString(fullKey)
                metaDataCache[fullKey] = value
                value
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Gets a boolean value from metadata
     */
    private fun getMetaDataBoolean(key: String): Boolean? {
        val fullKey = META_DATA_PREFIX + key
        return if (metaDataCache.containsKey(fullKey)) {
            metaDataCache[fullKey] as? Boolean
        } else {
            try {
                val ai: ApplicationInfo = context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                )
                val value = ai.metaData?.getBoolean(fullKey)
                metaDataCache[fullKey] = value
                value
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Gets a float value from metadata
     */
    private fun getMetaDataFloat(key: String): Float? {
        val fullKey = META_DATA_PREFIX + key
        return if (metaDataCache.containsKey(fullKey)) {
            metaDataCache[fullKey] as? Float
        } else {
            try {
                val ai: ApplicationInfo = context.packageManager.getApplicationInfo(
                    context.packageName, PackageManager.GET_META_DATA
                )
                val value = ai.metaData?.getFloat(fullKey)
                metaDataCache[fullKey] = value
                value
            } catch (e: Exception) {
                null
            }
        }
    }
}