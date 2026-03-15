# Reel Focus — Application Context & End-to-End Flow

> Generated: 2026-03-16  
> Package: `com.reelfocus.app`  
> Min SDK: 22 · Target SDK: 34  
> Language: Kotlin · UI: Material 3 (dark teal theme)

---

## 1. What the App Does

Reel Focus is a **screen-time self-control app**. It monitors selected apps (e.g. TikTok, Instagram, YouTube) and surfaces a floating overlay while the user is inside one of those apps. When the user hits a configurable time limit, an interrupt screen is shown to break the scroll loop. A daily session cap prevents unlimited restarts.

**Core value proposition**: Passive, non-intrusive overlay nudge → hard interrupt when limit is reached → structured cooldowns and daily limits to enforce real reduction.

---

## 2. Permissions Required

| Permission | Why | How to grant |
|---|---|---|
| **Usage Stats** (`PACKAGE_USAGE_STATS`) | Detect which app is in the foreground | Android Settings → Digital Wellbeing / Usage Access |
| **Draw Over Other Apps** (`SYSTEM_ALERT_WINDOW`) | Show the floating overlay on top of any app | Android Settings → Apps → Special Access → Display over other apps |

Both are *dangerous/special* permissions that cannot be granted via a runtime dialog — the user must be sent to system Settings. The home screen guides them through this.

---

## 3. Architecture Overview

```
MainActivity  ──────────────────────────────────────────────
│  (home screen: permissions, app select, settings, start) │
└──► OverlayService  (foreground service, 1-second poll)
       │
       ├── DetectionManager → AppUsageMonitor (UsageStatsManager, 5-min window)
       │   └── returns: packageName of active monitored app, or null
       │
       ├── SessionState  (in-memory + SharedPreferences)
       │   └── secondsElapsed, currentSession, limitValue, flags…
       │
       ├── OverlayView  (custom Canvas View, 3 styles)
       │   ├── TEXT   100×56dp — session label + countdown clock
       │   ├── DONUT  30×30dp  — circular ring drains as time passes
       │   └── SHRINKING_CIRCLE  30×30dp — filled circle shrinks to zero
       │
       ├── InterruptActivity  (launched when limit or extension ends)
       └── DailyBlockActivity (launched when all daily sessions used)

AppSelectionActivity  (choose which apps to monitor)
SettingsActivity      (limits, overlay style, position, session gap)
HistoryActivity       (past sessions log)
PreferencesHelper     (SharedPreferences wrapper — config + session state)
HistoryManager        (session history log)
```

---

## 4. Data Model

### AppConfig (persisted via `PreferencesHelper`)

| Field | Default | Meaning |
|---|---|---|
| `maxSessionsDaily` | 5 | Max number of usage sessions allowed per day |
| `sessionResetGapMinutes` | 10 | Minutes of inactivity before a new session is counted |
| `defaultLimitValue` | 20 | Minutes per session (applies to all apps unless per-app enabled) |
| `overlayPosition` | TOP_RIGHT | Corner/edge where floating overlay appears |
| `overlayTextSize` | MEDIUM | Text size for TEXT-style overlay |
| `overlayStyle` | TEXT | TEXT / DONUT / SHRINKING_CIRCLE |
| `monitoredApps` | TikTok, Instagram, YouTube | List of `MonitoredApp` objects |
| `isPerAppLimitEnabled` | false | Premium feature stub — custom per-app limits |

### SessionState (in-memory + persisted every 5 seconds)

| Field | Meaning |
|---|---|
| `secondsElapsed` | Cumulative seconds across all monitored apps for this session |
| `limitValue` | Current session's time limit in minutes |
| `currentSession` | Which session of the day this is (starts at 1) |
| `maxSessions` | Loaded from config at service start |
| `isActive` | True when a monitored app is in the foreground and timer is running |
| `sessionStartTime` | Timestamp when session started |
| `lastActivityTime` | Timestamp of last foreground tick — used to detect gaps |
| `extensionUsed` | True if the one-time 5-min extension was used this session |
| `isInExtension` | True during the 5-min extension window |
| `sessionCompleted` | True once the main time limit is reached |
| `isOnBreak` | True during a user-initiated 10-minute break |
| `breakStartTime` | Timestamp when break started |
| `activeAppPackage` | Package name of the currently monitored foreground app |

---

