# ReelFocus — Functional Flow Diagram

> **How to read this diagram**
> - **Solid arrows** → normal runtime flow
> - **Dashed arrows** → background / persistence calls
> - **🟡 Gap nodes** → open functional questions / potential changes needed

```mermaid
flowchart TD

    %% ──────────────────────────────────────────
    %% USER SETUP
    %% ──────────────────────────────────────────
    subgraph SETUP["📱 User Setup — One-time"]
        A1([Open ReelFocus]) --> A2{All permissions\ngranted?}
        A2 -- No --> A3["Grant Required\nPermissions:\nDraw Over Apps\nAccessibility Service\nUsage Stats"]
        A3 --> A2
        A2 -- Yes --> A4["Select Apps\nto Monitor"]
        A4 --> A5["Set Per-App\nTime Limit\ndefault 20 min"]
        A5 --> A6["Settings\nMax Daily Sessions\nSession Gap Duration\nOverlay Position/Size"]
        A6 --> A7([▶ Start Monitoring])
    end

    %% ──────────────────────────────────────────
    %% OVERLAY SERVICE — OPERATIONAL CORE
    %% ──────────────────────────────────────────
    subgraph SVC["⚙️ OverlayService — 1-second Background Poll"]
        B1["Load Config snapshot\n+ SessionState from prefs"] --> B2["checkAndResetIfNewDay\nfull state reset at midnight"]
        B2 --> B3{Monitored app\nin foreground?}
        B3 -- No --> B4["Pause timer\nHide overlay\nisActive = false"]
        B4 -.->|1 s tick| B3

        B3 -- Yes --> B5{"currentSession\n> maxSessions?"}
        B5 -- Yes --> BLK1

        B5 -- No --> B6{"isOnBreak\nAND break < 10 min?"}
        B6 -- Yes, break ongoing --> B4

        B6 -- No --> B7{isActive?}
        B7 -- "No — gap >= resetGap\nAND sessionCompleted" --> B8["currentSession++\nReset timer to 0\nstartNewSession()"]
        B7 -- "No — quick return\ngap < resetGap" --> B9["Resume\nisActive = true"]
        B7 -- Yes --> B10["Update limit if app\nswitched during session"]
        B8 --> B10
        B9 --> B10

        B10 --> B11["Show countdown\noverlay M:SS"]
        B11 --> B12["secondsElapsed++\nauto-save every 5 s"]
        B12 --> B13{"Limit reached?\ntime >= limitValue x 60 s\n(or +5 min if extension)"}
        B13 -- No --> B3
        B13 -- Yes --> B14["sessionCompleted = true\nRecord to History\nHide overlay\nisActive = false"]
    end

    %% ──────────────────────────────────────────
    %% INTERRUPT SCREEN
    %% ──────────────────────────────────────────
    subgraph INT["🛑 Interrupt Screen — User Decision Point"]
        C1{"dailyLimitReached?\ncurrentSession > maxSessions"}
        C2{"extensionUsed\nAND extension complete?"}

        C3["Session Limit Reached\nSession N of M\n─────────────────"]
        C4["Extension Complete\nSession N of M\n─────────────────"]
        C5["Daily Limit Reached\nAll sessions used today\n─────────────────"]

        C1 -- Yes --> C5
        C1 -- No --> C2
        C2 -- Yes --> C4
        C2 -- No --> C3

        C3 --> R1(["Extend 5 min\none-time per session"])
        C3 --> R2(["Next Session"])
        C3 --> R3(["Done"])
        C4 --> R4(["Next Session"])
        C4 --> R5(["Take 10-min Break"])
        C5 --> R6(["Done"])
    end

    %% ──────────────────────────────────────────
    %% DAILY BLOCK SCREEN
    %% ──────────────────────────────────────────
    subgraph BLK["🚫 Daily Block Screen"]
        BLK1["All sessions exhausted\nBlocked for today\nResets at midnight"]
        BLK1 --> BLK2(["Go Home"])
        BLK1 --> BLK3(["Open Settings"])
    end

    %% ──────────────────────────────────────────
    %% HISTORY
    %% ──────────────────────────────────────────
    subgraph HIST["📊 History"]
        H1["HistoryManager\n90-day retention\nGson + SharedPreferences"]
        H2["History Screen\nWeekly bar chart\nSession list"]
        H1 --> H2
    end

    %% ──────────────────────────────────────────
    %% DETECTION STACK
    %% ──────────────────────────────────────────
    subgraph DET["🔍 Detection Stack (per tick)"]
        D1["DetectionManager\ngetActiveReelApp()"]
        D2["ReelDetectionAccessibilityService\ngetLatestDetection()"]
        D3["AppUsageMonitor\nqueryEvents() — sliding 10 s window"]
        D1 --> D2
        D2 -- "Result <= 2 s old" --> D1
        D2 -- "No result / stale" --> D3
        D3 -- Foreground package --> D1
    end

    %% ──────────────────────────────────────────
    %% TERMINAL STATE
    %% ──────────────────────────────────────────
    STOP(["⏹ Service Stopped"])

    %% ──────────────────────────────────────────
    %% INTER-SUBGRAPH CONNECTIONS
    %% ──────────────────────────────────────────
    A7 --> B1
    B3 -.->|getActiveReelApp| DET
    B14 --> C1
    B14 -.->|recordSession| H1

    R1 -->|"ACTION_EXTEND\n+5 min, limitReached = false"| B3
    R2 -->|"ACTION_NEXT_SESSION\ncurrentSession++, timer = 0"| B3
    R3 -->|ACTION_STOP| STOP
    R4 -->|ACTION_NEXT_SESSION| B3
    R5 -->|"ACTION_TAKE_BREAK\nisOnBreak = true"| B3
    R6 -->|ACTION_STOP| STOP
    BLK2 --> STOP
    BLK3 --> A6

    STOP -.->|"Re-enable via\nMainActivity toggle"| A7

    %% ──────────────────────────────────────────
    %% FUNCTIONAL GAP NODES
    %% ──────────────────────────────────────────
    G1["🟡 GAP: Partial sessions\nnot saved to history\nif user exits before limit"]
    G2["🟡 GAP: Timer is cumulative\nacross ALL monitored apps\nin one session — not per-app"]
    G3["🟡 GAP: Next Session action\nhas NO gap enforcement —\ngap only gates re-entry\nafter service goes idle"]
    G4["🟡 GAP: Config snapshot loaded\nonce per startMonitoring call\nSettings changes need\nservice restart to take effect"]
    G5["🟡 GAP: YouTube default\nmonitors ENTIRE app\nnot just Shorts"]

    B14 -. relates .-> G1
    B10 -. relates .-> G2
    R2 -. relates .-> G3
    B1 -. relates .-> G4
    B1 -. relates .-> G5

    %% ──────────────────────────────────────────
    %% STYLES
    %% ──────────────────────────────────────────
    classDef gap fill:#fff3cd,stroke:#ffc107,color:#333
    classDef terminal fill:#e8f5e9,stroke:#4caf50
    classDef screen fill:#e3f2fd,stroke:#1976d2
    classDef action fill:#f3e5f5,stroke:#7b1fa2

    class G1,G2,G3,G4,G5 gap
    class STOP,A7 terminal
    class C3,C4,C5,BLK1 screen
    class R1,R2,R3,R4,R5,R6,BLK2,BLK3 action
```

