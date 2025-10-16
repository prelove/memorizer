package com.memorizer.service;

import com.memorizer.app.Config;
import com.memorizer.db.Database;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Daily plan builder and navigator.
 * Compiles today's sequence from due/leech/new/challenge pools and
 * provides helpers to navigate and maintain plan state.
 */
public class PlanService {

    public enum Kind { DUE(0), LEECH(1), NEW(2), CHALLENGE(3); public final int v; Kind(int v){this.v=v;} }
    public enum Status { PENDING(0), DONE(1), ROLLED(2), SKIPPED(3); public final int v; Status(int v){this.v=v;} }

    public static class PlanRow {
        private int orderNo;
        private int kind;
        private int status;
        private long cardId;
        private String deckName;
        private String front;

        public int getOrderNo() { return orderNo; }
        public int getKind() { return kind; }
        public int getStatus() { return status; }
        public long getCardId() { return cardId; }
        public String getDeckName() { return deckName; }
        public String getFront() { return front; }
    }

    /** Build or rebuild today's plan based on due/leech/new constraints. */
    public void buildToday() {
        LocalDate today = LocalDate.now();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        int dailyNew = Config.getInt("app.study.daily-new-limit", 20);
        int leechThresh = Config.getInt("app.study.leech-lapses-threshold", 8);

        try {
            Connection c = Database.get();
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // Carry over: bring yesterday's PENDING as today's PENDING (keep relative order)
                LocalDate yesterday = today.minusDays(1);
                int baseOrder = maxOrder(today) + 1;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT card_id, kind FROM study_plan WHERE plan_date=? AND status=0 ORDER BY order_no ASC")) {
                    ps.setDate(1, Date.valueOf(yesterday));
                    try (ResultSet rs = ps.executeQuery()) {
                        int seq = baseOrder;
                        while (rs.next()) {
                            long cardId = rs.getLong(1);
                            int kind = rs.getInt(2);
                            insert(today, cardId, null, kind, seq++);
                        }
                    }
                }
                // Mark yesterday leftovers as ROLLED
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE study_plan SET status=2, updated_at=? WHERE plan_date=? AND status=0")) {
                    ps.setTimestamp(1, now);
                    ps.setDate(2, Date.valueOf(yesterday));
                    ps.executeUpdate();
                }

                // Add DUE
                List<Long> due = findDueCards(200);
                int seq = maxOrder(today) + 1;
                for (Long id : due) insert(today, id, null, Kind.DUE.v, seq++);

                // Add LEECH
                List<Long> leech = findLeechCards(leechThresh, 100);
                seq = maxOrder(today) + 1;
                for (Long id : leech) insert(today, id, null, Kind.LEECH.v, seq++);

