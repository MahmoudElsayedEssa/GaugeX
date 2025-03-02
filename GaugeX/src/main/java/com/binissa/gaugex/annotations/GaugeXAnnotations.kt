package com.binissa.api.annotations


/**
 * Marks a method to be tracked for performance measurement.
 * The SDK will automatically measure and record execution time.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class TrackPerformance(
    val name: String = "", // Custom name for the event (defaults to method name)
    val category: String = "method_performance" // Performance category
)

/**
 * Marks an Activity, Fragment, or View to track screen view time.
 * For activities and fragments, it will track lifecycle events.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class TrackScreen(
    val screenName: String = "" // Custom name for the screen (defaults to class name)
)

/**
 * Marks a method to log its invocation.
 * Useful for tracking important operations.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
annotation class LogEvent(
    val level: String = "INFO", // Log level: INFO, DEBUG, WARNING, ERROR
    val message: String = "", // Custom message (defaults to "Called {methodName}")
    val includeParams: Boolean = false // Whether to include method parameters in log
)

/**
 * Marks a class, method, or field to be monitored for exceptions.
 * Any exceptions will be automatically reported.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD)
annotation class MonitorExceptions(
    val silent: Boolean = false // If true, exceptions will be caught and reported but not rethrown
)

/**
 * Marks a network-related class or method for monitoring.
 * Useful for tracking network calls that don't use OkHttp.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class MonitorNetwork(
    val trackPayload: Boolean = false // Whether to capture request/response payloads
)