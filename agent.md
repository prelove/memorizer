# Memorizer — Agent Task Plan (Stage A UI & Scheduler Refinement)

This file is the **authoritative task list and spec** for improving the stealth banner window and scheduler. It is written for an assistant agent (e.g., Codex) to execute **step by step**. Please **follow the order** and check off acceptance criteria before moving to the next task.

---

## 0) Context & Environment

- **Language/Stack**: Java 8 + JavaFX + Maven
- **Packaging**: fat-jar
- **OS targets**: Windows (primary), macOS (secondary), Linux (best-effort)
- **DB**: H2 1.4.x (embedded file), Flyway for migrations
- **Project goal**: Distraction-minimized micro-learning with a stealth banner (two modes: Normal/Mini) + SRS scheduling.
- **Current status**: App runs; DB + Flyway + H2 console OK; scheduler shows a banner; batch learning implemented; basic tray controls implemented.
- **Pain points to fix immediately**:
  - New ticks can overlap while the banner is still showing.
  - Banner appears on the taskbar (should be hidden).
  - Banner placement/width alignment is weak (large empty space; text off-center).
  - Normal/Mini mode skeleton needs to be solidified.
  - Properties must be consolidated to a single clear version.

---

## 1) Repository Layout (reference)

```
/src/main/java
  └─ com/memorizer
       ├─ app
       │   ├─ MainApp.java
       │   ├─ AppContext.java
       │   ├─ Scheduler.java
       │   ├─ TrayManager.java
       │   └─ TrayActions.java
       ├─ db
       │   ├─ Database.java, H2ConsoleServer.java
       │   ├─ CardRepository.java, NoteRepository.java, StatsRepository.java
       │   └─ migration (Flyway sql)
       ├─ model (Card, Note, Rating, etc.)
       ├─ service
       │   └─ StudyService.java (contains static DTO CardView)
       ├─ ui
       │   ├─ StealthStage.java (banner)
       │   └─ MainStage.java
       └─ util
           └─ ScreenUtil.java
/resources
  ├─ application.properties
  └─ (optional) stealth.css
```

---

## 2) Configuration — **Single Source of Truth**

Replace `application.properties` with the following keys and comments (keep values as-is unless told otherwise):

```properties
# ======================
# UI mode & size
# ======================
app.ui.mode=normal                     # normal | mini

# Normal mode geometry
app.window.stealth.height=64           # banner height (px)
app.window.stealth.width-fraction=0.98 # width as fraction of visual screen width (0~1)

# Mini mode geometry
app.window.mini.height=40
app.window.mini.width-fraction=0.50

# Visibility
app.window.opacity=0.90                # window opacity (0~1)
app.window.hide-from-taskbar=true      # hide banner from taskbar via invisible owner
app.window.overlay-taskbar=false       # try overlaying taskbar region (fallback: snap to top edge)

# Examples (S3 will use these)
app.ui.examples.autoroll=true
app.ui.examples.roll-interval-ms=2200
app.ui.examples.max-lines=3

# Today goal (for right-side progress)
app.study.daily-target=50

# ======================
# Scheduler (micro-reminders)
# ======================
app.study.batch-size=3                 # N cards per banner session
app.study.min-interval-minutes=20      # next tick random delay: min minutes
app.study.max-interval-minutes=60      # next tick random delay: max minutes
app.study.defer-when-busy-minutes=3    # if banner active, defer next tick by N minutes

# Queue & snooze
app.study.force-show-when-empty=true   # allow fallback when no due/new
app.study.snooze-on-hide-enabled=true  # hide (X/ESC) pushes current card
app.study.snooze-on-hide-minutes=10    # push minutes on hide
app.study.snooze-minutes=10            # tray Snooze (global tick defer)

# ======================
# Database
# ======================
app.db.type=h2
app.db.path=./data/memo

# H2 console
app.h2.console.enabled=true
app.h2.console.port=8082
app.h2.console.allow-others=false
```

**Acceptance**: app reads only these keys; no duplicates remain.

---

## 3) TASK S1 — Stabilize core behavior (must pass before UI polish)

Status: DONE (Stage A)
Notes:
- Ticks defer while banner active; single banner enforced.
- Invisible owner hides banner from taskbar; position logic implemented.

### S1.1 Serialize scheduler when banner active
**Goal**: Never spawn a second banner during an active session. If a tick occurs while the banner is showing/learning, **defer** by `app.study.defer-when-busy-minutes`.

**Files**: `StealthStage.java`, `Scheduler.java`, `TrayActions.java`

