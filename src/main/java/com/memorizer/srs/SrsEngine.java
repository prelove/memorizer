package com.memorizer.srs;

import com.memorizer.model.Card;
import com.memorizer.model.Rating;

import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

/**
 * Minimal SM-2 inspired scheduling:
 * - ease starts at 2.5, bounded [1.3, 2.8]
 * - AGAIN -> 10 minutes, ease -0.4, lapses+1
 * - HARD  -> next = max(1d, prev*1.2), ease -0.15
 * - GOOD  -> next = max(1d, prev*ease), ease +0.10
 * - EASY  -> next = max(1d, prev*ease*1.3), ease +0.15
 * For brand-new cards (interval null), seed intervals:
 * - AGAIN: 10 min; HARD/GOOD: 1 day; EASY: 3 days
 */
public class SrsEngine {
    private static final double EASE_MIN = 1.3;
    private static final double EASE_MAX = 2.8;

    public static class Result {
        public double nextIntervalDays;
        public double newEase;
        public boolean isLapse;
    }

    public Result schedule(Card c, Rating rating) {
        double prev = c.intervalDays == null ? 0.0 : c.intervalDays;
        double ease = c.ease <= 0 ? 2.5 : c.ease;
        Result r = new Result();

        switch (rating) {
            case AGAIN:
                r.nextIntervalDays = minutes(10);
                r.newEase = clamp(ease - 0.40, EASE_MIN, EASE_MAX);
                r.isLapse = true;
                break;
            case HARD:
                r.nextIntervalDays = Math.max(1.0, prev > 0 ? prev * 1.20 : 1.0);
                r.newEase = clamp(ease - 0.15, EASE_MIN, EASE_MAX);
                break;
            case GOOD:
                r.nextIntervalDays = Math.max(1.0, prev > 0 ? prev * ease : 1.0);
                r.newEase = clamp(ease + 0.10, EASE_MIN, EASE_MAX);
                break;
            case EASY:
            default:
                r.nextIntervalDays = Math.max(1.0, prev > 0 ? prev * ease * 1.30 : 3.0);
                r.newEase = clamp(ease + 0.15, EASE_MIN, EASE_MAX);
                break;
        }
        return r;
    }

    public static Timestamp nowPlusDays(double days) {
        long ms = System.currentTimeMillis() + (long) (days * 24 * 60 * 60 * 1000);
        return new Timestamp(ms);
    }

    private static double minutes(int m) {
        return m / (24.0 * 60.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
