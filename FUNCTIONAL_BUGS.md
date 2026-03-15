# Reel Focus — Functional Bug Report

> Analysed codebase: all `.kt` source files under `app/src/main/java/com/reelfocus/app/`
> Date: March 15, 2026

---

## Severity Legend

| Icon | Severity | Impact |
|------|----------|--------|
| 🔴 | **Critical** | Core feature broken / data loss / crash |
| 🟠 | **High** | Major feature works incorrectly or creates UX deadlocks |
| 🟡 | **Medium** | Incorrect behaviour under specific conditions |
| 🟢 | **Low** | Minor / deprecation / code quality |

---

## Critical Bugs 🔴

---

### BUG-001 ✅ FIXED — `limitReached` flag never reset when a new session starts via session-gap

**File:** `app/src/main/java/com/reelfocus/app/OverlayService.kt`

**Root cause:**  
`limitReached` is a private service-level variable. When the monitoring loop detects that the session-reset gap has elapsed and calls `startNewSession()`, `limitReached` is **not** reset inside `startNewSession()`. On the next monitoring tick, `checkLimitReached()` immediately returns because of the guard:

```kotlin
// checkLimitReached()
if (limitReached) return   // ← always true after first limit hit
```

**Effect:**  
After the user's first session limit is reached once, every subsequent session started through the gap mechanism **never checks the limit again**. The timer runs indefinitely without ever showing the interrupt screen.

**Code path:**
```
handleMonitoredAppActive()
  └── gapMinutes >= resetGap → startNewSession()   (limitReached still = true)
        └── checkLimitReached() → returns immediately every tick
```

**Fix (conceptual):** Set `limitReached = false` inside `startNewSession()`.

**✅ Fix applied:** Added `limitReached = false` as the first statement in `startNewSession()` in `OverlayService.kt`.

---

### BUG-002 ✅ FIXED — `resetDailySession()` leaves stale timer state after midnight

**File:** `app/src/main/java/com/reelfocus/app/utils/PreferencesHelper.kt`

**Root cause:**  
`resetDailySession()` only writes `current_session = 1` and `extension_count = 0` to `SharedPreferences`. It does **not** clear `seconds_elapsed`, `is_active`, `session_completed`, `session_start_time`, or `last_activity_time`.

```kotlin
fun resetDailySession() {
    prefs.edit().apply {
        putInt("current_session", 1)
        putInt("extension_count", 0)       // ← only these two are reset
        putLong("last_reset_date", System.currentTimeMillis())
        apply()
    }
}
```

`OverlayService.onCreate()` calls `checkAndResetIfNewDay()` and then immediately calls `loadSessionState()`. The loaded state will have `currentSession = 1` (correct) but `secondsElapsed`, `sessionStartTime`, and `sessionCompleted` from the **previous day**.

**Effect:**  
On the very first use of a new day the timer starts from yesterday's elapsed time (e.g., 1200 seconds already elapsed for a 20-minute limit), causing the interrupt screen to fire almost immediately.

**Fix (conceptual):** In `resetDailySession()`, also reset all timer-related keys (`seconds_elapsed`, `is_active`, `session_completed`, `session_start_time`, `last_activity_time`, `extension_used`, `is_in_extension`).

**✅ Fix applied:** `resetDailySession()` in `PreferencesHelper.kt` now writes `seconds_elapsed = 0`, `is_active = false`, `session_completed = false`, `session_start_time = 0`, `last_activity_time = 0`, `extension_used = false`, `is_in_extension = false`, `is_on_break = false`, and `break_start_time = 0` alongside the existing fields.

---

## High Severity Bugs 🟠

---

### BUG-004 ✅ FIXED — `isServiceRunning` resets to `false` on every `MainActivity` recreation

**File:** `app/src/main/java/com/reelfocus/app/MainActivity.kt`

**Root cause:**  
`isServiceRunning` is a plain `Boolean` field initialised to `false`. It is never saved to `savedInstanceState`, nor is the actual service running state queried from the system.

```kotlin
private var isServiceRunning = false   // always false after rotation / back-stack return
```