**Steps**:
1. In `StealthStage`, expose a session flag:
   - Field: `private volatile boolean sessionActive;`
   - Methods: `public boolean isSessionActive()`; set `true` in `startBatch()`, set `false` in `endBatchAndHide()`.
2. In `Scheduler.tick()`:
   - If `stealth.isSessionActive()` or `stealth.isShowing()`, schedule `tick` again after `defer-when-busy-minutes` and **return**.
3. In `TrayActions.showStealthNow(...)`:
   - If `stealth.isSessionActive()`, just `showAndFocus()`; **do not** start a new batch.

**Acceptance**:
- With tick set to 1–2 minutes for testing, if the banner is left open, logs show `Busy... Defer...`, and only one banner is visible.


### S1.2 Hide banner from taskbar
**Goal**: Stealth window should not create a taskbar/dock icon.

**Files**: `MainApp.java`, `AppContext.java`, `StealthStage.java`

**Steps**:
1. `MainApp` creates an invisible owner:
   - `Stage toolOwner = new Stage(StageStyle.UTILITY);`
   - `toolOwner.setOpacity(0); setWidth(1); setHeight(1); setX(-10000); setY(-10000); setIconified(true); show();`
   - Expose via `AppContext.setOwner(toolOwner)`.
2. In `StealthStage` constructor, if `app.window.hide-from-taskbar=true`, call `initOwner(AppContext.getOwner())` before `initStyle(...)`.
3. Keep `UNDECORATED` and `setAlwaysOnTop(true)`.

**Acceptance**:
- On Windows/macOS: banner appears without adding a taskbar/dock icon.


### S1.3 Position near or on taskbar
**Goal**: Default snap near taskbar; if `overlay-taskbar=true`, try to occupy the taskbar rectangle.

**Files**: `util/ScreenUtil.java`, `ui/StealthStage.java`

**Steps**:
1. `ScreenUtil.taskbarRect()` using AWT `Toolkit.getScreenInsets` to compute taskbar rectangle (already drafted).
2. In `StealthStage.applyPositionForMode()`:
   - If `overlay-taskbar=true`: set stage to taskbar rectangle (`x/y/w/h = taskbar`). Height = `min(taskbar.h, bannerHeight)`.
   - Else: compute visual bounds and place banner centered horizontally and **touching** the taskbar top edge (`visualBounds.maxY - height - 2`).

**Acceptance**:
- `overlay-taskbar=false`: banner is just above taskbar, centered; no large vertical gaps.
- `overlay-taskbar=true`: banner sits on the taskbar where OS allows; if not allowed, fallback still looks correct.


### S1.4 Make text readable & layout predictable (baseline)
**Goal**: Ensure main text is visible, not squashed to the left, with ellipsis when space is small.

**Files**: `StealthStage.java`

**Steps**:
1. For `frontLabel`, `backLabel`, `readingLabel`:
   - `setMaxWidth(Double.MAX_VALUE)` + `HBox.setHgrow(label, Priority.ALWAYS or SOMETIMES)`
   - `setTextOverrun(OverrunStyle.ELLIPSIS)`
   - Bright text colors on dark bg.
2. Normal mode:
   - Make main VBox (`front/back/reading`) `HBox.setHgrow(main, Priority.ALWAYS)`
   - Examples area can shrink (`HBox.setHgrow(examples, Priority.SOMETIMES)`).
3. Mini mode:
   - Two elastic spacers left/right of `frontLabel` to center it; ensure `frontLabel` has `setMaxWidth(Double.MAX_VALUE)` and `HGrow` to allow centering.

**Acceptance**:
- Normal: main text takes most of the middle area; examples never push text off-screen; text uses ellipsis instead of disappearing.
- Mini: main text is centered; buttons align on the right; no huge empty gaps.


### S1.5 Bind StudyService and null-safety
**Files**: `StealthStage.java`

**Steps**:
- Provide `bindStudy(StudyService)` and null-check in `rateAndHide(...)` / `hideWithSnooze()`.

**Acceptance**:
- No NPE during rating or hide.


---

## 4) TASK S2 — Dual-mode UI skeleton (Normal/Mini)

Status: DONE (Stage A)
Notes:
- `UIMode` toggles with keyboard `M` and via tray “Mode → Normal/Mini”.

**Goal**: Two switchable layouts; keyboard `M` toggles; tray menu has “Mode → Normal/Mini”.

**Files**: `StealthStage.java`, `TrayManager.java`

**Steps**:
1. In `StealthStage`:
   - Enum `UIMode { NORMAL, MINI }`, initial from `app.ui.mode`.
   - `buildNormalRoot()` and `buildMiniRoot()` returning `Pane` bars.
   - `applyMode(UIMode)` swaps root center; `toggleMode()` maps to `M`.
