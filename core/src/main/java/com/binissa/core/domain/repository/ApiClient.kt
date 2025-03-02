package com.binissa.core.domain.repository

import com.binissa.core.domain.model.Event

interface ApiClient {
    suspend fun sendEvents(events: List<Event>): Result<Unit>
}