`onResume()` calls `updateUI()`, which reads this stale `false` value, so:
- The button shows **"Start Monitoring"** even when the service is active.
- Tapping the button calls `startOverlayService()` again and starts a **second, duplicate** monitoring loop.

**Fix (conceptual):** Check `ActivityManager.getRunningServices()` or use a broadcast/bound-service pattern to query actual running state in `onResume()`.

**✅ Fix applied:** Added `isOverlayServiceRunning()` helper in `MainActivity.kt` that queries `ActivityManager.getRunningServices()`. `onResume()` now calls it to sync `isServiceRunning` before `updateUI()`, so the button label and duplicate-start guard are always accurate after rotation or back-stack return.

---

### BUG-005 ✅ FIXED — "Take Break" flag is set but never enforced in the monitoring loop

**File:** `app/src/main/java/com/reelfocus/app/OverlayService.kt`

**Root cause:**  
`handleTakeBreak()` sets `sessionState.isOnBreak = true` and `sessionState.isActive = false`. However, the monitoring loop's `handleMonitoredAppActive()` function never checks `isOnBreak`:

```kotlin
private fun handleMonitoredAppActive(packageName: String, config: AppConfig) {
    // ← no check for sessionState.isOnBreak here
    if (!sessionState.isActive) {
        val gapMinutes = ...
        // Resumes session based only on gapMinutes
    }
}
```

**Effect:**  
The break feature does nothing. As soon as the user returns to a monitored app, the session resumes immediately regardless of whether enough break time has passed. The `breakStartTime` timestamp is stored but never compared against anything.

**Fix (conceptual):** In `handleMonitoredAppActive()`, when `isOnBreak == true`, compare `System.currentTimeMillis() - breakStartTime` against a minimum break duration before allowing session resumption.

**✅ Fix applied:** At the top of `handleMonitoredAppActive()` in `OverlayService.kt`, added a guard that checks `isOnBreak`. If the break has not yet reached 10 minutes, the method returns immediately (timer stays paused, no overlay shown). Once 10 minutes have elapsed, `isOnBreak` and `breakStartTime` are cleared and normal resume logic proceeds.

---

### BUG-006 ✅ FIXED — `viewHistoryButton` gets two click listeners attached (SettingsActivity)

**File:** `app/src/main/java/com/reelfocus/app/SettingsActivity.kt`

**Root cause:**  
The history button click listener is registered in **two separate places** within `onCreate()`:

1. Inside `initializeViews()`:
```kotlin
viewHistoryButton.setOnClickListener {
    startActivity(Intent(this, HistoryActivity::class.java))
}
```

2. Inside `setupListeners()` (called right after `initializeViews()`):
```kotlin
val viewHistoryButton = findViewById<LinearLayout>(R.id.view_history_button)
viewHistoryButton.setOnClickListener {
    val intent = Intent(this, HistoryActivity::class.java)
    startActivity(intent)
}
```

Android's `setOnClickListener` **replaces** the previous listener, so only the second one fires. The first assignment is dead code. While this doesn't currently double-open the activity, the first registration is wasted work and a maintenance hazard prone to reintroducing the double-launch bug if the order of calls changes.

**Fix (conceptual):** Remove the duplicate registration from `initializeViews()`.

**✅ Fix applied:** The `setOnClickListener` call inside `initializeViews()` in `SettingsActivity.kt` has been removed. The single canonical registration in `setupListeners()` is retained.

---

### BUG-007 ✅ FIXED — Unmanaged `CoroutineScope` in `AppSelectionActivity` causes memory leaks and potential crashes

**File:** `app/src/main/java/com/reelfocus/app/AppSelectionActivity.kt`

**Root cause:**  
Both `loadInstalledApps()` and `AppListAdapter.onBindViewHolder()` create ad-hoc coroutine scopes with no lifecycle binding:

```kotlin
// loadInstalledApps
CoroutineScope(Dispatchers.IO).launch { ... withContext(Dispatchers.Main) { ... } }

// onBindViewHolder (inside adapter)
CoroutineScope(Dispatchers.IO).launch { ... withContext(Dispatchers.Main) { ... } }
```

