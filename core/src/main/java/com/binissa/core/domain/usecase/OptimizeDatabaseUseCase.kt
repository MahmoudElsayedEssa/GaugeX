package com.binissa.core.domain.usecase

interface OptimizeDatabaseUseCase {
    suspend fun execute(): Result<Unit>
}