## 5. Home Screen Flow (MainActivity)

The home screen is a **gamified quest board** — no numbered step labels. Instead, visual state (card borders, dimming, progress dots) guides the user forward.

### Visual States

```
[Progress track: dot1 ── dot2 ── dot3 ── dot4]

Quest Card 1: "Allow Access"      ← always active (teal border)
  ↓ arrow (teal when done)
Quest Card 2: "Choose Your Targets"  ← 45% dim until permissions granted
  ↓ arrow (teal when done)
Quest Card 3: "Set Your Rules"       ← 45% dim until ≥1 app selected
  ↓ arrow (teal when ready)
[Status hint text]
[START / STOP button]
```

### Button State Logic

```
canStart = hasOverlayPermission && hasUsageStatsPermission && hasMonitoredApps

startButton.isEnabled = canStart || isServiceRunning   ← BUG-F03 fix
```

The button is always enabled when the service is running so the user can always stop it, even if permissions were revoked.

### onResume Sync

Every time `MainActivity` regains focus, `isOverlayServiceRunning()` is called via `ActivityManager` to sync the button state with reality (handles rotation, back-stack, returning from Settings).

---

## 6. App Selection Screen (AppSelectionActivity)

### What It Shows

- All user-facing installed apps (launcher intent + keyword filter)
- Apps sorted alphabetically
- Each row: icon (lazy-loaded), name, package name, gear icon (per-app config), checkbox

### Selected-Apps Section (top)

- Hidden when nothing is selected
- Appears as a teal-bordered card above the full list as soon as ≥1 app is checked
- Shows compact rows: icon + name + × remove button
- Removing from this section also unchecks the app in the list below (bidirectional sync)
- Badge shows current selected count

### Filtering Logic

1. Exclude own package
2. Exclude system provider packages
3. Include if app name contains a social/entertainment keyword (hardcoded list)
4. Include any app that has a launcher intent (user-facing), excluding names that contain: `settings`, `setup`, `launcher`, `keyboard`, `wallpaper`

### Save Behaviour

- `Save N Apps` button enabled only when ≥1 app selected
- On save: creates `MonitoredApp` entries (preserving any existing `customLimitValue`)
- Saves to config via `PreferencesHelper`, then `finish()`

### Per-App Config Dialog (gear icon)

- Launches `dialog_app_config`
- Currently gated behind `isPerAppLimitEnabled = false` (premium stub)
- When disabled: shows "premium feature" notice with a Close button
- When enabled (future): seekbar to set custom per-app limit in minutes (5–60)

---

## 7. Settings Screen (SettingsActivity)

### Configurable Fields

| Setting | Control | Range / Options |
|---|---|---|
| Daily session limit | Slider | 1 – 10 sessions |
| Session gap | Slider | 5 – 60 minutes |
| Time limit per session | Slider | 5 – 60 minutes |
| Overlay position | 3-button toggle | Top / Centre-right / Bottom |
| Overlay text size | 3-button toggle | Small / Medium / Large |
| Overlay style | 3-button toggle | Text / Donut / Circle |
| Manage apps | Row tap | Opens AppSelectionActivity |

Config is saved on **every change** (no explicit Save button). The monitoring loop reloads config every 30 seconds to pick up changes without a restart (BUG-F02 fix).

---

## 8. Monitoring Service (OverlayService)

### Lifecycle

```
MainActivity taps Start
  └─► startForegroundService(ACTION_START)
        └─► startMonitoring()  — launches coroutine on lifecycleScope
              └─► while(true) { delay(1000); ... }  — 1-second poll loop
```

### Poll Loop Logic (every second)

```
detectionManager.getActiveReelApp(monitoredPackages)
  │
  ├── result != null  (a monitored app IS in the foreground)
  │     ├── consecutiveNullTicks = 0  (reset debounce)
  │     ├── if new app → handleMonitoredAppActive()
  │     └── if same app → updateTimerOnly()
  │
  └── result == null  (no monitored app in foreground)
        ├── consecutiveNullTicks++
        ├── if ticks < 5 AND currentMonitoredApp != null
        │     └─► updateTimerOnly()  (grace window — glitch protection)
        └── if ticks >= 5  (NULL_TICKS_BEFORE_INACTIVE)
              └─► handleMonitoredAppInactive()
```

