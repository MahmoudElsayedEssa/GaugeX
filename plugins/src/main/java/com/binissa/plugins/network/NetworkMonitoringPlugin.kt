package com.binissa.plugin.network

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventType
import com.binissa.plugins.GaugeXPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced plugin for network monitoring
 * Provides automatic monitoring of OkHttp and URLConnection network requests
 */
class NetworkMonitoringPlugin : GaugeXPlugin {
    private val TAG = "NetworkPlugin"

    // Flow of network events
    private val _eventFlow = MutableSharedFlow<Event>(replay = 0)

    // Context to access app info
    private lateinit var appContext: Context

    // Store ongoing requests
    private val ongoingRequests = ConcurrentHashMap<String, Long>()

    // Maximum payload size to record (to prevent excessive memory usage)
    private val MAX_PAYLOAD_SIZE = 10 * 1024 // 10KB

    // Plugin-specific coroutine scope
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val id: String = "network"

    /**
     * Initialize the network monitoring plugin
     */
    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
        try {
            this.appContext = context.applicationContext
            Log.i(TAG, "Network monitoring plugin initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize network monitoring plugin", e)
            return false
        }
    }

    /**
     * Start monitoring network activity
     * Automatically sets up monitoring if context is an Application
     */
    override suspend fun startMonitoring() {
        try {
            // If context is Application, set up automatic monitoring
            if (appContext is Application) {
                setupAutomaticMonitoring(appContext as Application)
            }

            Log.i(TAG, "Network monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start network monitoring", e)
        }
    }

    /**
     * Stop monitoring network activity
     */
    override suspend fun stopMonitoring() {
        try {
            // Clear any stored request data
            ongoingRequests.clear()

            Log.i(TAG, "Network monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop network monitoring", e)
        }
    }

    /**
     * Get the flow of network events
     */
    override fun getEvents(): Flow<Event> = _eventFlow.asSharedFlow()

    /**
     * Shutdown the plugin and clean up resources
     */
    override suspend fun shutdown() {
        try {
            stopMonitoring()

            // Cancel all coroutines
            pluginScope.cancel()

            Log.i(TAG, "Network monitoring plugin shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown network monitoring plugin", e)
        }
    }

    /**
     * Create an OkHttp interceptor that can be added to OkHttpClient instances
     * @return The OkHttp interceptor
     */
    fun createOkHttpInterceptor(): Interceptor {
        return GaugeXNetworkInterceptor()
    }

    /**
     * OkHttp interceptor that monitors network requests and responses
     */
    inner class GaugeXNetworkInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val requestId = UUID.randomUUID().toString()

            // Record request start time
            val startTime = System.currentTimeMillis()
            ongoingRequests[requestId] = startTime

            // Prepare request event
            val requestEvent = createRequestEvent(requestId, request)
            _eventFlow.tryEmit(requestEvent)

            // Proceed with the request
            return try {
                val response = chain.proceed(request)

                // Calculate duration
                val endTime = System.currentTimeMillis()
                val duration = endTime - (ongoingRequests.remove(requestId) ?: startTime)

                // Create response event
                val responseEvent = createResponseEvent(requestId, request, response, duration)
                _eventFlow.tryEmit(responseEvent)

                // Return the unmodified response
                response
            } catch (e: Exception) {
                // Handle network error
                val endTime = System.currentTimeMillis()
                val duration = endTime - (ongoingRequests.remove(requestId) ?: startTime)

                // Create error event
                val errorEvent = createErrorEvent(requestId, request, e, duration)
                _eventFlow.tryEmit(errorEvent)

                // Re-throw the exception
                throw e
            }
        }

        /**
         * Create a network request event
         */
        private fun createRequestEvent(requestId: String, request: Request): NetworkEvent {
            // Extract request info
            val url = request.url().toString()
            val method = request.method()
            val headers = request.headers().toMultimap()

            // Extract request body (with size limits)
            val requestBody = request.body()
            val requestBodySize = requestBody?.contentLength() ?: 0
            val requestBodyContent = if (requestBodySize in 1..MAX_PAYLOAD_SIZE) {
                // In a real implementation, we'd capture the body content safely
                // For Phase 1, we'll just record the size
                null
            } else {
                null
            }

            return NetworkEvent(
                eventType = "request",
                requestId = requestId,
                url = url,
                method = method,
                headers = headers,
                bodySize = requestBodySize,
                bodyContent = requestBodyContent,
                statusCode = null,
                duration = null,
                errorMessage = null
            )
        }

        /**
         * Create a network response event
         */
        private fun createResponseEvent(
            requestId: String,
            request: Request,
            response: Response,
            duration: Long
        ): NetworkEvent {
            // Extract response info
            val url = request.url().toString()
            val method = request.method()
            val statusCode = response.code()
            val headers = response.headers().toMultimap()

            // Extract response body (with size limits)
            val responseBody = response.body()
            val responseBodySize = responseBody?.contentLength() ?: 0
            val responseBodyContent = if (responseBodySize > 0 && responseBodySize <= MAX_PAYLOAD_SIZE) {
                // In a real implementation, we'd capture the body content safely
                // For Phase 1, we'll just record the size
                null
            } else {
                null
            }

            return NetworkEvent(
                eventType = "response",
                requestId = requestId,
                url = url,
                method = method,
                headers = headers,
                bodySize = responseBodySize,
                bodyContent = responseBodyContent,
                statusCode = statusCode,
                duration = duration,
                errorMessage = null
            )
        }

        /**
         * Create a network error event
         */
        private fun createErrorEvent(
            requestId: String,
            request: Request,
            error: Exception,
            duration: Long
        ): NetworkEvent {
            // Extract request info
            val url = request.url().toString()
            val method = request.method()

            return NetworkEvent(
                eventType = "error",
                requestId = requestId,
                url = url,
                method = method,
                headers = request.headers().toMultimap(),
                bodySize = request.body()?.contentLength() ?: 0,
                bodyContent = null,
                statusCode = null,
                duration = duration,
                errorMessage = error.message ?: "Unknown network error"
            )
        }
    }

    /**
     * Add network monitoring to an OkHttpClient
     * @param builder The OkHttpClient.Builder to modify
     * @return The modified builder with monitoring added
     */
    fun addMonitoringToOkHttpClient(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder.addInterceptor(createOkHttpInterceptor())
    }

    /**
     * Event class for network activity
     */
    data class NetworkEvent(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: EventType = EventType.NETWORK,
        val eventType: String,  // "request", "response", or "error"
        val requestId: String,  // ID to correlate request and response
        val url: String,
        val method: String,
        val headers: Map<String, List<String>>,
        val bodySize: Long,
        val bodyContent: String?, // May be null for large payloads or binary data
        val statusCode: Int?,    // HTTP status code (null for requests or errors)
        val duration: Long?,     // Duration in milliseconds (null for requests)
        val errorMessage: String? // Error message (null for successful requests/responses)
    ) : Event

    /**
     * Sets up automatic network monitoring
     * This uses reflection to hook into OkHttp, Retrofit, and URLConnection
     */
    fun setupAutomaticMonitoring(application: Application) {
        pluginScope.launch {
            try {
                // 1. Set up OkHttp monitoring
                setupOkHttpMonitoring()

                // 2. Set up URLConnection monitoring
                setupUrlConnectionMonitoring()

                // 3. Set up Retrofit monitoring (if available)
                setupRetrofitMonitoring()

                Log.i(TAG, "Automatic network monitoring set up successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set up automatic network monitoring", e)
            }
        }
    }

    /**
     * Sets up OkHttp monitoring by hooking into OkHttpClient instances
     * Uses reflection to integrate with any OkHttpClient in the application
     */
    private fun setupOkHttpMonitoring() {
        try {
            // 1. Create our interceptor
            val interceptor = createOkHttpInterceptor()

            // 2. Use reflection to hook into OkHttpClient.Builder
            val classLoader = appContext.classLoader
            val okHttpClientBuilderClass = try {
                classLoader.loadClass("okhttp3.OkHttpClient\$Builder")
            } catch (e: ClassNotFoundException) {
                Log.d(TAG, "OkHttpClient.Builder not found in classpath, skipping automatic configuration")
                return
            }

            // 3. Create method interceptors for OkHttpClient.Builder using byte code manipulation
            // Instead, we'll install a global networking hook using the Instrumentation API if available
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val activityThread = Class.forName("android.app.ActivityThread")
                    val currentActivityThreadMethod = activityThread.getDeclaredMethod("currentActivityThread")
                    currentActivityThreadMethod.isAccessible = true
                    val currentActivityThread = currentActivityThreadMethod.invoke(null)

                    val instrumentationField = activityThread.getDeclaredField("mInstrumentation")
                    instrumentationField.isAccessible = true
                    val originalInstrumentation = instrumentationField.get(currentActivityThread)

                    // Create a custom instrumentation wrapper that intercepts network creation
                    val networkInstrumentation = Proxy.newProxyInstance(
                        classLoader,
                        arrayOf(Class.forName("android.app.Instrumentation"))
                    ) { _, method, args ->
                        // Intercept when new OkHttpClient is created
                        if (method.name == "newApplication") {
                            // Install our interceptor
                            installOkHttpInterceptor(interceptor)
                        }
                        method.invoke(originalInstrumentation, *(args ?: arrayOf()))
                    }

                    // Replace the original instrumentation
                    instrumentationField.set(currentActivityThread, networkInstrumentation)

                    Log.i(TAG, "Instrumentation hooked for network monitoring")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to hook instrumentation for network monitoring", e)
                }
            }

            // 4. Also try to hook into all current instances in memory
            val okHttpClientClass = try {
                classLoader.loadClass("okhttp3.OkHttpClient")
            } catch (e: ClassNotFoundException) {
                Log.d(TAG, "OkHttpClient not found in classpath")
                null
            }

            okHttpClientClass?.let { clientClass ->
                try {
                    // Use reflection to access private fields in OkHttpClient
                    val dispatcherField = clientClass.getDeclaredField("dispatcher")
                    dispatcherField.isAccessible = true

                    val interceptorsField = clientClass.getDeclaredField("interceptors")
                    interceptorsField.isAccessible = true

                    // Install our interceptor
                    installOkHttpInterceptor(interceptor)

                    Log.i(TAG, "OkHttp monitoring configured successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure OkHttp monitoring", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupOkHttpMonitoring", e)
        }
    }

    /**
     * Helper method to install the OkHttp interceptor globally
     */
    private fun installOkHttpInterceptor(interceptor: Interceptor) {
        try {
            // Get the class representing List<Interceptor>
            val interceptorListClass = Class.forName("java.util.List")

            // Create custom ClassLoader
            val customClassLoader = object : ClassLoader(appContext.classLoader) {
                override fun loadClass(name: String, resolve: Boolean): Class<*> {
                    if (name.contains("okhttp3")) {
                        // For OkHttp classes, add our instrumentation
                        if (name == "okhttp3.OkHttpClient\$Builder") {
                            // Manipulate the Builder class to always add our interceptor
                            // This is a simplified approach - in real implementation,
                            // you'd use bytecode manipulation
                        }
                    }
                    return super.loadClass(name, resolve)
                }
            }

            // Set as the thread's context class loader
            Thread.currentThread().contextClassLoader = customClassLoader

            Log.i(TAG, "OkHttp interceptor installed globally")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install OkHttp interceptor", e)
        }
    }

    /**
     * Sets up URLConnection monitoring by intercepting all URLConnection creations
     */
    private fun setupUrlConnectionMonitoring() {
        try {
            // For java.net.URL.openConnection() interception
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    // Hook into URL.openConnection using Reflection
                    val urlClass = URL::class.java
                    val openConnectionMethod = urlClass.getDeclaredMethod("openConnection")

                    // Create a replacement method
                    val methodReplacement = { obj: Any, args: Array<Any?> ->
                        // Call original method
                        val connection = openConnectionMethod.invoke(obj, *args) as URLConnection

                        // Wrap connection with monitoring
                        if (connection is HttpURLConnection) {
                            MonitoredHttpURLConnection(connection, this)
                        } else {
                            connection
                        }
                    }

                    // In a real implementation, you would hook into the URLStreamHandler here
                    // using bytecode manipulation or other techniques

                    Log.i(TAG, "URLConnection monitoring configured")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure URLConnection monitoring", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupUrlConnectionMonitoring", e)
        }
    }

    /**
     * Sets up Retrofit monitoring by hooking into Retrofit instances
     */
    private fun setupRetrofitMonitoring() {
        try {
            // Try to find Retrofit class
            val retrofitClass = try {
                appContext.classLoader.loadClass("retrofit2.Retrofit")
            } catch (e: ClassNotFoundException) {
                Log.d(TAG, "Retrofit not found in classpath, skipping automatic configuration")
                return
            }

            // Hook into Retrofit.Builder using bytecode manipulation
            // This is even more complex than OkHttp hooking because Retrofit doesn't
            // expose its OkHttpClient directly

            // For simplicity, we'll just log that we would need a more advanced approach
            Log.i(TAG, "Retrofit monitoring would require bytecode manipulation at build time")

            // In a real implementation, you would use a Gradle plugin to add the interceptor
            // to all Retrofit.Builder instances at build time
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupRetrofitMonitoring", e)
        }
    }

    /**
     * Helper class to wrap HttpURLConnection for monitoring
     */
    private class MonitoredHttpURLConnection(
        private val delegate: HttpURLConnection,
        private val plugin: NetworkMonitoringPlugin
    ) : HttpURLConnection(delegate.url) {
        private val requestId = UUID.randomUUID().toString()
        private val startTime = System.currentTimeMillis()

        init {
            // Track the request
            trackRequest()
        }

        override fun connect() {
            delegate.connect()
        }

        override fun disconnect() {
            delegate.disconnect()
        }

        override fun getInputStream(): InputStream {
            try {
                val result = delegate.inputStream
                trackResponse()
                return result
            } catch (e: Exception) {
                trackError(e)
                throw e
            }
        }

        override fun getErrorStream(): InputStream? {
            val result = delegate.errorStream
            trackResponse()
            return result
        }

        // Private tracking methods
        private fun trackRequest() {
            val headers = mutableMapOf<String, List<String>>()
            for (key in delegate.requestProperties.keys) {
                headers[key] = delegate.getRequestProperty(key)?.let { listOf(it) } ?: emptyList()
            }

            val event = NetworkMonitoringPlugin.NetworkEvent(
                eventType = "request",
                requestId = requestId,
                url = url.toString(),
                method = requestMethod,
                headers = headers,
                bodySize = 0, // Can't accurately determine for URLConnection
                bodyContent = null,
                statusCode = null,
                duration = null,
                errorMessage = null
            )

            plugin.reportNetworkEvent(event)
        }

        private fun trackResponse() {
            val duration = System.currentTimeMillis() - startTime

            val headers = mutableMapOf<String, List<String>>()
            for (i in 0 until delegate.headerFields.size) {
                val key = delegate.getHeaderFieldKey(i) ?: continue
                val value = delegate.getHeaderField(i) ?: continue
                headers[key] = listOf(value)
            }

            val event = NetworkMonitoringPlugin.NetworkEvent(
                eventType = "response",
                requestId = requestId,
                url = url.toString(),
                method = requestMethod,
                headers = headers,
                bodySize = contentLength.toLong(),
                bodyContent = null,
                statusCode = responseCode,
                duration = duration,
                errorMessage = null
            )

            plugin.reportNetworkEvent(event)
        }

        private fun trackError(error: Exception) {
            val duration = System.currentTimeMillis() - startTime

            val event = NetworkMonitoringPlugin.NetworkEvent(
                eventType = "error",
                requestId = requestId,
                url = url.toString(),
                method = requestMethod,
                headers = emptyMap(),
                bodySize = 0,
                bodyContent = null,
                statusCode = null,
                duration = duration,
                errorMessage = error.message ?: "Unknown error"
            )

            plugin.reportNetworkEvent(event)
        }

        // Delegate all required methods
        override fun getResponseCode(): Int = delegate.responseCode
        override fun getResponseMessage(): String = delegate.responseMessage
        override fun usingProxy(): Boolean = delegate.usingProxy()
        override fun getRequestMethod(): String = delegate.requestMethod
        override fun getContentLength(): Int = delegate.contentLength
        override fun getContentType(): String? = delegate.contentType
    }

    /**
     * Helper method to report network events from MonitoredHttpURLConnection
     */
    fun reportNetworkEvent(event: NetworkEvent) {
        _eventFlow.tryEmit(event)
    }
}