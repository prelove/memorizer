Title: Taskbar Drawer (JavaFX 8)  Checkpoint & Restart Plan

Context Snapshot

- Goal: A Windows 10 taskbarlike JavaFX 8 drawer (Mini=1 row, Normal=2 rows) with true rounded corners, DPI-aware strict heights, subtle separators, and
  proper Dark/Light theme swapping.
- Current CSS: drawer-dark.css, drawer-light.css exist and load exclusively; ScrollPane viewport backgrounds set transparent to avoid white strips.
- Current code: StealthStage now has theme swap, scene transparency + clip + shadow, DPI strict heights, and a partial GridPane layout.

Whats Working

- Stage chrome:
    - True rounded corners: Scene transparent + Rectangle clip with arc=16 and DropShadow(18, rgba(0,0,0,.35)).
    - DPI-aware strict heights: Mini ~44 px, Normal ~76 px ( dpi/96).
    - Y-positioning: 8 px above taskbar; no bottom-edge clipping intended.
- Theming:
    - Only one stylesheet active at a time; taskbar-dark/taskbar-light applied.
    - Dark/Light ScrollPane viewport backgrounds set transparent (no white bar).
- Input/behavior:
    - Existing shortcuts preserved: Space (flip), 1..4 (answers), Esc (hide), M (mode toggle).
- Progress text (Today x/y) visible; batch (n/m) label is still available.

Whats Not Working (Blocking)

- Layout is messy in both modes:
    - FRONT/BACK/EXAMPLES center content not reliably visible/positioned.
    - Right cluster: Flip/answers not consistently rendered (answers were reparented before; fixed in part, but layout still not aligned).
    - Column widths unreasonable; separators sometimes mask content.
    - Mini/Normal visibility toggles dont perfectly match the spec; nodes can remain invisible/managed after toggles.
- Appearance is inconsistent:
    - Baseline alignment not perfect across Flip and [1..4].
    - Region growth intrudes on others due to insufficiently constrained ColumnConstraints.

Root-Cause Summary

- Node reparenting of the same buttons for both Mini and Normal caused disappearance.
- Center content migration incomplete: FRONT/BACK/ReadingPos/Examples not fully aligned to new columns.
- Columns HGrow/constraints not matching the spec (C1/C3 flex, C4 primary flex, fixed widths for C0/C2/C5/C6/C7).
- Visibility/managed toggles not reapplied after theme/mode swaps in all places.

Authoritative Layout (Use Exactly)

- Normal (one row  76 px, unresizable):
    - C0 [EDIT] (8096)
    - C1 [FRONT] (wrap to 2 lines max)
    - C2 [Reading/Pos] (small, single-line ellipsis)
    - C3 [BACK] (wrap to 2 lines max)
    - C4 [EXAMPLES] (multi-line auto-scroll; hide if empty)
    - C5 [Flip] (32 px high)
    - C6 [Again/Hard // Good/Easy] (22 grid, center vertically)
    - C7 [Progress bar // text] (bar above small text; right aligned)
    - Column HGrow: C4 = ALWAYS (primary flex), C1/C3 = SOMETIMES, others = NEVER (with fixed min/pref).
- Mini (one row  44 px):
    - C1/C3 single-line ellipsis; C2 single-line reading/pos ellipsis; C4 single-line marquee (fallback: ellipsis).
    - C5 Flip, C6 answers row [1][2][3][4] baseline aligned; C7 bar only (no text).
    - All separators hidden.
        - C1: HGrow=SOMETIMES
      batch) to C7.
2. Buttons:
    - Use original btn1..btn4 only in the Normal (22) grid.
    - Create dedicated mini buttons (m1..m4) for the Mini one-row toolbar to avoid reparenting.
    - Fix height via CSS: .controls .button { -fx-min/pref/max-height: 32; -fx-padding: 0 12; font-size: 13; font-weight: 600; }
3. Visibility & mode switch:
    - In applyMode(mini):
        - Examples: examplesScroll.visible/managed = !mini; examplesMini.visible/managed = mini.
        - Answers: answersNormal.visible/managed = !mini; answersMini.visible/managed = mini.
        - Progress: keep bar; toggle text (mini: hide).
        - Separators: for (Separator s : allSeps) { s.visible/managed = !mini; }
        - Heights: setHeight(mini ? 44scale : 76scale).
4. Center content population:
    - FRONT/BACK set on Labels (C1/C3), setWrapText(!mini); clamp to 2 lines in Normal via a clip height.
    - Reading/Pos small label (C2) = reading + "  " + pos, ellipsized.
    - StageStyle.TRANSPARENT; transparent scene; rounded clip + DropShadow; DPI strict heights; Y = maxY  height  8.

Files To Touch (next session)

- src/main/java/com/memorizer/ui/StealthStage.java
    - Confirm buildDrawerGrid sets C0C7 and separators exactly.
    - Confirm applyMode toggles only visibility/managed (no reparenting).
    - Add 2line clamp clip to FRONT/BACK in Normal.
- src/main/resources/css/drawer-dark.css and src/main/resources/css/drawer-light.css
    - Ensure .controls .button (32 px), .line-main/.line-sub styles, .separator style, .mini padding, and transparent ScrollPane are present and correct.
- Optional: tasks/CHECKPOINT_DRAWER.md (this doc)

Verification Checklist

- Normal (Dark/Light): C0C7 present in order; FRONT/BACK readable (2 lines max), Reading/Pos small, Examples multi-line auto-scroll; Flip visible;
  Answers 22 visible; Progress bar + text + (n/m) batch label visible; separators thin (1 px) and subtle.
- Mini (Dark/Light): One row 44 px; FRONT only single line; Reading/Pos compact; Examples singleline; Flip + [1][2][3][4] baseline-aligned; Progress bar
  only; separators hidden.
- Theme swap: Only one stylesheet loaded; no white strip; corner rounded without halos.
- DPI 100/125/150%: Strict heights; 8 px taskbar gap; no clipping.

