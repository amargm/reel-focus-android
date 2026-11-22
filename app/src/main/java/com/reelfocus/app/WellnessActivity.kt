package com.reelfocus.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BG = 0xFF090B09.toInt()
private const val PANEL = 0xFF171B17.toInt()
private const val PANEL_ALT = 0xFF222822.toInt()
private const val INK = 0xFFF3F3EC.toInt()
private const val MUTED = 0xFFA6AEA4.toInt()
private const val LIME = 0xFFC8F36B.toInt()
private const val DARK_INK = 0xFF1A2115.toInt()

private data class WellnessChallenge(
    val id: String,
    val title: String,
    val category: String,
    val level: String,
    val durationDays: Int,
    val description: String,
    val action: String,
    val benefit: String
)

private object ChallengeCatalog {
    val all = listOf(
        WellnessChallenge("screen_5h", "A 5-hour phone day", "Digital balance", "Ease In", 1,
            "Keep total phone use to five hours or less today.", "Set a daily intention, then check your Digital Wellbeing total tonight.",
            "Make room for the parts of your day that happen offline."),
        WellnessChallenge("morning_60", "First hour, fully yours", "Mindful mornings", "Ease In", 3,
            "Spend the first 60 minutes after waking without your phone.", "Leave your phone outside the bedroom and begin with water, light or movement.",
            "Start the day on your terms."),
        WellnessChallenge("phone_meals", "Meals without a screen", "Presence", "Ease In", 7,
            "Keep your phone away during one meal each day.", "Put your phone out of reach before you sit down to eat.",
            "Give food and company your full attention."),
        WellnessChallenge("notification_pause", "Quiet the noise", "Digital calm", "Ease In", 1,
            "Turn off non-essential notifications for the day.", "Keep calls and essential alerts; silence the rest in notification settings.",
            "Reduce interruptions and reclaim your attention."),
        WellnessChallenge("social_uninstall", "A week away from social feeds", "Digital boundaries", "Build Rhythm", 7,
            "Uninstall the social apps you choose and stay off their feeds for seven days.", "Choose the apps that pull you into scrolling, uninstall them yourself, and use the daily check-in to reflect. Reinstalling is always your choice.",
            "Create distance from habitual scrolling without shame."),
        WellnessChallenge("screen_3h", "Three-hour phone window", "Digital balance", "Build Rhythm", 7,
            "Aim for three hours or less of total phone use each day for seven days.", "Check your daily screen-time report and plan two phone-free blocks.",
            "Build a steadier relationship with your device."),
        WellnessChallenge("no_reels", "No short-form scroll", "Digital boundaries", "Build Rhythm", 7,
            "Take seven days away from infinite short-video feeds.", "Remove shortcuts, sign out or uninstall the apps that host short-form feeds.",
            "Break the automatic open-and-scroll loop."),
        WellnessChallenge("focus_blocks", "Two phone-free focus blocks", "Attention", "Build Rhythm", 5,
            "Create two 45-minute phone-free blocks every day.", "Keep your phone in another room and choose one task for each block.",
            "Make space for deeper attention."),
        WellnessChallenge("sleep_boundary", "A calmer final hour", "Rest", "Build Rhythm", 7,
            "Keep the final 60 minutes before sleep screen-free.", "Charge your phone away from your bed and choose a non-screen wind-down.",
            "Give your mind a gentler transition into rest."),
        WellnessChallenge("screen_2h", "Two-hour digital reset", "Digital balance", "Deep Reset", 7,
            "Keep total phone use to two hours or less each day for seven days.", "Plan essential use in advance and review your daily total without judgment.",
            "Practice intentional, deliberate device use."),
        WellnessChallenge("social_14", "Fourteen days off social media", "Digital boundaries", "Deep Reset", 14,
            "Take a two-week break from the social apps you select.", "Uninstall or log out of chosen apps. Keep important contacts reachable through another channel.",
            "Reset the habits that keep pulling your attention back."),
        WellnessChallenge("offline_day", "One full offline day", "Presence", "Deep Reset", 1,
            "Take one day away from non-essential phone use.", "Tell important contacts how to reach you in an emergency, then put your phone away.",
            "Experience a day with fewer digital demands.")
    )
}

