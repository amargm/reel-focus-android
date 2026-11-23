package com.reelfocus.app.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityDetectionPolicyTest {
    private val selected = listOf("app.instagram", "app.video")

    @Test
    fun acceptsFreshResultOnlyForConfirmedForegroundSelectedPackage() {
        assertTrue(
            AccessibilityDetectionPolicy.canUseResult(
                "app.instagram", selected, "app.instagram", 1_500L
            )
        )
    }

    @Test
    fun rejectsResultWhenForegroundHasSwitched() {
        assertFalse(
            AccessibilityDetectionPolicy.canUseResult(
                "app.instagram", selected, "app.video", 500L
            )
        )
    }

    @Test
    fun rejectsResultForPackageThatIsNoLongerSelected() {
        assertFalse(
            AccessibilityDetectionPolicy.canUseResult(
                "app.other", selected, "app.other", 500L
            )
        )
    }

    @Test
    fun rejectsStaleOrFutureDatedResult() {
        assertFalse(
            AccessibilityDetectionPolicy.canUseResult(
                "app.instagram", selected, "app.instagram", 10_001L
            )
        )
        assertFalse(
            AccessibilityDetectionPolicy.canUseResult(
                "app.instagram", selected, "app.instagram", -1L
            )
        )
    }
}
