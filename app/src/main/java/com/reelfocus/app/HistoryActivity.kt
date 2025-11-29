package com.reelfocus.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.reelfocus.app.models.DailyStats
import com.reelfocus.app.models.SessionHistory
import com.reelfocus.app.utils.HistoryManager
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    
    private lateinit var historyManager: HistoryManager
    private lateinit var weeklyStatsContainer: LinearLayout
    private lateinit var recentSessionsRecycler: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        supportActionBar?.title = "Usage History"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        historyManager = HistoryManager(this)
        
        weeklyStatsContainer = findViewById(R.id.weekly_stats_container)
        recentSessionsRecycler = findViewById(R.id.recent_sessions_recycler)
        
        loadWeeklyStats()
        loadRecentSessions()
    }
    
    private fun loadWeeklyStats() {
        val weeklyStats = historyManager.getWeeklyStats(7)
        weeklyStatsContainer.removeAllViews()
        
        weeklyStats.forEach { dayStats ->
            val dayView = layoutInflater.inflate(R.layout.item_daily_stats, weeklyStatsContainer, false)
            
            dayView.findViewById<TextView>(R.id.date_text).text = formatDate(dayStats.date)
            dayView.findViewById<TextView>(R.id.sessions_text).text = 
                "${dayStats.completedSessions}/${dayStats.totalSessions} sessions"
            dayView.findViewById<TextView>(R.id.time_text).text = 
                formatDuration(dayStats.totalTimeSeconds)
            dayView.findViewById<TextView>(R.id.extensions_text).text = 
                "${dayStats.totalExtensions} extensions"
            
            weeklyStatsContainer.addView(dayView)
        }
    }
    
    private fun loadRecentSessions() {
        val history = historyManager.getHistory().take(20)  // Last 20 sessions
        
        recentSessionsRecycler.layoutManager = LinearLayoutManager(this)
        recentSessionsRecycler.adapter = SessionHistoryAdapter(history)
    }
    
    private fun formatDate(dateString: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val outputFormat = SimpleDateFormat("MMM dd", Locale.US)
        return try {
            val date = inputFormat.parse(dateString)
            outputFormat.format(date ?: Date())
        } catch (e: java.text.ParseException) {
            android.util.Log.w("HistoryActivity", "Date parsing error: $dateString", e)
            dateString
        }
    }
    
    private fun formatDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    // RecyclerView Adapter
    inner class SessionHistoryAdapter(private val sessions: List<SessionHistory>) : 
        RecyclerView.Adapter<SessionHistoryAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appName: TextView = view.findViewById(R.id.session_app_name)
            val time: TextView = view.findViewById(R.id.session_time)
            val duration: TextView = view.findViewById(R.id.session_duration)
            val status: TextView = view.findViewById(R.id.session_status)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = layoutInflater.inflate(R.layout.item_session_history, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            
            holder.appName.text = session.appName
            holder.time.text = formatTimestamp(session.startTime)
            holder.duration.text = formatDuration(session.durationSeconds)
            
            holder.status.text = if (session.completed) {
                "✓ Completed" + if (session.extensionsUsed > 0) " (+${session.extensionsUsed})" else ""
            } else {
                "Stopped early"
            }
            holder.status.setTextColor(
                if (session.completed) 
                    getColor(android.R.color.holo_green_dark)
                else 
                    getColor(android.R.color.holo_orange_dark)
            )
        }
        
        override fun getItemCount() = sessions.size
        
        private fun formatTimestamp(timestamp: Long): String {
            val format = SimpleDateFormat("MMM dd, h:mm a", Locale.US)
            return format.format(Date(timestamp))
        }
    }
}
