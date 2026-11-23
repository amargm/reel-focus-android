package com.reelfocus.app.models

/**
 * Recovery hint is deliberately delayed and rate-limited. Basic detection can
 * continue while the optional Accessibility service reconnects.
 */
object AccessibilityRecoveryPolicy {
    const val DISCONNECTED_HINT_DELAY_MS = 60_000L

    fun shouldShowHint(
        accessibilityEnabled: Boolean,
        serviceConnected: Boolean,
        monitoringActive: Boolean,
        disconnectedDurationMs: Long,
        lastShownDay: String?,
        currentDay: String
    ): Boolean =
        accessibilityEnabled &&
            !serviceConnected &&
            monitoringActive &&
            disconnectedDurationMs >= DISCONNECTED_HINT_DELAY_MS &&
            lastShownDay != currentDay
}
