package com.reelfocus.app.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.reelfocus.app.models.AppDayStats
import com.reelfocus.app.models.DailyStats
import com.reelfocus.app.models.SessionHistory
import java.text.SimpleDateFormat
import java.util.*

class HistoryManager(context: Context) {
    
    private val prefs = context.getSharedPreferences("session_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    
    companion object {
        private const val KEY_HISTORY = "history_list"
        private const val MAX_HISTORY_DAYS = 90  // Keep 3 months of history
    }
    
    /**
     * Save a completed session to history
     */
    fun recordSession(session: SessionHistory) {
        val history = getHistory().toMutableList()
        history.add(0, session)  // Add to beginning
        
        // Clean old history (keep only last 90 days)
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -MAX_HISTORY_DAYS)
        }.time
        val filtered = history.filter { 
            parseDate(it.date)?.after(cutoffDate) ?: true
        }
        
        saveHistory(filtered)
    }
    
    /**
     * Get all session history
     */
    fun getHistory(): List<SessionHistory> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<SessionHistory>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get statistics for a specific date
     */
    fun getDailyStats(date: String): DailyStats {
        val sessions = getHistory().filter { it.date == date }
        
        val appBreakdown = sessions.groupBy { it.appPackage }
            .mapValues { (_, appSessions) ->
                AppDayStats(
                    appName = appSessions.first().appName,
                    sessions = appSessions.size,
                    totalTimeSeconds = appSessions.sumOf { it.durationSeconds },
                    extensions = appSessions.sumOf { it.extensionsUsed }
                )
            }
        
        return DailyStats(
            date = date,
            totalSessions = sessions.size,
            completedSessions = sessions.count { it.completed },
            totalTimeSeconds = sessions.sumOf { it.durationSeconds },
            totalExtensions = sessions.sumOf { it.extensionsUsed },
            appBreakdown = appBreakdown
        )
    }
    
    /**
     * Get statistics for the last N days
     */
    fun getWeeklyStats(days: Int = 7): List<DailyStats> {
        val calendar = Calendar.getInstance()
        val dates = mutableListOf<String>()
        
        repeat(days) {
            dates.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        
        return dates.map { getDailyStats(it) }
    }
    
    /**
     * Get total stats for an app (all time)
     */
    fun getAppTotalStats(packageName: String): AppDayStats? {
        val sessions = getHistory().filter { it.appPackage == packageName }
        if (sessions.isEmpty()) return null
        
        return AppDayStats(
            appName = sessions.first().appName,
            sessions = sessions.size,
            totalTimeSeconds = sessions.sumOf { it.durationSeconds },
            extensions = sessions.sumOf { it.extensionsUsed }
        )
    }
    
    /**
     * Clear all history
     */
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }
    
    private fun saveHistory(history: List<SessionHistory>) {
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }
    
    private fun parseDate(dateString: String): Date? {
        return try {
            dateFormat.parse(dateString)
        } catch (e: Exception) {
            null
        }
    }
    
    fun getTodayDate(): String = dateFormat.format(Date())
}
