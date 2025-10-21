package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.db.StatsRepository;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Handles Today progress text and bar. Keeps refresh logic isolated.
 */
final class TodayProgressView {
    private final Label todayLabel;
    private final ProgressBar todayProgress;
    private String suffix = "";

    TodayProgressView(Label todayLabel, ProgressBar todayProgress) {
        this.todayLabel = todayLabel;
        this.todayProgress = todayProgress;
        this.todayProgress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this.todayProgress, Priority.ALWAYS);
    }

    void refresh() {
        int target = Config.getInt("app.study.daily-target", 50);
        int done = 0;
        try { done = new StatsRepository().load().todayReviews; } catch (Exception ignored) {}
        int shown = (target > 0) ? Math.min(done, target) : done;
        String sfx = (suffix == null || suffix.trim().isEmpty()) ? "" : (" " + suffix.trim());
        todayLabel.setText("Today: " + shown + "/" + target + sfx);
        todayProgress.setProgress(target > 0 ? Math.min(1.0, done / (double) target) : 0);
    }

    void setSuffix(String s) { this.suffix = (s == null) ? "" : s; }

    static HBox wrapRight(Label todayLabel, ProgressBar bar) {
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox right = new HBox(10, spacer, todayLabel, bar);
        return right;
    }
}
