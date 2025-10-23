package com.memorizer.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Repository for chart data queries.
 * Provides data for dashboard charts and visualizations.
 */
public class ChartRepository {
    
    /**
     * Data class for daily review counts.
     */
    public static class DailyReviewCount {
        public final LocalDate date;
        public final int count;
        
        public DailyReviewCount(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }
    }
    
    /**
     * Data class for rating distribution.
     */
    public static class RatingDistribution {
        public final int rating;
        public final int count;
        
        public RatingDistribution(int rating, int count) {
            this.rating = rating;
            this.count = count;
        }
    }
    
    /**
     * Data class for card status distribution.
     */
    public static class CardStatusDistribution {
        public final String status;
        public final int count;
        
        public CardStatusDistribution(String status, int count) {
            this.status = status;
            this.count = count;
        }
    }
    
    /**
     * Get review counts for the last N days.
     * @param days number of days to look back
     * @return list of daily review counts
     */
    public List<DailyReviewCount> getDailyReviewCounts(int days) {
        List<DailyReviewCount> result = new ArrayList<>();
        
        // Calculate start date
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        Timestamp startTs = new Timestamp(startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        
        // Use DATE_TRUNC or CAST for H2 compatibility
        String sql = "SELECT CAST(reviewed_at AS DATE) as review_date, COUNT(*) as review_count " +
                     "FROM review_log " +
                     "WHERE reviewed_at >= ? " +
                     "GROUP BY CAST(reviewed_at AS DATE) " +
                     "ORDER BY review_date";
        
        try (PreparedStatement ps = Database.get().prepareStatement(sql)) {
            ps.setTimestamp(1, startTs);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate("review_date").toLocalDate();
                    int count = rs.getInt("review_count");
                    result.add(new DailyReviewCount(date, count));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load daily review counts", e);
        }
        
        return result;
    }
    
    /**
     * Get rating distribution for all reviews.
     * @return list of rating distributions
     */
    public List<RatingDistribution> getRatingDistribution() {
        List<RatingDistribution> result = new ArrayList<>();
        
        String sql = "SELECT rating, COUNT(*) as count " +
                     "FROM review_log " +
                     "GROUP BY rating " +
                     "ORDER BY rating";
        
        try (PreparedStatement ps = Database.get().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                int rating = rs.getInt("rating");
                int count = rs.getInt("count");
                result.add(new RatingDistribution(rating, count));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load rating distribution", e);
        }
        
        return result;
    }
    
    /**
     * Get card status distribution.
     * @return list of card status distributions
     */
    public List<CardStatusDistribution> getCardStatusDistribution() {
        List<CardStatusDistribution> result = new ArrayList<>();
        
        String sql = "SELECT " +
                     "CASE " +
                     "  WHEN status = 0 THEN 'New' " +
                     "  WHEN status = 1 THEN 'Learning' " +
                     "  WHEN status = 2 THEN 'Review' " +
                     "  WHEN status = 3 THEN 'Suspended' " +
                     "  ELSE 'Unknown' " +
                     "END as status_label, " +
                     "COUNT(*) as count " +
                     "FROM card " +
                     "GROUP BY status " +
                     "ORDER BY status";
        
        try (PreparedStatement ps = Database.get().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                String status = rs.getString("status_label");
                int count = rs.getInt("count");
                result.add(new CardStatusDistribution(status, count));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load card status distribution", e);
        }
        
        return result;
    }
}