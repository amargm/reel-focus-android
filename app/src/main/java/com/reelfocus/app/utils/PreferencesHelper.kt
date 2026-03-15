package com.reelfocus.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.reelfocus.app.models.*
import org.json.JSONArray
import org.json.JSONObject

class PreferencesHelper(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("reel_focus_prefs", Context.MODE_PRIVATE)
    
    init {
        // Initialize default configuration if first launch
        if (!prefs.contains("initialized")) {
            initializeDefaults()
        }
    }
    
    private fun initializeDefaults() {
        // Only add each default app if it is actually installed on this device.
        val pm = context.packageManager
        val candidates = listOf(
            MonitoredApp("com.zhiliaoapp.musically", "TikTok", true),
            MonitoredApp("com.instagram.android",   "Instagram", true),
            MonitoredApp("com.google.android.youtube", "YouTube", true)
        )
        val installedDefaults = candidates.filter { app ->
            try {
                pm.getApplicationInfo(app.packageName, 0)
                true
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                false
            }
        }

        val defaultConfig = AppConfig(
            monitoredApps = installedDefaults,
            defaultLimitValue = 20
        )
        saveConfig(defaultConfig)

        prefs.edit().putBoolean("initialized", true).apply()
    }
    
    // Save complete configuration
    fun saveConfig(config: AppConfig) {
        prefs.edit().apply {
            putInt("max_sessions", config.maxSessionsDaily)
            putInt("reset_gap_minutes", config.sessionResetGapMinutes)
            putInt("default_limit_value", config.defaultLimitValue)
            putString("overlay_position", config.overlayPosition.name)
            putString("overlay_text_size", config.overlayTextSize.name)
            putString("overlay_style", config.overlayStyle.name)
            putString("monitored_apps", monitoredAppsToJson(config.monitoredApps))
            putBoolean("per_app_limit_enabled", config.isPerAppLimitEnabled)
            putBoolean("haptic_enabled", config.hapticEnabled)
            apply()
        }
    }
    
    // Load complete configuration
    fun loadConfig(): AppConfig {
        return AppConfig(
            maxSessionsDaily = prefs.getInt("max_sessions", 5),
            sessionResetGapMinutes = prefs.getInt("reset_gap_minutes", 10),  // BUG-016 FIX: default matches AppConfig
            defaultLimitValue = prefs.getInt("default_limit_value", 20),
            overlayPosition = OverlayPosition.valueOf(prefs.getString("overlay_position", "TOP_RIGHT") ?: "TOP_RIGHT"),
            overlayTextSize = TextSize.valueOf(prefs.getString("overlay_text_size", "MEDIUM") ?: "MEDIUM"),
            overlayStyle = com.reelfocus.app.models.OverlayStyle.valueOf(prefs.getString("overlay_style", "TEXT") ?: "TEXT"),
            monitoredApps = jsonToMonitoredApps(prefs.getString("monitored_apps", "[]") ?: "[]"),
            isPerAppLimitEnabled = prefs.getBoolean("per_app_limit_enabled", false),
            hapticEnabled = prefs.getBoolean("haptic_enabled", false)
        )
    }
    
    // Session state persistence
    fun saveSessionState(state: SessionState) {
        prefs.edit().apply {
            putInt("current_session", state.currentSession)
            putInt("seconds_elapsed", state.secondsElapsed)
            putBoolean("is_active", state.isActive)
            putLong("session_start_time", state.sessionStartTime)
            putLong("last_activity_time", state.lastActivityTime)
            putBoolean("extension_used", state.extensionUsed)
            putBoolean("is_in_extension", state.isInExtension)
            putBoolean("is_on_break", state.isOnBreak)
            putLong("break_start_time", state.breakStartTime)
            putString("active_app_package", state.activeAppPackage)
            apply()
        }
    }
    
    fun loadSessionState(config: AppConfig): SessionState {
        return SessionState(
            currentSession = prefs.getInt("current_session", 1),
            secondsElapsed = prefs.getInt("seconds_elapsed", 0),
            isActive = prefs.getBoolean("is_active", false),
            maxSessions = config.maxSessionsDaily,
            limitValue = config.defaultLimitValue,
            sessionStartTime = prefs.getLong("session_start_time", 0),
            lastActivityTime = prefs.getLong("last_activity_time", 0),
            extensionUsed = prefs.getBoolean("extension_used", false),
            isInExtension = prefs.getBoolean("is_in_extension", false),
            isOnBreak = prefs.getBoolean("is_on_break", false),
            breakStartTime = prefs.getLong("break_start_time", 0),
            activeAppPackage = prefs.getString("active_app_package", null)
        )
    }
    
    fun resetDailySession() {
        // BUG-002 FIX: reset all timer-related state so a new day starts completely clean
        prefs.edit().apply {
            putInt("current_session", 1)
            putInt("extension_count", 0)
            putLong("last_reset_date", System.currentTimeMillis())
            // Clear timer state to prevent yesterday's elapsed time bleeding into today
            putInt("seconds_elapsed", 0)
            putBoolean("is_active", false)
            putBoolean("session_completed", false)
            putLong("session_start_time", 0)
            putLong("last_activity_time", 0)
            putBoolean("extension_used", false)
            putBoolean("is_in_extension", false)
            putBoolean("is_on_break", false)
            putLong("break_start_time", 0)
            apply()
        }
    }
    
    /** Returns true when the daily state was reset (new calendar day detected). */
    fun checkAndResetIfNewDay(): Boolean {
        val lastResetDate = prefs.getLong("last_reset_date", 0)
        if (lastResetDate == 0L) {
            // First run — set the baseline without resetting session state
            prefs.edit().putLong("last_reset_date", System.currentTimeMillis()).apply()
            return false
        }
        // BUG-F04 FIX: compare local calendar dates so the reset happens at local
        // midnight, not at UTC midnight (epoch-day division gave wrong times in
        // non-UTC timezones).
        val todayCal = java.util.Calendar.getInstance()
        val lastCal  = java.util.Calendar.getInstance().apply { timeInMillis = lastResetDate }
        val todayKey = "${todayCal.get(java.util.Calendar.YEAR)}-${todayCal.get(java.util.Calendar.DAY_OF_YEAR)}"
        val lastKey  = "${lastCal.get(java.util.Calendar.YEAR)}-${lastCal.get(java.util.Calendar.DAY_OF_YEAR)}"
        if (todayKey != lastKey) {
            // Before resetting, evaluate if yesterday was a "good day" (not daily-blocked)
            val sessAtReset = prefs.getInt("current_session", 1)
            val maxSessAtReset = prefs.getInt("max_sessions", 5)
            updateStreak(sessAtReset <= maxSessAtReset)
            resetDailySession()
            return true
        }
        return false
    }

    private fun updateStreak(wasGoodDay: Boolean) {
        val newStreak = if (wasGoodDay) prefs.getInt("streak_days", 0) + 1 else 0
        prefs.edit().putInt("streak_days", newStreak).apply()
    }

    fun getStreakDays(): Int = prefs.getInt("streak_days", 0)
    
    private fun monitoredAppsToJson(apps: List<MonitoredApp>): String {
        val jsonArray = JSONArray()
        apps.forEach { app ->
            val jsonObject = JSONObject().apply {
                put("packageName", app.packageName)
                put("appName", app.appName)
                put("isEnabled", app.isEnabled)
                app.customLimitValue?.let { put("customLimitValue", it) }
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
    
    private fun jsonToMonitoredApps(json: String): List<MonitoredApp> {
        val apps = mutableListOf<MonitoredApp>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                apps.add(
                    MonitoredApp(
                        packageName = jsonObject.getString("packageName"),
                        appName = jsonObject.getString("appName"),
                        isEnabled = jsonObject.optBoolean("isEnabled", true),
                        customLimitValue = if (jsonObject.has("customLimitValue")) {
                            jsonObject.getInt("customLimitValue")
                        } else null
                    )
                )
            }
        } catch (e: org.json.JSONException) {
            android.util.Log.e("PreferencesHelper", "JSON parsing error loading apps", e)
        } catch (e: IllegalStateException) {
            android.util.Log.e("PreferencesHelper", "Illegal state parsing apps", e)
        }
        return apps
    }
}
