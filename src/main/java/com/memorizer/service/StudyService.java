package com.memorizer.service;

import com.memorizer.db.CardRepository;
import com.memorizer.db.NoteRepository;
import com.memorizer.model.Card;
import com.memorizer.model.Note;
import com.memorizer.model.Rating;
import com.memorizer.srs.SrsEngine;

import java.sql.Timestamp;
import java.util.Optional;

/**
 * Orchestrates study flow: selects which card to show, assembles view data,
 * and applies SRS scheduling when the user submits a rating.
 */
public class StudyService {
    private final CardRepository cardRepo = new CardRepository();
    private final NoteRepository noteRepo = new NoteRepository();
    private final SrsEngine srs = new SrsEngine();

    private long showingCardId = -1;
    private long showStartedAtMs = 0;

    private com.memorizer.service.PlanService plan;

    public static class CardView {
        private long cardId;
        private String front;
        private String back;
        private String reading;
        private String pos;
        private java.util.List<String> examples;
        private java.util.List<String> tags;
        private String deckName;
        private Integer planKind;

        // getters
        public long getCardId() { return cardId; }
        public String getFront() { return front; }
        public String getBack() { return back; }
        public String getReading() { return reading; }
        public String getPos() { return pos; }
        public java.util.List<String> getExamples() { return examples; }
        public java.util.List<String> getTags() { return tags; }
        public String getDeckName() { return deckName; }
        public Integer getPlanKind() { return planKind; }

        // 可选：builder 或全参构造器
    }

    /** Bind a daily plan provider to prioritize planned cards. */
    public void bindPlan(com.memorizer.service.PlanService p) { this.plan = p; }
    /** Request a rebuild of today's study plan. */
    public void rebuildTodayPlan() {
        if (plan != null) plan.buildToday();
    }

    /** Append a challenge batch to today's plan. */
    public void appendChallengeBatch(int size) {
        if (plan != null) plan.appendChallengeBatch(size);
    }

    /** Return summary counts for today's plan; empty defaults on error. */
    public com.memorizer.service.PlanService.Counts planCounts() {
        try {
            return plan != null ? plan.todayCounts() : new com.memorizer.service.PlanService.Counts();
        } catch (Exception e) {
            return new com.memorizer.service.PlanService.Counts();
        }
    }

    /** List today's plan rows for UI presentation. */
    public java.util.List<com.memorizer.service.PlanService.PlanRow> planListToday() {
        try { return plan != null ? plan.listToday() : java.util.Collections.emptyList(); }
        catch (Exception e) { return java.util.Collections.emptyList(); }
    }

    /** Mark current card skipped in plan, without rating. */
    public void skipCurrent() {
        if (showingCardId <= 0) return;
        try {
            if (plan != null) plan.markSkipped(showingCardId);
            com.memorizer.app.TrayManager tm = com.memorizer.app.AppContext.getTray();
            if (tm != null) tm.updatePlanTooltip();
        } catch (Exception ignored) {}
        showingCardId = -1;
        showStartedAtMs = 0;
    }

    /** Mark remaining pending items today as rolled. */
    public void rollRemainingToday() {
        try { if (plan != null) plan.rollRemainingToday(); } catch (Exception ignored) {}
    }

    /** Remove all challenge entries from today's plan. */
    public void clearChallengeToday() {
        try { if (plan != null) plan.clearChallengeToday(); } catch (Exception ignored) {}
    }

