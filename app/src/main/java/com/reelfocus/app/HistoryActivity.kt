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
import com.reelfocus.app.ui.WeeklyBarChartView
import com.reelfocus.app.utils.HistoryManager
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    
    private lateinit var historyManager: HistoryManager
    private lateinit var weeklyStatsContainer: LinearLayout
    private lateinit var weeklyBarChart: WeeklyBarChartView
    private lateinit var recentSessionsRecycler: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        supportActionBar?.title = "Usage History"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        historyManager = HistoryManager(this)
        
        weeklyStatsContainer = findViewById(R.id.weekly_stats_container)
        weeklyBarChart = findViewById(R.id.weekly_bar_chart)
        recentSessionsRecycler = findViewById(R.id.recent_sessions_recycler)
        
        loadWeeklyStats()
        loadRecentSessions()
    }
    
    private fun loadWeeklyStats() {
        val weeklyStats = historyManager.getWeeklyStats(7)
        weeklyStatsContainer.removeAllViews()

        // Populate bar chart (oldest day first = left to right)
        val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale.US)
        val barEntries = weeklyStats.reversed().map { day ->
            val label = try {
                val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(day.date)
                if (d != null) fmt.format(d).take(3) else day.date.takeLast(2)
            } catch (e: java.text.ParseException) { day.date.takeLast(2) }
            WeeklyBarChartView.BarEntry(label, day.totalTimeSeconds / 60)
        }
        weeklyBarChart.setData(barEntries)
        
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
        val history = historyManager.getHistory().take(50)
        val items = buildHistoryItems(history)
        recentSessionsRecycler.layoutManager = LinearLayoutManager(this)
        recentSessionsRecycler.adapter = SessionHistoryAdapter(items)
    }

    private sealed class HistoryItem {
        data class Header(val label: String) : HistoryItem()
        data class Session(val data: SessionHistory) : HistoryItem()
    }

    private fun buildHistoryItems(sessions: List<SessionHistory>): List<HistoryItem> {
        val items = mutableListOf<HistoryItem>()
        var lastDate = ""
        sessions.forEach { s ->
            if (s.date != lastDate) {
                lastDate = s.date
                items.add(HistoryItem.Header(formatDate(s.date)))
            }
            items.add(HistoryItem.Session(s))
        }
        return items
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
    
    // RecyclerView Adapter — date-grouped with header rows
    inner class SessionHistoryAdapter(private val items: List<HistoryItem>) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_SESSION = 1

        inner class HeaderViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

        inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appName: TextView = view.findViewById(R.id.session_app_name)
            val time: TextView = view.findViewById(R.id.session_time)
            val duration: TextView = view.findViewById(R.id.session_duration)
            val status: TextView = view.findViewById(R.id.session_status)
        }

        override fun getItemViewType(position: Int) =
            if (items[position] is HistoryItem.Header) TYPE_HEADER else TYPE_SESSION

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val dp = resources.displayMetrics.density
                val tv = TextView(parent.context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
                    textSize = 11f
                    isAllCaps = true
                    letterSpacing = 0.08f
                    setTextColor(0xFF00BFA5.toInt())
                }
                HeaderViewHolder(tv)
            } else {
                SessionViewHolder(layoutInflater.inflate(R.layout.item_session_history, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is HistoryItem.Header -> (holder as HeaderViewHolder).tv.text = item.label
                is HistoryItem.Session -> {
                    val h = holder as SessionViewHolder
                    val s = item.data
                    h.appName.text = s.appName
                    h.time.text = java.text.SimpleDateFormat("h:mm a", Locale.US).format(java.util.Date(s.startTime))
                    h.duration.text = formatDuration(s.durationSeconds)
                    h.status.text = if (s.completed)
                        "✓ Completed" + if (s.extensionsUsed > 0) " (+${s.extensionsUsed})" else ""
                    else "Stopped early"
                    h.status.setTextColor(
                        if (s.completed) getColor(android.R.color.holo_green_dark)
                        else getColor(android.R.color.holo_orange_dark)
                    )
                }
            }
        }

        override fun getItemCount() = items.size
    }
}
