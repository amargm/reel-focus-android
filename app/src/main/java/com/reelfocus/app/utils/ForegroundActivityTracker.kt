package com.reelfocus.app.utils

/**
 * Tracks resumed activity instances rather than treating every ACTIVITY_PAUSED
 * event as proof that a package left the foreground. UsageStats instance IDs
 * distinguish two instances of the same Activity class on Android 10+.
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

    fun onEvent(
        kind: EventKind,
        packageName: String,
        className: String?,
        timestamp: Long,
        instanceId: Int? = null
    ) {
        val classKey = packageName + "/" + (className?.takeIf { it.isNotBlank() } ?: "<package>")
        val activityKey = instanceId?.takeIf { it >= 0 }?.let { "$packageName/#$it" } ?: classKey
        when (kind) {
            EventKind.LEGACY_FOREGROUND -> {
                activeActivities[classKey] = ActiveActivity(packageName, timestamp)
            }
            EventKind.ACTIVITY_RESUMED -> {
                // A detailed activity event supersedes its package/class
                // fallback and any legacy package-only foreground marker.
                activeActivities.remove(classKey)
                activeActivities.remove("$packageName/<package>")
                activeActivities[activityKey] = ActiveActivity(packageName, timestamp)
            }
            EventKind.LEGACY_BACKGROUND -> {
                activeActivities.entries.removeAll { it.value.packageName == packageName }
            }
            EventKind.ACTIVITY_PAUSED, EventKind.ACTIVITY_STOPPED -> {
                activeActivities.remove(activityKey)
                if (instanceId == null || instanceId < 0) activeActivities.remove(classKey)
            }
        }
    }

    fun currentPackage(): String? =
        activeActivities.values.maxByOrNull { it.resumedAt }?.packageName

    fun clear() { activeActivities.clear() }
}
