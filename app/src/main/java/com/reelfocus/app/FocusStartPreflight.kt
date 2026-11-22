package com.reelfocus.app

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.reelfocus.app.models.AppConfig
import com.reelfocus.app.utils.AppUsageMonitor

enum class StartRequirement {
    UsageAccess,
    OverlayPermission,
    NoAppsSelected
}

data class FocusStartPreflightReport(
    val missingRequirements: Set<StartRequirement>,
    val notificationsAvailable: Boolean,
    val reelDetectionEnabled: Boolean,
    val reelDetectionConnected: Boolean,
    val batteryOptimizationExempt: Boolean,
    val sanitizedConfig: AppConfig? = null,
    val prunedUninstalledApps: Int = 0
) {
    val canStart: Boolean get() = missingRequirements.isEmpty()
    val basicMode: Boolean get() = !reelDetectionConnected

    fun basicModeMessage(): String? =
        if (canStart && basicMode) "Basic mode: time in selected apps only; reel detection is unavailable."
        else null
}

/**
 * Re-evaluates live Android capability and selected-app availability at the
 * moment the user presses Start. Notifications, accessibility and battery
 * exemption are deliberately optional; usage access, overlay access and a
 * valid non-empty selection are required.
 */
internal fun pruneUninstalledApps(
    config: AppConfig,
    isInstalled: (String) -> Boolean
): AppConfig = config.copy(
    monitoredApps = config.monitoredApps.filter { isInstalled(it.packageName) }
)

object FocusStartPreflight {
    fun evaluate(context: Context, config: AppConfig): FocusStartPreflightReport {
        val appContext = context.applicationContext
        val missing = linkedSetOf<StartRequirement>()

        if (!AppUsageMonitor(appContext).hasUsageStatsPermission()) {
            missing += StartRequirement.UsageAccess
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
        ) {
            missing += StartRequirement.OverlayPermission
        }

        val pm = appContext.packageManager
        val sanitizedConfig = pruneUninstalledApps(config) { packageName ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        packageName,
                        android.content.pm.PackageManager.ApplicationInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(packageName, 0)
                }
                true
            } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
        val prunedUninstalledApps = config.monitoredApps.size - sanitizedConfig.monitoredApps.size
        if (sanitizedConfig.monitoredApps.none { it.isEnabled }) {
            missing += StartRequirement.NoAppsSelected
        }

        val notificationsAvailable = runCatching {
            val enabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled()
            val channelEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.getNotificationChannel(OverlayService.CHANNEL_ID)?.importance !=
                    NotificationManager.IMPORTANCE_NONE
            } else true
            enabled && channelEnabled
        }.getOrDefault(false)

        val accessibilityEnabled = runCatching {
            val enabledServices = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            enabledServices.split(':').any {
                it.equals(
                    "${appContext.packageName}/${ReelDetectionAccessibilityService::class.java.name}",
                    ignoreCase = true
                )
            }
        }.getOrDefault(false)

        val connected = accessibilityEnabled && ReelDetectionAccessibilityService.isConnected
        val batteryExempt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                (appContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                    .isIgnoringBatteryOptimizations(appContext.packageName)
            }.getOrDefault(false)
        } else true

        return FocusStartPreflightReport(
            missingRequirements = missing,
            notificationsAvailable = notificationsAvailable,
            reelDetectionEnabled = accessibilityEnabled,
            reelDetectionConnected = connected,
            batteryOptimizationExempt = batteryExempt,
            sanitizedConfig = sanitizedConfig,
            prunedUninstalledApps = prunedUninstalledApps
        )
    }
}
