# Changelog

All notable changes to this project will be documented in this file.

## [0.1.0-SNAPSHOT] - Stage A (UI & Scheduler Refinement)

### Added
- Scheduler serialization to avoid overlapping banner sessions; ticks defer by `app.study.defer-when-busy-minutes`.
- Hidden-from-taskbar owner window; stealth banner no longer creates a taskbar/dock icon when `app.window.hide-from-taskbar=true`.
- Taskbar-aware positioning with optional overlay via `app.window.overlay-taskbar` and fallback snapping above the taskbar.
- Dual‑mode stealth UI (Normal/Mini) with keyboard toggle `M` and tray menu “Mode → Normal/Mini”.
- Rich `StudyService.CardView` assembler: joins Note (+ deck name), adds `reading`, `pos`, `examples`, `tags`, `deckName`.
- Examples roller in stealth banner with autoroll and hover‑to‑pause; respects `app.ui.examples.*` config.
- Today progress: `Today x/y` label and thin progress bar (target via `app.study.daily-target`).
- Dashboard: task progress indicator (rectangles for small plans; auto-switch to progress bar for large plans) with tooltips.
- Exam: richer view (Reading/POS/Examples), Back-first toggle, Repeat-wrongs pass, inline progress bar, OK/NG counters, percentage score, large Show Answer and colorized Correct/Wrong buttons, Compact mode.
- Exam: CSV export of results and Edit dialog integration.
- Manage Decks window (Data → Manage Decks…) for list/rename/delete.
- Multi-monitor and taskbar-edge aware stealth positioning.
- ESC to hide with optional snooze of current card (`app.study.snooze-on-hide-enabled`, `app.study.snooze-on-hide-minutes`).
- Preference persistence for `app.ui.mode` to `data/prefs.properties` with `Config` overlay on startup.

### Changed
- Label layout made predictable with ellipsis, bright text on dark background; examples area can shrink to avoid pushing main text.
- Tray “Show Stealth Now” focuses ongoing session instead of spawning new batches.

### Configuration
Single source of truth in `src/main/resources/application.properties`:
- UI: `app.ui.mode`, `app.window.*`, `app.ui.examples.*`
- Scheduler: `app.study.batch-size`, `app.study.min/max-interval-minutes`, `app.study.defer-when-busy-minutes`, `app.study.force-show-when-empty`, `app.study.snooze-*`
- DB/H2 console: `app.db.*`, `app.h2.console.*`

### Build
- Java 8 + JavaFX recommended. For environments without bundled JavaFX, an optional Maven profile `openjfx` is provided:
  - `mvn -Popenjfx -Djavafx.platform=win clean package` (use `mac`/`linux` accordingly)

### Validation
- `mvn verify` (or `mvn clean package` during iteration) and launch `java -jar target/memorizer-0.1.0-SNAPSHOT-shaded.jar`.
- Quick test config: set `app.study.min-interval-minutes=1` and `app.study.max-interval-minutes=2`.
- Added Maven Central repositories in pom.xml to help avoid local mirror issues (may still be overridden by global settings.xml).