These scopes are never cancelled. If the user backs out of `AppSelectionActivity` while the app list is loading (or while icons are loading), the coroutines continue running, hold a reference to the activity context, and then attempt to mutate views that have been destroyed — causing either a memory leak or an `IllegalStateException` / window token error.

**Fix (conceptual):**  
- Replace `CoroutineScope(Dispatchers.IO)` in `loadInstalledApps()` with `lifecycleScope`.
- For the adapter, store a reference to a parent `CoroutineScope` (passed from the activity) and use `scope.launch`.

**✅ Fix applied:** In `AppSelectionActivity.kt`, `loadInstalledApps()` now uses `lifecycleScope.launch(Dispatchers.IO)`. `AppListAdapter` receives a `scope: CoroutineScope` constructor parameter; the adapter's `onBindViewHolder` icon-loading coroutine uses `scope.launch(Dispatchers.IO)` instead of creating a new `CoroutineScope`. The activity instantiates the adapter passing `lifecycleScope`, so all coroutines are automatically cancelled when the activity is destroyed.

---

## Medium Severity Bugs 🟡

---

### BUG-008 ✅ FIXED — `ReelDetectionAccessibilityService` is dead code — its results are never consumed

**Files:** `ReelDetectionAccessibilityService.kt`, `DetectionManager.kt`

**Root cause:**  
`ReelDetectionAccessibilityService` stores its latest result in `latestDetection`. `DetectionManager.getActiveReelApp()` is the only consumer of detection data, but it **completely ignores** the accessibility service:

```kotlin
// DetectionManager.kt
suspend fun getActiveReelApp(monitoredPackages: List<String>): DetectionResult? {
    val foregroundApp = appMonitor.getActiveMonitoredApp(monitoredPackages) // UsageStats only
    // ← ReelDetectionAccessibilityService.getLatestDetection() is never called
}
```

All the pattern-matching logic in `ReelPatternMatcher` (Reel/Shorts detection confidence scoring, full-screen scroll detection, portrait ratio checks) is therefore **never exercised**. The app falls back entirely to UsageStats-level detection (whole-app, not reel-specific).

**Effect:**  
The app monitors all YouTube usage as "Shorts" (noted even in the code comment: `"YouTube Shorts" // Monitors all YouTube usage`). A user watching a long YouTube video will have their timer running identically to someone scrolling Shorts.

**Fix (conceptual):** In `DetectionManager.getActiveReelApp()`, check `ReelDetectionAccessibilityService.getLatestDetection()` first and use its result when available and recent (e.g., within the last 2 seconds).

**✅ Fix applied:** `DetectionManager.getActiveReelApp()` now checks `ReelDetectionAccessibilityService.getLatestDetection()` first. If the result is non-null, `isReelDetected = true`, the package is in the monitored list, and the result is ≤ 2 seconds old, it is returned immediately. Only when that check fails does the code fall back to `AppUsageMonitor` (UsageStats).

---

### BUG-009 ✅ FIXED — `AppUsageMonitor.getForegroundApp()` returns the most-recently-used app, not necessarily the current foreground app

**File:** `app/src/main/java/com/reelfocus/app/utils/AppUsageMonitor.kt`

**Root cause:**  
The implementation scans a 60-second usage stats window and picks the entry with the highest `lastTimeUsed`:

```kotlin
for (usageStats in usageStatsList) {
    if (usageStats.lastTimeUsed > mostRecentTime) {
        mostRecentTime = usageStats.lastTimeUsed
        foregroundPackage = usageStats.packageName
    }
}
```

`UsageStats.lastTimeUsed` is updated when the app **moves to background**, not when it's foregrounded. If Instagram was in the foreground 30 seconds ago and the user is now on the home screen, `lastTimeUsed` for Instagram is the most recent timestamp and will still be returned as the "foreground" app.

**Effect:**  
The session timer continues ticking after the user has left the monitored app and returned to the home screen or opened another (non-monitored) app. This inflates recorded usage time and can trigger the interrupt screen even when the user is not watching reels.

**Fix (conceptual):** Use `UsageEvents` API (`UsageStatsManager.queryEvents()`) and look for the latest `MOVE_TO_FOREGROUND` / `MOVE_TO_BACKGROUND` pair to determine the true current foreground app, or use the Accessibility Service's `onAccessibilityEvent` with `TYPE_WINDOW_STATE_CHANGED` (which `ReelDetectionAccessibilityService` already does for its own purposes).

