package com.memorizer.service;

import com.memorizer.db.CardRepository;
import com.memorizer.db.NoteRepository;
import com.memorizer.model.Card;
import com.memorizer.model.Note;
import com.memorizer.model.Rating;
import com.memorizer.srs.SrsEngine;

import java.sql.Timestamp;
import java.util.Optional;

/** Orchestrates which card to show and applies SRS scheduling on rating. */
public class StudyService {
    private final CardRepository cardRepo = new CardRepository();
    private final NoteRepository noteRepo = new NoteRepository();
    private final SrsEngine srs = new SrsEngine();

    private long showingCardId = -1;
    private long showStartedAtMs = 0;

    public static class CardView {
        public long cardId;
        public String front;
        public String back;
    }

    /** Fetch next due or new card; return empty if none. */
    public Optional<CardView> nextCard() {
        Optional<Card> oc = cardRepo.findNextDueOrNew();
        if (!oc.isPresent()) return Optional.empty();
        Card c = oc.get();
        Optional<Note> on = noteRepo.findById(c.noteId);
        if (!on.isPresent()) return Optional.empty();

        showingCardId = c.id;
        showStartedAtMs = System.currentTimeMillis();

        CardView v = new CardView();
        v.cardId = c.id;
        v.front = on.get().front;
        v.back  = on.get().back;
        return Optional.of(v);
    }

    /** Apply rating to current card and write logs. */
    public void rate(Rating rating) {
        if (showingCardId <= 0) return;

        // Load current card
        Optional<Card> oc = cardRepo.findNextDueOrNew().filter(c -> c.id == showingCardId);
        // We need the exact card; if findNext returned different, refetch by id
        Card c = oc.orElseGet(() -> loadById(showingCardId));
        if (c == null) return;

        double prevInterval = c.intervalDays == null ? 0.0 : c.intervalDays;
        SrsEngine.Result r = srs.schedule(c, rating);

        c.intervalDays = r.nextIntervalDays;
        c.ease = r.newEase;
        c.reps = c.reps + 1;
        if (r.isLapse) c.lapses = c.lapses + 1;
        c.lastReviewAt = new Timestamp(System.currentTimeMillis());
        c.dueAt = SrsEngine.nowPlusDays(r.nextIntervalDays);
        c.status = (c.intervalDays != null && c.intervalDays > 0.99) ? 2 : 1; // review/learning

        cardRepo.updateSchedule(c);

        int latency = (int) Math.max(0, System.currentTimeMillis() - showStartedAtMs);
        cardRepo.insertReview(c.id, rating.value, prevInterval, r.nextIntervalDays, c.ease, latency);

        // reset
        showingCardId = -1;
        showStartedAtMs = 0;
    }

    private Card loadById(long id) {
        try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get()
                .prepareStatement("SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at FROM card WHERE id=?")) {
            ps.setLong(1, id);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Card c = new Card();
                c.id = rs.getLong(1);
                c.noteId = rs.getLong(2);
                c.dueAt = rs.getTimestamp(3);
                c.intervalDays = (Double) rs.getObject(4);
                c.ease = rs.getDouble(5);
                c.reps = rs.getInt(6);
                c.lapses = rs.getInt(7);
                c.status = rs.getInt(8);
                c.lastReviewAt = rs.getTimestamp(9);
                if (c.ease == 0) c.ease = 2.5;
                return c;
            }
        } catch (Exception e) {
            throw new RuntimeException("loadById failed", e);
        }
    }
}
