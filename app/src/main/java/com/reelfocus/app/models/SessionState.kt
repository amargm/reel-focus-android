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
    var extensionCount: Int = 0,                 // UX-003: Track extensions
    var activeAppPackage: String? = null         // Currently monitored app
)
