# Breakpoint 1020 — Status & TODOs

Status
- Main window opens at half-screen, centered (no first-open flash).
- Examples display restored in Normal and Mini; no forced hide on new card.
- Normal mode center area uses space better; fewer ellipses.
- Vertical splitters hidden; progress bar style left as approved.
- Deck switch rebuilds plan immediately (takes effect at once).
- Build clean (UTF-8 BOM removed from StealthStage).

Unfinished
- Mini single-pane cycle (Front→Back→Reading/Pos→Examples→Front) via Space/click.
- Refactor StealthStage: English comments + small helpers; keep Normal logic unchanged.
- Finalize Normal center width allocation (reserve Reading/Pos, split remaining evenly for Back/Examples).
- Sanity pass: examples roll in autoroll/static; tooltips readable in light/dark.

Files touched (today)
- src/main/java/com/memorizer/ui/MainStage.java (first-open sizing)
- src/main/java/com/memorizer/ui/StealthStage.java (examples visibility; cleanup)
- src/main/resources/css/drawer-*.css (splitter lines; progress bar earlier)

Next session plan
1) Implement Mini single-pane cycle (isolated helper + key/click routing).
2) Refactor StealthStage (English headers, helpers: showFrontOnly, setExamples, updateNormalFlipVisibility).
3) Verify Normal/Mini examples and layout; create checkpoint.
