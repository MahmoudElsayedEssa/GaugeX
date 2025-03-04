package com.binissa.core.domain.usecase

import android.content.Context
import com.binissa.core.data.datasource.local.EventDatabase
import com.binissa.core.data.datasource.remote.ObservaXApiClient
import com.binissa.core.data.manager.SessionTracker
import com.binissa.core.data.mapper.EventMapper
import com.binissa.core.data.repository.ConfigRepositoryImpl
import com.binissa.core.data.repository.EventRepositoryImpl
import com.binissa.core.data.datasource.remote.JsonSerializerImpl
import com.binissa.core.domain.repository.ApiClient
import com.binissa.core.domain.repository.ConfigRepository
import com.binissa.core.domain.repository.EventRepository
import kotlinx.coroutines.Dispatchers

/**
 * Factory implementation that creates use cases and manages dependencies
 */
class CoreModuleFactoryImpl(private val applicationContext: Context) : CoreModuleFactory {
    
    // Database
    private val database by lazy { EventDatabase.getInstance(applicationContext) }
    private val eventDao by lazy { database.eventDao() }
    
    // Repositories and dependencies
    private val jsonSerializer by lazy { JsonSerializerImpl() }
    
    private val configRepository: ConfigRepository by lazy { 
        ConfigRepositoryImpl(applicationContext) 
    }
    
    private val sessionTracker by lazy {
        SessionTracker(
            context = applicationContext,
            jsonSerializer = jsonSerializer,
            sessionTimeout = 30 * 60 * 1000 // 30 minutes
        )
    }
    
    private val eventMapper by lazy {
        EventMapper(
            jsonSerializer = jsonSerializer,
            sessionTracker = sessionTracker
        )
    }
    
//    private val analyticsEngine by lazy {
//        AnalyticsEngine(
//            eventDao = eventDao,
//            jsonSerializer = jsonSerializer,
//            ioDispatcher = Dispatchers.IO
//        )
//    }
    
    private val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(
            eventDao = eventDao,
            eventMapper = eventMapper,
            jsonSerializer = jsonSerializer,
            context = applicationContext,
            ioDispatcher = Dispatchers.IO
        )
    }
    
    private val apiClient: ApiClient by lazy {
        ObservaXApiClient(
            baseUrl = "https://api.gaugex.com/v1",
            apiKey = "",  // Will be populated from config
            jsonSerializer = jsonSerializer
        )
    }
    
    // Use case implementations
    override fun createCollectEventUseCase(): CollectEventUseCase {
        return CollectEventUseCaseImpl(
            eventRepository = eventRepository,
            configRepository = configRepository,
            ioDispatcher = Dispatchers.IO
        )
    }
    
    override fun createTransmitEventsUseCase(): TransmitEventsUseCase {
        return TransmitEventsUseCaseImpl(
            eventRepository = eventRepository,
            apiClient = apiClient,
            ioDispatcher = Dispatchers.IO
        )
    }
    
    override fun createPurgeEventsUseCase(): PurgeEventsUseCase {
        return PurgeEventsUseCaseImpl(
            eventRepository = eventRepository,
            ioDispatcher = Dispatchers.IO
        )
    }
    
    override fun createGetDatabaseStatsUseCase(): GetDatabaseStatsUseCase {
        return GetDatabaseStatsUseCaseImpl(
            eventRepository = eventRepository,
            ioDispatcher = Dispatchers.IO
        )
    }
    
    override fun createOptimizeDatabaseUseCase(): OptimizeDatabaseUseCase {
        return OptimizeDatabaseUseCaseImpl(
            eventRepository = eventRepository,
            ioDispatcher = Dispatchers.IO
        )
    }
    
    override fun shutdown() {
        database.close()
    }
}