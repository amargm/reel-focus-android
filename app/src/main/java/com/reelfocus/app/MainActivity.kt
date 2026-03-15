package com.reelfocus.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.content.res.ColorStateList
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.reelfocus.app.utils.AppUsageMonitor
import com.reelfocus.app.utils.PreferencesHelper

class MainActivity : AppCompatActivity() {

    private var isServiceRunning = false
    private lateinit var startButton: Button
    private lateinit var usageStatsContainer: android.view.View
    private lateinit var overlayContainer: android.view.View
    private lateinit var usageStatsStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var statusMessage: TextView
    private lateinit var selectedAppsSummary: TextView
    private lateinit var selectAppsCard: MaterialCardView
    private lateinit var settingsCard: MaterialCardView
    private lateinit var appUsageMonitor: AppUsageMonitor
    // Gamified UI views
    private lateinit var permissionsCard: MaterialCardView
    private lateinit var permBadge: TextView
    private lateinit var appsActionIcon: ImageView
    private lateinit var dot1: android.view.View
    private lateinit var dot2: android.view.View
    private lateinit var dot3: android.view.View
    private lateinit var dot4: android.view.View
    private lateinit var line12: android.view.View
    private lateinit var line23: android.view.View
    private lateinit var line34: android.view.View
    private lateinit var arrow1: ImageView
    private lateinit var arrow2: ImageView
    private lateinit var arrow3: ImageView

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
        private const val REQUEST_USAGE_STATS_PERMISSION = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appUsageMonitor = AppUsageMonitor(this)
        
        // Initialize views
        startButton = findViewById(R.id.start_button)
        usageStatsContainer = findViewById(R.id.usage_stats_container)
        overlayContainer = findViewById(R.id.overlay_permission_container)
        usageStatsStatus = findViewById(R.id.usage_stats_status)
        overlayStatus = findViewById(R.id.overlay_status)
        statusMessage = findViewById(R.id.status_message)
        selectedAppsSummary = findViewById(R.id.selected_apps_summary)
        selectAppsCard = findViewById(R.id.select_apps_card)
        settingsCard = findViewById(R.id.settings_card)
        // Gamified UI
        permissionsCard = findViewById(R.id.permissions_card)
        permBadge = findViewById(R.id.perm_badge)
        appsActionIcon = findViewById(R.id.apps_action_icon)
        dot1 = findViewById(R.id.dot_1)
        dot2 = findViewById(R.id.dot_2)
        dot3 = findViewById(R.id.dot_3)
        dot4 = findViewById(R.id.dot_4)
        line12 = findViewById(R.id.line_1_2)
        line23 = findViewById(R.id.line_2_3)
        line34 = findViewById(R.id.line_3_4)
        arrow1 = findViewById(R.id.arrow_1)
        arrow2 = findViewById(R.id.arrow_2)
        arrow3 = findViewById(R.id.arrow_3)

        updateUI()

        // Permission container click handlers
        usageStatsContainer.setOnClickListener {
            requestUsageStatsPermission()
        }
        
        overlayContainer.setOnClickListener {
            requestOverlayPermission()
        }

        // Step 2: Tap card to go to AppSelectionActivity
        selectAppsCard.setOnClickListener {
            startActivity(Intent(this, AppSelectionActivity::class.java))
        }

