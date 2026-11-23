package com.reelfocus.app.models

/** Pure policy for deciding whether a monitored-app launch must be blocked. */
object BreakEnforcementPolicy {
    fun shouldInterruptMonitoredApp(isOnBreak: Boolean, remainingMs: Long): Boolean =
        isOnBreak && remainingMs > 0L
}
