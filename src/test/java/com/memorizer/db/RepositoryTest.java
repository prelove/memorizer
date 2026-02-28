package com.memorizer.db;

import com.memorizer.app.Config;
import com.memorizer.model.Card;
import com.memorizer.model.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class RepositoryTest {

    @BeforeEach
    void setup() throws Exception {
        Path tmp = Files.createTempDirectory("memo-repo-test-");
        Config.set("app.db.path", tmp.resolve("memo").toString());
        Database.stop();
    }

    // ---- NoteRepository ----

    @Test
    void shouldInsertAndFindNoteById() {
        NoteRepository nr = new NoteRepository();
        Note n = new Note();
        n.front = "Hello"; n.back = "World"; n.reading = "helo"; n.pos = "interj"; n.examples = "Hello!"; n.tags = "test";
        long id = nr.insert(n);
        assertTrue(id > 0, "inserted id should be positive");

        Optional<Note> found = nr.findById(id);
        assertTrue(found.isPresent());
        assertEquals("Hello", found.get().front);
        assertEquals("World", found.get().back);
        assertEquals("helo", found.get().reading);
        assertEquals("interj", found.get().pos);
        assertEquals("Hello!", found.get().examples);
        assertEquals("test", found.get().tags);
    }

    @Test
    void shouldInsertNoteWithNullOptionalFields() {
        NoteRepository nr = new NoteRepository();
        Note n = new Note();
        n.front = "Front"; n.back = "Back";
        // reading, pos, examples, tags all null
        long id = nr.insert(n);
        assertTrue(id > 0);
        Optional<Note> found = nr.findById(id);
        assertTrue(found.isPresent());
        assertNull(found.get().reading);
        assertNull(found.get().pos);
    }

    @Test
    void shouldFindNoteByCardId() {
        DeckRepository dr = new DeckRepository();
        long deckId = dr.getOrCreate("Test Deck");

        NoteRepository nr = new NoteRepository();
        Note n = new Note();
        n.deckId = deckId; n.front = "A"; n.back = "B";
        long noteId = nr.insert(n);

        CardRepository cr = new CardRepository();
        long cardId = cr.insertForNote(noteId);
        assertTrue(cardId > 0);

        Optional<Note> found = nr.findByCardId(cardId);
        assertTrue(found.isPresent(), "should find note by card id");
        assertEquals("A", found.get().front);
        assertEquals(deckId, (long) found.get().deckId);
    }

    // ---- CardRepository ----

    @Test
    void shouldInsertCardForNoteWithDefaultEase() {
        NoteRepository nr = new NoteRepository();
        Note n = new Note(); n.front = "card-test"; n.back = "back";
        long noteId = nr.insert(n);

        CardRepository cr = new CardRepository();
        long cardId = cr.insertForNote(noteId);
        assertTrue(cardId > 0);

        // Card should be findable as new card
        Optional<Card> found = cr.findNextDueOrNew();
        assertTrue(found.isPresent());
        assertEquals(2.5, found.get().ease, 0.001, "default ease should be 2.5");
        assertEquals(0, found.get().status, "new card status should be 0");
        assertNull(found.get().dueAt, "new card should have no due_at");
    }

    @Test
    void shouldUpdateScheduleAndReflectInQuery() {
        NoteRepository nr = new NoteRepository();
        Note n = new Note(); n.front = "x"; n.back = "y";
        long noteId = nr.insert(n);

        CardRepository cr = new CardRepository();
        long cardId = cr.insertForNote(noteId);

        Optional<Card> opt = cr.findNextDueOrNew();
        assertTrue(opt.isPresent());
        Card card = opt.get();

        // Update schedule: mark as reviewed, set new due_at in the past so it shows as due
        card.reps = 1;
        card.lapses = 0;
        card.ease = 2.6;
        card.intervalDays = 1.0;
        card.status = 1; // reviewing
        card.dueAt = Timestamp.from(Instant.now().minusSeconds(60)); // due in the past
        card.lastReviewAt = Timestamp.from(Instant.now());
        cr.updateSchedule(card);

        Optional<Card> updated = cr.findNextDueOrNew();
        assertTrue(updated.isPresent());
        assertEquals(1, updated.get().reps);
        assertEquals(2.6, updated.get().ease, 0.001);
        assertEquals(1, updated.get().status);
    }

    @Test
    void shouldInsertReviewLogWithoutError() {
        NoteRepository nr = new NoteRepository();
        Note n = new Note(); n.front = "rev"; n.back = "log";
        long noteId = nr.insert(n);
        CardRepository cr = new CardRepository();
        long cardId = cr.insertForNote(noteId);

        // Should not throw
        assertDoesNotThrow(() -> cr.insertReview(cardId, 3, 0.0, 1.0, 2.5, 1500));
    }
}
