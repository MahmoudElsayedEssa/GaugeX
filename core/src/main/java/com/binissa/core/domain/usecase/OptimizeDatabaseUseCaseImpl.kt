package com.binissa.core.domain.usecase

import android.util.Log
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.domain.usecase.OptimizeDatabaseUseCase
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class OptimizeDatabaseUseCaseImpl(
    private val eventRepository: EventRepository,
    private val ioDispatcher: CoroutineContext
) : OptimizeDatabaseUseCase {

    private val TAG = "OptimizeDatabaseUseCase"

    override suspend fun execute(): Result<Unit> = withContext(ioDispatcher) {
        try {
            Log.d(TAG, "Running database optimization")
            eventRepository.optimizeDatabase()
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing database", e)
            Result.failure(e)
        }
    }
}