**Debounce (5 ticks)**: The UsageStats API occasionally misses an app for 1–2 seconds during a scroll. Without this, the overlay would flicker and pause the timer spuriously. After 5 consecutive null results, the app is considered truly inactive.

**Config reload (every 30 ticks)**: Settings changes are picked up without restarting the service.

---

## 9. Session State Machine

### States

```
IDLE ──► ACTIVE ──► LIMIT_REACHED ──► [InterruptActivity]
                                            │
              ┌─────────────────────────────┤
              │                             │
         EXTENDING                    TAKE_BREAK ──► BREAK (10 min cooldown)
              │                             │
         EXTENSION_DONE             BREAK_EXPIRED ──► ACTIVE (next session)
              │
       [InterruptActivity again]
              │
         NEXT_SESSION or STOP
```

### Detailed State Transitions

#### A. Normal session (no extension)

1. **First open**: `sessionStartTime == 0` → `startNewSession()` called
   - Resets `secondsElapsed = 0`, sets `limitValue`, `isActive = true`
2. **Timer running**: `secondsElapsed++` every second, overlay updates
3. **Limit reached**: `secondsElapsed >= limitValue * 60`
   - `sessionCompleted = true`
   - Launches `InterruptActivity` (not daily-block, extension not used)
   - Overlay stays visible

#### B. User extends (one-time per session)

1. `InterruptActivity` shows "Extend 5 min" button
2. Sends `ACTION_EXTEND` to service
3. `handleExtend()`:
   - `extensionUsed = true`, `isInExtension = true`
   - `isActive = true` — timer continues from where it left off
   - Overlay shows remaining extension time
4. After 5 extra minutes (`secondsElapsed >= limitValue * 60 + 5 * 60`):
   - Launches `InterruptActivity` again with `isExtensionCompleted = true`
   - Extend button disabled this time

#### C. User takes a break (10-minute forced gap)

1. `InterruptActivity` shows "Take a Break" button
2. Sends `ACTION_TAKE_BREAK` to service
3. `handleTakeBreak()`:
   - `isOnBreak = true`, `breakStartTime = now`, `isActive = false`
   - Overlay immediately shows break countdown
4. While break is active and user opens a monitored app:
   - Overlay shows remaining break time (counts down)
   - Timer is NOT running
5. After 10 minutes: `isOnBreak = false` → session resumes normally

#### D. User starts next session

1. `InterruptActivity` shows "Next Session"
2. Sends `ACTION_NEXT_SESSION` to service
3. `handleNextSession()`:
   - `currentSession++`, `secondsElapsed = 0`, `extensionUsed = false`
   - `activeAppPackage = null` (forces re-initialisation on next tick)
   - Timer restarts from zero

#### E. Session gap auto-reset

If the user leaves all monitored apps for ≥ `sessionResetGapMinutes` (default 10 min):

```
next open of monitored app
  → gapMinutes >= config.sessionResetGapMinutes
    → if sessionCompleted: currentSession++, sessionCompleted = false
    → startNewSession()  (timer resets to 0)
```

This is the *passive* path — no interrupt screen needed; the gap itself counts as the break.

#### F. App switching mid-session

Timer is cumulative. Switching apps does NOT reset the timer:

```
Instagram (20min limit): 15min elapsed
→ switch to YouTube (30min limit)
→ secondsElapsed stays at 15min
→ limitValue updates to 30min
→ limitReached resets to false (fresh check for new limit)
→ timer continues from 15min toward 30min
```

#### G. Daily limit reached

When `currentSession > maxSessions`:

```
handleMonitoredAppActive()
  └─► showDailyBlockScreen()
        └─► DailyBlockActivity launched
              back button → home screen
              Settings button → SettingsActivity
```

Resets at **local midnight** (Calendar.getInstance() comparison — BUG-F04 fix).

---

## 10. Detection System

### DetectionManager → AppUsageMonitor

```kotlin
AppUsageMonitor.getRecentActiveApp(monitoredPackages)
```

- Uses `UsageStatsManager.queryEvents()` with a 5-minute lookback window
- Walks events newest-first; returns the first `MOVE_TO_FOREGROUND` event  
  whose package is in `monitoredPackages`
- Returns `null` if no monitored app has been in the foreground in the last 5 min

> **Why 5 minutes?** The window is wide enough to detect an app even if the  
> UsageStats API delays its event by a few seconds, but short enough to not  
> falsely detect apps that were recently closed.

