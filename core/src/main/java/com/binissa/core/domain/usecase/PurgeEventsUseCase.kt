package com.binissa.core.domain.usecase

interface PurgeEventsUseCase {
    suspend fun execute(olderThan: Long): Result<Int>
}