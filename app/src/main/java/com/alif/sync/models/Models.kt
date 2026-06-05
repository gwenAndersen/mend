package com.alif.sync.models

import kotlinx.serialization.Serializable

@Serializable
data class UserGoal(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val progress: Int = 0
)

@Serializable
data class UserGoalsData(
    val goals: List<UserGoal> = emptyList()
)

@Serializable
data class DailyBehaviorLog(
    val date: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BehaviorLogData(
    val logs: List<DailyBehaviorLog> = emptyList()
)

@Serializable
data class BehaviorTrend(
    val type: String, // "weekly" or "monthly"
    val dateRange: String,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class BehaviorTrendData(
    val trends: List<BehaviorTrend> = emptyList()
)
