package com.reelfocus.app.models

data class SessionState(
    var secondsElapsed: Int = 0,
    var limitValue: Int = 20,                    // Minutes or Count
    var limitType: LimitType = LimitType.TIME,
    var currentSession: Int = 1,
    var maxSessions: Int = 5,
    var isActive: Boolean = false,
    var sessionStartTime: Long = 0,              // Timestamp when session started
    var lastActivityTime: Long = 0,              // For session reset gap tracking
    var extensionUsed: Boolean = false,          // Track if 5-min extension was used for current session
    var isInExtension: Boolean = false,          // Currently in extension period
    var activeAppPackage: String? = null,        // Currently monitored app
    var sessionCompleted: Boolean = false,       // Main session limit reached
    var isOnBreak: Boolean = false,              // User took break (10-min gap timer)
    var breakStartTime: Long = 0                 // When break started
)
