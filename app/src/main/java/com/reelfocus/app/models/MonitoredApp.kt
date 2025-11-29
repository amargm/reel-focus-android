package com.reelfocus.app.models

// C-002: Monitored App data model
data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val customLimitValue: Int? = null  // C-005: Override global timer limit (in minutes)
    // Note: Sessions (maxSessionsDaily) are GLOBAL and cannot be overridden per app
)
