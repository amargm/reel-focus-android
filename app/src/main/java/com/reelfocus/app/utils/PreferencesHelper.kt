package com.reelfocus.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.reelfocus.app.models.*
import org.json.JSONArray
import org.json.JSONObject

class PreferencesHelper(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("reel_focus_prefs", Context.MODE_PRIVATE)
    
    init {
        // Initialize default configuration if first launch
        if (!prefs.contains("initialized")) {
            initializeDefaults()
        }
    }
    
    private fun initializeDefaults() {
        val defaultApps = listOf(
            MonitoredApp("com.zhiliaoapp.musically", "TikTok", true),
            MonitoredApp("com.instagram.android", "Instagram", true),
            MonitoredApp("com.google.android.youtube", "YouTube Shorts", true)
        )
        
        val defaultConfig = AppConfig(
            monitoredApps = defaultApps,
            defaultLimitValue = 1  // 1 minute for quick testing
        )
        saveConfig(defaultConfig)
        
        prefs.edit().putBoolean("initialized", true).apply()
    }
    
    // Save complete configuration
    fun saveConfig(config: AppConfig) {
        prefs.edit().apply {
            putInt("max_sessions", config.maxSessionsDaily)
            putInt("reset_gap_minutes", config.sessionResetGapMinutes)
            putString("default_limit_type", config.defaultLimitType.name)
            putInt("default_limit_value", config.defaultLimitValue)
            putString("overlay_position", config.overlayPosition.name)
            putString("overlay_text_size", config.overlayTextSize.name)
            putString("monitored_apps", monitoredAppsToJson(config.monitoredApps))
            apply()
        }
    }
    
    // Load complete configuration
    fun loadConfig(): AppConfig {
        return AppConfig(
            maxSessionsDaily = prefs.getInt("max_sessions", 5),
            sessionResetGapMinutes = prefs.getInt("reset_gap_minutes", 30),
            defaultLimitType = LimitType.valueOf(prefs.getString("default_limit_type", "TIME") ?: "TIME"),
            defaultLimitValue = prefs.getInt("default_limit_value", 20),
            overlayPosition = OverlayPosition.valueOf(prefs.getString("overlay_position", "TOP_RIGHT") ?: "TOP_RIGHT"),
            overlayTextSize = TextSize.valueOf(prefs.getString("overlay_text_size", "MEDIUM") ?: "MEDIUM"),
            monitoredApps = jsonToMonitoredApps(prefs.getString("monitored_apps", "[]") ?: "[]")
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
            putInt("extension_count", state.extensionCount)
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
            limitType = config.defaultLimitType,
            limitValue = config.defaultLimitValue,
            sessionStartTime = prefs.getLong("session_start_time", 0),
            lastActivityTime = prefs.getLong("last_activity_time", 0),
            extensionCount = prefs.getInt("extension_count", 0),
            activeAppPackage = prefs.getString("active_app_package", null)
        )
    }
    
    fun resetDailySession() {
        prefs.edit().apply {
            putInt("current_session", 1)
            putInt("extension_count", 0)
            putLong("last_reset_date", System.currentTimeMillis())
            apply()
        }
    }
    
    fun checkAndResetIfNewDay() {
        val lastResetDate = prefs.getLong("last_reset_date", 0)
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val lastResetDay = lastResetDate / (24 * 60 * 60 * 1000)
        
        if (today > lastResetDay) {
            resetDailySession()
        }
    }
    
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return apps
    }
}
