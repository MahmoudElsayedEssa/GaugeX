package com.binissa.core.domain.model

class GaugeXConfig(
    val apiKey: String?,
    val enableCrashReporting: Boolean,
    val enablePerformanceMonitoring: Boolean,
    val enableNetworkMonitoring: Boolean,
    val enableUserBehaviorTracking: Boolean,
    val enableLogCollection: Boolean,
    val maxStorageSize: Long,
    val maxEventAge: Long,
    val samplingRates: Map<String, Float>,
    val endpointUrl: String
) {
    /**
     * Builder for GaugeXConfig
     */
    class Builder {
        private var apiKey: String? = null
        private var enableCrashReporting: Boolean = true
        private var enablePerformanceMonitoring: Boolean = true
        private var enableNetworkMonitoring: Boolean = true
        private var enableUserBehaviorTracking: Boolean = true
        private var enableLogCollection: Boolean = true
        private var maxStorageSize: Long = 50 * 1024 * 1024 // 50MB default
        private var maxEventAge: Long = 7 * 24 * 60 * 60 * 1000 // 7 days default
        private var samplingRates: MutableMap<String, Float> = mutableMapOf(
            "CRASH" to 1.0f,
            "PERFORMANCE" to 0.5f,
            "NETWORK" to 0.3f,
            "USER_ACTION" to 0.1f,
            "LOG" to 0.05f
        )
        private var endpointUrl: String = "https://api.gaugex.com/v1"

        /**
         * Set the API key for the GaugeX backend
         */
        fun apiKey(apiKey: String): Builder {
            this.apiKey = apiKey
            return this
        }

        /**
         * Enable or disable crash reporting
         */
        fun enableCrashReporting(enable: Boolean): Builder {
            this.enableCrashReporting = enable
            return this
        }

        /**
         * Enable or disable performance monitoring
         */
        fun enablePerformanceMonitoring(enable: Boolean): Builder {
            this.enablePerformanceMonitoring = enable
            return this
        }

        /**
         * Enable or disable network monitoring
         */
        fun enableNetworkMonitoring(enable: Boolean): Builder {
            this.enableNetworkMonitoring = enable
            return this
        }

        /**
         * Enable or disable user behavior tracking
         */
        fun enableUserBehaviorTracking(enable: Boolean): Builder {
            this.enableUserBehaviorTracking = enable
            return this
        }

        /**
         * Enable or disable log collection
         */
        fun enableLogCollection(enable: Boolean): Builder {
            this.enableLogCollection = enable
            return this
        }

        /**
         * Set the maximum storage size for events
         * @param bytes Maximum size in bytes
         */
        fun maxStorageSize(bytes: Long): Builder {
            this.maxStorageSize = bytes
            return this
        }

        /**
         * Set the maximum age for stored events
         * @param millis Maximum age in milliseconds
         */
        fun maxEventAge(millis: Long): Builder {
            this.maxEventAge = millis
            return this
        }

        /**
         * Set the sampling rate for a specific event type
         * @param eventType The event type (CRASH, PERFORMANCE, etc.)
         * @param rate The sampling rate (0.0-1.0)
         */
        fun setSamplingRate(eventType: String, rate: Float): Builder {
            this.samplingRates[eventType] = rate.coerceIn(0.0f, 1.0f)
            return this
        }

        /**
         * Set the endpoint URL for the GaugeX backend
         */
        fun endpointUrl(url: String): Builder {
            this.endpointUrl = url
            return this
        }

        /**
         * Build the configuration
         */
        fun build(): GaugeXConfig {
            return GaugeXConfig(
                apiKey = apiKey,
                enableCrashReporting = enableCrashReporting,
                enablePerformanceMonitoring = enablePerformanceMonitoring,
                enableNetworkMonitoring = enableNetworkMonitoring,
                enableUserBehaviorTracking = enableUserBehaviorTracking,
                enableLogCollection = enableLogCollection,
                maxStorageSize = maxStorageSize,
                maxEventAge = maxEventAge,
                samplingRates = samplingRates.toMap(),
                endpointUrl = endpointUrl
            )
        }
    }
}
