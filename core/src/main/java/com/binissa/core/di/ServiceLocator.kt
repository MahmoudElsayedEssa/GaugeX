package com.binissa.core.di

import android.content.Context
import com.binissa.core.data.manager.SessionTracker
import com.binissa.core.domain.model.GaugeXConfig
import com.binissa.core.domain.repository.ApiClient
import com.binissa.core.domain.repository.ConfigRepository
import com.binissa.core.domain.repository.EventRepository
import com.binissa.core.domain.usecase.CollectEventUseCase
import com.binissa.core.domain.usecase.CoreModule
import com.binissa.core.domain.usecase.TransmitEventsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

object ServiceLocator {
    private lateinit var appContext: Context
    private lateinit var dataModule: DataModule
    private lateinit var config: GaugeXConfig

    /**
     * Initialize the service locator with application context
     */
    fun initialize(context: Context, config: GaugeXConfig = GaugeXConfig.Builder().build()) {
        appContext = context.applicationContext
        this.config = config
        dataModule = DataModule(appContext, config)
    }

    /**
     * Get the core module
     */
    fun getCoreModule(): CoreModule {
        return dataModule.getCoreModule()
    }

    /**
     * Clean up resources
     */
    fun shutdown() {
        dataModule.shutdown()
    }
}