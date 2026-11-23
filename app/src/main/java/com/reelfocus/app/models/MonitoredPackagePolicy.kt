package com.reelfocus.app.models

/**
 * Keeps foreground/reel detection aligned with the user's current app selection.
 * Disabled entries are intentionally excluded; package names are treated as
 * opaque Android identifiers rather than inferred from display labels.
 */
object MonitoredPackagePolicy {
    fun enabledPackages(apps: List<MonitoredApp>): Set<String> =
        apps.asSequence()
            .filter { it.isEnabled }
            .map { it.packageName.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun contains(enabledPackages: Set<String>, packageName: String?): Boolean =
        packageName != null && packageName in enabledPackages
}
