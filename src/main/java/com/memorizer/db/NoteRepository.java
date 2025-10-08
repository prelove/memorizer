package com.memorizer.db;

import com.memorizer.model.Note;

import java.sql.*;
import java.util.Optional;

public class NoteRepository {
    public Optional<Note> findById(long id) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, deck_id, front, back, reading, pos, examples, tags FROM note WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Note n = new Note();
                n.id = rs.getLong(1);
                n.deckId = (Long) rs.getObject(2);
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
}