**✅ Fix applied:** `getForegroundApp()` in `AppUsageMonitor.kt` now calls `usageStatsManager.queryEvents()` over a 10-second window and walks each event chronologically. It tracks the last `MOVE_TO_FOREGROUND` package; if that package subsequently fires `MOVE_TO_BACKGROUND`, it is cleared to `null`. The final value is the truly active foreground app (or `null` if none).

---

### BUG-010 ✅ FIXED — `dailyLimitReached` off-by-one: last session shows "All done" but service still allows one more start

**Files:** `OverlayService.kt`, `InterruptActivity.kt`

**Root cause:**  
`showDailyBlockScreen` fires when `currentSession > maxSessions` (strictly greater):
```kotlin
// OverlayService
if (sessionState.currentSession > sessionState.maxSessions) {
    showDailyBlockScreen(...)
}
```

But `InterruptActivity` sets `dailyLimitReached = true` when `currentSession >= maxSessions` (greater-or-equal):
```kotlin
val dailyLimitReached = sessionState.currentSession >= sessionState.maxSessions
```

**Effect:**  
On the **last** session (`currentSession == maxSessions`), `InterruptActivity` shows "Daily Limit Reached", disables both buttons except "Done", and sends the user home. However, the service condition uses `>`, so one more session is still creatable. If the user taps "Start Next Session" via any other path, `currentSession` increments to `maxSessions + 1` and `DailyBlockActivity` is shown — essentially two consecutive "you're done" screens for the final session.

**Fix (conceptual):** Make both checks use the same operator. Since the intent is to show the block screen _after_ exhausting all sessions, use `>` consistently in both places, or adjust `InterruptActivity`'s check to also use `>`.

**✅ Fix applied:** In `OverlayService.kt` `showInterruptScreen()`, `dailyLimitReached` now uses `currentSession > maxSessions` (strictly greater), matching the `showDailyBlockScreen` guard. Both paths are now consistent.

---

### BUG-011 ✅ FIXED — Recycler view icon loading uses display-name equality as stale-view guard (fragile)

**File:** `app/src/main/java/com/reelfocus/app/AppSelectionActivity.kt`

**Root cause:**  
After loading an app icon on a background thread, the adapter tries to guard against updating a recycled view holder:

```kotlin
withContext(Dispatchers.Main) {
    if (holder.name.text == app.appName) {   // ← compares display names
        holder.icon.setImageDrawable(appIcon)
    }
}
```

Two apps on the device may share the same display name (e.g., two "Clock" entries). If the ViewHolder has been recycled to display a different app with the same name, the wrong app's icon is set.

**Fix (conceptual):** Use `holder.adapterPosition` compared to the position captured at bind time, or use `holder.bindingAdapterPosition != RecyclerView.NO_ID && apps[holder.bindingAdapterPosition].packageName == app.packageName`.

**✅ Fix applied:** The recycling guard in `AppListAdapter.onBindViewHolder()` in `AppSelectionActivity.kt` now checks `holder.bindingAdapterPosition != RecyclerView.NO_ID && apps[bindingAdapterPosition].packageName == app.packageName` instead of comparing display names.

---

### BUG-012 ✅ FIXED — `SimpleDateFormat` in `HistoryManager` is not thread-safe

**File:** `app/src/main/java/com/reelfocus/app/utils/HistoryManager.kt`

**Root cause:**  
`dateFormat` is a shared instance-level field:
```kotlin
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
```

`SimpleDateFormat` is explicitly documented as **not thread-safe**. `HistoryManager.recordSession()` is called from `OverlayService` (which runs coroutines on `lifecycleScope`) and `HistoryManager.getTodayDate()` could be called concurrently.

**Effect:**  
Under concurrent access, `dateFormat.parse()` or `dateFormat.format()` can return corrupt date strings, causing sessions to be bucketed under wrong dates or silently dropped by the history filter.

**Fix (conceptual):** Replace with `java.time.LocalDate` (API 26+) or create `SimpleDateFormat` instances on demand within each method call.