---

## Functional Gaps Summary

| # | Gap | Impact | Suggested Fix |
|---|-----|--------|---------------|
| G1 | Partial sessions (user exits before limit) are **not recorded** in history | History understates actual usage | Record session on `handleMonitoredAppInactive` when `secondsElapsed > 0` with `completed = false` |
| G2 | Timer is **cumulative across all monitored apps** in a session, not per-app | Opening Instagram for 10 min then YouTube for 10 min counts as 20 min total — may surprise users | Clarify in UI, or make timer per-app and track separately |
| G3 | "Next Session" button has **no gap enforcement** — user can tap it immediately | Gap rule is only enforced on re-entry after the service goes idle, not on the interrupt screen | Either disable the button for `sessionResetGapMinutes` minutes, or start a mandatory cooldown |
| G4 | Config loaded **once per `startMonitoring()` call** — settings changes while service runs are ignored | Changing app list or time limits requires stopping and restarting the service | Reload config each tick (lightweight), or send a `ACTION_RELOAD_CONFIG` intent to the service |
| G5 | YouTube default entry monitors the **entire YouTube app**, not just YouTube Shorts | Penalises users watching regular YouTube | Use Accessibility Service's `ReelPatternMatcher` to detect Shorts-specific UI; already wired in via BUG-008 fix |
