package com.reelfocus.app.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRecoveryPolicyTest {
    @Test
    fun hintRequiresEnabledDisconnectedMonitoringAndSixtySeconds() {
        assertFalse(AccessibilityRecoveryPolicy.shouldShowHint(false, false, true, 60_000, null, "day"))
        assertFalse(AccessibilityRecoveryPolicy.shouldShowHint(true, true, true, 60_000, null, "day"))
        assertFalse(AccessibilityRecoveryPolicy.shouldShowHint(true, false, false, 60_000, null, "day"))
        assertFalse(AccessibilityRecoveryPolicy.shouldShowHint(true, false, true, 59_999, null, "day"))
        assertTrue(AccessibilityRecoveryPolicy.shouldShowHint(true, false, true, 60_000, null, "day"))
    }

    @Test
    fun hintIsLimitedToOncePerLocalDay() {
        assertFalse(AccessibilityRecoveryPolicy.shouldShowHint(true, false, true, 90_000, "day", "day"))
        assertTrue(AccessibilityRecoveryPolicy.shouldShowHint(true, false, true, 90_000, "yesterday", "day"))
    }
}
