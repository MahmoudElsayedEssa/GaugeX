package com.binissa.plugin.user

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.binissa.core.domain.model.Event
import com.binissa.core.domain.model.EventType
import com.binissa.plugin.GaugeXPlugin
import com.binissa.plugins.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Enhanced plugin for tracking user behavior including screen views and interactions
 * Provides automatic activity/fragment tracking and view instrumentation
 */
class UserBehaviorPlugin : GaugeXPlugin {
    private val TAG = "UserBehaviorPlugin"

    // Flow of user behavior events
    private val _eventFlow = MutableSharedFlow<Event>(replay = 0)

    // Context to access app info
    private lateinit var appContext: Context

    // Application lifecycle callbacks
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    // Fragment lifecycle callbacks
    private var fragmentCallbacks: FragmentManager.FragmentLifecycleCallbacks? = null

    // View instrumentation callbacks
    private var viewInstrumentationCallbacks: Application.ActivityLifecycleCallbacks? = null

    // Breadcrumb trail - stored in memory
    private val breadcrumbs = ConcurrentLinkedQueue<Breadcrumb>()
    private val MAX_BREADCRUMBS = 100

    // Screen tracking
    private var currentActivityRef: WeakReference<Activity>? = null
    private var currentScreenName: String? = null

    // Plugin-specific coroutine scope
    private val pluginScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val id: String = "user"

    /**
     * Initialize the user behavior plugin
     */
    override suspend fun initialize(context: Context, config: Map<String, Any>): Boolean {
        try {
            this.appContext = context.applicationContext

            // Initialize lifecycle callbacks
            initializeLifecycleCallbacks()

            // Initialize view instrumentation callbacks
            initializeViewInstrumentationCallbacks()

            Log.i(TAG, "User behavior plugin initialized")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize user behavior plugin", e)
            return false
        }
    }

    /**
     * Start monitoring user behavior
     */
    override suspend fun startMonitoring() {
        try {
            // Register activity lifecycle callbacks if context is an Application
            if (appContext is Application) {
                val app = appContext as Application

                // Register screen tracking callback
                lifecycleCallbacks?.let {
                    app.registerActivityLifecycleCallbacks(it)
                }

                // Register view instrumentation callback
                viewInstrumentationCallbacks?.let {
                    app.registerActivityLifecycleCallbacks(it)
                }
            }

            Log.i(TAG, "User behavior monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start user behavior monitoring", e)
        }
    }

    /**
     * Stop monitoring user behavior
     */
    override suspend fun stopMonitoring() {
        try {
            // Unregister activity lifecycle callbacks
            if (appContext is Application) {
                val app = appContext as Application

                lifecycleCallbacks?.let {
                    app.unregisterActivityLifecycleCallbacks(it)
                }

                viewInstrumentationCallbacks?.let {
                    app.unregisterActivityLifecycleCallbacks(it)
                }
            }

            // Clear current activity reference
            currentActivityRef = null
            currentScreenName = null

            Log.i(TAG, "User behavior monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop user behavior monitoring", e)
        }
    }

    /**
     * Get the flow of user behavior events
     */
    override fun getEvents(): Flow<Event> = _eventFlow.asSharedFlow()

    /**
     * Shutdown the plugin and clean up resources
     */
    override suspend fun shutdown() {
        try {
            stopMonitoring()
            breadcrumbs.clear()

            // Cancel all coroutines
            pluginScope.cancel()

            Log.i(TAG, "User behavior plugin shutdown")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to shutdown user behavior plugin", e)
        }
    }