private data class ActiveChallenge(
    val id: String,
    val startedAt: Long,
    val checkInDays: Set<Long>,
    val status: String = "active"
)

private class ChallengeStore(context: Context) {
    private val prefs = context.getSharedPreferences("wellness_challenges", Context.MODE_PRIVATE)
    fun active(): ActiveChallenge? {
        val raw = prefs.getString("active_challenge", null) ?: return null
        return try {
            val j = JSONObject(raw)
            val days = j.optJSONArray("checkIns") ?: JSONArray()
            ActiveChallenge(j.getString("id"), j.getLong("startedAt"),
                (0 until days.length()).map { days.getLong(it) }.toSet(), j.optString("status", "active"))
        } catch (_: Exception) { null }
    }
    fun start(challenge: WellnessChallenge) {
        save(ActiveChallenge(challenge.id, System.currentTimeMillis(), emptySet()))
    }
    fun checkIn(item: ActiveChallenge) {
        val today = System.currentTimeMillis() / 86_400_000L
        if (today !in item.checkInDays) save(item.copy(checkInDays = item.checkInDays + today))
    }
    fun drop() { prefs.edit().remove("active_challenge").apply() }
    fun complete() {
        val item = active() ?: return
        val history = JSONArray(prefs.getString("completed_challenges", "[]") ?: "[]")
        history.put(JSONObject().put("id", item.id).put("completedAt", System.currentTimeMillis()))
        prefs.edit().putString("completed_challenges", history.toString()).remove("active_challenge").apply()
    }
    fun completedIds(): Set<String> {
        val a = JSONArray(prefs.getString("completed_challenges", "[]") ?: "[]")
        return (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("id") }.toSet()
    }
    private fun save(item: ActiveChallenge) {
        val days = JSONArray()
        item.checkInDays.sorted().forEach { days.put(it) }
        val j = JSONObject().put("id", item.id).put("startedAt", item.startedAt)
            .put("checkIns", days).put("status", item.status)
        prefs.edit().putString("active_challenge", j.toString()).apply()
    }
}

class WellnessActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var store: ChallengeStore
    private var selectedLevel = "All"
    private var selectedTab = "Challenges"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ChallengeStore(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) render()
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            clipToPadding = false
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(28))
            setBackgroundColor(BG)
        }
        scroll.addView(root)
        setContentView(scroll)

        row(root) {
            label(this, "REEL FOCUS", 12, LIME, true, 2f)
            label(this, "WELLBEING / 01", 10, MUTED, true)
        }
        space(root, 30)
        label(root, "Make space\nfor your life.", 34, INK, true)
        space(root, 9)
        label(root, "Small challenges. Meaningful shifts.\nYour attention belongs to you.", 14, MUTED)
        space(root, 24)

        val active = store.active()
        if (active != null) activeCard(active) else welcomeCard()
        space(root, 26)

        row(root) {
            label(this, "Choose your next shift", 21, INK, true, 1f)
            label(this, "${ChallengeCatalog.all.size} challenges", 11, MUTED)
        }
        space(root, 6)
        label(root, "No levels to unlock. Pick what feels right for you.", 12, MUTED)
        space(root, 17)
        levelSelector()
        space(root, 16)
        ChallengeCatalog.all.filter { selectedLevel == "All" || it.level == selectedLevel }
            .forEach { challengeCard(it, active) }
        space(root, 20)
        val footer = MaterialButton(this).apply {
            text = "FOCUS TOOLS  ↗"
            setTextColor(INK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(PANEL_ALT)
            cornerRadius = dp(24)
            setOnClickListener { startActivity(Intent(this@WellnessActivity, MainActivity::class.java)) }
        }
        root.addView(footer, matchWrap())
        space(root, 12)
        label(root, "Progress, not perfection. You can drop a challenge whenever you need.", 11, MUTED)
    }

    private fun welcomeCard() {
        val card = card(PANEL, 30)
        root.addView(card, matchWrap())
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(22))
        }
        card.addView(content)
        row(content) {
            label(this, "YOUR NEXT CHAPTER", 10, LIME, true, 1f)
            label(this, "✳", 24, LIME, true)
        }
        space(content, 22)
        label(content, "One intentional\nchoice at a time.", 25, INK, true)
        space(content, 9)
        label(content, "Choose a challenge that gives your time, energy and attention a little more room.", 13, MUTED)
        space(content, 20)
        label(content, "01  CHOOSE     02  PRACTISE     03  REFLECT", 10, LIME, true)
    }

    private fun activeCard(active: ActiveChallenge) {
        val challenge = ChallengeCatalog.all.firstOrNull { it.id == active.id } ?: return
        val elapsed = ((System.currentTimeMillis() - active.startedAt) / 86_400_000L).toInt() + 1
        val day = elapsed.coerceAtMost(challenge.durationDays)
        val today = System.currentTimeMillis() / 86_400_000L
        val checkedToday = today in active.checkInDays
        val completeReady = active.checkInDays.size >= challenge.durationDays && elapsed >= challenge.durationDays
        val card = card(0xFFEDEEE5.toInt(), 30)
        root.addView(card, matchWrap())
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(22), dp(22), dp(22)) }
        card.addView(c)
        label(c, "YOUR ACTIVE CHALLENGE", 10, 0xFF59634F.toInt(), true)
        space(c, 10)
        label(c, challenge.title, 23, DARK_INK, true)
        space(c, 5)
        label(c, "${challenge.category}  ·  ${challenge.durationDays} days", 12, 0xFF66705F.toInt())
        space(c, 18)
        row(c) {
            label(this, "DAY $day OF ${challenge.durationDays}", 11, DARK_INK, true, 1f)
            label(this, "${active.checkInDays.size}/${challenge.durationDays} check-ins", 11, 0xFF66705F.toInt())
        }
        val progress = View(this).apply { background = rounded(0xFFD2D8C8.toInt(), 8) }
        c.addView(progress, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = dp(9) })
        val fill = View(this).apply { background = rounded(0xFF6F8E37.toInt(), 8) }
        val progressRow = android.widget.FrameLayout(this)
        progressRow.addView(fill, android.widget.FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * .78f * active.checkInDays.size / challenge.durationDays).toInt().coerceAtLeast(dp(4)), dp(7)))
        c.addView(progressRow, LinearLayout.LayoutParams(-1, dp(7)).apply { topMargin = -dp(7) })
        space(c, 16)
        label(c, challenge.action, 13, 0xFF46503F.toInt())
        space(c, 17)
        val check = MaterialButton(this).apply {
            text = if (checkedToday) "TODAY'S CHECK-IN COMPLETE ✓" else "CHECK IN FOR TODAY"
            isEnabled = !checkedToday
            setTextColor(DARK_INK)
            backgroundTintList = android.content.res.ColorStateList.valueOf(LIME)
            cornerRadius = dp(24)
            setOnClickListener { store.checkIn(active); render() }
        }
        c.addView(check, matchWrap())
        if (completeReady) {
            space(c, 8)
            val done = MaterialButton(this).apply {
                text = "COMPLETE CHALLENGE"
                setTextColor(INK); backgroundTintList = android.content.res.ColorStateList.valueOf(DARK_INK); cornerRadius = dp(24)
                setOnClickListener { store.complete(); render() }
            }
            c.addView(done, matchWrap())
        }
        space(c, 4)
        val drop = TextView(this).apply {
            text = "Drop this challenge"
            setTextColor(0xFF6A7064.toInt()); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(5))
            setOnClickListener { confirmDrop() }
        }
        c.addView(drop, matchWrap())
    }

    private fun confirmDrop() {
        AlertDialog.Builder(this).setTitle("Drop this challenge?")
            .setMessage("Your current run will end. You can choose any challenge again right away.")
            .setNegativeButton("Keep going", null)
            .setPositiveButton("Drop challenge") { _, _ -> store.drop(); render() }.show()
    }

    private fun levelSelector() {
        val levels = listOf("All", "Ease In", "Build Rhythm", "Deep Reset")
        row(root, 7) {
            levels.forEach { level ->
                val chip = TextView(this@WellnessActivity).apply {
                    text = level
                    textSize = 11f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    setPadding(dp(13), dp(11), dp(13), dp(11))
                    setTextColor(if (selectedLevel == level) DARK_INK else MUTED)
                    background = rounded(if (selectedLevel == level) LIME else PANEL, 30)
                    setOnClickListener { selectedLevel = level; render() }
                }
                addView(chip, LinearLayout.LayoutParams(-2, -2))
            }
        }
    }

    private fun challengeCard(item: WellnessChallenge, active: ActiveChallenge?) {
        val c = card(PANEL, 24)
        root.addView(c, matchWrap().apply { bottomMargin = dp(11) })
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(17)) }
        c.addView(body)
        row(body) {
            label(this, item.level.uppercase(Locale.getDefault()), 10, LIME, true, 1f)
            label(this, "${item.durationDays} DAY${if (item.durationDays == 1) "" else "S"}", 10, MUTED, true)
        }
        space(body, 10)
        label(body, item.title, 19, INK, true)
        space(body, 5)
        label(body, item.category.uppercase(Locale.getDefault()), 9, MUTED, true)
        space(body, 9)
        label(body, item.description, 13, 0xFFD0D5CC.toInt())
        space(body, 12)
        label(body, "WHY IT HELPS", 9, LIME, true)
        space(body, 4)
        label(body, item.benefit, 12, MUTED)
        space(body, 14)
        val action = MaterialButton(this).apply {
            text = when {
                active != null -> "ONE ACTIVE CHALLENGE AT A TIME"
                else -> "CHOOSE THIS CHALLENGE  →"
            }
            isEnabled = active == null
            setTextColor(if (active == null) DARK_INK else MUTED)
            backgroundTintList = android.content.res.ColorStateList.valueOf(if (active == null) LIME else PANEL_ALT)
            cornerRadius = dp(22)
            setOnClickListener { startChallenge(item) }
        }
        body.addView(action, matchWrap())
    }

    private fun startChallenge(item: WellnessChallenge) {
        if (store.active() != null) return
        AlertDialog.Builder(this).setTitle("Begin ${item.title}?")
            .setMessage("${item.description}\n\n${item.action}\n\nYou can drop this challenge at any time. You can choose another after it ends.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Start challenge") { _, _ -> store.start(item); render() }.show()
    }

    private fun card(color: Int, radius: Int): MaterialCardView =
        MaterialCardView(this).apply {
            setCardBackgroundColor(color); radius = dp(radius).toFloat(); cardElevation = 0f
            strokeWidth = dp(1); strokeColor = if (color == PANEL) 0xFF252C25.toInt() else color
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun label(parent: LinearLayout, text: String, size: Int, color: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            this.text = text; textSize = size.toFloat(); setTextColor(color)
            typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            includeFontPadding = true
        }, matchWrap())
    }

    private fun label(parent: LinearLayout, text: String, size: Int, color: Int, bold: Boolean, weight: Float) {
        parent.addView(TextView(this).apply {
            this.text = text; textSize = size.toFloat(); setTextColor(color)
            typeface = Typeface.create("sans-serif-medium", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }, LinearLayout.LayoutParams(0, -2, weight))
    }

    private fun row(parent: LinearLayout, gap: Int = 10, content: LinearLayout.() -> Unit) {
        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        parent.addView(line, matchWrap()); line.content()
    }

    private fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
