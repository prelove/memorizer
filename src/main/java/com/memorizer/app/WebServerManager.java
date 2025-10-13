package com.memorizer.app;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;

/**
 * Embedded HTTPS web server exposing minimal API for PWA.
 * Java 8 compatible (Javalin 3.x + Jetty 9).
 */
public final class WebServerManager {
    private static final Logger log = LoggerFactory.getLogger(WebServerManager.class);
    private static final WebServerManager INSTANCE = new WebServerManager();

    private volatile Javalin app;
    private volatile boolean running;
    private volatile boolean starting;
    private volatile int boundPort;
    private volatile String boundHost;
    private volatile boolean httpsActive;
    private static String SERVER_ID;

    public static WebServerManager get() { return INSTANCE; }

    private WebServerManager() {}

    public synchronized void start() {
        if (running || starting) return;
        starting = true;
        int port = com.memorizer.app.Config.getInt("app.web.port", 7070);
        String host = com.memorizer.app.Config.get("app.web.host", "0.0.0.0");
        boolean preferHttps = Boolean.parseBoolean(com.memorizer.app.Config.get("app.web.https.enabled", "true"));

        // Build app with either HTTPS or HTTP server
        Javalin candidate;
        try {
            final String pwaDist = findPwaDist();
            if (preferHttps) {
                // Try local CA workflow first if enabled
                boolean useLocalCA = Config.getBool("app.web.tls.ca.enabled", true);
                Path ks;
                String pass;
                try {
                    if (useLocalCA) {
                        String lanIp = pickLanAddress(host);
                        LocalCAService.ensureCA();
                        ks = LocalCAService.ensureLeafKeystore(host, lanIp);
                        pass = new String(LocalCAService.getLeafStorePass());
                    } else {
                        ks = com.memorizer.app.CertificateManager.ensureKeystore();
                        pass = com.memorizer.app.CertificateManager.keystorePassword();
                    }
                } catch (Exception e) {
                    // fallback to old self-signed path
                    ks = com.memorizer.app.CertificateManager.ensureKeystore();
                    pass = com.memorizer.app.CertificateManager.keystorePassword();
                }
                final String ksPath = ks.toString();
                final String ksPass = pass;
                candidate = Javalin.create(cfg -> {
                    cfg.server(() -> buildHttpsServer(host, port, ksPath, ksPass));
                    cfg.enableCorsForAllOrigins();
                });
                httpsActive = true;
            } else {
                candidate = Javalin.create(cfg -> {
                    cfg.server(() -> buildHttpServer(host, port));
                    cfg.enableCorsForAllOrigins();
                });
                httpsActive = false;
            }
        } catch (Exception e) {
            // Build failure (e.g., SSL setup) -> fall back to HTTP
            log.warn("HTTPS server build failed: {}. Falling back to HTTP.", e.toString());
            final String pwaDist = findPwaDist();
            candidate = Javalin.create(cfg -> {
                cfg.server(() -> buildHttpServer(host, port));
                cfg.enableCorsForAllOrigins();
            });
            httpsActive = false;
        }

        this.app = candidate;

        // routes
        app.get("/api/health", ctx -> {
            Map<String, Object> m = new HashMap<>();
            m.put("status", "ok");
            m.put("version", "1.0");
            ctx.json(m);
        });

        // root: link hub
        app.get("/", ctx -> {
            String scheme = httpsActive ? "https" : "http";
            String base = String.format("%s://%s:%d", scheme, host, port);
            String html = "<html><head><meta charset='utf-8'><title>Memorizer Server</title></head><body style='font-family:sans-serif'>"+
                    "<h2>Memorizer Server</h2>"+
                    "<ul>"+
                    "<li><a href='"+base+"/api/health'>/api/health</a></li>"+
                    "<li><a href='"+base+"/pair'>/pair</a> (mobile pairing)</li>"+
                    "<li><a href='"+base+"/pwa/'>/pwa/</a> (serve PWA if built)</li>"+
                    "</ul>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
        });

        // server info
        app.get("/api/server/info", ctx -> {
            Map<String,Object> info = new HashMap<>();
            info.put("serverId", getOrCreateServerId());
            info.put("mode", httpsActive ? "https" : "http");
            info.put("host", boundHost == null ? host : boundHost);
            info.put("port", boundPort == 0 ? port : boundPort);
            info.put("version", "1.0");
            ctx.json(info);
        });

        // pairing: start/verify + QR helpers
        app.get("/api/pair/start", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            Map<String, Object> m = new HashMap<>();
            m.put("token", t);
            m.put("issuedAt", PairingManager.get().getIssuedAt());
            m.put("expiresAt", PairingManager.get().getExpiresAt());
            String scheme = httpsActive ? "https" : "http";
            String base = String.format("%s://%s:%d", scheme, boundHost == null? host : boundHost, boundPort == 0? port : boundPort);
            m.put("server", base);
            ctx.json(m);
        });
        app.get("/api/pair/verify", ctx -> {
            String t = ctx.queryParam("token");
            boolean ok = PairingManager.get().verify(t);
            ctx.json(java.util.Collections.singletonMap("ok", ok));
        });
        // Alias without /api prefix for legacy clients
        app.get("/pair/verify", ctx -> {
            String t = ctx.queryParam("token");
            boolean ok = PairingManager.get().verify(t);
            ctx.json(java.util.Collections.singletonMap("ok", ok));
        });
        app.get("/pair", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String scheme = httpsActive ? "https" : "http";
            String base = String.format("%s://%s:%d", scheme, boundHost == null? host : boundHost, boundPort == 0? port : boundPort);
            String payloadJson = "{\"server\":\""+base+"\",\"token\":\""+t+"\"}";
            String html = "<html><head><meta charset='utf-8'><title>Pair Mobile</title>"+
                    "<style>body{font-family:sans-serif;background:#1f2327;color:#f1f3f5} .row{margin:10px 0} .box{background:#2a2f34;padding:10px;border-radius:8px} input{width:100%;padding:8px;border-radius:6px;border:1px solid #121417;background:#1e2327;color:#e6e6e6} button{background:#3b82f6;color:#fff;border:none;border-radius:6px;padding:8px 10px;margin-left:8px} a{color:#9ecbff;text-decoration:none} .muted{color:#bdbdbd;font-size:12px} .pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#2a2f34;margin-right:8px} .ok{color:#90ee90} .bad{color:#ff9aa2}</style>"+
                    "<script>function cp(id){var el=document.getElementById(id); el.select(); el.setSelectionRange(0,99999); try{navigator.clipboard.writeText(el.value);}catch(e){document.execCommand('copy');}} function st(){var sc=(window.isSecureContext? 'yes':'no'); var cam=(navigator.mediaDevices && navigator.mediaDevices.getUserMedia? 'yes':'no'); var scEl=document.getElementById('sc'); var cmEl=document.getElementById('cm'); if(scEl){ scEl.textContent=sc; scEl.className='pill '+(sc==='yes'?'ok':'bad'); } if(cmEl){ cmEl.textContent=cam; cmEl.className='pill '+(cam==='yes'?'ok':'bad'); }}</script>"+
                    "</head><body>"+
                    "<h2>Pair Mobile</h2>"+
                    "<div class='row'>Scan this QR in the mobile app:</div>"+
                    "<div class='row'><img src='/pair/qr.png' alt='QR' style='background:white;padding:8px;border-radius:8px;max-width:80vw;height:auto'/></div>"+
                    "<div class='row box'><div>Server URL</div><div style='display:flex;align-items:center;gap:8px'><input id='srv' value='"+base+"' readonly><button onclick=\"cp('srv')\">Copy</button><a href='"+base+"/pwa/' target='_blank'>Open PWA</a></div></div>"+
                    "<div class='row box'><div>Pairing Token</div><div style='display:flex;align-items:center;gap:8px'><input id='tok' value='"+t+"' readonly><button onclick=\"cp('tok')\">Copy</button></div></div>"+
                    "<div class='row box'><div>QR Payload (JSON)</div><div style='display:flex;align-items:center;gap:8px'><input id='jp' value='"+payloadJson+"' readonly><button onclick=\"cp('jp')\">Copy</button></div></div>"+
                    "<div class='row box'><div><b>Status</b></div><div>Secure Context: <span id='sc' class='pill'>...</span> Camera API: <span id='cm' class='pill'>...</span> <button onclick=\"st()\">Check</button></div></div>"+
                    "<div class='row box'><div><b>Guide</b></div><ol><li>Install Local CA: <a href='/pair/ca.crt'>/pair/ca.crt</a> (Android: Settings→Security→Install cert; iOS: AirDrop/email, then Settings→Profile→Trust)</li><li>Open PWA: <a href='"+base+"/pwa/' target='_blank'>"+base+"/pwa/</a></li><li>In PWA Connect, tap Scan QR. If not detected, tap Use server decode or Scan from photo.</li></ol><div class='muted'>After installing the CA, reload the PWA so the origin is secure and camera can activate.</div></div>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
        });
        app.get("/pair/qr.png", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String payload = buildPairingPayload((boundHost == null? host : boundHost), (boundPort == 0? port : boundPort), t, httpsActive);
            byte[] png = qrPng(payload, 384);
            ctx.contentType("image/png").result(png);
        });

        // Decode QR from image (data URL or base64) without requiring token
        app.post("/api/pair/decode", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            String data = body == null ? null : (String) body.get("image");
            if (data == null || data.trim().isEmpty()) { ctx.status(400).json(err("no_image")); return; }
            String b64 = data;
            int comma = b64.indexOf(",");
            if (b64.startsWith("data:") && comma > 0) b64 = b64.substring(comma+1);
            byte[] bytes;
            try {
                bytes = java.util.Base64.getDecoder().decode(b64);
            } catch (Exception e) { ctx.status(400).json(err("bad_base64")); return; }
            String text = decodeQrBytes(bytes);
            if (text == null) { ctx.status(422).json(err("decode_failed")); return; }
            ctx.json(java.util.Collections.singletonMap("text", text));
        });

        // Serve PWA static files under /pwa if build output is present
        final String pwaDist = findPwaDist();
        if (pwaDist != null) {
            app.get("/pwa", ctx -> ctx.redirect("/pwa/"));
            app.get("/pwa/", ctx -> {
                java.nio.file.Path f = java.nio.file.Paths.get(pwaDist, "index.html");
                serveFile(ctx, f);
            });
            app.get("/pwa/*", ctx -> {
                String rel = ctx.path().substring("/pwa/".length());
                java.nio.file.Path f = java.nio.file.Paths.get(pwaDist, rel);
                if (!java.nio.file.Files.exists(f)) {
                    f = java.nio.file.Paths.get(pwaDist, "index.html");
                }
                serveFile(ctx, f);
            });
        }
        // Serve CA certificate for mobile install
        app.get("/pair/ca.crt", ctx -> {
            try {
                java.nio.file.Path ca = LocalCAService.caCertPath();
                if (!java.nio.file.Files.exists(ca)) {
                    // Try lazily generating CA if not present
                    LocalCAService.ensureCA();
                }
                ca = LocalCAService.caCertPath();
                if (!java.nio.file.Files.exists(ca)) { ctx.status(404).result("missing"); return; }
                ctx.contentType("application/x-x509-ca-cert");
                ctx.result(java.nio.file.Files.newInputStream(ca));
            } catch (Exception e) {
                log.warn("/pair/ca.crt error: {}", e.toString());
                ctx.status(500).result("error");
            }
        });

        // Unified sync endpoint: accepts lastSyncTimestamp and reviewLogs
        app.post("/api/sync", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            long since = toLong(body == null ? null : body.get("lastSyncTimestamp")) == null ? 0L : toLong(body.get("lastSyncTimestamp"));
            List<?> logs = (List<?>) (body == null ? null : body.get("reviewLogs"));
            if (logs != null && !logs.isEmpty()) {
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                        "INSERT INTO review_log(card_id, reviewed_at, rating, latency_ms, client_uuid) VALUES (?,?,?,?,?)")) {
                    for (Object o : logs) {
                        if (!(o instanceof Map)) continue;
                        Map<?,?> m = (Map<?,?>) o;
                        Long cardId = toLong(m.get("cardId"));
                        Integer rating = toRating(m.get("rating"));
                        Long tsMs = toLong(m.get("reviewedAt"));
                        Integer latMs = toInt(m.get("latencyMs"));
                        Object uuid = m.get("uuid");
                        if (cardId == null || rating == null) continue;
                        // dedupe by uuid first
                        boolean exists = false;
                        if (uuid != null) {
                            try (PreparedStatement chk = com.memorizer.db.Database.get().prepareStatement("SELECT COUNT(*) FROM review_log WHERE client_uuid=?")){
                                chk.setString(1, String.valueOf(uuid));
                                try (ResultSet crs = chk.executeQuery()){ if (crs.next() && crs.getLong(1) > 0) { exists = true; } }
                            }
                        }
                        Timestamp rvAt = new Timestamp(tsMs == null ? System.currentTimeMillis() : tsMs);
                        if (!exists) try (PreparedStatement chk = com.memorizer.db.Database.get().prepareStatement("SELECT COUNT(*) FROM review_log WHERE card_id=? AND reviewed_at=? AND rating=?")){
                            chk.setLong(1, cardId);
                            chk.setTimestamp(2, rvAt);
                            chk.setInt(3, rating);
                            try (ResultSet crs = chk.executeQuery()){ if (crs.next() && crs.getLong(1) > 0) { exists = true; } }
                        }
                        if (exists) continue;
                        ps.setLong(1, cardId);
                        ps.setTimestamp(2, rvAt);
                        ps.setInt(3, rating);
                        if (latMs == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, latMs);
                        if (uuid == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, String.valueOf(uuid));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            List<Map<String,Object>> decks = new ArrayList<>();
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "SELECT id, name FROM deck ORDER BY id ASC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("name", rs.getString(2));
                        decks.add(o);
                    }
                }
            }
            List<Map<String,Object>> notes = new ArrayList<>();
            String sqlN = "SELECT id, deck_id, front, back, reading, pos, examples, tags, created_at, updated_at FROM note" +
                    (since > 0 ? " WHERE (COALESCE(updated_at, created_at) >= ?)" : "") + " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sqlN)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        Object deckObj = rs.getObject(2);
                        o.put("deckId", deckObj == null ? null : ((Number)deckObj).longValue());
                        o.put("front", rs.getString(3));
                        o.put("back", rs.getString(4));
                        o.put("reading", rs.getString(5));
                        o.put("pos", rs.getString(6));
                        o.put("examples", rs.getString(7));
                        o.put("tags", rs.getString(8));
                        Timestamp cAt = rs.getTimestamp(9);
                        Timestamp uAt = rs.getTimestamp(10);
                        o.put("updatedAt", (uAt != null ? uAt.getTime() : (cAt!=null?cAt.getTime():null)));
                        o.put("deleted", false);
                        notes.add(o);
                    }
                }
            }

            List<Map<String,Object>> cards = new ArrayList<>();
            String sqlC = "SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at FROM card" +
                    (since > 0 ? " WHERE (COALESCE(last_review_at, due_at) >= ?)" : "") + " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sqlC)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("noteId", rs.getLong(2));
                        Timestamp dueTs = rs.getTimestamp(3);
                        Long dueMs = (dueTs == null ? null : Long.valueOf(dueTs.getTime()));
                        Object ivl = rs.getObject(4);
                        Double intervalDays = null;
                        try { intervalDays = (ivl == null ? null : ((Number)ivl).doubleValue()); } catch (Exception ignored) {}
                        o.put("dueAt", dueMs);
                        o.put("intervalDays", intervalDays);
                        o.put("ease", rs.getDouble(5));
                        o.put("reps", rs.getInt(6));
                        o.put("lapses", rs.getInt(7));
                        o.put("status", rs.getInt(8));
                        Timestamp lrTs = rs.getTimestamp(9);
                        Long updMs = (lrTs != null ? Long.valueOf(lrTs.getTime()) : dueMs);
                        o.put("updatedAt", updMs);
                        o.put("deleted", false);
                        cards.add(o);
                    }
                }
            }

            Map<String,Object> data = new HashMap<>();
            data.put("decks", decks);
            data.put("notes", notes);
            data.put("cards", cards);
            Map<String,Object> out = new HashMap<>();
            out.put("syncTimestamp", System.currentTimeMillis());
            out.put("data", data);
            ctx.json(out);
        });

        // (removed duplicate guarded registrations for notes/decks; single definitions kept below)

        // Delete note (and its cards)
        app.post("/api/notes/delete", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            Long noteId = toLong(body == null ? null : body.get("id"));
            if (noteId == null) { ctx.status(400).json(err("invalid_id")); return; }
            try {
                com.memorizer.db.Database.get().setAutoCommit(false);
                try (PreparedStatement delCards = com.memorizer.db.Database.get().prepareStatement("DELETE FROM card WHERE note_id=?")){
                    delCards.setLong(1, noteId); delCards.executeUpdate();
                }
                try (PreparedStatement delNote = com.memorizer.db.Database.get().prepareStatement("DELETE FROM note WHERE id=?")){
                    delNote.setLong(1, noteId); delNote.executeUpdate();
                }
                com.memorizer.db.Database.get().commit();
                ctx.json(ok(1));
            } catch (Exception e) {
                try { com.memorizer.db.Database.get().rollback(); } catch (Exception ignored) {}
                ctx.status(500).json(err("delete_failed"));
            } finally { try { com.memorizer.db.Database.get().setAutoCommit(true); } catch (Exception ignored) {} }
        });

        // Update deck name
        app.post("/api/decks/update", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            Long id = toLong(body == null ? null : body.get("id"));
            String name = String.valueOf(body == null ? null : body.get("name"));
            if (id == null || name == null || name.trim().isEmpty()){ ctx.status(400).json(err("invalid")); return; }
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("UPDATE deck SET name=? WHERE id=?")){
                ps.setString(1, name.trim()); ps.setLong(2, id); int n = ps.executeUpdate(); ctx.json(ok(n));
            } catch (Exception e){ ctx.status(500).json(err("update_failed")); }
        });

        // Delete deck (and all notes/cards in it)
        app.post("/api/decks/delete", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            Long id = toLong(body == null ? null : body.get("id"));
            if (id == null){ ctx.status(400).json(err("invalid_id")); return; }
            try {
                com.memorizer.db.Database.get().setAutoCommit(false);
                // find notes
                java.util.List<Long> noteIds = new java.util.ArrayList<>();
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("SELECT id FROM note WHERE deck_id=?")){
                    ps.setLong(1, id); try (ResultSet rs = ps.executeQuery()){ while (rs.next()) noteIds.add(rs.getLong(1)); }
                }
                if (!noteIds.isEmpty()){
                    try (PreparedStatement delCards = com.memorizer.db.Database.get().prepareStatement("DELETE FROM card WHERE note_id=?")){
                        for (Long nid : noteIds){ delCards.setLong(1, nid); delCards.addBatch(); }
                        delCards.executeBatch();
                    }
                    try (PreparedStatement delNotes = com.memorizer.db.Database.get().prepareStatement("DELETE FROM note WHERE id=?")){
                        for (Long nid : noteIds){ delNotes.setLong(1, nid); delNotes.addBatch(); }
                        delNotes.executeBatch();
                    }
                }
                try (PreparedStatement delDeck = com.memorizer.db.Database.get().prepareStatement("DELETE FROM deck WHERE id=?")){
                    delDeck.setLong(1, id); delDeck.executeUpdate();
                }
                com.memorizer.db.Database.get().commit();
                ctx.json(ok(1));
            } catch (Exception e){ try { com.memorizer.db.Database.get().rollback(); } catch (Exception ignored) {} ctx.status(500).json(err("delete_failed")); }
            finally { try { com.memorizer.db.Database.get().setAutoCommit(true); } catch (Exception ignored) {} }
        });

        // --- CORS preflight + Auth-protected sync endpoints ---
        // Allow preflight before filters
        app.options("/*", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, X-Token");
            ctx.header("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            ctx.status(204);
        });
        app.before("/api/*", ctx -> {
            if ("OPTIONS".equalsIgnoreCase(ctx.method())) return; // let preflight through
            if ("/api/health".equals(ctx.path())) return;
            if ("/api/pair/verify".equals(ctx.path())) return; // allow verify without token
            if ("/api/pair/decode".equals(ctx.path())) return; // allow decode without token
            String tok = ctx.header("X-Token");
            if (tok == null || !PairingManager.get().verify(tok)) {
                log.warn("401 unauthorized path={} token={}", ctx.path(), mask(tok));
                ctx.status(401).json(java.util.Collections.singletonMap("error", "unauthorized"));
            }
        });

        app.get("/api/decks", ctx -> {
            List<Map<String,Object>> out = new ArrayList<>();
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "SELECT id, name FROM deck ORDER BY id ASC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("name", rs.getString(2));
                        o.put("updatedAt", null);
                        out.add(o);
                    }
                }
            }
            log.info("GET /api/decks -> {}", out.size());
            ctx.json(out);
        });

        app.get("/api/notes", ctx -> {
            long since = parseSince(ctx.queryParam("since"));
            log.info("GET /api/notes since={}", since);
            List<Map<String,Object>> out = new ArrayList<>();
            String sql = "SELECT id, deck_id, front, back, reading, pos, examples, tags, created_at, updated_at FROM note" +
                    (since > 0 ? " WHERE (COALESCE(updated_at, created_at) >= ?)" : "") +
                    " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        Object deckObj = rs.getObject(2);
                        o.put("deckId", deckObj == null ? null : ((Number)deckObj).longValue());
                        o.put("front", rs.getString(3));
                        o.put("back", rs.getString(4));
                        o.put("reading", rs.getString(5));
                        o.put("pos", rs.getString(6));
                        o.put("examples", rs.getString(7));
                        o.put("tags", rs.getString(8));
                        Timestamp cAt = rs.getTimestamp(9);
                        Timestamp uAt = rs.getTimestamp(10);
                        o.put("updatedAt", (uAt != null ? uAt.getTime() : (cAt!=null?cAt.getTime():null)));
                        o.put("deleted", false);
                        out.add(o);
                    }
                }
            }
            log.info("GET /api/notes -> {}", out.size());
            ctx.json(out);
        });

        app.get("/api/cards", ctx -> {
            long since = parseSince(ctx.queryParam("since"));
            log.info("GET /api/cards since={}", since);
            List<Map<String,Object>> out = new ArrayList<>();
            String sql = "SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at FROM card" +
                    (since > 0 ? " WHERE (COALESCE(last_review_at, due_at) >= ?)" : "") +
                    " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("noteId", rs.getLong(2));
                        Timestamp dueTs = rs.getTimestamp(3);
                        Long dueMs = (dueTs == null ? null : Long.valueOf(dueTs.getTime()));
                        Object ivl = rs.getObject(4);
                        Double intervalDays = null;
                        try { intervalDays = (ivl == null ? null : ((Number)ivl).doubleValue()); } catch (Exception ignored) {}
                        o.put("dueAt", dueMs);
                        o.put("intervalDays", intervalDays);
                        o.put("ease", rs.getDouble(5));
                        o.put("reps", rs.getInt(6));
                        o.put("lapses", rs.getInt(7));
                        o.put("status", rs.getInt(8));
                        Timestamp lrTs = rs.getTimestamp(9);
                        Long updMs = (lrTs != null ? Long.valueOf(lrTs.getTime()) : dueMs);
                        o.put("updatedAt", updMs);
                        o.put("deleted", false);
                        out.add(o);
                    }
                }
            }
            log.info("GET /api/cards -> {}", out.size());
            ctx.json(out);
        });

        // Create new note + card (CRUD: Create)
        app.post("/api/cards/create", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            if (body == null) { ctx.status(400).json(err("invalid_body")); return; }
            Long deckId = toLong(body.get("deckId"));
            String front = String.valueOf(body.get("front"));
            String back  = String.valueOf(body.get("back"));
            String reading = body.get("reading") == null ? null : String.valueOf(body.get("reading"));
            String pos     = body.get("pos") == null ? null : String.valueOf(body.get("pos"));
            String examples = body.get("examples") == null ? null : String.valueOf(body.get("examples"));
            String tags     = body.get("tags") == null ? null : String.valueOf(body.get("tags"));
            if (front == null || front.trim().isEmpty() || back == null || back.trim().isEmpty()) {
                ctx.status(400).json(err("missing_fields")); return;
            }
            try {
                com.memorizer.db.Database.get().setAutoCommit(false);
                long noteId;
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                        "INSERT INTO note(deck_id, front, back, reading, pos, examples, tags, created_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    if (deckId == null) ps.setNull(1, java.sql.Types.BIGINT); else ps.setLong(1, deckId);
                    ps.setString(2, front);
                    ps.setString(3, back);
                    if (reading == null) ps.setNull(4, java.sql.Types.VARCHAR); else ps.setString(4, reading);
                    if (pos == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, pos);
                    if (examples == null) ps.setNull(6, java.sql.Types.CLOB); else ps.setString(6, examples);
                    if (tags == null) ps.setNull(7, java.sql.Types.VARCHAR); else ps.setString(7, tags);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) noteId = rs.getLong(1); else throw new RuntimeException("no_note_id"); }
                }
                long cardId;
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                        "INSERT INTO card(note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at) VALUES (?,?,NULL,2.5,0,0,0,NULL)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, noteId);
                    ps.setTimestamp(2, new Timestamp(System.currentTimeMillis())); // due now
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) cardId = rs.getLong(1); else throw new RuntimeException("no_card_id"); }
                }
                com.memorizer.db.Database.get().commit();
                Map<String,Object> out = new HashMap<>();
                out.put("note", new HashMap<String,Object>() {{ put("id", noteId); put("deckId", deckId); put("front", front); put("back", back); put("reading", reading); put("pos", pos); put("examples", examples); put("tags", tags); put("updatedAt", System.currentTimeMillis()); }});
                out.put("card", new HashMap<String,Object>() {{ put("id", cardId); put("noteId", noteId); put("dueAt", System.currentTimeMillis()); put("updatedAt", System.currentTimeMillis()); }});
                ctx.json(out);
            } catch (Exception e) {
                try { com.memorizer.db.Database.get().rollback(); } catch (Exception ignored) {}
                ctx.status(500).json(err("create_failed"));
            } finally {
                try { com.memorizer.db.Database.get().setAutoCommit(true); } catch (Exception ignored) {}
            }
        });

        // Batch note update with LWW (updatedAt)
        app.post("/api/notes/update", ctx -> {
            List<?> arr = ctx.bodyAsClass(List.class);
            if (arr == null) { ctx.status(400).json(err("invalid_body")); return; }
            int updated = 0;
            java.util.List<Map<String,Object>> outNotes = new java.util.ArrayList<>();
            try (PreparedStatement sel = com.memorizer.db.Database.get().prepareStatement("SELECT updated_at FROM note WHERE id=?");
                 PreparedStatement upd = com.memorizer.db.Database.get().prepareStatement("UPDATE note SET front=?, back=?, reading=?, pos=?, examples=?, tags=?, deck_id=?, updated_at=CURRENT_TIMESTAMP WHERE id=?");
                 PreparedStatement echo = com.memorizer.db.Database.get().prepareStatement("SELECT id, deck_id, front, back, reading, pos, examples, tags, updated_at FROM note WHERE id=?");) {
                for (Object o : arr) {
                    if (!(o instanceof Map)) continue;
                    Map<?,?> m = (Map<?,?>) o;
                    Long id = toLong(m.get("id")); if (id == null) continue;
                    Long clientUpdated = toLong(m.get("updatedAt"));
                    Timestamp serverUpdated = null;
                    sel.setLong(1, id);
                    try (ResultSet rs = sel.executeQuery()) { if (rs.next()) serverUpdated = rs.getTimestamp(1); }
                    if (serverUpdated != null && clientUpdated != null && serverUpdated.getTime() >= clientUpdated) {
                        continue; // server newer or equal
                    }
                    upd.setString(1, String.valueOf(m.get("front")));
                    upd.setString(2, String.valueOf(m.get("back")));
                    if (m.get("reading") == null) upd.setNull(3, java.sql.Types.VARCHAR); else upd.setString(3, String.valueOf(m.get("reading")));
                    if (m.get("pos") == null) upd.setNull(4, java.sql.Types.VARCHAR); else upd.setString(4, String.valueOf(m.get("pos")));
                    if (m.get("examples") == null) upd.setNull(5, java.sql.Types.CLOB); else upd.setString(5, String.valueOf(m.get("examples")));
                    if (m.get("tags") == null) upd.setNull(6, java.sql.Types.VARCHAR); else upd.setString(6, String.valueOf(m.get("tags")));
                    // optional deck change
                    Long did = toLong(m.get("deckId"));
                    if (did == null) upd.setNull(7, java.sql.Types.BIGINT); else upd.setLong(7, did);
                    upd.setLong(8, id);
                    updated += upd.executeUpdate();
                    echo.setLong(1, id);
                    try (ResultSet rs = echo.executeQuery()){
                        if (rs.next()){
                            Map<String,Object> n = new HashMap<>();
                            n.put("id", rs.getLong(1));
                            Object deckObj = rs.getObject(2);
                            n.put("deckId", deckObj == null ? null : ((Number)deckObj).longValue());
                            n.put("front", rs.getString(3));
                            n.put("back", rs.getString(4));
                            n.put("reading", rs.getString(5));
                            n.put("pos", rs.getString(6));
                            n.put("examples", rs.getString(7));
                            n.put("tags", rs.getString(8));
                            Timestamp uAt = rs.getTimestamp(9);
                            n.put("updatedAt", uAt == null ? null : uAt.getTime());
                            outNotes.add(n);
                        }
                    }
                }
            }
            Map<String,Object> payload = new HashMap<>();
            payload.put("updated", updated);
            payload.put("notes", outNotes);
            ctx.json(payload);
        });

        app.post("/api/reviews", ctx -> {
            // Expect JSON array [{cardId,rating,ts,latencyMs}] rating may be string or number
            List<?> arr = ctx.bodyAsClass(List.class);
            if (arr == null) { ctx.status(400).json(err("invalid_body")); return; }
            log.info("POST /api/reviews size={}", (arr==null?0:arr.size()));
            int processed = 0;
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "INSERT INTO review_log(card_id, reviewed_at, rating, latency_ms, client_uuid) VALUES (?,?,?,?,?)")) {
                for (Object o : arr) {
                    if (!(o instanceof Map)) continue;
                    Map<?,?> m = (Map<?,?>) o;
                    Object cid = m.get("cardId"); Object rt = m.get("rating"); Object ts = m.get("ts"); Object lat = m.get("latencyMs"); Object uuid = m.get("uuid");
                    Long cardId = toLong(cid);
                    Integer rating = toRating(rt);
                    if (cardId == null || rating == null) continue;
                    // simple de-dup guard by (card_id, reviewed_at, rating)
                    Long tsMsPre = toLong(ts);
                    Timestamp rvAt = new Timestamp(tsMsPre == null ? System.currentTimeMillis() : tsMsPre);
                    boolean exists = false;
                    if (uuid != null) {
                        try (PreparedStatement chk = com.memorizer.db.Database.get().prepareStatement("SELECT COUNT(*) FROM review_log WHERE client_uuid=?")){
                            chk.setString(1, String.valueOf(uuid));
                            try (ResultSet crs = chk.executeQuery()){ if (crs.next() && crs.getLong(1) > 0) { exists = true; } }
                        }
                    }
                    if (!exists) try (PreparedStatement chk = com.memorizer.db.Database.get().prepareStatement("SELECT COUNT(*) FROM review_log WHERE card_id=? AND reviewed_at=? AND rating=?")){
                        chk.setLong(1, cardId);
                        chk.setTimestamp(2, rvAt);
                        chk.setInt(3, rating);
                        try (ResultSet crs = chk.executeQuery()){ if (crs.next() && crs.getLong(1) > 0) { exists = true; } }
                    }
                    if (exists) continue;
                    ps.setLong(1, cardId);
                    ps.setTimestamp(2, rvAt);
                    ps.setInt(3, rating);
                    Integer latMs = toInt(lat);
                    if (latMs == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, latMs);
                    if (uuid == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, String.valueOf(uuid));
                    ps.addBatch();
                    processed++;
                }
                ps.executeBatch();
            }
            log.info("POST /api/reviews processed={}", processed);
            ctx.json(ok(processed));
        });

        // Create deck
        app.post("/api/decks/create", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            String name = String.valueOf(body == null ? null : body.get("name"));
            if (name == null || name.trim().isEmpty()) { ctx.status(400).json(err("invalid")); return; }
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "INSERT INTO deck(name) VALUES (?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name.trim());
                ps.executeUpdate();
                long id = -1L;
                try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) id = rs.getLong(1); }
                Map<String,Object> out = new HashMap<>();
                out.put("id", id);
                out.put("name", name.trim());
                ctx.json(out);
            } catch (Exception e){ ctx.status(500).json(err("create_failed")); }
        });

        // (removed duplicate fallback definition of /api/cards/create; single definition kept above)

        try {
            app.start(port);
            running = true;
            boundPort = port; boundHost = pickLanAddress(host);
            log.info("Web server started on {}://{}:{}/", (httpsActive?"https":"http"), host, port);
            starting = false;
        } catch (Exception ex) {
            // Try fallback to HTTP if initial attempt failed and HTTPS was preferred
            if (preferHttps) {
                log.warn("HTTPS start failed: {}. Retrying with HTTP...", ex.toString());
                try {
                    // stop previous instance just in case
                    try { app.stop(); } catch (Exception ignored) {}
                    final String pwaDist2 = findPwaDist();
                    this.app = Javalin.create(cfg -> {
                        cfg.server(() -> buildHttpServer(host, port));
                        cfg.enableCorsForAllOrigins();
                    });
                    httpsActive = false;
                    // re-register minimal routes
                    this.app.get("/api/health", ctx -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("status", "ok");
                        m.put("version", "1.0");
                        ctx.json(m);
                    });
                    // reuse pairing endpoints
                    registerFallbackPairing(this.app, host, port);
                    // and re-register protected API routes
                    registerApiRoutes(this.app);
                    this.app.start(port);
                    running = true; boundPort = port; boundHost = pickLanAddress(host);
                    log.info("Web server started on http://{}:{}/", host, port);
                    starting = false;
                } catch (Exception ex2) {
                    running = false; this.app = null; starting = false;
                    throw new RuntimeException("Failed to start web server (HTTP fallback also failed)", ex2);
                }
            } else {
                running = false; this.app = null; starting = false;
                throw new RuntimeException("Failed to start web server", ex);
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;
        try { if (app != null) app.stop(); } catch (Exception ignored) {}
        app = null; running = false; boundPort = 0; boundHost = null;
        log.info("Web server stopped.");
    }

    public boolean isRunning() { return running; }
    public int getPort() { return boundPort; }
    public String getHost() { return boundHost; }

    private static Server buildHttpsServer(String host, int port, String keystorePath, String password) {
        // SSL context
        SslContextFactory.Server ssl = new SslContextFactory.Server();
        ssl.setKeyStorePath(keystorePath);
        ssl.setKeyStorePassword(password);
        ssl.setKeyManagerPassword(password);

        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new SecureRequestCustomizer());

        Server server = new Server();
        ServerConnector sslConnector = new ServerConnector(server,
                new SslConnectionFactory(ssl, "http/1.1"),
                new HttpConnectionFactory(https));
        sslConnector.setPort(port);
        sslConnector.setHost(host);
        server.setConnectors(new Connector[]{sslConnector});
        server.setHandler(new HandlerCollection());
        return server;
    }

    private static Server buildHttpServer(String host, int port) {
        HttpConfiguration http = new HttpConfiguration();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http));
        connector.setPort(port);
        connector.setHost(host);
        server.setConnectors(new Connector[]{connector});
        server.setHandler(new HandlerCollection());
        return server;
    }

    private static String buildPairingPayload(String host, int port, String token, boolean https) {
        // simple JSON payload for the PWA to parse
        String scheme = https ? "https" : "http";
        return String.format("{\"server\":\"%s://%s:%d\",\"token\":\"%s\"}", scheme, host, port, token);
    }

    private static byte[] qrPng(String content, int size) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix m = w.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y=0;y<size;y++){
                for (int x=0;x<size;x++){
                    int v = m.get(x,y) ? 0x000000 : 0xFFFFFF;
                    img.setRGB(x,y, 0xFF000000 | (v & 0x00FFFFFF));
                }
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("QR encode failed", e);
        }
    }

    private void registerFallbackPairing(Javalin app, String host, int port) {
        app.get("/", ctx -> ctx.redirect("/pair"));
        app.get("/api/pair/start", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            Map<String, Object> m = new HashMap<>();
            m.put("token", t);
            m.put("issuedAt", PairingManager.get().getIssuedAt());
            m.put("expiresAt", PairingManager.get().getExpiresAt());
            String base = String.format("http://%s:%d", host, port);
            m.put("server", base);
            ctx.json(m);
        });
        app.get("/api/pair/verify", ctx -> {
            String t = ctx.queryParam("token");
            boolean ok = PairingManager.get().verify(t);
            ctx.json(java.util.Collections.singletonMap("ok", ok));
        });
        // Also expose /pair/verify for clients using the non-API path
        app.get("/pair/verify", ctx -> {
            String t = ctx.queryParam("token");
            boolean ok = PairingManager.get().verify(t);
            ctx.json(java.util.Collections.singletonMap("ok", ok));
        });
        app.get("/pair", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String base = String.format("http://%s:%d", host, port);
            String payloadJson = "{\"server\":\""+base+"\",\"token\":\""+t+"\"}";
            String html = "<html><head><meta charset='utf-8'><title>Pair Mobile</title>"+
                    "<style>body{font-family:sans-serif;background:#1f2327;color:#f1f3f5} .row{margin:10px 0} .box{background:#2a2f34;padding:10px;border-radius:8px} input{width:100%;padding:8px;border-radius:6px;border:1px solid #121417;background:#1e2327;color:#e6e6e6} button{background:#3b82f6;color:#fff;border:none;border-radius:6px;padding:8px 10px;margin-left:8px} a{color:#9ecbff;text-decoration:none} .muted{color:#bdbdbd;font-size:12px} .pill{display:inline-block;padding:4px 8px;border-radius:999px;background:#2a2f34;margin-right:8px} .ok{color:#90ee90} .bad{color:#ff9aa2}</style>"+
                    "<script>function cp(id){var el=document.getElementById(id); el.select(); el.setSelectionRange(0,99999); try{navigator.clipboard.writeText(el.value);}catch(e){document.execCommand('copy');}} function st(){var sc=(window.isSecureContext? 'yes':'no'); var cam=(navigator.mediaDevices && navigator.mediaDevices.getUserMedia? 'yes':'no'); var scEl=document.getElementById('sc'); var cmEl=document.getElementById('cm'); if(scEl){ scEl.textContent=sc; scEl.className='pill '+(sc==='yes'?'ok':'bad'); } if(cmEl){ cmEl.textContent=cam; cmEl.className='pill '+(cam==='yes'?'ok':'bad'); }}</script>"+
                    "</head><body>"+
                    "<h2>Pair Mobile</h2>"+
                    "<div class='row'>Scan this QR in the mobile app:</div>"+
                    "<div class='row'><img src='/pair/qr.png' alt='QR' style='background:white;padding:8px;border-radius:8px;max-width:80vw;height:auto'/></div>"+
                    "<div class='row box'><div>Server URL</div><div style='display:flex;align-items:center;gap:8px'><input id='srv' value='"+base+"' readonly><button onclick=\"cp('srv')\">Copy</button><a href='"+base+"/pwa/' target='_blank'>Open PWA</a></div></div>"+
                    "<div class='row box'><div>Pairing Token</div><div style='display:flex;align-items:center;gap:8px'><input id='tok' value='"+t+"' readonly><button onclick=\"cp('tok')\">Copy</button></div></div>"+
                    "<div class='row box'><div>QR Payload (JSON)</div><div style='display:flex;align-items:center;gap:8px'><input id='jp' value='"+payloadJson+"' readonly><button onclick=\"cp('jp')\">Copy</button></div></div>"+
                    "<div class='row box'><div><b>Status</b></div><div>Secure Context: <span id='sc' class='pill'>...</span> Camera API: <span id='cm' class='pill'>...</span> <button onclick=\"st()\">Check</button></div></div>"+
                    "<div class='row box'><div><b>Guide</b></div><ol><li>Install Local CA: <a href='/pair/ca.crt'>/pair/ca.crt</a> (Android: Settings→Security→Install cert; iOS: AirDrop/email, then Settings→Profile→Trust)</li><li>Open PWA: <a href='"+base+"/pwa/' target='_blank'>"+base+"/pwa/</a></li><li>In PWA Connect, tap Scan QR. If not detected, tap Use server decode or Scan from photo.</li></ol><div class='muted'>After installing the CA, reload the PWA so the origin is secure and camera can activate.</div></div>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
        });
        app.get("/pair/qr.png", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String payload = buildPairingPayload(host, port, t, false);
            byte[] png = qrPng(payload, 384);
            ctx.contentType("image/png").result(png);
        });
        app.get("/pair/ca.crt", ctx -> {
            try {
                java.nio.file.Path ca = LocalCAService.caCertPath();
                if (!java.nio.file.Files.exists(ca)) {
                    LocalCAService.ensureCA();
                }
                ca = LocalCAService.caCertPath();
                if (!java.nio.file.Files.exists(ca)) { ctx.status(404).result("missing"); return; }
                ctx.contentType("application/x-x509-ca-cert");
                ctx.result(java.nio.file.Files.newInputStream(ca));
            } catch (Exception e) {
                ctx.status(500).result("error");
            }
        });
        app.post("/api/pair/decode", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            String data = body == null ? null : (String) body.get("image");
            if (data == null || data.trim().isEmpty()) { ctx.status(400).json(err("no_image")); return; }
            String b64 = data;
            int comma = b64.indexOf(",");
            if (b64.startsWith("data:") && comma > 0) b64 = b64.substring(comma+1);
            byte[] bytes;
            try {
                bytes = java.util.Base64.getDecoder().decode(b64);
            } catch (Exception e) { ctx.status(400).json(err("bad_base64")); return; }
            String text = decodeQrBytes(bytes);
            if (text == null) { ctx.status(422).json(err("decode_failed")); return; }
            ctx.json(java.util.Collections.singletonMap("text", text));
        });
    }

    /** Register protected API routes when rebuilding HTTP fallback app. */
    private void registerApiRoutes(Javalin app) {
        // Preflight
        app.options("/*", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, X-Token");
            ctx.header("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            ctx.status(204);
        });
        app.before("/api/*", ctx -> {
            if ("OPTIONS".equalsIgnoreCase(ctx.method())) return; // let preflight through
            if ("/api/health".equals(ctx.path())) return;
            if ("/api/pair/verify".equals(ctx.path())) return;
            if ("/api/pair/decode".equals(ctx.path())) return;
            String tok = ctx.header("X-Token");
            if (tok == null || !PairingManager.get().verify(tok)) {
                log.warn("401 unauthorized (fallback) path={} token={}", ctx.path(), mask(tok));
                ctx.status(401).json(java.util.Collections.singletonMap("error", "unauthorized"));
            }
        });

        app.get("/api/decks", ctx -> {
            List<Map<String,Object>> out = new ArrayList<>();
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "SELECT id, name FROM deck ORDER BY id ASC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("name", rs.getString(2));
                        o.put("updatedAt", null);
                        out.add(o);
                    }
                }
            }
            log.info("[fb] GET /api/decks -> {}", out.size());
            ctx.json(out);
        });

        app.get("/api/notes", ctx -> {
            long since = parseSince(ctx.queryParam("since"));
            log.info("[fb] GET /api/notes since={}", since);
            List<Map<String,Object>> out = new ArrayList<>();
            String sql = "SELECT id, deck_id, front, back, reading, pos, examples, tags, created_at, updated_at FROM note" +
                    (since > 0 ? " WHERE (COALESCE(updated_at, created_at) >= ?)" : "") +
                    " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        Object deckObj = rs.getObject(2);
                        o.put("deckId", deckObj == null ? null : ((Number)deckObj).longValue());
                        o.put("front", rs.getString(3));
                        o.put("back", rs.getString(4));
                        o.put("reading", rs.getString(5));
                        o.put("pos", rs.getString(6));
                        o.put("examples", rs.getString(7));
                        o.put("tags", rs.getString(8));
                        Timestamp cAt = rs.getTimestamp(9);
                        Timestamp uAt = rs.getTimestamp(10);
                        o.put("updatedAt", (uAt != null ? uAt.getTime() : (cAt!=null?cAt.getTime():null)));
                        o.put("deleted", false);
                        out.add(o);
                    }
                }
            }
            log.info("[fb] GET /api/notes -> {}", out.size());
            ctx.json(out);
        });

        app.get("/api/cards", ctx -> {
            long since = parseSince(ctx.queryParam("since"));
            log.info("[fb] GET /api/cards since={}", since);
            List<Map<String,Object>> out = new ArrayList<>();
            String sql = "SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at FROM card" +
                    (since > 0 ? " WHERE (COALESCE(last_review_at, due_at) >= ?)" : "") +
                    " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("noteId", rs.getLong(2));
                        Timestamp dueTs = rs.getTimestamp(3);
                        Long dueMs = (dueTs == null ? null : Long.valueOf(dueTs.getTime()));
                        Object ivl = rs.getObject(4);
                        Double intervalDays = null;
                        try { intervalDays = (ivl == null ? null : ((Number)ivl).doubleValue()); } catch (Exception ignored) {}
                        o.put("dueAt", dueMs);
                        o.put("intervalDays", intervalDays);
                        o.put("ease", rs.getDouble(5));
                        o.put("reps", rs.getInt(6));
                        o.put("lapses", rs.getInt(7));
                        o.put("status", rs.getInt(8));
                        Timestamp lrTs = rs.getTimestamp(9);
                        Long updMs = (lrTs != null ? Long.valueOf(lrTs.getTime()) : dueMs);
                        o.put("updatedAt", updMs);
                        o.put("deleted", false);
                        out.add(o);
                    }
                }
            }
            log.info("[fb] GET /api/cards -> {}", out.size());
            ctx.json(out);
        });

        // Create new note + card (fallback HTTP app)
        app.post("/api/cards/create", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            if (body == null) { ctx.status(400).json(err("invalid_body")); return; }
            Long deckId = toLong(body.get("deckId"));
            String front = String.valueOf(body.get("front"));
            String back  = String.valueOf(body.get("back"));
            String reading = body.get("reading") == null ? null : String.valueOf(body.get("reading"));
            String pos     = body.get("pos") == null ? null : String.valueOf(body.get("pos"));
            String examples = body.get("examples") == null ? null : String.valueOf(body.get("examples"));
            String tags     = body.get("tags") == null ? null : String.valueOf(body.get("tags"));
            if (front == null || front.trim().isEmpty() || back == null || back.trim().isEmpty()) {
                ctx.status(400).json(err("missing_fields")); return;
            }
            try {
                com.memorizer.db.Database.get().setAutoCommit(false);
                long noteId;
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                        "INSERT INTO note(deck_id, front, back, reading, pos, examples, tags, created_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    if (deckId == null) ps.setNull(1, java.sql.Types.BIGINT); else ps.setLong(1, deckId);
                    ps.setString(2, front);
                    ps.setString(3, back);
                    if (reading == null) ps.setNull(4, java.sql.Types.VARCHAR); else ps.setString(4, reading);
                    if (pos == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, pos);
                    if (examples == null) ps.setNull(6, java.sql.Types.CLOB); else ps.setString(6, examples);
                    if (tags == null) ps.setNull(7, java.sql.Types.VARCHAR); else ps.setString(7, tags);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) noteId = rs.getLong(1); else throw new RuntimeException("no_note_id"); }
                }
                long cardId;
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                        "INSERT INTO card(note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at) VALUES (?,?,NULL,2.5,0,0,0,NULL)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, noteId);
                    ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) cardId = rs.getLong(1); else throw new RuntimeException("no_card_id"); }
                }
                com.memorizer.db.Database.get().commit();
                Map<String,Object> out2 = new HashMap<>();
                out2.put("note", new HashMap<String,Object>() {{ put("id", noteId); put("deckId", deckId); put("front", front); put("back", back); put("reading", reading); put("pos", pos); put("examples", examples); put("tags", tags); put("updatedAt", System.currentTimeMillis()); }});
                out2.put("card", new HashMap<String,Object>() {{ put("id", cardId); put("noteId", noteId); put("dueAt", System.currentTimeMillis()); put("updatedAt", System.currentTimeMillis()); }});
                ctx.json(out2);
            } catch (Exception e) {
                try { com.memorizer.db.Database.get().rollback(); } catch (Exception ignored) {}
                ctx.status(500).json(err("create_failed"));
            } finally {
                try { com.memorizer.db.Database.get().setAutoCommit(true); } catch (Exception ignored) {}
            }
        });

        // Delete note (fallback HTTP app)
        app.post("/api/notes/delete", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            Long noteId = toLong(body == null ? null : body.get("id"));
            if (noteId == null) { ctx.status(400).json(err("invalid_id")); return; }
            try {
                com.memorizer.db.Database.get().setAutoCommit(false);
                try (PreparedStatement delCards = com.memorizer.db.Database.get().prepareStatement("DELETE FROM card WHERE note_id=?")){
                    delCards.setLong(1, noteId); delCards.executeUpdate();
                }
                try (PreparedStatement delNote = com.memorizer.db.Database.get().prepareStatement("DELETE FROM note WHERE id=?")){
                    delNote.setLong(1, noteId); delNote.executeUpdate();
                }
                com.memorizer.db.Database.get().commit();
                ctx.json(ok(1));
            } catch (Exception e) {
                try { com.memorizer.db.Database.get().rollback(); } catch (Exception ignored) {}
                ctx.status(500).json(err("delete_failed"));
            } finally { try { com.memorizer.db.Database.get().setAutoCommit(true); } catch (Exception ignored) {} }
        });

        // Update deck name (fallback HTTP app)
        app.post("/api/decks/update", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            Long id = toLong(body == null ? null : body.get("id"));
            String name = String.valueOf(body == null ? null : body.get("name"));
            if (id == null || name == null || name.trim().isEmpty()){ ctx.status(400).json(err("invalid")); return; }
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("UPDATE deck SET name=? WHERE id=?")){
                ps.setString(1, name.trim()); ps.setLong(2, id); int n = ps.executeUpdate(); ctx.json(ok(n));
            } catch (Exception e){ ctx.status(500).json(err("update_failed")); }
        });

        // Delete deck (fallback HTTP app)
        app.post("/api/decks/delete", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            Long id = toLong(body == null ? null : body.get("id"));
            if (id == null){ ctx.status(400).json(err("invalid_id")); return; }
            try {
                com.memorizer.db.Database.get().setAutoCommit(false);
                java.util.List<Long> noteIds = new java.util.ArrayList<>();
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("SELECT id FROM note WHERE deck_id=?")){
                    ps.setLong(1, id); try (ResultSet rs = ps.executeQuery()){ while (rs.next()) noteIds.add(rs.getLong(1)); }
                }
                if (!noteIds.isEmpty()){
                    try (PreparedStatement delCards = com.memorizer.db.Database.get().prepareStatement("DELETE FROM card WHERE note_id=?")){
                        for (Long nid : noteIds){ delCards.setLong(1, nid); delCards.addBatch(); }
                        delCards.executeBatch();
                    }
                    try (PreparedStatement delNotes = com.memorizer.db.Database.get().prepareStatement("DELETE FROM note WHERE id=?")){
                        for (Long nid : noteIds){ delNotes.setLong(1, nid); delNotes.addBatch(); }
                        delNotes.executeBatch();
                    }
                }
                try (PreparedStatement delDeck = com.memorizer.db.Database.get().prepareStatement("DELETE FROM deck WHERE id=?")){
                    delDeck.setLong(1, id); delDeck.executeUpdate();
                }
                com.memorizer.db.Database.get().commit();
                ctx.json(ok(1));
            } catch (Exception e){ try { com.memorizer.db.Database.get().rollback(); } catch (Exception ignored) {} ctx.status(500).json(err("delete_failed")); }
            finally { try { com.memorizer.db.Database.get().setAutoCommit(true); } catch (Exception ignored) {} }
        });

        // Create deck (fallback HTTP app)
        app.post("/api/decks/create", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            String name = String.valueOf(body == null ? null : body.get("name"));
            if (name == null || name.trim().isEmpty()) { ctx.status(400).json(err("invalid")); return; }
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "INSERT INTO deck(name) VALUES (?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name.trim());
                ps.executeUpdate();
                long id = -1L;
                try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) id = rs.getLong(1); }
                Map<String,Object> out = new HashMap<>();
                out.put("id", id);
                out.put("name", name.trim());
                ctx.json(out);
            } catch (Exception e){ ctx.status(500).json(err("create_failed")); }
        });

        app.post("/api/reviews", ctx -> {
            List<?> arr = ctx.bodyAsClass(List.class);
            if (arr == null) { ctx.status(400).json(err("invalid_body")); return; }
            log.info("[fb] POST /api/reviews size={}", (arr==null?0:arr.size()));
            int processed = 0;
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "INSERT INTO review_log(card_id, reviewed_at, rating, latency_ms) VALUES (?,?,?,?)")) {
                for (Object o : arr) {
                    if (!(o instanceof Map)) continue;
                    Map<?,?> m = (Map<?,?>) o;
                    Long cardId = toLong(m.get("cardId"));
                    Integer rating = toRating(m.get("rating"));
                    Long tsMs = toLong(m.get("ts"));
                    Integer latMs = toInt(m.get("latencyMs"));
                    if (cardId == null || rating == null) continue;
                    ps.setLong(1, cardId);
                    ps.setTimestamp(2, new Timestamp(tsMs == null ? System.currentTimeMillis() : tsMs));
                    ps.setInt(3, rating);
                    if (latMs == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, latMs);
                    ps.addBatch();
                    processed++;
                }
                ps.executeBatch();
            }
            log.info("[fb] POST /api/reviews processed={}", processed);
            ctx.json(ok(processed));
        });

        // Notes LWW update (fallback)
        app.post("/api/notes/update", ctx -> {
            List<?> arr = ctx.bodyAsClass(List.class);
            if (arr == null) { ctx.status(400).json(err("invalid_body")); return; }
            int updated = 0;
            java.util.List<Map<String,Object>> outNotes = new java.util.ArrayList<>();
            try (PreparedStatement sel = com.memorizer.db.Database.get().prepareStatement("SELECT updated_at FROM note WHERE id=?");
                 PreparedStatement upd = com.memorizer.db.Database.get().prepareStatement("UPDATE note SET front=?, back=?, reading=?, pos=?, examples=?, tags=?, deck_id=?, updated_at=CURRENT_TIMESTAMP WHERE id=?");
                 PreparedStatement echo = com.memorizer.db.Database.get().prepareStatement("SELECT id, deck_id, front, back, reading, pos, examples, tags, updated_at FROM note WHERE id=?");) {
                for (Object o : arr) {
                    if (!(o instanceof Map)) continue;
                    Map<?,?> m = (Map<?,?>) o;
                    Long id = toLong(m.get("id")); if (id == null) continue;
                    Long clientUpdated = toLong(m.get("updatedAt"));
                    Timestamp serverUpdated = null;
                    sel.setLong(1, id);
                    try (ResultSet rs = sel.executeQuery()) { if (rs.next()) serverUpdated = rs.getTimestamp(1); }
                    if (serverUpdated != null && clientUpdated != null && serverUpdated.getTime() >= clientUpdated) {
                        continue;
                    }
                    upd.setString(1, String.valueOf(m.get("front")));
                    upd.setString(2, String.valueOf(m.get("back")));
                    if (m.get("reading") == null) upd.setNull(3, java.sql.Types.VARCHAR); else upd.setString(3, String.valueOf(m.get("reading")));
                    if (m.get("pos") == null) upd.setNull(4, java.sql.Types.VARCHAR); else upd.setString(4, String.valueOf(m.get("pos")));
                    if (m.get("examples") == null) upd.setNull(5, java.sql.Types.CLOB); else upd.setString(5, String.valueOf(m.get("examples")));
                    if (m.get("tags") == null) upd.setNull(6, java.sql.Types.VARCHAR); else upd.setString(6, String.valueOf(m.get("tags")));
                    Long did = toLong(m.get("deckId"));
                    if (did == null) upd.setNull(7, java.sql.Types.BIGINT); else upd.setLong(7, did);
                    upd.setLong(8, id);
                    updated += upd.executeUpdate();
                    echo.setLong(1, id);
                    try (ResultSet rs = echo.executeQuery()){
                        if (rs.next()){
                            Map<String,Object> n = new HashMap<>();
                            n.put("id", rs.getLong(1));
                            Object deckObj = rs.getObject(2);
                            n.put("deckId", deckObj == null ? null : ((Number)deckObj).longValue());
                            n.put("front", rs.getString(3));
                            n.put("back", rs.getString(4));
                            n.put("reading", rs.getString(5));
                            n.put("pos", rs.getString(6));
                            n.put("examples", rs.getString(7));
                            n.put("tags", rs.getString(8));
                            Timestamp uAt = rs.getTimestamp(9);
                            n.put("updatedAt", uAt == null ? null : uAt.getTime());
                            outNotes.add(n);
                        }
                    }
                }
            }
            Map<String,Object> payload = new HashMap<>();
            payload.put("updated", updated);
            payload.put("notes", outNotes);
            ctx.json(payload);
        });

        // server info (fallback)
        app.get("/api/server/info", ctx -> {
            Map<String,Object> info = new HashMap<>();
            info.put("serverId", getOrCreateServerId());
            info.put("mode", "http");
            info.put("host", (this.boundHost == null ? "localhost" : this.boundHost));
            info.put("port", (this.boundPort == 0 ? 7070 : this.boundPort));
            info.put("version", "1.0");
            ctx.json(info);
        });

        // Unified sync endpoint for fallback server as well
        app.post("/api/sync", ctx -> {
            Map<?,?> body = ctx.bodyAsClass(Map.class);
            long since = toLong(body == null ? null : body.get("lastSyncTimestamp")) == null ? 0L : toLong(body.get("lastSyncTimestamp"));
            List<?> logs = (List<?>) (body == null ? null : body.get("reviewLogs"));
            if (logs != null && !logs.isEmpty()) {
                try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                        "INSERT INTO review_log(card_id, reviewed_at, rating, latency_ms, client_uuid) VALUES (?,?,?,?,?)")) {
                    for (Object o : logs) {
                        if (!(o instanceof Map)) continue;
                        Map<?,?> m = (Map<?,?>) o;
                        Long cardId = toLong(m.get("cardId"));
                        Integer rating = toRating(m.get("rating"));
                        Long tsMs = toLong(m.get("reviewedAt"));
                        Integer latMs = toInt(m.get("latencyMs"));
                        Object uuid = m.get("uuid");
                        if (cardId == null || rating == null) continue;
                        boolean exists = false;
                        if (uuid != null) {
                            try (PreparedStatement chk = com.memorizer.db.Database.get().prepareStatement("SELECT COUNT(*) FROM review_log WHERE client_uuid=?")){
                                chk.setString(1, String.valueOf(uuid));
                                try (ResultSet crs = chk.executeQuery()){ if (crs.next() && crs.getLong(1) > 0) { exists = true; } }
                            }
                        }
                        Timestamp rvAt = new Timestamp(tsMs == null ? System.currentTimeMillis() : tsMs);
                        if (!exists) try (PreparedStatement chk = com.memorizer.db.Database.get().prepareStatement("SELECT COUNT(*) FROM review_log WHERE card_id=? AND reviewed_at=? AND rating=?")){
                            chk.setLong(1, cardId);
                            chk.setTimestamp(2, rvAt);
                            chk.setInt(3, rating);
                            try (ResultSet crs = chk.executeQuery()){ if (crs.next() && crs.getLong(1) > 0) { exists = true; } }
                        }
                        if (exists) continue;
                        ps.setLong(1, cardId);
                        ps.setTimestamp(2, rvAt);
                        ps.setInt(3, rating);
                        if (latMs == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, latMs);
                        if (uuid == null) ps.setNull(5, java.sql.Types.VARCHAR); else ps.setString(5, String.valueOf(uuid));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }

            List<Map<String,Object>> decks = new ArrayList<>();
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "SELECT id, name FROM deck ORDER BY id ASC")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("name", rs.getString(2));
                        decks.add(o);
                    }
                }
            }
            List<Map<String,Object>> notes = new ArrayList<>();
            String sqlN = "SELECT id, deck_id, front, back, reading, pos, examples, tags, created_at, updated_at FROM note" +
                    (since > 0 ? " WHERE (COALESCE(updated_at, created_at) >= ?)" : "") + " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sqlN)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        Object deckObj = rs.getObject(2);
                        o.put("deckId", deckObj == null ? null : ((Number)deckObj).longValue());
                        o.put("front", rs.getString(3));
                        o.put("back", rs.getString(4));
                        o.put("reading", rs.getString(5));
                        o.put("pos", rs.getString(6));
                        o.put("examples", rs.getString(7));
                        o.put("tags", rs.getString(8));
                        Timestamp cAt = rs.getTimestamp(9);
                        Timestamp uAt = rs.getTimestamp(10);
                        o.put("updatedAt", (uAt != null ? uAt.getTime() : (cAt!=null?cAt.getTime():null)));
                        o.put("deleted", false);
                        notes.add(o);
                    }
                }
            }

            List<Map<String,Object>> cards = new ArrayList<>();
            String sqlC = "SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at FROM card" +
                    (since > 0 ? " WHERE (COALESCE(last_review_at, due_at) >= ?)" : "") + " ORDER BY id ASC";
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sqlC)) {
                if (since > 0) ps.setTimestamp(1, new Timestamp(since));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("noteId", rs.getLong(2));
                        Timestamp dueTs = rs.getTimestamp(3);
                        Long dueMs = (dueTs == null ? null : Long.valueOf(dueTs.getTime()));
                        Object ivl = rs.getObject(4);
                        Double intervalDays = null;
                        try { intervalDays = (ivl == null ? null : ((Number)ivl).doubleValue()); } catch (Exception ignored) {}
                        o.put("dueAt", dueMs);
                        o.put("intervalDays", intervalDays);
                        o.put("ease", rs.getDouble(5));
                        o.put("reps", rs.getInt(6));
                        o.put("lapses", rs.getInt(7));
                        o.put("status", rs.getInt(8));
                        Timestamp lrTs = rs.getTimestamp(9);
                        Long updMs = (lrTs != null ? Long.valueOf(lrTs.getTime()) : dueMs);
                        o.put("updatedAt", updMs);
                        o.put("deleted", false);
                        cards.add(o);
                    }
                }
            }

            Map<String,Object> data = new HashMap<>();
            data.put("decks", decks);
            data.put("notes", notes);
            data.put("cards", cards);
            Map<String,Object> out = new HashMap<>();
            out.put("syncTimestamp", System.currentTimeMillis());
            out.put("data", data);
            ctx.json(out);
        });

        // quick counts debug endpoint
        app.get("/api/debug/counts", ctx -> {
            long decks = scalarLong("SELECT COUNT(*) FROM deck");
            long notes = scalarLong("SELECT COUNT(*) FROM note");
            long cards = scalarLong("SELECT COUNT(*) FROM card");
            Map<String,Object> m = new HashMap<>();
            m.put("decks", decks); m.put("notes", notes); m.put("cards", cards);
            ctx.json(m);
        });

        // helpers to coerce incoming JSON values safely
    }

    private static long parseSince(String s) {
        try { return (s == null || s.trim().isEmpty()) ? 0L : Long.parseLong(s.trim()); }
        catch (Exception e) { return 0L; }
    }

    private static Map<String,Object> err(String code) {
        Map<String,Object> m = new HashMap<>();
        m.put("error", code);
        return m;
    }

    private static Map<String,Object> ok(int processed) {
        Map<String,Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("processed", processed);
        return m;
    }

    private static String mask(String tok) {
        if (tok == null) return "-";
        String t = tok.trim();
        if (t.length() <= 6) return t;
        return t.substring(0,6) + "...";
    }

    private static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number)o).longValue();
        if (o instanceof String) {
            try { return Long.parseLong(((String)o).trim()); } catch (Exception ignored) {}
        }
        return null;
    }

    private static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number)o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt(((String)o).trim()); } catch (Exception ignored) {}
        }
        return null;
    }

    private static Integer toRating(Object o) {
        if (o == null) return null;
        if (o instanceof Number) {
            int v = ((Number)o).intValue();
            return (v >= 1 && v <= 4) ? v : null;
        }
        if (o instanceof String) {
            String s = ((String)o).trim();
            // numeric string
            try {
                int v = Integer.parseInt(s);
                if (v >= 1 && v <= 4) return v;
            } catch (Exception ignored) {}
            // named ratings
            String up = s.toUpperCase();
            if ("AGAIN".equals(up)) return 1;
            if ("HARD".equals(up))  return 2;
            if ("GOOD".equals(up))  return 3;
            if ("EASY".equals(up))  return 4;
        }
        return null;
    }

    /** Try to choose a non-loopback IPv4 for QR/pairing; fallback to provided host. */
    private static String pickLanAddress(String defaultHost) {
        try {
            java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                java.net.NetworkInterface nif = ifaces.nextElement();
                if (!nif.isUp() || nif.isLoopback()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isMulticastAddress()) continue;
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        // Prefer site-local addresses
                        if (addr.isSiteLocalAddress()) return ip;
                        if (defaultHost == null || defaultHost.equals("0.0.0.0")) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return (defaultHost == null || defaultHost.equals("0.0.0.0")) ? "localhost" : defaultHost;
    }

    private static long scalarLong(String sql) {
        try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (Exception ignored) {}
        return -1L;
    }

    private static String findPwaDist() {
        try {
            java.nio.file.Path p = java.nio.file.Paths.get("pwa", "dist");
            if (java.nio.file.Files.isDirectory(p)) {
                LoggerFactory.getLogger(WebServerManager.class).info("Serving PWA static files from {} at /pwa/", p.toAbsolutePath());
                return p.toAbsolutePath().toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void serveFile(io.javalin.http.Context ctx, java.nio.file.Path file) {
        try {
            String name = file.getFileName().toString().toLowerCase();
            String ct = contentTypeFor(name);
            ctx.contentType(ct);
            java.nio.file.Path f = file;
            if (!java.nio.file.Files.exists(f)) { ctx.status(404).result("Not found"); return; }
            ctx.result(java.nio.file.Files.newInputStream(f));
        } catch (Exception e) {
            ctx.status(500).result("err");
        }
    }

    private static String contentTypeFor(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".webmanifest") || name.endsWith(".manifest")) return "application/manifest+json";
        return "application/octet-stream";
    }

    private static String decodeQrBytes(byte[] bytes) {
        try {
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            if (img == null) return null;
            com.google.zxing.LuminanceSource src = new com.google.zxing.client.j2se.BufferedImageLuminanceSource(img);
            java.util.Map<com.google.zxing.DecodeHintType,Object> hints = new java.util.EnumMap<>(com.google.zxing.DecodeHintType.class);
            hints.put(com.google.zxing.DecodeHintType.TRY_HARDER, Boolean.TRUE);
            java.util.List<com.google.zxing.BarcodeFormat> fmts = new java.util.ArrayList<>();
            fmts.add(com.google.zxing.BarcodeFormat.QR_CODE);
            hints.put(com.google.zxing.DecodeHintType.POSSIBLE_FORMATS, fmts);

            com.google.zxing.Reader[] readers = new com.google.zxing.Reader[] {
                    new com.google.zxing.qrcode.QRCodeReader(), new com.google.zxing.MultiFormatReader()
            };
            com.google.zxing.Binarizer[] bins = new com.google.zxing.Binarizer[] {
                    new com.google.zxing.common.HybridBinarizer(src), new com.google.zxing.common.GlobalHistogramBinarizer(src)
            };
            for (com.google.zxing.Binarizer bin : bins) {
                com.google.zxing.BinaryBitmap bmp = new com.google.zxing.BinaryBitmap(bin);
                for (com.google.zxing.Reader r : readers) {
                    try { return r.decode(bmp, hints).getText(); } catch (Exception ignored) {}
                }
            }
            if (src.isRotateSupported()) {
                com.google.zxing.LuminanceSource rot = src.rotateCounterClockwise();
                com.google.zxing.BinaryBitmap bmp = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(rot));
                for (com.google.zxing.Reader r : readers) {
                    try { return r.decode(bmp, hints).getText(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static synchronized String getOrCreateServerId() {
        if (SERVER_ID != null) return SERVER_ID;
        try {
            java.nio.file.Path p = java.nio.file.Paths.get("data", "server_id.txt");
            java.nio.file.Files.createDirectories(p.getParent());
            if (java.nio.file.Files.exists(p)) {
                SERVER_ID = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!SERVER_ID.isEmpty()) return SERVER_ID;
            }
            SERVER_ID = java.util.UUID.randomUUID().toString();
            java.nio.file.Files.write(p, SERVER_ID.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return SERVER_ID;
        } catch (Exception e) {
            SERVER_ID = java.util.UUID.randomUUID().toString();
            return SERVER_ID;
        }
    }
}
