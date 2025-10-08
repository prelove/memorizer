package com.memorizer.db;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Simple counters for dashboard. */
public class StatsRepository {

    public static class Stats {
        public int dueCount;
        public int newCount;
        public int totalCards;
        public int totalNotes;
        public int todayReviews;
    }

    public Stats load() {
        Stats s = new Stats();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT COUNT(*) FROM card WHERE due_at IS NOT NULL AND due_at <= ? AND status <> 3")) {
            ps.setTimestamp(1, now);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) s.dueCount = rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("load dueCount failed", e); }

        try (Statement st = Database.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM card WHERE (due_at IS NULL OR status=0) AND status <> 3")) {
            if (rs.next()) s.newCount = rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException("load newCount failed", e); }

        try (Statement st = Database.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM card")) {
            if (rs.next()) s.totalCards = rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException("load totalCards failed", e); }

        try (Statement st = Database.get().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM note")) {
            if (rs.next()) s.totalNotes = rs.getInt(1);
        } catch (SQLException e) { throw new RuntimeException("load totalNotes failed", e); }

        // today reviews since 00:00 local time
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Timestamp startTs = new Timestamp(start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT COUNT(*) FROM review_log WHERE reviewed_at >= ?")) {
            ps.setTimestamp(1, startTs);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) s.todayReviews = rs.getInt(1); }
        } catch (SQLException e) { throw new RuntimeException("load todayReviews failed", e); }

        return s;
    }
}