                // Add NEW up to dailyNew
                List<Long> news = findNewCards(dailyNew);
                seq = maxOrder(today) + 1;
                for (Long id : news) insert(today, id, null, Kind.NEW.v, seq++);

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RuntimeException("buildToday failed", e);
        }
    }

    /** Append N new cards to today's plan as challenge items. */
    public void appendChallengeBatch(int size) {
        LocalDate today = LocalDate.now();
        List<Long> pick = findNewCards(size);
        int seq = maxOrder(today) + 1;
        for (Long id : pick) insert(today, id, null, Kind.CHALLENGE.v, seq++);
    }

    /** Return the next pending card id from today's plan (honors deck filter). */
    public Optional<Long> nextFromPlan() {
        LocalDate today = LocalDate.now();
        String whereFilter = deckFilterWhereClause("p", "c", "n");
        String sql = "SELECT p.card_id FROM study_plan p JOIN card c ON c.id=p.card_id JOIN note n ON n.id=c.note_id WHERE p.plan_date=? AND p.status=0"
                + whereFilter + " ORDER BY p.order_no ASC LIMIT 1";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int idx = 1;
            ps.setDate(idx++, java.sql.Date.valueOf(today));
            idx = bindDeckFilterIfAny(ps, idx);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getLong(1)) : Optional.empty();
            }
        } catch (SQLException e) { throw new RuntimeException("nextFromPlan failed", e); }
    }

    /** Mark a planned card as done. */
    public void markDone(long cardId) {
        LocalDate today = LocalDate.now();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE study_plan SET status=1, updated_at=CURRENT_TIMESTAMP WHERE plan_date=? AND card_id=? AND status=0")) {
            ps.setDate(1, java.sql.Date.valueOf(today));
            ps.setLong(2, cardId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("markDone failed", e); }
    }

    /** Mark a planned card as skipped. */
    public void markSkipped(long cardId) {
        LocalDate today = LocalDate.now();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE study_plan SET status=3, updated_at=CURRENT_TIMESTAMP WHERE plan_date=? AND card_id=? AND status=0")) {
            ps.setDate(1, java.sql.Date.valueOf(today));
            ps.setLong(2, cardId);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("markSkipped failed", e); }
    }

    /** Mark all today's pending items as rolled. */
    public void rollRemainingToday() {
        LocalDate today = LocalDate.now();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE study_plan SET status=2, updated_at=CURRENT_TIMESTAMP WHERE plan_date=? AND status=0")) {
            ps.setDate(1, java.sql.Date.valueOf(today));
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("rollRemainingToday failed", e); }
    }

    /** Skip all today's pending CHALLENGE items. */
    public void clearChallengeToday() {
        LocalDate today = LocalDate.now();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE study_plan SET status=3, updated_at=CURRENT_TIMESTAMP WHERE plan_date=? AND status=0 AND kind=3")) {
            ps.setDate(1, java.sql.Date.valueOf(today));
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("clearChallengeToday failed", e); }
    }

    public static class Counts {
        public int pending;
        public int done;
        public int rolled;
        public int skipped;
        public int total;
    }

    /** Aggregate today's plan counts. */
    public Counts todayCounts() {
        Counts c = new Counts();
        java.time.LocalDate today = java.time.LocalDate.now();
        String whereFilter = deckFilterWhereClause("p", "c", "n");
        String sql = "SELECT p.status, COUNT(*) FROM study_plan p JOIN card c ON c.id=p.card_id JOIN note n ON n.id=c.note_id " +
                "WHERE p.plan_date=? " + whereFilter + " GROUP BY p.status";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int idx = 1; ps.setDate(idx++, java.sql.Date.valueOf(today)); idx = bindDeckFilterIfAny(ps, idx);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int st = rs.getInt(1);
                    int cnt = rs.getInt(2);
                    if (st == 0) c.pending = cnt;
                    else if (st == 1) c.done = cnt;
                    else if (st == 2) c.rolled = cnt;
                    else if (st == 3) c.skipped = cnt;
                }
            }
        } catch (SQLException e) { throw new RuntimeException("todayCounts failed", e); }
        c.total = c.pending + c.done + c.rolled + c.skipped;
        return c;
    }

    /** List today's plan rows (order/kind/status/deck/front). */
    public java.util.List<PlanRow> listToday() {
        java.util.List<PlanRow> out = new java.util.ArrayList<PlanRow>();
        LocalDate today = LocalDate.now();
        String whereFilter = deckFilterWhereClause("p", "c", "n");
        String sql = "SELECT p.order_no, p.kind, p.status, p.card_id, d.name, n.front " +
                "FROM study_plan p JOIN card c ON c.id=p.card_id " +
                "JOIN note n ON n.id=c.note_id " +
                "LEFT JOIN deck d ON d.id=n.deck_id " +
                "WHERE p.plan_date=? " + whereFilter + " ORDER BY p.order_no ASC";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int idx = 1; ps.setDate(idx++, java.sql.Date.valueOf(today)); idx = bindDeckFilterIfAny(ps, idx);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PlanRow r = new PlanRow();
                    r.orderNo = rs.getInt(1);
                    r.kind = rs.getInt(2);
                    r.status = rs.getInt(3);
                    r.cardId = rs.getLong(4);
                    r.deckName = rs.getString(5);
                    r.front = rs.getString(6);
                    out.add(r);
                }
            }
        } catch (SQLException e) { throw new RuntimeException("listToday failed", e); }
        return out;
    }

    /** Return today's kind for the given card if present. */
    public java.util.Optional<Integer> kindForToday(long cardId) {
        LocalDate today = LocalDate.now();
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT kind FROM study_plan WHERE plan_date=? AND card_id=? LIMIT 1")) {
            ps.setDate(1, java.sql.Date.valueOf(today));
            ps.setLong(2, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return java.util.Optional.of(rs.getInt(1));
                return java.util.Optional.empty();
            }
        } catch (SQLException e) { throw new RuntimeException("kindForToday failed", e); }
    }

    // ---- helpers ----
    private int maxOrder(LocalDate day) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT COALESCE(MAX(order_no),0) FROM study_plan WHERE plan_date=?")) {
            ps.setDate(1, java.sql.Date.valueOf(day));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { throw new RuntimeException("maxOrder failed", e); }
    }

    private void insert(LocalDate day, long cardId, Long deckId, int kind, int orderNo) {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "MERGE INTO study_plan(plan_date, card_id, deck_id, kind, status, order_no) KEY(plan_date, card_id) VALUES (?,?,?,?,0,?)")) {
            ps.setDate(1, java.sql.Date.valueOf(day));
            ps.setLong(2, cardId);
            if (deckId == null) ps.setNull(3, Types.BIGINT); else ps.setLong(3, deckId);
            ps.setInt(4, kind);
            ps.setInt(5, orderNo);
            ps.executeUpdate();
        } catch (SQLException e) { throw new RuntimeException("insert plan failed", e); }
    }

    private List<Long> findDueCards(int limit) {
        List<Long> out = new ArrayList<Long>();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        String whereFilter = deckFilterWhereClause(null, "c", "n");
        String sql = "SELECT c.id FROM card c JOIN note n ON n.id=c.note_id WHERE (c.due_at IS NOT NULL AND c.due_at <= ?) AND c.status <> 3"
                + whereFilter + " ORDER BY c.due_at ASC LIMIT ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int idx=1; ps.setTimestamp(idx++, now); ps.setInt(idx++, limit); idx = bindDeckFilterIfAny(ps, idx);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getLong(1)); }
        } catch (SQLException e) { throw new RuntimeException("findDueCards failed", e); }
        return out;
    }

    private List<Long> findNewCards(int limit) {
        List<Long> out = new ArrayList<Long>();
        String whereFilter = deckFilterWhereClause(null, "c", "n");
        String sql = "SELECT c.id FROM card c JOIN note n ON n.id=c.note_id WHERE (c.due_at IS NULL OR c.status = 0) AND c.status <> 3"
                + whereFilter + " ORDER BY c.id ASC LIMIT ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int idx=1; ps.setInt(idx++, Math.max(0, limit)); idx = bindDeckFilterIfAny(ps, idx);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getLong(1)); }
        } catch (SQLException e) { throw new RuntimeException("findNewCards failed", e); }
        return out;
    }

    private List<Long> findLeechCards(int lapsesThresh, int limit) {
        List<Long> out = new ArrayList<Long>();
        String whereFilter = deckFilterWhereClause(null, "c", "n");
        String sql = "SELECT c.id FROM card c JOIN note n ON n.id=c.note_id WHERE (c.lapses >= ? OR c.ease <= 1.3) AND c.status <> 3"
                + whereFilter + " ORDER BY c.lapses DESC LIMIT ?";
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            int idx=1; ps.setInt(idx++, lapsesThresh); ps.setInt(idx++, limit); idx = bindDeckFilterIfAny(ps, idx);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(rs.getLong(1)); }
        } catch (SQLException e) { throw new RuntimeException("findLeechCards failed", e); }
        return out;
    }

    // ---- deck filter helpers ----
    private static String deckFilterWhereClause(String pAlias, String cAlias, String nAlias) {
        String sel = com.memorizer.app.Config.get("app.deck.filter", "all");
        if (sel == null || sel.trim().isEmpty() || "all".equalsIgnoreCase(sel)) return "";
        String n = (nAlias == null || nAlias.isEmpty()) ? "n" : nAlias;
        return " AND " + n + ".deck_id = ?";
    }
    private static int bindDeckFilterIfAny(PreparedStatement ps, int idx) throws SQLException {
        String sel = com.memorizer.app.Config.get("app.deck.filter", "all");
        if (sel == null || sel.trim().isEmpty() || "all".equalsIgnoreCase(sel)) return idx;
        long did = Long.parseLong(sel.trim());
        ps.setLong(idx++, did);
        return idx;
    }
}
