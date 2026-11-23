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
}
