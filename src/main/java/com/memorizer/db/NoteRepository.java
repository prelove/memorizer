package com.memorizer.db;

import com.memorizer.model.Note;

import java.sql.*;
import java.util.Optional;

/** Repository for {@code note} table: load/insert/update and joins by card id. */
public class NoteRepository {

    /** Load a note by its primary id. */
    public Optional<Note> findById(long id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, deck_id, front, back, reading, pos, examples, tags FROM note WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Note n = new Note();
                n.id = rs.getLong(1);
                Object deckObj = rs.getObject(2);
                n.deckId = deckObj == null ? null : ((Number) deckObj).longValue();
                n.front = rs.getString(3);
                n.back = rs.getString(4);
                n.reading = rs.getString(5);
                n.pos = rs.getString(6);
                n.examples = rs.getString(7);
                n.tags = rs.getString(8);
                return Optional.of(n);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findById failed", e);
        }
    }

    /** Insert a note and return its id. */
    /** Insert a new note and return its id. */
    public long insert(Note n) {
        String sql = "INSERT INTO note(deck_id, front, back, reading, pos, examples, synonyms, antonyms, mnemo, tags, created_at) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?, CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = Database.get().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (n.deckId == null) ps.setNull(1, Types.BIGINT); else ps.setLong(1, n.deckId);
            ps.setString(2, n.front);
            ps.setString(3, n.back);
            ps.setString(4, n.reading);
            ps.setString(5, n.pos);
            ps.setString(6, n.examples);
            ps.setString(7, null);
            ps.setString(8, null);
            ps.setString(9, null);
            ps.setString(10, n.tags);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                throw new RuntimeException("insert note: no generated key");
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert note failed", e);
        }
    }

    /** Update editable fields of a note by id. */
    /** Update editable fields of a note and bump updated_at. */
    public void update(Note n) {
        if (n == null) return;
        String sql = "UPDATE note SET front=?, back=?, reading=?, pos=?, examples=?, tags=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setString(1, n.front);
            ps.setString(2, n.back);
            ps.setString(3, n.reading);
            ps.setString(4, n.pos);
            ps.setString(5, n.examples);
            ps.setString(6, n.tags);
            ps.setLong(7, n.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("update note failed", e);
        }
    }

    /** Load a note by card id (join card->note). */
    /** Load the note associated with a given card id. */
    public Optional<Note> findByCardId(long cardId) {
        String sql = "SELECT n.id, n.deck_id, n.front, n.back, n.reading, n.pos, n.examples, n.tags " +
                "FROM card c JOIN note n ON n.id=c.note_id WHERE c.id=?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setLong(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Note n = new Note();
                n.id = rs.getLong(1);
                Object deckObj = rs.getObject(2);
                n.deckId = deckObj == null ? null : ((Number) deckObj).longValue();
                n.front = rs.getString(3);
                n.back = rs.getString(4);
                n.reading = rs.getString(5);
                n.pos = rs.getString(6);
                n.examples = rs.getString(7);
                n.tags = rs.getString(8);
                return Optional.of(n);
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByCardId failed", e);
        }
    }
}
