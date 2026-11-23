package com.reelfocus.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundActivityTrackerTest {
    @Test
    fun pausingOneActivityDoesNotClearAnotherResumedActivityInSamePackage() {
        val tracker = ForegroundActivityTracker()
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED, "app.a", "MainActivity", 10)
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED, "app.a", "PlayerActivity", 20)
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_PAUSED, "app.a", "MainActivity", 30)

        assertEquals("app.a", tracker.currentPackage())
    }

    @Test
    fun mostRecentlyResumedPackageWinsDuringMultiWindow() {
        val tracker = ForegroundActivityTracker()
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED, "app.a", "MainActivity", 10)
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED, "app.b", "HomeActivity", 20)

        assertEquals("app.b", tracker.currentPackage())
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_PAUSED, "app.b", "HomeActivity", 30)
        assertEquals("app.a", tracker.currentPackage())
    }

    @Test
    fun legacyBackgroundClearsOnlyItsOwnPackage() {
        val tracker = ForegroundActivityTracker()
        tracker.onEvent(ForegroundActivityTracker.EventKind.LEGACY_FOREGROUND, "app.a", null, 10)
        tracker.onEvent(ForegroundActivityTracker.EventKind.LEGACY_FOREGROUND, "app.b", null, 20)
        tracker.onEvent(ForegroundActivityTracker.EventKind.LEGACY_BACKGROUND, "app.b", null, 30)

        assertEquals("app.a", tracker.currentPackage())
    }

    @Test
    fun stoppingLastResumedActivityClearsForeground() {
        val tracker = ForegroundActivityTracker()
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED, "app.a", "MainActivity", 10)
        tracker.onEvent(ForegroundActivityTracker.EventKind.ACTIVITY_STOPPED, "app.a", "MainActivity", 20)

        assertNull(tracker.currentPackage())
    }
    @Test
    fun distinctInstancesOfSameActivityClassRemainTrackedIndependently() {
        val tracker = ForegroundActivityTracker()
        tracker.onEvent(
            ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED,
            "app.a", "PlayerActivity", 10, instanceId = 101
        )
        tracker.onEvent(
            ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED,
            "app.a", "PlayerActivity", 20, instanceId = 202
        )
        tracker.onEvent(
            ForegroundActivityTracker.EventKind.ACTIVITY_PAUSED,
            "app.a", "PlayerActivity", 30, instanceId = 202
        )

        assertEquals("app.a", tracker.currentPackage())
        tracker.onEvent(
            ForegroundActivityTracker.EventKind.ACTIVITY_PAUSED,
            "app.a", "PlayerActivity", 40, instanceId = 101
        )
        assertNull(tracker.currentPackage())
    }

    @Test
    fun detailedActivityResumeReplacesPackageOnlyLegacyMarker() {
        val tracker = ForegroundActivityTracker()
        tracker.onEvent(ForegroundActivityTracker.EventKind.LEGACY_FOREGROUND, "app.a", null, 10)
        tracker.onEvent(
            ForegroundActivityTracker.EventKind.ACTIVITY_RESUMED,
            "app.a", "MainActivity", 20, instanceId = 7
        )
        tracker.onEvent(
            ForegroundActivityTracker.EventKind.ACTIVITY_PAUSED,
            "app.a", "MainActivity", 30, instanceId = 7
        )

        assertNull(tracker.currentPackage())
    }

}