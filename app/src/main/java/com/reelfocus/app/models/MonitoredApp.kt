package com.reelfocus.app.models

// C-002: Monitored App data model
data class MonitoredApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val customLimitType: LimitType? = null,  // C-005: Override default
    val customLimitValue: Int? = null        // C-005: Override default
)
