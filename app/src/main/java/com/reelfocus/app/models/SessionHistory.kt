package com.reelfocus.app.models

data class SessionHistory(
    val id: String,
    val appName: String,
    val appPackage: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val limitType: LimitType,
    val limitValue: Int,
    val extensionsUsed: Int,
    val completed: Boolean,  // True if limit reached, false if manually stopped
    val date: String  // YYYY-MM-DD format for grouping
)

data class DailyStats(
    val date: String,
    val totalSessions: Int,
    val completedSessions: Int,
    val totalTimeSeconds: Int,
    val totalExtensions: Int,
    val appBreakdown: Map<String, AppDayStats>
)

data class AppDayStats(
    val appName: String,
    val sessions: Int,
    val totalTimeSeconds: Int,
    val extensions: Int
)
