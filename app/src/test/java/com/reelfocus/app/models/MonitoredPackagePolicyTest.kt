package com.reelfocus.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoredPackagePolicyTest {
    @Test
    fun enabledSelectionOnlyIncludesEnabledNonBlankPackages() {
        val apps = listOf(
            MonitoredApp("com.instagram.android", "Instagram", true),
            MonitoredApp("com.example.disabled", "Disabled", false),
            MonitoredApp("  com.example.custom  ", "Custom", true),
            MonitoredApp(" ", "Blank", true)
        )

        assertEquals(
            setOf("com.instagram.android", "com.example.custom"),
            MonitoredPackagePolicy.enabledPackages(apps)
        )
    }

    @Test
    fun membershipUsesPackageIdentityNotDisplayName() {
        val selected = setOf("com.example.video")

        assertTrue(MonitoredPackagePolicy.contains(selected, "com.example.video"))
        assertFalse(MonitoredPackagePolicy.contains(selected, "Video"))
        assertFalse(MonitoredPackagePolicy.contains(selected, null))
    }
}
