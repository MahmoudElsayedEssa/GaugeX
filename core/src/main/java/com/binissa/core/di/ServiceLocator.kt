package com.binissa.core.di

import android.content.Context
import com.binissa.core.data.analytics.AnalyticsEngine
import com.binissa.core.data.datasource.local.EventDatabase
import com.binissa.core.data.datasource.remote.ObservaXApiClient
import com.binissa.core.data.manager.SessionTracker
import com.binissa.core.data.repository.ConfigRepositoryImpl
import com.binissa.core.data.repository.EventDataAggregator
import com.binissa.core.data.repository.EventStream
import com.binissa.core.data.repository.EventRepositoryImpl
import com.binissa.core.data.repository.AdaptiveSampler
import com.binissa.core.domain.repository.ApiClient
import com.binissa.core.domain.repository.ConfigRepository
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.domain.usecase.CollectEventUseCase
import com.binissa.core.domain.usecase.TransmitEventsUseCase
import com.binissa.core.data.mapper.EventMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object ServiceLocator {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var appContext: Context
    private val database by lazy { EventDatabase.getInstance(appContext) }
    private val jsonSerializer by lazy { JsonSerializerImpl() }
    private val configRepository by lazy { ConfigRepositoryImpl(appContext) }
    private val sessionTracker by lazy {
        SessionTracker(
            context = appContext,
            eventDao = database.eventDao(),
            jsonSerializer = jsonSerializer,
            sessionTimeout = 30 * 60 * 1000 // 30 minutes
        )
    }
    private val eventDataAggregator by lazy {
        EventDataAggregator(
            context = appContext,
            sessionTracker = sessionTracker
        )
    }
    private val adaptiveSampler by lazy {
        AdaptiveSampler(configRepository = configRepository)
    }
    private val eventRepository by lazy {
        EventRepositoryImpl(
            eventDao = database.eventDao(),
            eventMapper = EventMapper(jsonSerializer,sessionTracker),
            jsonSerializer = jsonSerializer,
            context = appContext,
            analyticsService = analyticsEngine,
            ioDispatcher = Dispatchers.IO
        )
    }

    private val analyticsEngine by lazy {
        AnalyticsEngine(
            eventDao = database.eventDao(),
            jsonSerializer = jsonSerializer,
            ioDispatcher = Dispatchers.IO
        )
    }

    private val eventStream by lazy {
        EventStream(
            samplingStrategy = adaptiveSampler,
            contextProvider = eventDataAggregator,
            eventRepository = eventRepository
        )
    }
    private var apiClient: ApiClient? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun getApplicationScope(): CoroutineScope = applicationScope

    fun getConfigRepository(): ConfigRepository = configRepository

    fun getEventRepository(): EventRepository = eventRepository

    /**
     * Get the API client
     */
    fun getApiClient(): ApiClient {
        return apiClient ?: synchronized(this) {
            ObservaXApiClient(
                baseUrl = "https://api.gaugex.com/v1",
                apiKey = "",  // Will be populated from config
                jsonSerializer = jsonSerializer
            ).also {
                apiClient = it
            }
        }
    }

    fun getSessionManager(): SessionTracker = sessionTracker

    fun getEventContextProvider(): EventDataAggregator = eventDataAggregator

    fun getSmartSamplingStrategy(): AdaptiveSampler = adaptiveSampler

    fun getEventAnalyticsService(): AnalyticsEngine = analyticsEngine

    fun getEventPipeline(): EventStream = eventStream

    fun getCollectEventUseCase(): CollectEventUseCase = CollectEventUseCase(
        eventRepository = eventRepository,
        configRepository = configRepository,
        ioDispatcher = Dispatchers.IO
    )

    fun getTransmitEventsUseCase(): TransmitEventsUseCase = TransmitEventsUseCase(
        eventRepository = eventRepository,
        apiClient = getApiClient(),
        ioDispatcher = Dispatchers.IO
    )

    // Cleanup method for testing or shutdown
    fun clear() {
        applicationScope.cancel()
        EventDatabase.getInstance(appContext).close()
    }
}
