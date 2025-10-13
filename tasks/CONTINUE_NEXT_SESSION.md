# Memorizer — Continuation Plan (Next Session)

This document captures what we’ve completed in this window and what’s left to do so we can resume quickly in a new session without losing context.

## Status Summary

Backend
- Embedded HTTPS server (Javalin + Jetty) with Local CA issuance and leaf keystore (SAN includes LAN IP); self‑signed fallback works.
- Flyway migration V3 adds `review_log.client_uuid` + unique index for idempotency.
- Pairing & QR:
  - `/pair` page (QR, copyable Server URL/Token/JSON), `/pair/qr.png`, `/pair/ca.crt` (CA download), status chips.
  - QR decode endpoint `/api/pair/decode` (ZXing). LAN pairing link.
- PWA backend endpoints:
  - Health: `GET /api/health`
  - Server info: `GET /api/server/info` (serverId, mode, host, port)
  - Sync: `POST /api/sync` (unified) in both HTTPS and fallback HTTP
  - Reviews: `POST /api/reviews` (dedupe by `client_uuid` then `card_id+reviewed_at+rating`)
  - Notes/Decks CRUD:
    - Notes LWW update: `POST /api/notes/update`
    - Notes delete: `POST /api/notes/delete`
    - Deck rename: `POST /api/decks/update`
    - Deck delete: `POST /api/decks/delete`
    - Card create (note + card): `POST /api/cards/create`

PWA (Web)
- Project: Vite + Vue 3 + Dexie + vite-plugin-pwa.
- Theme: Day/Night toggle with CSS variables; inputs themed.
- Function menu: Top‑left slide‑over drawer (animated), focus‑trapped, ESC to close; actions: Decks, Connect, Sync Now, Full Refresh, Theme switch.
- Global bottom sticky progress bar: shows Today X/Y.
- Page transitions between routes.
- Connect:
  - Pairing UI (URL, Token) fixed; QR “Scan” with server decode fallback; draft persistence; prefilled previous config.
- Decks:
  - List with due counts; actions: Study, Browse, Rename (modal), Delete (with confirm).
  - Empty state hint + Refresh button.
- Browse (Notes):
  - Filter, sorting (Due first, Due date, Updated, A→Z/Z→A), “Due X” pill.
  - Note actions: Open, Delete (with confirm); Add Card modal (Front, Back, Reading, POS, Examples).
- Study (CardView):
  - Stats chips (Deck/Due/Today), progress bar; Mini mode toggle; Hide/Show Stats toggle.
  - Front/back emphasis; Reading shown only after Flip; POS chip visible (configurable later).
  - Centered examples roller (prev/next, auto‑roll).
  - Rating controls (top + mobile bottom bar). Bottom bar has color cues & labels.
  - Keyboard shortcuts: Space (Flip), 1‑4 (Again/Hard/Good/Easy), S (Snooze), E (Edit).
  - Edit modal: Front/Back/Reading/POS/Examples; saves local + posts LWW to server.
  - No‑due inline message with Snooze + Show next anyway.
  - Preloads next card for snappier flips.
- Auto‑sync: Periodic loop with backoff; triggers on online/visibility; sync‑complete event updates Decks/Browse and shows toast.
- Server identity check: Compares `serverId` on boot; force Full Refresh if changed.

Build/Run
- Maven build passes (desktop). PWA builds with `npm run build`; desktop serves PWA at `/pwa/`.

## Known Issues / Polishing
- POS field visibility: currently visible pre‑flip; may want to hide pre‑flip like Reading (configurable option).
- QR live scan relies on BarcodeDetector support; “server decode” fallback present.
- Edit LWW: server returns no payload; client assumes success; full sync will reconcile. Consider returning updated row(s) soon.
- Auto‑sync: conservative backoff; logs could be surfaced in UI.
- CRUD UX: Notes/Decks delete use confirm() prompts; modals added for rename and note add; could unify with nicer dialogs.

## Next Session TODOs

1) Deck Create (To‑Do)
- Backend: `POST /api/decks/create` (name required) → returns `{ id, name }`.
- PWA: Decks page “+ Add Deck” button → modal (Name). On save, call server, upsert Dexie, reload.

2) CRUD Depth & Consistency
- Notes Edit (server echo): Change `/api/notes/update` to return updated rows; client upsert immediately.
- Card Delete endpoint (delete by cardId) for parity.

3) Sync Hardening
- Use `client_uuid` strictly for review dedupe (already in DB + payload); add unique index check to protect concurrency.
- Show serverId mismatch warning in UI and confirm before auto refresh (optional safeguard).

4) Study UX
- Option to hide POS pre‑flip (user preference); surface a Settings page.
- Animate flips and rating presses (micro‑animations).

5) Pairing UX
- Add platform‑specific illustrated steps (iOS/Android) on `/pair` and Connect to guide camera permissions/TLS.

6) Desktop Parity
- Add “Create Card” dialog in desktop (normal/mini windows) matching PWA fields.
- Add “Create Deck” in desktop UI (with rename/delete parity).

7) Tests & QA
- Add lightweight integration tests for endpoints (deck/note/card create/delete, reviews, sync) and migrations.
- Manual QA script for pairing, HTTPS, mobile scan, CRUD, and sync reconciliation.

## Quick Commands / How to Verify

Backend
- Build: `mvn -P openjfx -DskipTests clean package`
- Run jar, enable Sync Server, open `/pair` → download `/pair/ca.crt` and install on device; verify `/api/health` and `/api/server/info`.

PWA
- Dev: `cd pwa && npm install && npm run dev` (LAN: use `--host`)
- Build: `npm run build`; served at `https://<desktop-ip>:7070/pwa/`.
- Verify: Connect (scan or paste), Decks → Browse → Study; Add/Delete; Rename/Delete deck; rating + auto sync.

## Notes for the Next Engineer
- Day/Night variables are centralized in `pwa/src/App.vue`. Tune brand palette under “Brand rating palette”.
- If `/api/sync` returns 404, server may be in fallback mode; we implemented the route for both modes; ensure server restarted.
- When Pairing server changes, PWA will Full Refresh on next boot; serverId stored in Dexie settings.
- For Reading (pronunciation) memorization, Reading is shown only after Flip; POS currently stays visible.

---

Document: tasks/CONTINUE_NEXT_SESSION.md

This file is the handoff for the next window. Start at the TODOs section (Deck Create) and proceed down.

