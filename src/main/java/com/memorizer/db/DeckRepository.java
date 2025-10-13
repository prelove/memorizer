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

    /** Find deck name by id; return null if not found. */
    public String findNameById(long id) {
        try (PreparedStatement ps = Database.get()
                .prepareStatement("SELECT name FROM deck WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new RuntimeException("findNameById failed", e);
        }
    }

    /** List all decks (id, name) ordered by id. */
    public java.util.List<com.memorizer.model.Deck> listAll() {
        java.util.List<com.memorizer.model.Deck> out = new java.util.ArrayList<>();
        try (PreparedStatement ps = Database.get().prepareStatement("SELECT id, name FROM deck ORDER BY id ASC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.memorizer.model.Deck d = new com.memorizer.model.Deck();
                    d.id = rs.getLong(1);
                    d.name = rs.getString(2);
                    out.add(d);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("listAll decks failed", e);
        }
        return out;
    }
}
