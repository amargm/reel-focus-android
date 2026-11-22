package com.reelfocus.app

import com.reelfocus.app.models.AppConfig
import com.reelfocus.app.models.MonitoredApp
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

    @Test fun uninstalledAppsArePrunedWithoutChangingInstalledChoices() {
        val config = AppConfig(monitoredApps = listOf(
            MonitoredApp("installed.enabled", "Enabled", true),
            MonitoredApp("installed.disabled", "Disabled", false),
            MonitoredApp("removed.app", "Removed", true)
        ))
        val result = pruneUninstalledApps(config) { it != "removed.app" }
        assertEquals(listOf("installed.enabled", "installed.disabled"), result.monitoredApps.map { it.packageName })
        assertTrue(result.monitoredApps.first().isEnabled)
        assertFalse(result.monitoredApps.last().isEnabled)
    }

    @Test fun pruningAllSelectedAppsLeavesAnEmptySelectionForPreflightToBlock() {
        val config = AppConfig(monitoredApps = listOf(MonitoredApp("removed.app", "Removed", true)))
        val result = pruneUninstalledApps(config) { false }
        assertTrue(result.monitoredApps.isEmpty())
    }
}