        // Step 3: Tap card to open Settings
        settingsCard.setOnClickListener {
            android.util.Log.d("MainActivity", "Opening Settings")
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // Start button
        startButton.setOnClickListener {
            if (canDrawOverlays() && hasUsageStatsPermission()) {
                toggleService()
            } else {
                val missing = mutableListOf<String>()
                if (!canDrawOverlays()) missing.add("Overlay")
                if (!hasUsageStatsPermission()) missing.add("Usage Stats")
                Toast.makeText(
                    this,
                    "Missing permissions: ${missing.joinToString(", ")}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        // Use the improved AppUsageMonitor's built-in check
        return appUsageMonitor.hasUsageStatsPermission()
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
        } else {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivityForResult(intent, REQUEST_USAGE_STATS_PERMISSION)
        } else {
            Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Delay to ensure system registers permission changes
        android.os.Handler(mainLooper).postDelayed({
            when (requestCode) {
                REQUEST_OVERLAY_PERMISSION -> {
                    updateUI()
                    if (canDrawOverlays()) {
                        Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Overlay permission is required for this app to work",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                REQUEST_USAGE_STATS_PERMISSION -> {
                    updateUI()
                    if (hasUsageStatsPermission()) {
                        Toast.makeText(this, "Usage stats permission granted!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            this,
                            "Usage stats permission is required to monitor apps",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }, 200) // Increased delay for better permission state sync
    }

    private fun toggleService() {
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "Please grant usage stats permission first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Toggle based on current state
        if (isServiceRunning) {
            stopOverlayService()
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        // Set state immediately
        isServiceRunning = true
        updateUI()
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        }
        startService(intent)
        
        // Set state immediately
        isServiceRunning = false
        updateUI()
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val hasOverlay = canDrawOverlays()
        val hasUsageStats = hasUsageStatsPermission()
        val config = PreferencesHelper(this).loadConfig()
        val hasMonitoredApps = config.monitoredApps.any { it.isEnabled }
        val permsDone = hasOverlay && hasUsageStats
        val canStart = permsDone && hasMonitoredApps

        val colorPrimary   = getColor(R.color.md_theme_primary)
        val colorSuccess   = getColor(R.color.md_theme_success)
        val colorOutline   = getColor(R.color.md_theme_outline)
        val colorTertiary  = getColor(R.color.md_theme_tertiary)
        val colorOnSurfaceVariant = getColor(R.color.md_theme_on_surface_variant)

        // ── Permission row status ────────────────────────────────
        usageStatsStatus.text = if (hasUsageStats) getString(R.string.granted) else getString(R.string.grant)
        usageStatsStatus.setTextColor(if (hasUsageStats) colorSuccess else colorPrimary)
        overlayStatus.text = if (hasOverlay) getString(R.string.granted) else getString(R.string.grant)
        overlayStatus.setTextColor(if (hasOverlay) colorSuccess else colorPrimary)
        usageStatsContainer.isClickable = true
        overlayContainer.isClickable = true

        // ── Card 1 (Permissions) — always active first ───────────
        permissionsCard.strokeColor = if (permsDone) colorSuccess else colorPrimary
        permBadge.text = if (permsDone) "✓ Done" else "Needed"
        permBadge.setTextColor(if (permsDone) colorSuccess else colorTertiary)

        // ── Card 2 (Apps) — locks until permissions granted ──────
        selectAppsCard.strokeColor = when {
            hasMonitoredApps -> colorSuccess
            permsDone        -> colorPrimary
            else             -> colorOutline
        }
        selectAppsCard.alpha = if (permsDone) 1f else 0.45f
        val enabledApps = config.monitoredApps.filter { it.isEnabled }
        selectedAppsSummary.text = if (enabledApps.isEmpty()) {
            if (permsDone) "Tap to pick apps to track" else "Unlock above first"
        } else {
            "${enabledApps.size} app${if (enabledApps.size == 1) "" else "s"}: " +
                enabledApps.take(3).joinToString(", ") { it.appName } +
                if (enabledApps.size > 3) " +${enabledApps.size - 3} more" else ""
        }
        appsActionIcon.setImageResource(
            if (hasMonitoredApps) R.drawable.ic_check_circle else R.drawable.ic_chevron_right
        )
        appsActionIcon.imageTintList = ColorStateList.valueOf(
            if (hasMonitoredApps) colorSuccess else colorOnSurfaceVariant
        )

        // ── Card 3 (Settings) — locks until apps selected ────────
        settingsCard.strokeColor = if (hasMonitoredApps) colorPrimary else colorOutline
        settingsCard.alpha = if (hasMonitoredApps) 1f else 0.45f

        // ── Progress dots ────────────────────────────────────────
        dot1.backgroundTintList = ColorStateList.valueOf(
            if (permsDone) colorSuccess else colorPrimary
        )
        dot2.backgroundTintList = ColorStateList.valueOf(
            when {
                hasMonitoredApps -> colorSuccess
                permsDone        -> colorPrimary
                else             -> colorOutline
            }
        )
        dot3.backgroundTintList = ColorStateList.valueOf(
            when {
                canStart         -> colorSuccess
                hasMonitoredApps -> colorPrimary
                else             -> colorOutline
            }
        )
        dot4.backgroundTintList = ColorStateList.valueOf(
            if (isServiceRunning) colorSuccess else if (canStart) colorPrimary else colorOutline
        )
        line12.backgroundTintList = ColorStateList.valueOf(if (permsDone) colorPrimary else colorOutline)
        line23.backgroundTintList = ColorStateList.valueOf(if (hasMonitoredApps) colorPrimary else colorOutline)
        line34.backgroundTintList = ColorStateList.valueOf(if (canStart) colorPrimary else colorOutline)

        // ── Connector arrows ─────────────────────────────────────
        arrow1.imageTintList = ColorStateList.valueOf(if (permsDone) colorPrimary else colorOutline)
        arrow2.imageTintList = ColorStateList.valueOf(if (hasMonitoredApps) colorPrimary else colorOutline)
        arrow3.imageTintList = ColorStateList.valueOf(if (canStart) colorPrimary else colorOutline)

        // ── Start button (BUG-F03: always enabled when running) ──
        startButton.isEnabled = canStart || isServiceRunning
        startButton.text = if (isServiceRunning) getString(R.string.stop_monitoring) else getString(R.string.start_monitoring)
        startButton.backgroundTintList = ColorStateList.valueOf(
            if (isServiceRunning) getColor(R.color.md_theme_error) else colorPrimary
        )

        // ── Status hint ──────────────────────────────────────────
        statusMessage.text = when {
            isServiceRunning  -> "✓ Monitoring active"
            !permsDone        -> "Grant the required permissions above"
            !hasMonitoredApps -> "Pick at least one app to monitor"
            else              -> "Ready — tap to begin"
        }
        statusMessage.setTextColor(if (isServiceRunning) colorSuccess else colorOnSurfaceVariant)
    }

    override fun onResume() {
        super.onResume()
        // BUG-004 FIX: sync isServiceRunning from actual system state on every resume
        // so button label and duplicate-start protection are always correct after
        // rotation, back-stack return, or returning from Settings.
        isServiceRunning = isOverlayServiceRunning()
        updateUI()
    }

    @Suppress("DEPRECATION")
    private fun isOverlayServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE).any {
            it.service.className == OverlayService::class.java.name
        }
    }
}
