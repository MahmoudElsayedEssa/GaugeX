package com.binissa.core.data.repository

import com.binissa.core.domain.repository.EventRepository

class InsightGenerator(private val eventRepository: EventRepository) {
    
    suspend fun generatePerformanceInsights(): List<Insight> {
        val insights = mutableListOf<Insight>()
        
        // Find slow screens
        val slowScreens = eventRepository.getSlowestScreens(5)
        if (slowScreens.isNotEmpty()) {
            insights.add(Insight(
                type = "performance",
                title = "Slow Screen Loads",
                description = "The following screens have the slowest load times",
                data = slowScreens,
                severity = calculateSeverity(slowScreens.first().avgDuration.toLong())
            ))
        }
        
        // Find performance regressions
        val regressions = eventRepository.getPerformanceRegressions()
        if (regressions.isNotEmpty()) {
            insights.add(Insight(
                type = "regression",
                title = "Performance Regressions",
                description = "${regressions.size} operations have gotten slower",
                data = regressions,
                severity = "high"
            ))
        }
        
        // Find memory leaks
        val potentialLeaks = eventRepository.getPotentialMemoryLeaks()
        if (potentialLeaks.isNotEmpty()) {
            insights.add(Insight(
                type = "memory",
                title = "Potential Memory Leaks",
                description = "Memory usage is steadily increasing in these screens",
                data = potentialLeaks,
                severity = "critical"
            ))
        }
        
        return insights
    }
    
    private fun calculateSeverity(duration: Long): String {
        return when {
            duration > 5000 -> "critical"
            duration > 1000 -> "high"
            duration > 500 -> "medium"
            else -> "low"
        }
    }
}