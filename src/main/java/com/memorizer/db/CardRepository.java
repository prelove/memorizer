package com.memorizer.db;

import com.memorizer.model.Card;

import java.sql.*;
import java.util.Optional;

public class CardRepository {

    public Optional<Card> findNextDueOrNew() {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        // 1) due cards first
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at " +
                "FROM card WHERE (due_at IS NOT NULL AND due_at <= ?) AND status <> 3 ORDER BY due_at ASC LIMIT 1")) {
            ps.setTimestamp(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findNextDue query failed", e);
        }

        // 2) otherwise a new card
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, note_id, due_at, interval_days, ease, reps, lapses, status, last_review_at " +
                "FROM card WHERE (due_at IS NULL OR status = 0) AND status <> 3 ORDER BY id ASC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findNew query failed", e);
        }

        return Optional.empty();
    }

    public void updateSchedule(Card c) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE card SET due_at=?, interval_days=?, ease=?, reps=?, lapses=?, status=?, last_review_at=? WHERE id=?")) {
            ps.setTimestamp(1, c.dueAt);
            if (c.intervalDays == null) ps.setNull(2, Types.DOUBLE); else ps.setDouble(2, c.intervalDays);
            ps.setDouble(3, c.ease);
            ps.setInt(4, c.reps);
            ps.setInt(5, c.lapses);
            ps.setInt(6, c.status);
            ps.setTimestamp(7, c.lastReviewAt);
            ps.setLong(8, c.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("updateSchedule failed", e);
        }
    }

    public void insertReview(long cardId, int rating, double prevInterval, double nextInterval, double ease, int latencyMs) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO review_log(card_id, rating, prev_interval, next_interval, ease, latency_ms) VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, cardId);
            ps.setInt(2, rating);
            ps.setDouble(3, prevInterval);
            ps.setDouble(4, nextInterval);
            ps.setDouble(5, ease);
            ps.setInt(6, latencyMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("insertReview failed", e);
        }
    }

    private Card map(ResultSet rs) throws SQLException {
        Card c = new Card();
        c.id = rs.getLong(1);
        c.noteId = rs.getLong(2);
        c.dueAt = rs.getTimestamp(3);
        Double idays = (Double) rs.getObject(4);
        c.intervalDays = idays;
        c.ease = rs.getDouble(5);
        c.reps = rs.getInt(6);
        c.lapses = rs.getInt(7);
        c.status = rs.getInt(8);
        c.lastReviewAt = rs.getTimestamp(9);
        if (c.ease == 0) c.ease = 2.5; // default safety
        return c;
    }
}
