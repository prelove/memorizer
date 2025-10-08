package com.memorizer.db;

import java.sql.*;

/** Deck table helper. */
public class DeckRepository {

    public Long findIdByName(String name) {
        try (PreparedStatement ps = Database.get()
                .prepareStatement("SELECT id FROM deck WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findIdByName failed", e);
        }
    }

    public long insert(String name, String description) {
        try (PreparedStatement ps = Database.get()
                .prepareStatement("INSERT INTO deck(name, description) VALUES (?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                throw new RuntimeException("insert deck: no generated key");
            }
        } catch (SQLException e) {
            throw new RuntimeException("insert deck failed", e);
        }
    }

    public long getOrCreate(String name) {
        Long id = findIdByName(name);
        if (id != null) return id;
        return insert(name, null);
    }
}