**✅ Fix applied:** The shared `dateFormat` field in `HistoryManager.kt` has been removed. Every method that needs date formatting or parsing now creates its own `SimpleDateFormat("yyyy-MM-dd", Locale.US)` instance locally, eliminating the shared mutable state.

---

### BUG-013 ✅ FIXED — `handleNextSession()` does not reset `activeAppPackage`

**File:** `app/src/main/java/com/reelfocus/app/OverlayService.kt`

**Root cause:**  
```kotlin
private fun handleNextSession() {
    sessionState.currentSession++
    sessionState.extensionUsed = false
    sessionState.isInExtension = false
    sessionState.sessionCompleted = false
    sessionState.secondsElapsed = 0
    sessionState.sessionStartTime = System.currentTimeMillis()
    limitReached = false
    sessionState.isActive = true
    // ← sessionState.activeAppPackage is NOT cleared
    ...
}
```

After the user clicks "Start Next Session" in `InterruptActivity`, the service is back in an active state but `activeAppPackage` still refers to the app from the **previous** session. The monitoring loop's app-switch detection compares `sessionState.activeAppPackage != packageName`, so when the next `handleMonitoredAppActive()` call comes in for the same package it will enter the `currentMonitoredApp == activeApp` branch and call `updateTimerOnly()` without ever calling `startNewSession()` properly to set the per-app limit.

However, if the user switches to a different app, `activeAppPackage` is updated correctly. The issue is primarily cosmetic (wrong package in history record) when staying on the same app, but can mismatch if the user context has changed.

**Fix (conceptual):** Set `sessionState.activeAppPackage = null` inside `handleNextSession()` so the next `handleMonitoredAppActive()` call always goes through the full app-initialisation path.

**✅ Fix applied:** Added `sessionState.activeAppPackage = null` in `handleNextSession()` in `OverlayService.kt`. The monitoring loop will now always enter the `startNewSession()` path on the very next tick, correctly setting the per-app limit.

---

### BUG-014 ✅ FIXED — `SettingsActivity.saveSettings()` unconditionally restarts the `OverlayService`

**File:** `app/src/main/java/com/reelfocus/app/SettingsActivity.kt`

**Root cause:**  
```kotlin
private fun saveSettings() {
    ...
    // Restart service to apply changes
    val stopIntent = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP }
    startService(stopIntent)

    android.os.Handler(mainLooper).postDelayed({
        val startIntent = Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START }
        startForegroundService(startIntent)  // ← always starts, even if service wasn't running
    }, 500)
    finish()
}
```

Even when the user opens Settings purely to look at options and the monitoring service is **not running**, `saveSettings()` sends `ACTION_START` after the delay, effectively starting the service without the user's consent.

**Fix (conceptual):** Before sending `ACTION_START`, check whether the service was already running; only restart it if it was.

**✅ Fix applied:** The entire orphaned `saveSettings()` function body has been removed from `SettingsActivity.kt`. It was never called (no button or listener referenced it) and was also syntactically broken (missing its `private fun saveSettings()` signature from a previous edit). All settings are already persisted incrementally by `autoSave()` on every slider/button change.

---

## Low Severity Bugs 🟢

---

### BUG-015 ✅ FIXED — `onBackPressed()` is deprecated since Android 13 (API 33)

**Files:** `InterruptActivity.kt`, `DailyBlockActivity.kt`

Both activities override `onBackPressed()` to prevent back navigation:

```kotlin
override fun onBackPressed() {
    // Prevent back button - user must make a choice
}
```

`onBackPressed()` is deprecated in API 33. On Android 13+ devices, the system Predictive Back Gesture can bypass a deprecated `onBackPressed()` override. The correct approach is to use `OnBackPressedDispatcher`:

```kotlin
onBackPressedDispatcher.addCallback(this) { /* consume back */ }
```

**Effect:**  
On Android 13+ with Predictive Back enabled, users may be able to dismiss the interrupt/block screen using the system gesture, bypassing the app's limit enforcement.

