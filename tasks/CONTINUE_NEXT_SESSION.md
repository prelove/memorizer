# Memorizer — Next Session (Unfinished Only)

This file now contains only tasks that remain unfinished. Completed items are recorded in agent.md and will not be repeated.

## Unfinished Tasks

1) Desktop API Parity
- [Done] `POST /api/cards/delete` added (cascades review_log and study_plan).

2) Study UX Preference
- [Done] `app.ui.stealth.hide-pos` preference added; wired to PreferencesStage (`cbHidePos` checkbox) and StealthStage rendering.

3) Pairing UX Polish
- [Done] Illustrated iOS/Android setup steps (CA install + PWA pairing) added to `/pair` page in PairingController.

4) Build/CI Hygiene
- [Done] `settings.xml` uses Maven Central directly with no Nexus mirrors. `pom.xml` also declares Central repositories explicitly.

5) Tests & QA
- [Done] PlanService deck filter and counts (`PlanServiceTest`)
- [Done] Stealth flip-cycle states (`FlipStateManagerTest`)
- [Done] Repository insert/update paths (`RepositoryTest`)
- [Done] Minimal API endpoint tests (`WebApiTest`): health, decks CRUD, cards CRUD, reviews
- Prepare a short manual QA checklist: pairing, HTTPS, QR decode fallback, CRUD, sync reconciliation.

6) Optional/Stretch
- Persist per-deck challenge "last size" hint; reuse on challenge append.

## Quick Commands / Verify

- Desktop build: `mvn clean package`
- Run tests: `mvn -s settings.xml -Popenjfx -Djavafx.platform=linux test`
- Enable Sync Server; open `/pair` to verify pairing page and QR image.
- PWA: `cd pwa && npm install && npm run build` then visit `/pwa/`.

Document: tasks/CONTINUE_NEXT_SESSION.md
