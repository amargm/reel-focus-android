package com.reelfocus.app.models

import org.junit.Assert.assertEquals
import org.junit.Test

class BootAwareTimeTest {
    @Test fun monotonicElapsedTimeIgnoresWallClockChanges() {
        val start = BootClockStamp(wallTimeMs = 10_000L, elapsedRealtimeMs = 5_000L, bootCount = 7)
        val now = BootClockStamp(wallTimeMs = 2_000L, elapsedRealtimeMs = 8_500L, bootCount = 7)
        assertEquals(3_500L, BootAwareTime.elapsedDurationMs(start, now))
    }

    @Test fun monotonicDurationDoesNotCrossReboot() {
        val start = BootClockStamp(wallTimeMs = 10_000L, elapsedRealtimeMs = 5_000L, bootCount = 7)
        val afterReboot = BootClockStamp(wallTimeMs = 20_000L, elapsedRealtimeMs = 100L, bootCount = 8)
        assertEquals(0L, BootAwareTime.elapsedDurationMs(start, afterReboot))
    }

    @Test fun breakDeadlineIsClampedToOriginalDuration() {
        assertEquals(
            60_000L,
            BootAwareTime.remainingDurationMs(
                deadlineWallTimeMs = 900_000L,
                nowWallTimeMs = 840_000L,
                originalDurationMs = 600_000L
            )
        )
        assertEquals(
            600_000L,
            BootAwareTime.remainingDurationMs(
                deadlineWallTimeMs = 9_000_000L,
                nowWallTimeMs = 0L,
                originalDurationMs = 600_000L
            )
        )
        assertEquals(
            0L,
            BootAwareTime.remainingDurationMs(
                deadlineWallTimeMs = 1_000L,
                nowWallTimeMs = 2_000L,
                originalDurationMs = 600_000L
            )
        )
    }
    @Test fun sessionGapKeepsSameSessionThroughSixtySeconds() {
        assertEquals(
            false,
            BootAwareTime.shouldStartFreshSession(
                lastActivityBootCount = 4,
                currentBootCount = 4,
                lastActivityElapsedRealtimeMs = 10_000L,
                nowElapsedRealtimeMs = 70_000L
            )
        )
    }

    @Test fun sessionGapOverSixtySecondsStartsFresh() {
        assertEquals(
            true,
            BootAwareTime.shouldStartFreshSession(
                lastActivityBootCount = 4,
                currentBootCount = 4,
                lastActivityElapsedRealtimeMs = 10_000L,
                nowElapsedRealtimeMs = 70_001L
            )
        )
    }

    @Test fun rebootOrLegacyMissingMonotonicTimestampStartsFresh() {
        assertEquals(
            true,
            BootAwareTime.shouldStartFreshSession(
                lastActivityBootCount = 4,
                currentBootCount = 5,
                lastActivityElapsedRealtimeMs = 10_000L,
                nowElapsedRealtimeMs = 100L
            )
        )
        assertEquals(
            true,
            BootAwareTime.shouldStartFreshSession(
                lastActivityBootCount = -1,
                currentBootCount = 5,
                lastActivityElapsedRealtimeMs = 0L,
                nowElapsedRealtimeMs = 100L
            )
        )
    }

    @Test fun breakUsesMonotonicDeadlineWhenBootHasNotChanged() {
        assertEquals(
            120_000L,
            BootAwareTime.remainingDurationMs(
                deadlineWallTimeMs = 1_000_000L,
                nowWallTimeMs = 900_000L,
                originalDurationMs = 600_000L,
                deadlineElapsedRealtimeMs = 500_000L,
                deadlineBootCount = 9,
                nowElapsedRealtimeMs = 380_000L,
                currentBootCount = 9
            )
        )
    }

    @Test fun breakFallsBackToBoundedWallDeadlineAfterReboot() {
        assertEquals(
            60_000L,
            BootAwareTime.remainingDurationMs(
                deadlineWallTimeMs = 900_000L,
                nowWallTimeMs = 840_000L,
                originalDurationMs = 600_000L,
                deadlineElapsedRealtimeMs = 500_000L,
                deadlineBootCount = 9,
                nowElapsedRealtimeMs = 100L,
                currentBootCount = 10
            )
        )
    }

}
