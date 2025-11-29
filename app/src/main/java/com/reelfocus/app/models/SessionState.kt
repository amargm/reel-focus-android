package com.reelfocus.app.models

data class SessionState(
    var secondsElapsed: Int = 0,
    var limitValue: Int = 20, // Minutes or Count
    var limitType: LimitType = LimitType.TIME,
    var currentSession: Int = 1,
    var maxSessions: Int = 5,
    var isActive: Boolean = false
)
