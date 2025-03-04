package com.binissa.core.domain.usecase

interface CoreModuleFactory {
    fun createCollectEventUseCase(): CollectEventUseCase
    fun createTransmitEventsUseCase(): TransmitEventsUseCase
    fun createPurgeEventsUseCase(): PurgeEventsUseCase
    fun createGetDatabaseStatsUseCase(): GetDatabaseStatsUseCase
    fun createOptimizeDatabaseUseCase(): OptimizeDatabaseUseCase
    fun shutdown()
}