    /** Preview the next planned card summary: Deck • Front. */
    /** Preview "Deck • Front" of the next planned card, if any. */
    public java.util.Optional<String> previewNextFromPlanFront() {
        try {
            if (plan == null) return java.util.Optional.empty();
            java.util.Optional<Long> oc = plan.nextFromPlan();
            if (!oc.isPresent()) return java.util.Optional.empty();
            Card c = loadById(oc.get());
            if (c == null) return java.util.Optional.empty();
            java.util.Optional<com.memorizer.model.Note> on = noteRepo.findById(c.noteId);
            if (!on.isPresent()) return java.util.Optional.empty();
            String front = on.get().front == null ? "" : on.get().front;
            String deck = "";
            if (on.get().deckId != null) {
                try {
                    String dn = new com.memorizer.db.DeckRepository().findNameById(on.get().deckId);
                    if (dn != null && !dn.isEmpty()) deck = dn;
                } catch (Exception ignored) {}
            }
            String summary = (deck.isEmpty()?"":deck + " • ") + front;
            if (summary.isEmpty()) return java.util.Optional.empty();
            return java.util.Optional.of(summary);
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

/** Build a view for the given card id (and set state as showing). */
    public Optional<CardView> currentOrNextOrFallback() {
        // 1) reopen current if any
        if (showingCardId > 0) {
            CardView v = viewOf(showingCardId);
            if (v != null) return Optional.of(v);
        }
        // 2) next due/new
        Optional<CardView> n = nextCard();
        if (n.isPresent()) return n;
        // 3) fallback any available card
        java.util.Optional<com.memorizer.model.Card> any =
                new com.memorizer.db.CardRepository().findAnyAvailable();
        if (!any.isPresent()) return Optional.empty();
        com.memorizer.model.Card c = any.get();
        CardView v = viewOf(c.id);
        return v == null ? Optional.empty() : Optional.of(v);
    }

    private CardView viewOf(long cardId) {
        Card c = loadById(cardId);
        if (c == null) return null;
        java.util.Optional<com.memorizer.model.Note> on = noteRepo.findById(c.noteId);
        if (!on.isPresent()) return null;
        showingCardId = cardId;
        showStartedAtMs = System.currentTimeMillis();
        return assembleView(c, on.get());
    }

    /** Public: build a CardView for a specific card id and set as current. */
    /** Build a CardView for a specific card id and set as current. */
    public Optional<CardView> viewCardById(long cardId) {
        try {
            Card c = loadById(cardId);
            if (c == null) return Optional.empty();
            Optional<Note> on = noteRepo.findById(c.noteId);
            if (!on.isPresent()) return Optional.empty();
            showingCardId = cardId;
            showStartedAtMs = System.currentTimeMillis();
            return Optional.of(assembleView(c, on.get()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Build rich CardView by joining Note (+optional Deck) and splitting examples/tags. */
    /** Build a rich CardView by joining Note/Deck and splitting text fields. */
    private CardView assembleView(Card card, com.memorizer.model.Note note) {
        CardView v = new CardView();
        v.cardId = card.id;
        v.front = note.front;
        v.back = note.back;
        v.reading = note.reading;
        v.pos = note.pos;
        v.examples = splitList2(note.examples);
        v.tags = splitList2(note.tags);
        if (note.deckId != null) {
            try {
                String dn = new com.memorizer.db.DeckRepository().findNameById(note.deckId);
                v.deckName = dn;
            } catch (Exception ignored) {}
        }
        try { if (plan != null) v.planKind = plan.kindForToday(card.id).orElse(null); } catch (Exception ignored) {}
        return v;
    }
    
    // New safe splitter to avoid regex typo in old splitList
    private java.util.List<String> splitList2(String s) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null) return out;
        String[] parts = s.split("[\\n;|]+");
        for (String p : parts) {
            if (p == null) continue;
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private java.util.List<String> splitList(String s) {
        // delegate to the safe splitter
        return splitList2(s);
    }

        /** Prefer pulling from today's plan; build if empty; fallback to queue if allowed. */
    public java.util.Optional<CardView> nextFromPlanPreferred(boolean allowFallback) {
        try {
            if (plan != null) {
                java.util.Optional<java.lang.Long> ocid = plan.nextFromPlan();
                if (!ocid.isPresent()) {
                    plan.buildToday();
                    ocid = plan.nextFromPlan();
                }
                if (ocid.isPresent()) {
                    CardView v = viewOf(ocid.get());
                    return java.util.Optional.ofNullable(v);
                }
            }
        } catch (Exception ignored) {}
        // Respect deck filter: if a specific deck is selected, avoid cross-deck fallback
        boolean allDecks = isDeckFilterAll();
        if (allowFallback && allDecks) return nextCardOrFallback();
        return java.util.Optional.empty();
    }

    private boolean isDeckFilterAll() {
        String sel = com.memorizer.app.Config.get("app.deck.filter", "all");
        return sel == null || sel.trim().isEmpty() || "all".equalsIgnoreCase(sel.trim());
    }

/** Try next due/new; if none, fallback to any available card. */
    public Optional<CardView> nextCardOrFallback() {
        Optional<CardView> o = nextCard();
        if (o.isPresent()) return o;

        // No due/new -> fallback to any available card so user sees something
        java.util.Optional<com.memorizer.model.Card> any = new com.memorizer.db.CardRepository().findAnyAvailable();
        if (!any.isPresent()) return Optional.empty();

        com.memorizer.model.Card c = any.get();
        java.util.Optional<com.memorizer.model.Note> on = noteRepo.findById(c.noteId);
        if (!on.isPresent()) return Optional.empty();

        showingCardId = c.id;
        showStartedAtMs = System.currentTimeMillis();

        return Optional.of(assembleView(c, on.get()));
    }
    
    /** Get next card for batch session: prefer due/new, excluding the previous card; fallback optional. */
    public Optional<CardView> nextForBatch(long excludeCardId, boolean allowFallback) {
        com.memorizer.db.CardRepository repo = new com.memorizer.db.CardRepository();
        java.util.Optional<com.memorizer.model.Card> oc = repo.findNextDueOrNewExcluding(excludeCardId);
        boolean allDecks = isDeckFilterAll();
        if (!oc.isPresent() && allowFallback && allDecks) {
            oc = repo.findAnyAvailableExcluding(excludeCardId);
        }
        if (!oc.isPresent()) return Optional.empty();
        com.memorizer.model.Card c = oc.get();
        java.util.Optional<com.memorizer.model.Note> on = noteRepo.findById(c.noteId);
        if (!on.isPresent()) return Optional.empty();

        showingCardId = c.id;
        showStartedAtMs = System.currentTimeMillis();

        return Optional.of(assembleView(c, on.get()));
    }


    /** Fetch next due or new card; return empty if none. */
    /** Fetch the next due or new card and assemble its view. */
    public Optional<CardView> nextCard() {
        Optional<Card> oc = cardRepo.findNextDueOrNew();
        if (!oc.isPresent()) return Optional.empty();
        Card c = oc.get();
        Optional<Note> on = noteRepo.findById(c.noteId);
        if (!on.isPresent()) return Optional.empty();

        showingCardId = c.id;
        showStartedAtMs = System.currentTimeMillis();

        return Optional.of(assembleView(c, on.get()));
    }

    /** Apply rating to current card and write logs. */
    /**
     * Apply a user rating to the current card: compute next interval/ease,
     * persist schedule and review log, and advance the plan.
     */
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

        // mark plan done if present
        try {
            if (plan != null) plan.markDone(showingCardId);
            com.memorizer.app.TrayManager tm = com.memorizer.app.AppContext.getTray();
            if (tm != null) tm.updatePlanTooltip();
        } catch (Exception ignored) {}

        // reset
        showingCardId = -1;
        showStartedAtMs = 0;
    }
    
    /** Hide without rating: optionally snooze the current card, then clear current state. */
    public void dismissWithoutRating(boolean snoozeEnabled, int snoozeMinutes) {
        if (showingCardId <= 0) return;
        if (snoozeEnabled) {
            snoozeCurrent(snoozeMinutes);
        } else {
            // just forget current focus
            showingCardId = -1;
            showStartedAtMs = 0;
        }
    }

    /** Move current card's due to now + minutes; make it 'learning' if it was new. */
    public void snoozeCurrent(int minutes) {
        if (showingCardId <= 0) return;
        Card c = loadById(showingCardId);
        if (c == null) return;

        double ivlDays = minutes / (24.0 * 60.0);
        c.dueAt = com.memorizer.srs.SrsEngine.nowPlusDays(ivlDays);

        // for brand-new cards (interval null), give them a tiny interval and set to learning
        if (c.intervalDays == null || c.intervalDays <= 0.0) c.intervalDays = ivlDays;
        if (c.status == 0) c.status = 1; // learning

        c.lastReviewAt = new java.sql.Timestamp(System.currentTimeMillis());
        new com.memorizer.db.CardRepository().updateSchedule(c);

        // clear current focus
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
