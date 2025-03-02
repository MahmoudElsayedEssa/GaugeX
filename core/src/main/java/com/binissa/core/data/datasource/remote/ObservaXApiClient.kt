package com.binissa.core.data.datasource.remote


import com.binissa.core.domain.model.Event
import com.binissa.core.domain.repository.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Implementation of the API client for sending events to the GaugeX backend
 */
class ObservaXApiClient(
    private val baseUrl: String,
    private val apiKey: String,
    private val jsonSerializer: JsonSerializer
) : ApiClient {

    /**
     * Sends a batch of events to the GaugeX backend
     * @param events The events to send
     * @return Result of the operation
     */
    override suspend fun sendEvents(events: List<Event>): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("$baseUrl/events")
                val connection = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-API-Key", apiKey)
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
                return@withContext if (responseCode in 200..299) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("API request failed with status $responseCode"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}