    /**
     * Initialize lifecycle callbacks for screen monitoring
     */
    private fun initializeLifecycleCallbacks() {
        // Activity lifecycle callbacks
        lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivityRef = WeakReference(activity)
                val screenName = activity.javaClass.simpleName
                currentScreenName = screenName

                // Track screen view
                trackScreenView(screenName)

                // Register fragment callbacks if it's a FragmentActivity
                if (activity is FragmentActivity) {
                    registerFragmentCallbacks(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) {
                // Update current activity reference
                currentActivityRef = WeakReference(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivityRef = WeakReference(activity)

                // Track resumed state (may be coming back from another app)
                val screenName = activity.javaClass.simpleName
                if (currentScreenName != screenName) {
                    currentScreenName = screenName
                    trackScreenView(screenName)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                // Nothing to do
            }

            override fun onActivityStopped(activity: Activity) {
                // Nothing to do
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                // Nothing to do
            }

            override fun onActivityDestroyed(activity: Activity) {
                // Clear reference if it's the current activity
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                }
            }
        }

        // Fragment lifecycle callbacks
        fragmentCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                val fragmentName = f.javaClass.simpleName
                trackScreenView(fragmentName)
            }
        }
    }

    /**
     * Initialize callbacks for automatic view instrumentation
     */
    private fun initializeViewInstrumentationCallbacks() {
        viewInstrumentationCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Wait for view hierarchy to be ready
                activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            try {
                                // Find and instrument all clickable views
                                instrumentViewHierarchy(activity.window.decorView)

                                // Remove listener to avoid multiple calls
                                activity.window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                                Log.d(TAG, "Automatically instrumented views for ${activity.javaClass.simpleName}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error automatically instrumenting views", e)
                            }
                        }
                    }
                )
            }

            override fun onActivityStarted(activity: Activity) {
                // Nothing to do
            }

            override fun onActivityResumed(activity: Activity) {
                // Nothing to do
            }

            override fun onActivityPaused(activity: Activity) {
                // Nothing to do
            }

            override fun onActivityStopped(activity: Activity) {
                // Nothing to do
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                // Nothing to do
            }

            override fun onActivityDestroyed(activity: Activity) {
                // Nothing to do
            }
        }
    }

    /**
     * Register fragment callbacks for a FragmentActivity
     */
    private fun registerFragmentCallbacks(activity: FragmentActivity) {
        fragmentCallbacks?.let { callbacks ->
            activity.supportFragmentManager.registerFragmentLifecycleCallbacks(callbacks, true)
        }
    }

    /**
     * Track a screen view
     * @param screenName Name of the screen
     */
    fun trackScreenView(screenName: String) {
        // Add to breadcrumb trail
        addBreadcrumb(
            type = "screen_view",
            category = "navigation",
            message = "Viewed screen: $screenName",
            data = mapOf("screen_name" to screenName)
        )

        // Create screen view event
        val event = UserEvent(
            eventType = "screen_view",
            screenName = screenName,
            eventName = "view",
            properties = mapOf("screen_name" to screenName)
        )

        _eventFlow.tryEmit(event)

        Log.d(TAG, "Screen view: $screenName")
    }

    /**
     * Track a user interaction
     * @param eventName Name of the interaction
     * @param properties Additional properties about the interaction
     */
    fun trackUserInteraction(eventName: String, properties: Map<String, Any> = emptyMap()) {
        // Add to breadcrumb trail
        addBreadcrumb(
            type = "interaction",
            category = "ui",
            message = "User interaction: $eventName",
            data = properties.mapValues { it.value.toString() }
        )

        // Create user interaction event
        val event = UserEvent(
            eventType = "interaction",
            screenName = currentScreenName ?: "unknown",
            eventName = eventName,
            properties = properties
        )

        _eventFlow.tryEmit(event)

        Log.d(TAG, "User interaction: $eventName, properties: $properties")
    }

    /**
     * Add a breadcrumb to the trail
     */
    private fun addBreadcrumb(
        type: String,
        category: String,
        message: String,
        data: Map<String, String> = emptyMap(),
        level: String = "info"
    ) {
        val breadcrumb = Breadcrumb(
            timestamp = System.currentTimeMillis(),
            type = type,
            category = category,
            message = message,
            data = data,
            level = level
        )

        // Add to queue, maintaining maximum size
        breadcrumbs.add(breadcrumb)
        while (breadcrumbs.size > MAX_BREADCRUMBS) {
            breadcrumbs.poll()
        }
    }

    /**
     * Get the breadcrumb trail
     * @return List of breadcrumbs, newest first
     */
    fun getBreadcrumbs(): List<Breadcrumb> {
        return breadcrumbs.toList().sortedByDescending { it.timestamp }
    }

    /**
     * Data class for breadcrumbs
     */
    data class Breadcrumb(
        val timestamp: Long,
        val type: String,        // screen_view, interaction, etc.
        val category: String,    // navigation, ui, network, etc.
        val message: String,     // Human-readable description
        val data: Map<String, String>, // Additional context
        val level: String        // info, warning, error, etc.
    )

    /**
     * Event class for user behavior
     */
    data class UserEvent(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val type: EventType = EventType.USER_ACTION,
        val eventType: String,  // screen_view, interaction, etc.
        val screenName: String,  // Current screen name
        val eventName: String,   // Specific event name
        val properties: Map<String, Any> = emptyMap() // Additional properties
    ) : Event

    /**
     * Utility method to instrument a view with touch tracking
     * @param view The view to instrument
     * @param eventName Custom event name (defaults to view's class name)
     */
    fun instrumentView(view: View, eventName: String? = null) {
        val name = eventName ?: view.javaClass.simpleName

        // Instead of trying to get the original listener (which isn't directly accessible)
        // We'll create a new listener that wraps any existing functionality
        val existingListener = view.getTag(R.id.gauge_touch_listener_tag) as? View.OnTouchListener

        val newListener = View.OnTouchListener { v, event ->
            val handled = existingListener?.onTouch(v, event) ?: false

            if (event.action == MotionEvent.ACTION_UP) {
                // Determine a better name based on the view type
                val viewName = when (v) {
                    is Button -> "Button: ${(v as? Button)?.text ?: name}"
                    is TextView -> "TextView: ${(v as? TextView)?.text?.toString()?.take(20) ?: name}"
                    else -> name
                }

                // Track the interaction
                trackUserInteraction(
                    eventName = "tap",
                    properties = mapOf(
                        "element_type" to v.javaClass.simpleName,
                        "element_name" to viewName
                    )
                )
            }

            handled
        }

        // Store our listener for future reference
        view.setTag(R.id.gauge_touch_listener_tag, newListener)
        view.setOnTouchListener(newListener)
    }

    /**
     * Recursively instrument all views in a view hierarchy
     * @param root The root view to start from
     */
    fun instrumentViewHierarchy(root: View) {
        try {
            // Skip already instrumented views
            if (root.getTag(R.id.gauge_touch_listener_tag) != null) {
                return
            }

            // Instrument the root view if it's clickable
            if (root.isClickable) {
                instrumentView(root)
            }

            // Recursively instrument child views if this is a ViewGroup
            if (root is ViewGroup) {
                for (i in 0 until root.childCount) {
                    val child = root.getChildAt(i)
                    instrumentViewHierarchy(child)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error instrumenting view hierarchy", e)
        }
    }

    /**
     * Set up automatic view instrumentation for all activities
     * @param application The application to monitor
     */
    fun setupAutomaticViewInstrumentation(application: Application) {
        viewInstrumentationCallbacks?.let { callback ->
            application.registerActivityLifecycleCallbacks(callback)
            Log.i(TAG, "Automatic view instrumentation set up")
        }
    }

    /**
     * Monitor a specific activity
     * @param activity The activity to monitor
     */
    fun monitorActivity(activity: Activity) {
        try {
            // Track screen view
            trackScreenView(activity.javaClass.simpleName)

            // Instrument views
            instrumentViewHierarchy(activity.window.decorView)

            // If it's a FragmentActivity, monitor fragments
            if (activity is FragmentActivity) {
                registerFragmentCallbacks(activity)
            }

            Log.i(TAG, "Activity ${activity.javaClass.simpleName} monitored")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to monitor activity", e)
        }
    }
}