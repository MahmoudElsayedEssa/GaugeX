package com.binissa.plugin

import android.content.Context
import android.util.Log
import com.binissa.core.domain.model.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class PluginRegistry(private val coroutineScope: CoroutineScope) {

    private val plugins: MutableMap<String, GaugeXPlugin> = mutableMapOf()

    private val pluginsUpdated = MutableSharedFlow<Unit>()

    /**
     * Register a plugin with the registry
     * @param plugin The plugin to register
     */
    fun registerPlugin(plugin: GaugeXPlugin) {
        plugins[plugin.id] = plugin
        // Emit to signal that plugins have been updated
        coroutineScope.launch {
            pluginsUpdated.emit(Unit)
        }
    }

    /**
     * Get a plugin by its ID
     * @param id The plugin ID
     * @return The plugin, or null if not found
     */
    fun getPlugin(id: String): GaugeXPlugin? {
        return plugins[id]
    }

    /**
     * Initialize all registered plugins
     * @param context Application context
     * @param configs Configuration for each plugin
     */
    suspend fun initializePlugins(context: Context, configs: Map<String, Map<String, Any>>) {
        plugins.forEach { (id, plugin) ->
            val config = configs[id] ?: emptyMap()
            plugin.initialize(context, config)
        }
    }

    /**
     * Start monitoring with all registered plugins
     */
    suspend fun startMonitoring() {
        plugins.values.forEach { plugin ->
            plugin.startMonitoring()
        }
    }

    /**
     * Stop monitoring with all registered plugins
     */
    suspend fun stopMonitoring() {
        plugins.values.forEach { plugin ->
            plugin.stopMonitoring()
        }
    }

    /**
     * Shutdown all plugins and clean up resources
     */
    suspend fun shutdown() {
        plugins.values.forEach { plugin ->
            plugin.shutdown()
        }
        plugins.clear()
    }

    fun getPluginCount(): Int = plugins.size

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllPluginEventFlows(): Flow<Event> {
        Log.d("PluginRegistry", "Getting all plugin flows. Plugins count: ${plugins.size}")
        if (plugins.isNotEmpty()) {
            Log.d("PluginRegistry", "Creating merged flow from ${plugins.size} plugins")
            return plugins.values.map { plugin ->
                plugin.getEvents()
            }.merge()
        }

        // If no plugins yet, wait for plugins to be registered
        Log.d("PluginRegistry", "No plugins available, waiting for registration")
        return pluginsUpdated
            .onEach { Log.d("PluginRegistry", "Plugins updated, recreating flow") }
            .flatMapLatest {
                if (plugins.isEmpty()) {
                    Log.w("PluginRegistry", "Still no plugins available after update")
                    emptyFlow()
                } else {
                    Log.d(
                        "PluginRegistry",
                        "Creating merged flow from ${plugins.size} plugins after update"
                    )
                    plugins.values.map { plugin -> plugin.getEvents() }.merge()
                }
            }
    }
}
