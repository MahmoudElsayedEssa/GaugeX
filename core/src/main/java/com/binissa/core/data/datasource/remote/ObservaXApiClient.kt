package com.binissa.core.data.datasource.remote

import android.util.Log
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.repository.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.time.Duration.Companion.seconds

/**
 * Implementation of the API client for sending events to the GaugeX backend
 * with improved error handling and timeout management
 */
class ObservaXApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val jsonSerializer: JsonSerializer
) : ApiClient {

    private val TAG = "ObservaXApiClient"
    private val CONNECT_TIMEOUT_MS = 15_000
    private val READ_TIMEOUT_MS = 30_000
    private val OVERALL_TIMEOUT_SECONDS = 45

    /**
     * Sends a batch of events to the GaugeX backend
     * @param events The events to send
     * @return Result of the operation with specific error types
     */
    override suspend fun sendEvents(events: List<Event>): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (events.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            try {
                withTimeout(OVERALL_TIMEOUT_SECONDS.seconds) {
                    val url = URL("$baseUrl/events")

                    var connection: HttpsURLConnection? = null
                    try {
                        connection = (url.openConnection() as HttpsURLConnection).apply {
                            requestMethod = "POST"
                            doOutput = true
                            connectTimeout = CONNECT_TIMEOUT_MS
                            readTimeout = READ_TIMEOUT_MS
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("X-API-Key", apiKey)
                            setRequestProperty("Accept", "application/json")
                            // Add compression if supported
                            setRequestProperty("Accept-Encoding", "gzip")
                        }

                        // Serialize events to JSON
                        val jsonPayload = jsonSerializer.serialize(events)

                        // Send data
                        connection.outputStream.use { os ->
                            os.write(jsonPayload.toByteArray())
                            os.flush()
                        }

                        // Check response
                        val responseCode = connection.responseCode
                        if (responseCode in 200..299) {
                            // Success
                            Log.d(
                                TAG,
                                "Successfully sent ${events.size} events, response code: $responseCode"
                            )
                            Result.success(Unit)
                        } else {
                            // Server error
                            val errorMessage =
                                connection.errorStream?.bufferedReader()?.use { it.readText() }
                                    ?: "Unknown server error"
                            Log.e(
                                TAG, "API request failed with status $responseCode: $errorMessage"
                            )
                            Result.failure(
                                ApiClient.NetworkError.ServerError(
                                    responseCode, errorMessage
                                )
                            )
                        }
                    } catch (e: ConnectException) {
                        Log.e(TAG, "Connection error", e)
                        Result.failure(ApiClient.NetworkError.ConnectionError(cause = e))
                    } catch (e: SocketTimeoutException) {
                        Log.e(TAG, "Timeout error", e)
                        Result.failure(ApiClient.NetworkError.Timeout(e.message))
                    } catch (e: IOException) {
                        Log.e(TAG, "I/O error", e)
                        Result.failure(ApiClient.NetworkError.ConnectionError(e.message, e))
                    } catch (e: Exception) {
                        Log.e(TAG, "Unknown error", e)
                        Result.failure(ApiClient.NetworkError.UnknownError(e.message, e))
                    } finally {
                        connection?.disconnect()
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e(TAG, "Overall timeout exceeded", e)
                Result.failure(ApiClient.NetworkError.Timeout("Overall timeout exceeded"))
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                Result.failure(ApiClient.NetworkError.UnknownError(e.message, e))
            }
        }
}