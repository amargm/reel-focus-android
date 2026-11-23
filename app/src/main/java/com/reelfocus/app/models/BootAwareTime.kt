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

    /**
     * Across a reboot, use the persisted wall deadline but clamp it to the
     * original duration. Clock changes therefore cannot extend the timer or
     * produce a negative/overlong remaining duration.
     */
    fun remainingDurationMs(
        deadlineWallTimeMs: Long,
        nowWallTimeMs: Long,
        originalDurationMs: Long
    ): Long = (deadlineWallTimeMs - nowWallTimeMs)
        .coerceIn(0L, originalDurationMs.coerceAtLeast(0L))
}
