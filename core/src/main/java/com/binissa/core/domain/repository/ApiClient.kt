package com.binissa.core.domain.repository

import com.binissa.core.domain.model.Event

interface ApiClient {
    /**
     * Network error types that can occur during API communication
     */
    sealed class NetworkError : Exception() {
        class ConnectionError(
            override val message: String? = "Connection failed",
            override val cause: Throwable? = null
        ) : NetworkError()

        class ServerError(val statusCode: Int, override val message: String? = "Server error") :
            NetworkError()

        class Timeout(override val message: String? = "Request timed out") : NetworkError()
        class UnknownError(
            override val message: String? = "Unknown error", override val cause: Throwable? = null
        ) : NetworkError()
    }

    /**
     * Sends a batch of events to the backend
     * @param events The events to send
     * @return Result indicating success or failure with specific error type
     */
    suspend fun sendEvents(events: List<Event>): Result<Unit>
}