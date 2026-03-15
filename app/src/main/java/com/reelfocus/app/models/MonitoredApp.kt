package com.reelfocus.app.models

// C-002: Monitored App data model
data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    // C-005: Per-app time limit override (minutes).
    // PREMIUM FEATURE (upcoming) — stored always, but only applied when AppConfig.isPerAppLimitEnabled == true.
    val customLimitValue: Int? = null
    // Note: Sessions (maxSessionsDaily) are GLOBAL and cannot be overridden per app
)
