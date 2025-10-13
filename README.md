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

## Build & Run

Prereqs: Java 8, Maven

1. mvn clean package
2. java -jar target/memorizer-0.1.0-SNAPSHOT-shaded.jar

Note: If Maven attempts to use a local Nexus, update `settings.xml` to point to Maven Central.

## Configuration (application.properties)

Key settings are documented inline. Highlights:

- app.ui.mode=normal — default Stealth mode (normal|mini)
- app.ui.theme=dark — default theme for the Stealth drawer (dark|light)
- app.study.daily-target=50 — progress target
- app.deck.filter=all — deck filter (all or deck id)
- Scheduling: app.study.batch-size, min/max intervals, snooze options
- Geometry: banner heights/width fractions for Normal/Mini

## Usage

- Stealth shortcuts: SPACE/ENTER flip, 1/2/3/4 rating, ESC hide, M mode toggle, T theme toggle, F8 show/hide
- Main window → Data menu: New Deck, New Entry (adds note + card)
- Main window → View → Deck: select All Decks or a specific deck

## PWA Parity

- Decks and notes are shared via the same DB. The deck filter is persisted (app.deck.filter) so Desktop and PWA stay in sync conceptually.
- Challenge batches can be appended from the tray; per‑deck challenge tracking can be added later.

## Troubleshooting

- If the Stealth banner shows a gap near the taskbar, confirm overlay-taskbar=false and typical DPI (100–150%).
- If Today shows 54/50, this is capped visually to 50/50; the bar never exceeds 100%.
- If Examples don’t appear in Normal, ensure flip state is All (third click) and there is example content.

## Contributing

- Style: Java 8, SLF4J logging, 4‑space indentation
- Tests: JUnit 5 patterns can be added under src/test/java