**✅ Fix applied:** Both `InterruptActivity.kt` and `DailyBlockActivity.kt` now register an `OnBackPressedDispatcher.addCallback()` in `onCreate()`. `InterruptActivity` swallows the back event (user must choose an action). `DailyBlockActivity` navigates home on back. The legacy `onBackPressed()` override is kept with `@Suppress("DEPRECATION")` calling `super` so the dispatcher callback takes effect.

---

### BUG-016 ✅ FIXED — `initializeDefaults()` hard-codes `sessionResetGapMinutes` to 30 min via comment mismatch

**File:** `app/src/main/java/com/reelfocus/app/utils/PreferencesHelper.kt`

The `AppConfig` data class declares the default as 10 minutes:
```kotlin
// AppConfig.kt
val sessionResetGapMinutes: Int = 10   // C-003: Minimum 10 min gap
```

`initializeDefaults()` uses `AppConfig(...)` without providing `sessionResetGapMinutes`, so it will correctly be 10 for new installs.

However, the label comment in `AppConfig` says "Minimum 10 min gap" while `PreferencesHelper.loadConfig()` uses `prefs.getInt("reset_gap_minutes", 30)` as its fallback default:

```kotlin
sessionResetGapMinutes = prefs.getInt("reset_gap_minutes", 30),  // fallback = 30, not 10
```

For users who never had `reset_gap_minutes` written (e.g., first-launch edge case where `initializeDefaults()` fails), the gap would silently default to **30 minutes instead of 10**, inconsistent with the documented default and UI display.

**Fix (conceptual):** Change the `loadConfig()` fallback to `10` to match `AppConfig`'s documented default.

**✅ Fix applied:** `PreferencesHelper.loadConfig()` now uses `prefs.getInt("reset_gap_minutes", 10)`, consistent with the `AppConfig` data class default and the Settings UI range.

---

## Summary Table

| ID | Severity | Location | Description |
|----|----------|----------|-------------|
| BUG-001 | ✅ Fixed | `OverlayService.kt` | `limitReached` never reset on gap-triggered new session — timer runs forever |
| BUG-002 | ✅ Fixed | `PreferencesHelper.kt` | `resetDailySession()` leaves stale `secondsElapsed` / `isActive` after midnight |
| BUG-004 | ✅ Fixed | `MainActivity.kt` | `isServiceRunning` always `false` on activity recreation — wrong button state & duplicate service starts |
| BUG-005 | ✅ Fixed | `OverlayService.kt` | `isOnBreak` flag set but never checked — break feature is entirely non-functional |
| BUG-006 | ✅ Fixed | `SettingsActivity.kt` | `viewHistoryButton` receives two `setOnClickListener` calls (second overwrites first silently) |
| BUG-007 | ✅ Fixed | `AppSelectionActivity.kt` | Unmanaged `CoroutineScope` — memory leak + potential crash after activity destruction |
| BUG-008 | ✅ Fixed | `DetectionManager.kt` | `ReelDetectionAccessibilityService` results never consumed — all pattern matching is dead code |
| BUG-009 | ✅ Fixed | `AppUsageMonitor.kt` | `getForegroundApp()` returns last-used, not truly foreground app — false positive timer ticks |
| BUG-010 | ✅ Fixed | `OverlayService.kt` / `InterruptActivity.kt` | Off-by-one in `dailyLimitReached` vs `showDailyBlockScreen` condition |
| BUG-011 | ✅ Fixed | `AppSelectionActivity.kt` | Icon recycling guard uses display-name equality — fails for apps with identical names |
| BUG-012 | ✅ Fixed | `HistoryManager.kt` | `SimpleDateFormat` is not thread-safe — can corrupt date bucketing under concurrency |
| BUG-013 | ✅ Fixed | `OverlayService.kt` | `handleNextSession()` does not clear `activeAppPackage` |
| BUG-014 | ✅ Fixed | `SettingsActivity.kt` | `saveSettings()` always starts the service even if it wasn't running |
| BUG-015 | ✅ Fixed | `InterruptActivity.kt`, `DailyBlockActivity.kt` | Deprecated `onBackPressed()` bypassable on Android 13+ |
| BUG-016 | ✅ Fixed | `PreferencesHelper.kt` | `loadConfig()` fallback for `reset_gap_minutes` is 30, inconsistent with documented default of 10 |
