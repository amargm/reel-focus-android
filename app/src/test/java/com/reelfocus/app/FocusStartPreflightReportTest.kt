package com.reelfocus.app

import com.reelfocus.app.models.AppConfig
import org.junit.Assert.*
import org.junit.Test

class FocusStartPreflightReportTest {
    @Test fun requiredFailuresBlockStart() {
        StartRequirement.values().forEach { requirement ->
            val report = FocusStartPreflightReport(
                missingRequirements = setOf(requirement),
                notificationsAvailable = false,
                reelDetectionEnabled = false,
                reelDetectionConnected = false,
                batteryOptimizationExempt = false
            )
            assertFalse(report.canStart)
            assertNull(report.basicModeMessage())
        }
    }

    @Test fun optionalCapabilitiesDoNotBlockStart() {
        val report = FocusStartPreflightReport(
            missingRequirements = emptySet(),
            notificationsAvailable = false,
            reelDetectionEnabled = true,
            reelDetectionConnected = false,
            batteryOptimizationExempt = false
        )
        assertTrue(report.canStart)
        assertTrue(report.basicMode)
        assertNotNull(report.basicModeMessage())
    }

    @Test fun connectedDetectionAvoidsBasicModeMessage() {
        val report = FocusStartPreflightReport(
            missingRequirements = emptySet(),
            notificationsAvailable = true,
            reelDetectionEnabled = true,
            reelDetectionConnected = true,
            batteryOptimizationExempt = true
        )
        assertTrue(report.canStart)
        assertFalse(report.basicMode)
        assertNull(report.basicModeMessage())
    }

    @Test fun emptySelectionIsRepresentedAsRequiredFailure() {
        val config = AppConfig(monitoredApps = emptyList())
        assertTrue(config.monitoredApps.none { it.isEnabled })
    }
}
