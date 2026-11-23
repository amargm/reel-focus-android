package com.reelfocus.app.models

/**
 * Prevents a recent accessibility classification from being attributed to a
 * different foreground app or to an app the user no longer monitors.
 */
object AccessibilityDetectionPolicy {
    const val MAX_RESULT_AGE_MS = 10_000L

    fun canUseResult(
        resultPackage: String,
        selectedPackages: List<String>,
        foregroundPackage: String?,
        ageMs: Long
    ): Boolean =
        foregroundPackage == resultPackage &&
            selectedPackages.contains(resultPackage) &&
            ageMs in 0..MAX_RESULT_AGE_MS
}
