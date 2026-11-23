package com.reelfocus.app.utils

/**
 * Tracks resumed activities rather than treating every ACTIVITY_PAUSED event
 * as proof that the whole package left the foreground. Android can pause one
 * activity while another activity from the same package remains resumed, and
 * multi-window can have more than one resumed package.
 *
 * The most recently resumed package is the best UsageStats approximation of
 * the active window. Accessibility remains the more precise reel-level source.
 */
internal class ForegroundActivityTracker {
    private data class ActiveActivity(val packageName: String, val resumedAt: Long)

    enum class EventKind {
        LEGACY_FOREGROUND, LEGACY_BACKGROUND, ACTIVITY_RESUMED, ACTIVITY_PAUSED, ACTIVITY_STOPPED
    }

    private val activeActivities = linkedMapOf<String, ActiveActivity>()

    fun onEvent(kind: EventKind, packageName: String, className: String?, timestamp: Long) {
        val activityKey = packageName + "/" + (className?.takeIf { it.isNotBlank() } ?: "<package>")
        when (kind) {
            EventKind.LEGACY_FOREGROUND, EventKind.ACTIVITY_RESUMED -> {
                activeActivities[activityKey] = ActiveActivity(packageName, timestamp)
            }
            EventKind.LEGACY_BACKGROUND -> {
                activeActivities.entries.removeAll { it.value.packageName == packageName }
            }
            EventKind.ACTIVITY_PAUSED, EventKind.ACTIVITY_STOPPED -> activeActivities.remove(activityKey)
        }
    }

    fun currentPackage(): String? =
        activeActivities.values.maxByOrNull { it.resumedAt }?.packageName

    fun clear() { activeActivities.clear() }
}