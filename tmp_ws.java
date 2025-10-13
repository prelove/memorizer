package com.memorizer.app;

import io.javalin.Javalin;
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
    private volatile int boundPort;
    private volatile String boundHost;
    private volatile boolean httpsActive;

    public static WebServerManager get() { return INSTANCE; }

    private WebServerManager() {}

    public synchronized void start() {
        if (running) return;
        int port = com.memorizer.app.Config.getInt("app.web.port", 7070);
        String host = com.memorizer.app.Config.get("app.web.host", "0.0.0.0");
        boolean preferHttps = Boolean.parseBoolean(com.memorizer.app.Config.get("app.web.https.enabled", "true"));

        // Build app with either HTTPS or HTTP server
        Javalin candidate;
        try {
            if (preferHttps) {
                Path ks = com.memorizer.app.CertificateManager.ensureKeystore();
                String pass = com.memorizer.app.CertificateManager.keystorePassword();
                candidate = Javalin.create(cfg -> {
                    cfg.server(() -> buildHttpsServer(host, port, ks.toString(), pass));
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
                    "</ul>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
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
        app.get("/pair", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String scheme = httpsActive ? "https" : "http";
            String base = String.format("%s://%s:%d", scheme, boundHost == null? host : boundHost, boundPort == 0? port : boundPort);
            String html = "<html><head><meta charset='utf-8'><title>Pair Mobile</title></head><body style='font-family:sans-serif;background:#1f2327;color:#f1f3f5'>"+
                    "<h2>Pair Mobile</h2>"+
                    "<p>Scan this QR in the mobile app:</p>"+
                    "<img src='/pair/qr.png' alt='QR' style='background:white;padding:8px;border-radius:8px'/>"+
                    "<p>Server: "+base+"</p>"+
                    "<p>Token: <code>"+t+"</code></p>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
        });
        app.get("/pair/qr.png", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String payload = buildPairingPayload((boundHost == null? host : boundHost), (boundPort == 0? port : boundPort), t, httpsActive);
            byte[] png = qrPng(payload, 256);
            ctx.contentType("image/png").result(png);
        });

        // --- Auth-protected sync endpoints ---
        app.before("/api/*", ctx -> {
            if ("/api/health".equals(ctx.path())) return;
            String tok = ctx.header("X-Token");
            if (tok == null || !PairingManager.get().verify(tok)) {
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
            ctx.json(out);
        });

        app.get("/api/notes", ctx -> {
            long since = parseSince(ctx.queryParam("since"));
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
            ctx.json(out);
        });

        app.get("/api/cards", ctx -> {
            long since = parseSince(ctx.queryParam("since"));
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
                        Timestamp due = rs.getTimestamp(3);
                        o.put("dueAt", due == null ? null : due.getTime());
                        Object ivl = rs.getObject(4);
                        o.put("intervalDays", ivl == null ? null : ((Number)ivl).doubleValue());
                        o.put("ease", rs.getDouble(5));
                        o.put("reps", rs.getInt(6));
                        o.put("lapses", rs.getInt(7));
                        o.put("status", rs.getInt(8));
                        Timestamp lr = rs.getTimestamp(9);
                        o.put("updatedAt", lr == null ? (due==null? null: due.getTime()) : lr.getTime());
                        o.put("deleted", false);
                        out.add(o);
                    }
                }
            }
            ctx.json(out);
        });

        app.post("/api/reviews", ctx -> {
            // Expect JSON array [{cardId,rating,ts,latencyMs}]
            List<?> arr = ctx.bodyAsClass(List.class);
            if (arr == null) { ctx.status(400).json(err("invalid_body")); return; }
            int processed = 0;
            try (PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(
                    "INSERT INTO review_log(card_id, reviewed_at, rating, latency_ms) VALUES (?,?,?,?)")) {
                for (Object o : arr) {
                    if (!(o instanceof Map)) continue;
                    Map<?,?> m = (Map<?,?>) o;
                    Object cid = m.get("cardId"); Object rt = m.get("rating"); Object ts = m.get("ts"); Object lat = m.get("latencyMs");
                    if (cid == null || rt == null) continue;
                    ps.setLong(1, ((Number)cid).longValue());
                    if (ts == null) ps.setTimestamp(2, new Timestamp(System.currentTimeMillis())); else ps.setTimestamp(2, new Timestamp(((Number)ts).longValue()));
                    ps.setInt(3, ((Number)rt).intValue());
                    if (lat == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, ((Number)lat).intValue());
                    ps.addBatch();
                    processed++;
                }
                ps.executeBatch();
            }
            ctx.json(ok(processed));
        });

        try {
            app.start(port);
            running = true;
            boundPort = port; boundHost = host;
            log.info("Web server started on {}://{}:{}/", (httpsActive?"https":"http"), host, port);
        } catch (Exception ex) {
            // Try fallback to HTTP if initial attempt failed and HTTPS was preferred
            if (preferHttps) {
                log.warn("HTTPS start failed: {}. Retrying with HTTP...", ex.toString());
                try {
                    // stop previous instance just in case
                    try { app.stop(); } catch (Exception ignored) {}
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
                    this.app.start(port);
                    running = true; boundPort = port; boundHost = host;
                    log.info("Web server started on http://{}:{}/", host, port);
                } catch (Exception ex2) {
                    running = false; this.app = null;
                    throw new RuntimeException("Failed to start web server (HTTP fallback also failed)", ex2);
                }
            } else {
                running = false; this.app = null;
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
        app.get("/pair", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String base = String.format("http://%s:%d", host, port);
            String html = "<html><head><meta charset='utf-8'><title>Pair Mobile</title></head><body style='font-family:sans-serif;background:#1f2327;color:#f1f3f5'>"+
                    "<h2>Pair Mobile</h2>"+
                    "<p>Scan this QR in the mobile app:</p>"+
                    "<img src='/pair/qr.png' alt='QR' style='background:white;padding:8px;border-radius:8px'/>"+
                    "<p>Server: "+base+"</p>"+
                    "<p>Token: <code>"+t+"</code></p>"+
                    "</body></html>";
            ctx.contentType("text/html; charset=utf-8").result(html);
        });
        app.get("/pair/qr.png", ctx -> {
            String t = PairingManager.get().getOrCreateToken();
            String payload = buildPairingPayload(host, port, t, false);
            byte[] png = qrPng(payload, 256);
            ctx.contentType("image/png").result(png);
        });
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
}

