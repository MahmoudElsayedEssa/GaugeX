package com.binissa.core.di

import android.content.Context
import com.binissa.core.domain.model.GaugeXConfig
import com.binissa.core.domain.usecase.CoreModule
import com.binissa.core.domain.usecase.CoreModuleImpl

/**
 * Module for providing all core data dependencies
 * This replaces the ServiceLocator pattern with a more structured approach
 */
class DataModule(private val context: Context, private val config: GaugeXConfig) {
    // Single Core Module instance
    private val coreModule by lazy { CoreModuleImpl(context, config) }

    /**
     * Gets the core module instance
     * This is the only method needed since all functionality is exposed through the core module
     */
    fun getCoreModule(): CoreModule = coreModule

    /**
     * Clean up resources
     */
    fun shutdown() {
        // Any cleanup required when the app is shutting down
    }
}