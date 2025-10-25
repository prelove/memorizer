# Memorizer (Desktop + Stealth Banner)

Memorizer is a distraction‑minimized spaced‑repetition helper with a taskbar‑like Stealth banner (Normal/Mini) for quick reviews and a Main window for dashboards, planning, and manual data entry.

## Features

- Stealth Banner (Normal/Mini) with Dark/Light theme
- Front/Back/Reading/Pos/Examples flip cycle (Front → Back → All → Front)
- Four rating buttons (Again/Hard/Good/Easy) with clear tints in Dark and pastels in Light
- Progress bar with Today x/target overlay (capped to target)
- Deck filter (All Decks or specific deck id) shared with PWA via DB/Config
- Manual data entry on Desktop (New Deck, New Entry) in addition to Excel import
- Tray controls and serialized reminders (no overlapping banners)
- Dashboard with statistics charts and task progress indicator
- Application icons for all windows
- Improved window sizing and layout

## Build & Run

Prereqs: Java 8, Maven

1. mvn clean package
2. java -jar target/memorizer-0.3.0-shaded.jar

Note: If Maven attempts to use a local Nexus, update `settings.xml` to point to Maven Central.

## Configuration (application.properties)

Key settings are documented inline. Highlights:

- app.ui.mode=normal — default Stealth mode (normal|mini)
- app.ui.theme=dark — default theme for the Stealth drawer (dark|light)
- app.study.daily-target=50 — progress target
- app.deck.filter=all — deck filter (all or deck id)
- Scheduling: app.study.batch-size, min/max intervals, snooze options
- Geometry: banner heights/width fractions for Normal/Mini
- Window settings: app.window.mini.width-fraction, app.window.stealth.width-fraction

## Usage

- Stealth shortcuts: SPACE/ENTER flip, 1/2/3/4 rating, ESC hide, M mode toggle, T theme toggle, F8 show/hide
- Main window → Data menu: New Deck, New Entry (adds note + card)
- Main window → View → Deck: select All Decks or a specific deck
- Main window → Data → Sync Server: Enable/disable sync server, pair mobile device
- Dashboard: View statistics charts and today's task progress

## Dashboard Features

- Summary statistics: Due cards, New cards, Total cards, Total notes, Today's reviews, Plan progress
- Charts:
  - Daily Reviews (Last 7 Days) - Bar chart showing review activity
  - Rating Distribution - Bar chart showing rating frequency
  - Card Status Distribution - Bar chart showing card statuses
- Task Progress Indicator: Visual indicator showing today's task progress with color coding
  - Green: Completed tasks
  - Orange: Pending tasks
  - Gray: Not started tasks
  - For large plans (>60), switches to a compact progress bar with Done/Total label

## PWA Parity

- Decks and notes are shared via the same DB. The deck filter is persisted (app.deck.filter) so Desktop and PWA stay in sync conceptually.
- Challenge batches can be appended from the tray; per‑deck challenge tracking can be added later.

## Troubleshooting

- If the Stealth banner shows a gap near the taskbar, confirm overlay-taskbar=false and typical DPI (100–150%).
- If Today shows 54/50, this is capped visually to 50/50; the bar never exceeds 100%.
- If Examples don’t appear in Normal, ensure flip state is All (third click) and there is example content.
- If charts don't display data, ensure there is review history in the database.

## Contributing

- Style: Java 8, SLF4J logging, 4‑space indentation
- Tests: JUnit 5 patterns can be added under src/test/java

## Goals & Roadmap

### Goals
- Deliver a fast, low-friction Stealth review banner with reliable positioning.
- Provide a simple Main window for overview and manual data entry (decks, notes).
- Keep Desktop and PWA aligned via shared DB, including an optional deck filter.
- Provide visual feedback through charts and progress indicators.

### Completed
- Stealth UI (Normal/Mini), Dark/Light themes, strict heights, progress overlay.
- Dark rating buttons with filled tints; Light pastel styles; proper alignment.
- Flip-cycle logic: Front → Back → Front+Back+Reading/Pos+Examples → Front.
- Examples in Normal (multiline scroller) and Mini (single-line; marquee).
- Dark popups (Edit/Add) with visible input fields and focus styling.
- Main/Study windows: top alignment, vertical scrolling, size to OS work area.
- Deck filter (View → Deck) with PlanService filtering; manual New Deck/New Entry.
- User Manual in Help menu; status-bar notices on create/save.
- Dashboard with statistics charts and task progress indicator.
- Application icons for all windows.
- Improved window sizing and layout.
- Sync server controls moved to Data menu.

### Pending / Next
- Stealth layout polish (baseline alignment, clamps) per checkpoint notes
- Optional: Exam hover/pressed states via CSS tokens
- Maven Central build reliability (avoid global Nexus mirrors) / CI
- Add tests (PlanService deck filter, Stealth flip states, repository paths)
- Persist per-deck challenge last-size hint
