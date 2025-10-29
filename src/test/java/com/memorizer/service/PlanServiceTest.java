package com.memorizer.service;

import com.memorizer.app.Config;
import com.memorizer.db.CardRepository;
import com.memorizer.db.Database;
import com.memorizer.db.DeckRepository;
import com.memorizer.db.NoteRepository;
import com.memorizer.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class PlanServiceTest {

    private long deckA;
    private long deckB;

    @BeforeEach
    void setup() throws Exception {
        // Use isolated DB per test run
        Path tmp = Files.createTempDirectory("memo-db-");
        Config.set("app.db.path", tmp.resolve("memo").toString());
        Database.stop();
        // Create two decks and some cards
        DeckRepository dr = new DeckRepository();
        deckA = dr.getOrCreate("Deck A");
        deckB = dr.getOrCreate("Deck B");

        NoteRepository nr = new NoteRepository();
        CardRepository cr = new CardRepository();

        // Deck A: 2 due, 1 new
        for (int i = 0; i < 3; i++) {
            Note n = new Note();
            n.deckId = deckA; n.front = "A"+i; n.back = "back"+i; n.reading = null; n.pos = null; n.examples = null; n.tags = null;
            long nid = nr.insert(n);
            long cid = cr.insertForNote(nid);
            if (i < 2) {
                // make due now
                try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("UPDATE card SET due_at=? WHERE id=?")) {
                    ps.setTimestamp(1, Timestamp.from(Instant.now()));
                    ps.setLong(2, cid);
                    ps.executeUpdate();
                }
            }
        }

        // Deck B: 1 due, 1 new
        for (int i = 0; i < 2; i++) {
            Note n = new Note();
            n.deckId = deckB; n.front = "B"+i; n.back = "back"+i; n.reading = null; n.pos = null; n.examples = null; n.tags = null;
            long nid = nr.insert(n);
            long cid = cr.insertForNote(nid);
            if (i == 0) {
                try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("UPDATE card SET due_at=? WHERE id=?")) {
                    ps.setTimestamp(1, Timestamp.from(Instant.now()));
                    ps.setLong(2, cid);
                    ps.executeUpdate();
                }
            }
        }
    }

    @Test
    void shouldComputeTodayCountsAndHonorDeckFilter() {
        PlanService ps = new PlanService();
        // Build full plan (all decks)
        Config.set("app.deck.filter", "all");
        ps.buildToday();
        PlanService.Counts all = ps.todayCounts();
        assertEquals(5, all.total, "total items across decks" );
        assertEquals(0, all.done);

        // Filter to Deck A
        Config.set("app.deck.filter", String.valueOf(deckA));
        ps.buildToday();
        PlanService.Counts onlyA = ps.todayCounts();
        assertEquals(3, onlyA.total, "deck A items only");

        // Mark one as done and verify counts update
        java.util.List<PlanService.PlanRow> rows = ps.listToday();
        assertFalse(rows.isEmpty());
        ps.markDone(rows.get(0).getCardId());
        PlanService.Counts afterDone = ps.todayCounts();
        assertEquals(1, afterDone.done);
        assertEquals(2, afterDone.pending);
    }

    @Test
    void shouldRebuildPlanOnDeckSwitch() {
        PlanService ps = new PlanService();
        // Start with Deck B only
        Config.set("app.deck.filter", String.valueOf(deckB));
        ps.buildToday();
        int totalB = ps.todayCounts().total;
        assertEquals(2, totalB);

        // Switch to Deck A and rebuild
        Config.set("app.deck.filter", String.valueOf(deckA));
        ps.buildToday();
        int totalA = ps.todayCounts().total;
        assertEquals(3, totalA);
    }
}

