# Memorizer — Agent Plan (Current Status + Next Tasks)

This document reflects the current project state and the focused next tasks. Completed items are recorded for reference and will not be re‑done. Only unfinished, actionable work remains in the Next Tasks section.

---

## 0) Context & Environment

- **Language/Stack**: Java 8 + JavaFX + Maven
- **Packaging**: fat-jar
- **OS targets**: Windows (primary), macOS (secondary), Linux (best-effort)
- **DB**: H2 1.4.x (embedded file), Flyway for migrations
- **Project goal**: Distraction‑minimized micro‑learning with a Stealth banner (Normal/Mini) + SRS planning + PWA companion.
- **Current status**: Desktop app is stable; DB + Flyway + H2 console OK; serialized scheduler; Stealth Drawer Normal/Mini implemented; pairing server + APIs operational; PWA project present and served when built.

---

## Context Budget Rules
- If context remaining < 20% OR diff > 120 lines:
  -> STOP and run CHECKPOINT PROTOCOL v1 (summary + touched files + unified diff + next steps).
- Always implement per-file with focus; do not modify multiple files in one run.
- Each run must be commit-safe: code compiles and tests pass where applicable.
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

## 2) Configuration — Single Source of Truth (Completed)

- Runtime preferences persist to `data/prefs.properties` (e.g., `app.ui.mode`, theme, scheduler settings).
- `src/main/resources/application.properties` only carries DB/H2 console keys.
- No duplicate config keys remain.

---

## 3) S1 — Core Behavior (Completed)

- Ticks defer while banner is active; single banner enforced.
- Invisible owner hides banner from taskbar; always‑on‑top; transparent scene.
- Placement: snaps relative to taskbar; overlay option supported.

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

## 4) S2 — Dual‑mode UI (Completed)

- Normal/Mini layouts implemented with `M` toggle and tray menu.
- Separate answer clusters (Normal labeled, Mini numeric) without reparenting.

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

## 5) S3 — Content Enrichment (Completed)

- `CardView` includes reading/pos/examples/tags/deck; examples roller; today progress bar with overlay text.

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

## 6) S4 — Preferences Persistence (Completed)

- `app.ui.mode` and theme persist; reloaded on startup.

**Goal**: Persist UI mode and key preferences across sessions.

**Files**: `Config` or a small `PreferencesService`

**Steps**:
- On mode switch (`Normal`/`Mini`) update `app.ui.mode` value in properties (or a dedicated prefs store).
- Reload on startup; no duplicate keys.

**Acceptance**:
- Restarting the app preserves the chosen UI mode.


---

## 7) Study Plan (Completed)

- Migration `V2__study_plan.sql` and `PlanService` implemented (buildToday, appendChallengeBatch, nextFromPlan, markDone, counts, listToday).
- Deck filter integrated in queries; carryover and challenge kinds supported.


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

## 11) Next Tasks (Unfinished Only)

- Desktop API parity
  - [Done] `POST /api/cards/delete` added to delete by `cardId` with dependent rows cleanup.
- Study UX options
  - Preference to hide POS pre‑flip (toggle in Preferences; default current behavior maintained).
- Pairing UX polish
  - Add illustrated steps/images for iOS/Android on `/pair` and in desktop “Connect” panel to guide TLS/permissions.
- Build/CI hygiene
  - Verify `settings.xml` uses Maven Central in CI; remove reliance on local Nexus mirrors; document CI commands in README.
- Tests
  - Add JUnit 5 tests: PlanService deck filter and counts; Stealth flip‑cycle states (Normal 0/1/2; Mini 0/1/2/3); repository insert/update paths; minimal API endpoint tests where feasible.
- Optional/Stretch
  - Persist per‑deck “challenge last size” hint (e.g., `app.study.challenge.<deckId>.lastSize`).

All prior Stage A/B items are complete and should not be re‑implemented.