---

## 11. Floating Overlay (OverlayView)

### Style: TEXT (100×56dp)

- Rounded rectangle with semi-transparent dark background
- Top line: `S 1/5` (session counter)
- Bottom line: `MM:SS` countdown
- Colour transitions: teal (> 20% remaining) → amber (> 10%) → red (≤ 10%)
- During break: shows `BRK MM:SS`
- During extension: shows extension time remaining

### Style: DONUT (30×30dp)

- Circular ring (3dp stroke, no text)
- Ring drains clockwise as time passes
- Track ring is dark (#2D333B); arc uses same colour transitions as TEXT
- Minimal footprint — just a visual cue

### Style: SHRINKING_CIRCLE (30×30dp)

- Filled circle whose radius shrinks linearly from full to zero
- Same colour transitions
- No text

### Overlay positioning

Set via `WindowManager.LayoutParams.gravity`:  
TOP_LEFT · TOP_RIGHT · CENTER_RIGHT · BOTTOM_LEFT · BOTTOM_RIGHT

---

## 12. Interrupt Screen (InterruptActivity)

Launched from `OverlayService.checkLimitReached()`. Back button is disabled — the user must make a choice.

### Scenario variants

| Condition | Title | Buttons shown |
|---|---|---|
| Session limit reached, extension not used | "Time's Up" | Stop · Extend 5 min · Next Session · Take a Break |
| Session limit reached, extension already used | "Time's Up" | Stop · Next Session · Take a Break (Extend disabled) |
| Extension completed | "Extension Complete" | Stop · Next Session · Take a Break |
| Daily limit reached | "Daily Limit Reached" | Done (only) |

**Extend** → `ACTION_EXTEND`  
**Next Session** → `ACTION_NEXT_SESSION`  
**Take a Break** → `ACTION_TAKE_BREAK`  
**Stop / Done** → stops service, returns to home

---

## 13. Daily Block Screen (DailyBlockActivity)

Shown when `currentSession > maxSessions`. Tells the user the daily limit is reached and resets at midnight. Two buttons: **Go Home** · **Open Settings** (to raise the daily limit).

Back button sends the user to the Android home screen (not the app) to enforce the block.

---

## 14. History Screen (HistoryActivity)

Displays a log of past sessions from `HistoryManager` (stored in SharedPreferences as a JSON array of `SessionHistory` objects). Each entry: app name, date, duration, session number.

---

## 15. Day Reset

`PreferencesHelper.checkAndResetIfNewDay()` is called in `OverlayService.onCreate()`.

```kotlin
val today = Calendar.getInstance()  // local timezone — BUG-F04 fix
val lastReset = Calendar.getInstance().apply { timeInMillis = lastResetDate }
if (today.get(YEAR) != lastReset.get(YEAR) ||
    today.get(DAY_OF_YEAR) != lastReset.get(DAY_OF_YEAR)) {
    resetDailySession()  // currentSession=1, secondsElapsed=0, all flags cleared
}
```

---

## 16. SharedPreferences Keys (reference)

| Key | Type | Description |
|---|---|---|
| `initialized` | Boolean | First-launch flag |
| `max_sessions` | Int | Daily session cap |
| `reset_gap_minutes` | Int | Gap before new session |
| `default_limit_value` | Int | Minutes per session |
| `overlay_position` | String | enum name |
| `overlay_text_size` | String | enum name |
| `overlay_style` | String | enum name |
| `monitored_apps` | String (JSON) | Array of MonitoredApp |
| `per_app_limit_enabled` | Boolean | Premium feature flag |
| `current_session` | Int | Today's session index |
| `seconds_elapsed` | Int | Cumulative seconds this session |
| `is_active` | Boolean | Timer running flag |
| `session_start_time` | Long | Epoch ms |
| `last_activity_time` | Long | Epoch ms |
| `extension_used` | Boolean | |
| `is_in_extension` | Boolean | |
| `is_on_break` | Boolean | |
| `break_start_time` | Long | Epoch ms |
| `active_app_package` | String? | Current monitored app pkg |
| `last_reset_date` | Long | Epoch ms of last midnight reset |

---

## 17. Default Configuration (first launch)

- Monitored apps: TikTok, Instagram, YouTube (all enabled)
- Time limit: 20 minutes/session
- Daily sessions: 5
- Session gap: 10 minutes
- Overlay: TEXT style, MEDIUM size, TOP_RIGHT position

---

## 18. Known Design Decisions & Guards

| Topic | Decision |
|---|---|
| Timer is cumulative across apps | Counts total time in any monitored app, not per-app |
| No per-app timer (yet) | `isPerAppLimitEnabled = false` by default; per-app config shows "coming soon" dialog |
| 5-tick debounce | Prevents overlay flicker from 1-2s API glitches |
| 5-min overlay window | `AppUsageMonitor` looks back 5 minutes to avoid false negatives |
| Extension is one-time per session | `extensionUsed` flag; Extend button disabled on second interrupt |
| Back button blocked on interrupts | User must consciously choose an action |
| Day reset uses local timezone | `Calendar.getInstance()` not UTC epoch comparison |
| Service reload config every 30s | Settings changes take effect without restart |
| Session history dedup guard | `sessionRecordedForCurrentSession` prevents double-entry when user extends |
| Service state sync | `OverlayService.isRunning` static `@Volatile` flag set in `onCreate`/`onDestroy`; replaces deprecated `ActivityManager.getRunningServices()` |

---

## 19. Screens Summary

| Screen | Class | Entry Point |
|---|---|---|
| Home (quest board) | `MainActivity` | App launcher |
| App Selection | `AppSelectionActivity` | Home "Choose Your Targets" card |
| Settings | `SettingsActivity` | Home "Set Your Rules" card |
| Interrupt | `InterruptActivity` | Launched by `OverlayService` when limit hit |
| Daily Block | `DailyBlockActivity` | Launched by `OverlayService` when all sessions used |
| History | `HistoryActivity` | From Settings or nav |

---

## 20. Build & Deploy

```powershell
# Build APK
.\android-dev.ps1 -Action build

# Install on connected device
.\android-dev.ps1 -Action install

# Build + install in one step
.\android-dev.ps1 -Action deploy

# View logcat
.\android-dev.ps1 -Action logs
```

Script lives at: `C:\Users\AMUGALI\Downloads\reel-focus-googleai-studio\android-dev.ps1`

---

## 21. Play Store Compliance Checklist

> Last reviewed: 2026-03-16

### C1 · Data Safety Form ⚠️ ACTION REQUIRED (Play Console)
**Status: Must complete before submission.**

Data collected and stored:
- **App info** (`monitored_apps` key): package names of apps the user chose to monitor. Stored locally in SharedPreferences. Never transmitted.
- **App activity** (`session_history` key): timestamps, durations, completion status of each session. Stored locally in SharedPreferences as JSON. Never transmitted.
- **Usage Stats** via `PACKAGE_USAGE_STATS`: only the foreground app package name is read. Not stored long-term; used only to drive the overlay timer.

In Play Console → App content → Data safety:
- Declare **App info and performance · App interactions** (session history).
- Mark **not shared** with third parties, **not sold**.
- Mark **encrypted in transit** as N/A (nothing leaves the device).

---

### C2 · Package Visibility (Android 11+) ✅ Fixed
**Status: `<queries>` block already in `AndroidManifest.xml`.**

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent>
</queries>
```

This allows the app to enumerate launcher apps without `QUERY_ALL_PACKAGES`. Using a `<queries>` launcher-intent filter is the recommended Play policy-safe approach for screen-time apps.

---

### C3 · Privacy Policy ⚠️ ACTION REQUIRED
**Status: URL must be hosted and added to Play Console before submission.**

1. Host a privacy policy at a public URL. Suggested: GitHub Pages at  
   `https://amargm.github.io/reel-focus-android/privacy`  
   (create a `docs/privacy.md` in the repo, enable GitHub Pages on `docs/`).

2. Add the URL to **Play Console → Store listing → Privacy Policy**.

3. In-app link already wired: **Settings → Legal → Privacy Policy** row opens the URL in the system browser (`SettingsActivity.kt`).

**Privacy policy must state:**
- What data is stored (session history, selected app package names).
- That no data leaves the device.
- That no data is shared with or sold to third parties.
- Contact email for data deletion requests.

---

### H1 & H2 · Prominent Disclosure ✅ Fixed
**Status: `AlertDialog` disclosure shown before both Settings redirects.**

Both `PACKAGE_USAGE_STATS` and `SYSTEM_ALERT_WINDOW` now trigger a full-screen `AlertDialog` (per Google's written policy requirement) before the user is sent to Android Settings. The dialog states:
- What data is accessed (`requestOverlayPermission` → screen overlay; `requestUsageStatsPermission` → foreground app name only).
- Why it is needed.
- That no content is read or stored externally.

Implementation: `MainActivity.showProminentDisclosure()` — called from both `requestOverlayPermission()` and `requestUsageStatsPermission()`.

---

### H3 · Foreground Service Type ✅ Already correct
**Status: `android:foregroundServiceType="specialUse"` present in manifest.**

```xml
<service
    android:name=".OverlayService"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Monitors app usage and displays session timer overlay"/>
</service>
```

`FOREGROUND_SERVICE_SPECIAL_USE` permission also declared. Play Console will ask for a **Special Use Case justification form** — fill it in stating: "The app monitors foreground app usage to enforce screen-time limits selected by the user, and displays a floating timer overlay on top of monitored apps."

---

### H4 · Battery Optimization Exemption ✅ Already implemented
**Status: One-time nudge already in `MainActivity.onCreate()`.**

```kotlin
if (!pm.isIgnoringBatteryOptimizations(packageName)) {
    // shown once via battery_opt_nudged pref flag
    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .apply { data = Uri.parse("package:$packageName") })
}
```

- Uses `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission (declared in manifest).
- Shown only once (guarded by `battery_opt_nudged` SharedPreference flag).
- Required Play Console documentation: explain why the 1-second foreground poll loop needs to run unthrottled (screen-time enforcement breaks immediately if the service is killed or throttled).

---

### M1 · Store Metadata ⚠️ ACTION REQUIRED (Play Console)
**Status: Not yet created.**

Recommended values:
- **Category**: Productivity
- **Short description** (80 chars): Beat doom-scrolling with smart session timers and overlay nudges.
- **Full description**: mention the session-based approach, overlay styles, streak tracking — describe TikTok/Instagram/YouTube naturally (not as keywords).
- **Do NOT** keyword-stuff the title or description with app names.
- **Tags** (if available): screen time, digital wellbeing, focus, self-control.

---

### M2 · Store Assets ⚠️ ACTION REQUIRED
**Status: Must be created before submission.**

Required:
| Asset | Size | Note |
|---|---|---|
| App icon | 512×512 PNG | Hi-res version (not the launcher XML) |
| Feature graphic | 1024×500 PNG | Shown at top of store listing |
| Phone screenshots | ≥ 2 | Suggested: home quest board · overlay in use · interrupt screen · settings |

Suggested screenshot sequence:
1. Home screen (quest board, monitoring chip active)
2. An Instagram screen with the teal TEXT overlay visible
3. "Time's Up" interrupt screen
4. History screen showing the 7-day bar chart

---

### M3 · IARC Content Rating ⚠️ ACTION REQUIRED (Play Console)
**Status: Must complete questionnaire before going live.**

Navigate to **Play Console → App content → Content rating → Start questionnaire**.
- Category: **Utility**
- No user-generated content, no violence, no mature themes → expected rating: **Everyone**.

---

### M4 · ActivityManager.getRunningServices() ✅ Fixed
**Status: Replaced with `OverlayService.isRunning` static flag.**

`ActivityManager.getRunningServices()` is deprecated since API 26, unreliable on modern Android, and visible to third parties. Replaced with:
```kotlin
// In OverlayService companion object:
@Volatile var isRunning = false
    private set
// Set to true in onCreate(), false in onDestroy()

// In MainActivity.isOverlayServiceRunning():
return OverlayService.isRunning
```

This is accurate, zero-overhead, and not deprecated.

---

### M5 · Crash Reporting ⚠️ RECOMMENDED before launch
**Status: Not implemented.**

Options (in order of recommendation):
1. **Firebase Crashlytics** — free, integrates with Play Console, real-time alerts.  
   Add to `build.gradle`: `implementation 'com.google.firebase:firebase-crashlytics-ktx'`  
   If added, update the Data Safety form to declare **Crash logs** collected, **shared with Google Firebase**, not sold.
2. **Sentry** — more detail, free tier available, no Google dependency.
3. Minimum alternative: wrap the monitoring coroutine in a try/catch with `Log.e` (already done) and inspect via ADB logcat post-launch.

---

*End of document*
