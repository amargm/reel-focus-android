package com.reelfocus.app.models

// Complete user configuration matching C-001 to C-007
data class AppConfig(
    val maxSessionsDaily: Int = 5,                          // C-001: Default 5
    val sessionResetGapMinutes: Int = 10,                    // C-003: Minimum 10 min gap between sessions
    val defaultLimitValue: Int = 20,                         // C-004: Global time limit in minutes; applies to ALL monitored apps
    val overlayPosition: OverlayPosition = OverlayPosition.TOP_RIGHT,  // C-006
    val overlayTextSize: TextSize = TextSize.MEDIUM,         // C-007
    val monitoredApps: List<MonitoredApp> = emptyList(),     // C-002
    // PREMIUM FEATURE (upcoming) — when false (default) every monitored app uses defaultLimitValue.
    // When true (future premium tier), each MonitoredApp.customLimitValue is honoured if set.
    val isPerAppLimitEnabled: Boolean = false
)
