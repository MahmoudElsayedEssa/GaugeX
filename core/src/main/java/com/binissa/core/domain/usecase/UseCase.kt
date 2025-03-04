package com.binissa.core.domain.usecase

interface UseCase<in P, R> {
    suspend operator fun invoke(params: P): Result<R>
}

// For use cases that don't need parameters
interface NoParamUseCase<R> {
    suspend operator fun invoke(): Result<R>
}