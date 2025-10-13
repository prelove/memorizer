# Breakpoints — Project Checkpoint (Next-Session Starter)

This document summarizes what has been implemented, what remains unfinished, and the next actionable items. It is intended for Codex to quickly regain full context at the start of a new session.

## Context Snapshot

- Platform: Java 8 + JavaFX 8 (desktop), embedded H2 + Flyway.
- Core UX: Stealth Drawer (Normal/Mini) + Main window (+ Study window) + Tray controls.
- PWA exists with deck management; desktop now has manual deck/entry creation and a deck filter.

## Completed (Key Outcomes)

- Stealth Drawer (Normal & Mini)
  - One active stylesheet; Dark/Light tokens; transparent scroll viewports; no white strips.
  - Strict heights (Mini 44, Normal 76) with DPI awareness; paddings rebalanced; no clipping.
  - Progress bar overlay with Today x/target (text capped to target); visible in both modes.
  - Rating buttons: full-body tints in Dark, pastel fills in Light; 32 px; proper alignment.
  - Flip cycle (Front→Back→All→Front); Examples multiline in Normal, single line in Mini (marquee).
  - Dark popups (Edit/Add) with clear input backgrounds and focus ring; rounded corners.
  - Bottom edge flush with taskbar (drawer) — removed bottom padding in placement logic.

- Main & Study Windows
  - Content aligned to top; vertical ScrollPane to avoid clipping.
  - Set stage bounds to Screen.getPrimary().getVisualBounds() on show (with DPI rounding guard) — flush with taskbar.
  - Help menu: in-app User Manual dialog (also persisted under `src/main/resources/USER_MANUAL.txt`).

- Decks & Data Entry (Desktop)
  - Data → New Deck… (DeckRepository.getOrCreate) and Data → New Entry… (Note + Card creation).
  - View → Deck filter: persists `app.deck.filter` ("all" or deck id); PlanService queries respect filter.
  - In-window notices (status bar) for creation success.

- Configuration & Docs
  - application.properties: documented `app.ui.theme`, `app.deck.filter`, and key UI/scheduling settings.
  - README.md: updated with features, build/run, config highlights, usage, PWA parity, troubleshooting.

## Unfinished / Known Gaps

- Multi-monitor support and non-bottom taskbar positions (top/left/right) for Stealth placement.
- Optional Stealth UI: small deck badge and quick deck-cycle hotkey.
- Manage Decks window (list/rename/delete) for parity with PWA.
- Tray balloons for create/save actions (currently in-window notices only).
- Maven repo: remove reliance on localhost Nexus; ensure Central is used in settings.xml/CI.
- Tests: add targeted unit tests (PlanService deck filter; Stealth flip-cycle state; NoteRepository insert/update paths).
- Optional: remember per-deck challenge last size (e.g., `app.study.challenge.<deckId>.lastSize`).

## Recent Fixes Worth Remembering

- Stealth theme swap text visibility fixed with global cascades.
- Progress text render: Normal mode shows overlay on bar (right-aligned, contained).
- Today count capped at target (no 54/50 display).

## Next-Session Plan (Prioritized)

1) Placement Robustness
- Implement multi-monitor & taskbar-edge detection (use AWT insets per screen; fall back gracefully).

2) Deck Management & UX Polish
- Add "Manage Decks…" window (list/rename/delete).
- Add optional deck badge + deck-cycle hotkey in Stealth.

3) Notifications & Feedback
- Add tray balloons for Deck/Entry creation; keep in-window notice.

4) Build & Test
- Update Maven settings to use Maven Central for CI.
- Add tests for PlanService deck filter & Stealth flip-cycle (state=0/1/2).

5) Stretch
- Persist per-deck challenge last-size hint for convenient repeats.

## Files to Read First (for Codex)

- UI & Behavior:
  - `src/main/java/com/memorizer/ui/StealthStage.java` (layout, flip, progress overlay)
  - `src/main/resources/css/drawer-dark.css`, `src/main/resources/css/drawer-light.css` (tokens + controls)
- Planning & Deck Filter:
  - `src/main/java/com/memorizer/service/PlanService.java` (deckFilterWhereClause/bindDeckFilterIfAny)
- Desktop Menus & CRUD:
  - `src/main/java/com/memorizer/ui/MainStage.java` (menus, notices, dialogs)
- Config & Docs:
  - `src/main/resources/application.properties` (theme/deck filter, geometry)
  - `README.md`, `src/main/resources/USER_MANUAL.txt`

