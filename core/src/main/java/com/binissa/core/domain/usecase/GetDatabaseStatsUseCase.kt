package com.binissa.core.domain.usecase

interface GetDatabaseStatsUseCase {
    suspend fun execute(): Result<DatabaseStats>
    
    data class DatabaseStats(
        val totalEvents: Int,
        val databaseSizeBytes: Long,
        val pendingEvents: Int,
        val failedEvents: Int
    )
}