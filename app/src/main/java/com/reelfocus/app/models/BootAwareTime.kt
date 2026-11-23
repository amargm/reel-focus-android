package com.reelfocus.app.models

/**
 * A clock sample pairs wall time (for display/history) with monotonic time
 * (for measuring durations) and Android's boot counter (to detect reboot).
 */
data class BootClockStamp(
    val wallTimeMs: Long,
    val elapsedRealtimeMs: Long,
    val bootCount: Int
)

object BootAwareTime {
    fun elapsedDurationMs(start: BootClockStamp, now: BootClockStamp): Long {
        if (start.bootCount != now.bootCount) return 0L
        return (now.elapsedRealtimeMs - start.elapsedRealtimeMs).coerceAtLeast(0L)
    }


    fun shouldStartFreshSession(
        lastActivityBootCount: Int,
        currentBootCount: Int,
        lastActivityElapsedRealtimeMs: Long,
        nowElapsedRealtimeMs: Long,
        maximumGapMs: Long = 60_000L
    ): Boolean {
        if (lastActivityBootCount != currentBootCount || lastActivityElapsedRealtimeMs <= 0L) {
            return true
        }
        val gap = (nowElapsedRealtimeMs - lastActivityElapsedRealtimeMs).coerceAtLeast(0L)
        return gap > maximumGapMs
    }

    /**
     * Across a reboot, use the persisted wall deadline but clamp it to the
     * original duration. Clock changes therefore cannot extend the timer or
     * produce a negative/overlong remaining duration.
     */
    fun remainingDurationMs(
        deadlineWallTimeMs: Long,
        nowWallTimeMs: Long,
        originalDurationMs: Long,
        deadlineElapsedRealtimeMs: Long = 0L,
        deadlineBootCount: Int = -1,
        nowElapsedRealtimeMs: Long = 0L,
        currentBootCount: Int = -2
    ): Long {
        val maximum = originalDurationMs.coerceAtLeast(0L)
        val remaining = if (
            deadlineBootCount == currentBootCount &&
            deadlineElapsedRealtimeMs > 0L &&
            nowElapsedRealtimeMs > 0L
        ) {
            deadlineElapsedRealtimeMs - nowElapsedRealtimeMs
        } else {
            deadlineWallTimeMs - nowWallTimeMs
        }
        return remaining.coerceIn(0L, maximum)
    }
}