2. Tray menu:
   - Menu “Mode” with two `CheckboxMenuItem`s; switch calls `stealthStage.setUIMode(...)` (mutually exclusive).

**Acceptance**:
- Press `M` switches modes; tray toggles mirror the change (no persistence required yet).


---

## 5) TASK S3 — Content enrichment (DTO + examples roller + today progress)

Status: DONE (Stage A)
Notes:
- `CardView` assembler adds reading/pos/examples/tags/deck name.
- Examples roller added with autoroll + hover pause; today progress wired.

**Goal**: Fill the banner with richer info but still subtle.

**Files**: `StudyService.java`, `StealthStage.java`, optional `stealth.css`

**Steps**:
1. Extend `StudyService.CardView` (already public static) with:
   - `public String reading, pos, deckName;`
   - `public List<String> examples, tags;`
2. Build a single **assembler** method inside `StudyService` that, for any `Card`, joins `Note` (+ optional Deck) and splits `examples/tags` into lists.
3. In `StealthStage.showCardView(...)`: render reading/pos; call `setExamples(list)`.
4. Implement simple **Examples Roller** (JavaFX `Timeline`) that vertically cycles 1~N lines every `roll-interval-ms`, pause on mouse hover.
5. Today progress: `StatsRepository.todayReviews` vs `app.study.daily-target`.

**Acceptance**:
- Normal shows reading/pos/examples and a thin progress bar + `Today x/y`.
- Mini hides reading/pos/examples, keeps `Today x/y` text.


---

## 6) TASK S4 — Preferences persistence (optional in A)

Status: DONE (Stage A)
Notes:
- `app.ui.mode` persisted to `data/prefs.properties`; loaded on startup; no duplicate keys.

**Goal**: Persist UI mode and key preferences across sessions.

**Files**: `Config` or a small `PreferencesService`

**Steps**:
- On mode switch (`Normal`/`Mini`) update `app.ui.mode` value in properties (or a dedicated prefs store).
- Reload on startup; no duplicate keys.

**Acceptance**:
- Restarting the app preserves the chosen UI mode.


---

## 7) TASK B (Next stage after A) — Study modes & plan table

**Scope (design-ready, not to implement in S1–S4)**:
- **Fixed Mode**: daily task pool `study_plan` = DUE + LEECH + NEW(≤limit) + carryover.
- **Challenge Mode**: after clearing fixed tasks, prompt to add a batch of NEW (configurable size).
- Add Flyway `V2__study_plan.sql` with columns: `plan_date, card_id, deck_id, kind(0=DUE,1=LEECH,2=NEW,3=CHALLENGE), status(0=PENDING,1=DONE,2=ROLLED,3=SKIPPED), order_no, created_at, updated_at` + indexes.
- Implement `PlanService` with: `buildToday()`, `appendChallengeBatch()`, `nextFromPlan()`, `markDone()`.

**Acceptance** (later):
- Daily pool is deterministic; unfinished items carry over; challenge batches append and are tracked.


---

## 8) Coding Standards

- Java 8 compatible (no Java 9+ APIs).
- English comments; concise and practical.
- UI code: avoid over-abstraction; small helpers (e.g., `ScreenUtil`) are fine.
- Avoid NPE: guard `study` before use in banner actions.
- Keep imports explicit (`javafx.*` where required).


---

## 9) How to Test

1. **Config**: set `app.study.min/max-interval` to `1/2` minutes for fast iteration.
2. Run `MainApp`. Insert a few notes/cards in H2 console if needed.
3. Verify:
   - Only one banner at a time (leave it open; check logs say defer).
   - No taskbar icon for the banner.
   - Normal/Mini toggling via `M` and tray menu.
   - Layout: main text visible; examples don’t push text off-screen; mini text centered.
   - Snooze-on-hide works; batch-size respected (e.g., 3 cards per session).


---

## 10) Known Fragilities / Edge Cases

- Some Linux WMs may still show a taskbar entry even with owner set; acceptable for now.
- Taskbar overlay can be blocked by OS; fallback placement must look good.
- Multi-monitor: initial implementation is primary screen only; future task to support active screen under mouse.
- If color/contrast varies by platform, move to a CSS and tune per platform in S3.


---

## 11) Deliverables per Task

- **S1**: PR with Scheduler serialization, hidden taskbar owner, positioning, readable labels, and properties clean-up.
- **S2**: PR with dual-mode UI skeleton + tray toggle.
- **S3**: PR with CardView assembler, examples roller, today progress wired.
- **S4**: PR with preference persistence for UI mode.
- Update `CHANGELOG.md` and this `agent.md` (check off tasks and note decisions).
