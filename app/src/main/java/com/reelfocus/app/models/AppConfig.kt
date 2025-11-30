package com.reelfocus.app.models

// Complete user configuration matching C-001 to C-007
data class AppConfig(
    val maxSessionsDaily: Int = 5,                          // C-001: Default 5
    val sessionResetGapMinutes: Int = 10,                    // C-003: Minimum 10 min gap between sessions
    val defaultLimitType: LimitType = LimitType.TIME,        // C-004: Default TIME
    val defaultLimitValue: Int = 20,                         // C-004: Default 20 minutes
    val overlayPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,  // C-006
    val overlayTextSize: TextSize = TextSize.MEDIUM,         // C-007
    val monitoredApps: List<MonitoredApp> = emptyList()      // C-002
)
