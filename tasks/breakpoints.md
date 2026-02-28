# Breakpoints — Status

## All Prior Tasks Complete

- Study UX: `app.ui.stealth.hide-pos` implemented in PreferencesStage + StealthStage.
- Pairing UX: iOS/Android illustrated steps in PairingController `/pair` page.
- Build/CI: `settings.xml` and `pom.xml` use Maven Central; no Nexus mirrors.
- Tests: PlanServiceTest, FlipStateManagerTest, RepositoryTest, WebApiTest all passing (19 total).

## Remaining (Optional/Stretch)

- Persist per-deck challenge last-size hint.
- Optional Stealth UI: small deck badge and quick deck-cycle hotkey.

## Quick Pointers

- UI & Behavior: `src/main/java/com/memorizer/ui/StealthStage.java`
- Planning & Deck Filter: `src/main/java/com/memorizer/service/PlanService.java`
- Desktop Menus & CRUD: `src/main/java/com/memorizer/ui/MainStage.java`
- Config & Docs: `src/main/resources/application.properties`, `README.md`
- API Tests: `src/test/java/com/memorizer/web/WebApiTest.java`
