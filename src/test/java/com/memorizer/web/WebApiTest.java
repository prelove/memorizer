package com.memorizer.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorizer.app.Config;
import com.memorizer.db.Database;
import io.javalin.Javalin;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal integration tests for key REST API endpoints.
 * Starts a standalone plain-HTTP Javalin server backed by an in-memory H2 database.
 */
public class WebApiTest {

    private static final Logger log = LoggerFactory.getLogger(WebApiTest.class);
    private static Javalin server;
    private static int port;
    private static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void startServer() throws Exception {
        Path tmp = Files.createTempDirectory("memo-api-test-");
        Config.set("app.db.path", tmp.resolve("memo").toString());
        Database.stop();
        Database.get(); // trigger Flyway migrations

        // Find a free ephemeral port
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        server = Javalin.create(cfg -> cfg.enableCorsForAllOrigins());

        // GET /api/health
        server.get("/api/health", ctx ->
                ctx.json(Collections.singletonMap("status", "ok")));

        // GET /api/decks
        server.get("/api/decks", ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT id, name FROM deck ORDER BY id ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> o = new HashMap<>();
                    o.put("id", rs.getLong(1));
                    o.put("name", rs.getString(2));
                    out.add(o);
                }
            }
            ctx.json(out);
        });

        // POST /api/decks/create
        server.post("/api/decks/create", ctx -> {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String name = (body != null && body.get("name") != null)
                    ? body.get("name").toString().trim() : null;
            if (name == null || name.isEmpty()) {
                ctx.status(400).json(Collections.singletonMap("error", "invalid"));
                return;
            }
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "INSERT INTO deck(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        Map<String, Object> o = new HashMap<>();
                        o.put("id", rs.getLong(1));
                        o.put("name", name);
                        ctx.status(201).json(o);
                    }
                }
            }
        });

        // GET /api/notes
        server.get("/api/notes", ctx -> {
            List<Map<String, Object>> out = new ArrayList<>();
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "SELECT id, deck_id, front, back FROM note ORDER BY id ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> o = new HashMap<>();
                    o.put("id", rs.getLong(1));
                    Object dk = rs.getObject(2);
                    o.put("deckId", dk == null ? null : ((Number) dk).longValue());
                    o.put("front", rs.getString(3));
                    o.put("back", rs.getString(4));
                    out.add(o);
                }
            }
            ctx.json(out);
        });

        // POST /api/cards/create
        server.post("/api/cards/create", ctx -> {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            String front = (body != null && body.get("front") != null) ? body.get("front").toString() : null;
            String back  = (body != null && body.get("back")  != null) ? body.get("back").toString()  : null;
            if (front == null || front.isEmpty() || back == null || back.isEmpty()) {
                ctx.status(400).json(Collections.singletonMap("error", "missing_fields"));
                return;
            }
            Object deckObj = (body != null) ? body.get("deckId") : null;
            final Long deckId = (deckObj == null) ? null : Long.parseLong(deckObj.toString());
            try {
                Database.get().setAutoCommit(false);
                long noteId;
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "INSERT INTO note(deck_id, front, back, created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    if (deckId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, deckId);
                    ps.setString(2, front);
                    ps.setString(3, back);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        noteId = rs.next() ? rs.getLong(1) : -1;
                    }
                }
                long cardId;
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "INSERT INTO card(note_id, due_at, ease, reps, lapses, status) VALUES (?,CURRENT_TIMESTAMP,2.5,0,0,0)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, noteId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        cardId = rs.next() ? rs.getLong(1) : -1;
                    }
                }
                Database.get().commit();
                Map<String, Object> result = new HashMap<>();
                result.put("noteId", noteId);
                result.put("cardId", cardId);
                ctx.status(201).json(result);
            } catch (Exception e) {
                try { Database.get().rollback(); } catch (Exception re) { log.warn("rollback failed", re); }
                ctx.status(500).json(Collections.singletonMap("error", "create_failed"));
            } finally {
                try { Database.get().setAutoCommit(true); } catch (Exception ignored) {}
            }
        });

        // POST /api/cards/delete
        server.post("/api/cards/delete", ctx -> {
            Map<?, ?> body = ctx.bodyAsClass(Map.class);
            Object cidObj = (body != null) ? body.get("cardId") : null;
            if (cidObj == null) {
                ctx.status(400).json(Collections.singletonMap("error", "invalid"));
                return;
            }
            long cardId = Long.parseLong(cidObj.toString());
            try {
                Database.get().setAutoCommit(false);
                long noteId = -1;
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "SELECT note_id FROM card WHERE id=?")) {
                    ps.setLong(1, cardId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) noteId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "DELETE FROM review_log WHERE card_id=?")) {
                    ps.setLong(1, cardId); ps.executeUpdate();
                }
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "DELETE FROM study_plan WHERE card_id=?")) {
                    ps.setLong(1, cardId); ps.executeUpdate();
                }
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "DELETE FROM card WHERE id=?")) {
                    ps.setLong(1, cardId); ps.executeUpdate();
                }
                if (noteId > 0) {
                    try (PreparedStatement ps = Database.get().prepareStatement(
                            "DELETE FROM note WHERE id=?")) {
                        ps.setLong(1, noteId); ps.executeUpdate();
                    }
                }
                Database.get().commit();
                ctx.json(Collections.singletonMap("deleted", 1));
            } catch (Exception e) {
                try { Database.get().rollback(); } catch (Exception re) { log.warn("rollback failed", re); }
                ctx.status(500).json(Collections.singletonMap("error", "delete_failed"));
            } finally {
                try { Database.get().setAutoCommit(true); } catch (Exception ignored) {}
            }
        });

        // POST /api/reviews
        server.post("/api/reviews", ctx -> {
            List<?> arr = ctx.bodyAsClass(List.class);
            if (arr == null || arr.isEmpty()) {
                ctx.status(400).json(Collections.singletonMap("error", "empty"));
                return;
            }
            int processed = 0;
            for (Object item : arr) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> r = (Map<?, ?>) item;
                Object cidObj = r.get("cardId");
                Object ratingObj = r.get("rating");
                if (cidObj == null || ratingObj == null) continue;
                long cardId = Long.parseLong(cidObj.toString());
                int rating = Integer.parseInt(ratingObj.toString());
                try (PreparedStatement ps = Database.get().prepareStatement(
                        "INSERT INTO review_log(card_id, rating, reviewed_at) VALUES (?,?,CURRENT_TIMESTAMP)")) {
                    ps.setLong(1, cardId);
                    ps.setInt(2, rating);
                    ps.executeUpdate();
                }
                processed++;
            }
            ctx.json(Collections.singletonMap("processed", processed));
        });

        server.start(port);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.stop();
        Database.stop();
    }

    // ---- HTTP helpers ----

    private static String get(String path) throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        int status = c.getResponseCode();
        try (InputStream is = (status < 400 ? c.getInputStream() : c.getErrorStream())) {
            return is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        }
    }

    private static String post(String path, String jsonBody) throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        int status = c.getResponseCode();
        try (InputStream is = (status < 400 ? c.getInputStream() : c.getErrorStream())) {
            return is == null ? "" : new String(readAll(is), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        byte[] buf = new byte[4096];
        int n;
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }

    // ---- Tests ----

    @Test
    void shouldReturnHealthOk() throws Exception {
        String resp = get("/api/health");
        assertTrue(resp.contains("ok"), "health response should contain 'ok': " + resp);
    }

    @Test
    void shouldCreateAndListDeck() throws Exception {
        String resp = post("/api/decks/create", "{\"name\":\"ApiTestDeck\"}");
        assertTrue(resp.contains("ApiTestDeck"), "create deck response should contain deck name: " + resp);
        assertTrue(resp.contains("id"), "create deck response should contain id: " + resp);

        String list = get("/api/decks");
        assertTrue(list.contains("ApiTestDeck"), "deck list should contain created deck: " + list);
    }

    @Test
    void shouldCreateAndDeleteCard() throws Exception {
        // Create a deck
        String deckResp = post("/api/decks/create", "{\"name\":\"CardApiTestDeck\"}");
        Map<?, ?> deck = json.readValue(deckResp, Map.class);
        long deckId = ((Number) deck.get("id")).longValue();

        // Create a card
        String cardResp = post("/api/cards/create",
                String.format("{\"deckId\":%d,\"front\":\"ApiTestFront\",\"back\":\"ApiTestBack\"}", deckId));
        assertEquals(201, postStatus("/api/cards/create",
                String.format("{\"deckId\":%d,\"front\":\"ApiTestFront2\",\"back\":\"ApiTestBack2\"}", deckId)),
                "card creation should return 201");
        Map<?, ?> created = json.readValue(cardResp, Map.class);
        long cardId = ((Number) created.get("cardId")).longValue();
        assertTrue(cardId > 0, "cardId should be positive");

        // Verify note appears
        String notesResp = get("/api/notes");
        assertTrue(notesResp.contains("ApiTestFront"), "notes list should contain created card front");

        // Delete the card
        String delResp = post("/api/cards/delete", String.format("{\"cardId\":%d}", cardId));
        assertTrue(delResp.contains("deleted"), "delete response should contain 'deleted': " + delResp);

        // Verify the specific note is gone
        String notesAfterDel = get("/api/notes");
        assertFalse(notesAfterDel.contains("\"ApiTestFront\""),
                "notes list should not contain deleted card front");
    }

    @Test
    void shouldSubmitReview() throws Exception {
        // Create a card to review
        String cardResp = post("/api/cards/create",
                "{\"front\":\"ReviewApiQ\",\"back\":\"ReviewApiA\"}");
        Map<?, ?> created = json.readValue(cardResp, Map.class);
        long cardId = ((Number) created.get("cardId")).longValue();

        // Submit a review
        String reviewBody = String.format("[{\"cardId\":%d,\"rating\":4}]", cardId);
        String reviewResp = post("/api/reviews", reviewBody);
        assertTrue(reviewResp.contains("processed"),
                "review response should contain 'processed': " + reviewResp);
        Map<?, ?> reviewResult = json.readValue(reviewResp, Map.class);
        assertEquals(1, ((Number) reviewResult.get("processed")).intValue(),
                "should have processed 1 review");
    }

    @Test
    void shouldRejectCardCreateWithMissingFields() throws Exception {
        int status = postStatus("/api/cards/create", "{\"front\":\"OnlyFront\"}");
        assertEquals(400, status, "missing back field should return 400");
    }

    private int postStatus(String path, String jsonBody) throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        try (OutputStream os = c.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        return c.getResponseCode();
    }
}
