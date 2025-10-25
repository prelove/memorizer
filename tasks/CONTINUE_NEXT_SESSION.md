# Memorizer — Next Session (Unfinished Only)

This file now contains only tasks that remain unfinished. Completed items are recorded in agent.md and will not be repeated.

## Unfinished Tasks

1) Desktop API Parity
- [Done] `POST /api/cards/delete` added (cascades review_log and study_plan).

2) Study UX Preference
- Add a preference to hide POS pre‑flip (default keep current behavior). Wire to Preferences UI and Stealth rendering.

3) Pairing UX Polish
- Add illustrated, platform‑specific steps (iOS/Android) on `/pair` and desktop Connect panel for TLS/installing CA and enabling camera.

4) Build/CI Hygiene
- Confirm `settings.xml` uses Maven Central on CI; remove reliance on local Nexus mirrors. Document CI commands in README.

5) Tests & QA
- Add JUnit 5 tests:
  - PlanService deck filter and counts
  - Stealth flip‑cycle states (Normal: 0/1/2; Mini: 0/1/2/3)
  - Repository insert/update paths
  - Minimal API endpoint tests (decks/notes/cards CRUD, reviews, sync)
- Prepare a short manual QA checklist: pairing, HTTPS, QR decode fallback, CRUD, sync reconciliation.

6) Optional/Stretch
- Persist per‑deck challenge “last size” hint; reuse on challenge append.

## Quick Commands / Verify

- Desktop build: `mvn clean package`
- Enable Sync Server; open `/pair` to verify pairing page and QR image.
- PWA: `cd pwa && npm install && npm run build` then visit `/pwa/`.

Document: tasks/CONTINUE_NEXT_SESSION.md
