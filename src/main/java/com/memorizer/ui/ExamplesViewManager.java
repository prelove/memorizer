package com.memorizer.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages examples rendering for both Normal (scrolling list) and Mini (single-line marquee).
 */
final class ExamplesViewManager {
    private final VBox examplesBox;          // Normal mode container (multiple lines)
    private final ScrollPane examplesScroll; // Normal mode scroll pane
    private final Label examplesMini;        // Mini mode single-line label

    private List<String> current = Collections.emptyList();
    private int index = 0;
    private Timeline roller;
    private Timeline marquee;
    private Label marqueeTarget;

    ExamplesViewManager(VBox examplesBox, ScrollPane examplesScroll, Label examplesMini) {
        this.examplesBox = examplesBox;
        this.examplesScroll = examplesScroll;
        this.examplesMini = examplesMini;
        if (examplesScroll != null) {
            examplesScroll.setFitToWidth(true);
            examplesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        }
    }

    /** Set examples list and render first state. */
    void setExamples(List<String> items, StealthStage.UIMode mode) {
        stop();
        current = items == null ? Collections.emptyList() : new ArrayList<>(items);
        index = 0;

        if (current.isEmpty()) {
            hideAll();
            return;
        }

        // Normal renders multiple lines; Mini renders only first line by default
        renderNormalLines();
        renderMiniLine();

        if (shouldAutoroll()) startAutoroll();
        if (mode == StealthStage.UIMode.MINI) startMiniMarquee();
    }

    /** Show examples in Normal mode only. */
    void updateForMode(StealthStage.UIMode mode, int state) {
        if (mode == StealthStage.UIMode.MINI) {
            // Mini: show examples only when state==3
            boolean showMini = (state == 3) && !current.isEmpty();
            if (examplesMini != null) { examplesMini.setVisible(showMini); examplesMini.setManaged(showMini); }
            if (examplesScroll != null) { examplesScroll.setVisible(false); examplesScroll.setManaged(false); }
            if (showMini) startMiniMarquee(); else stopMiniMarquee();
        } else {
            // Normal: examples shown in combined state (2) only
            boolean showNormal = (state == 2) && !current.isEmpty();
            if (examplesScroll != null) { examplesScroll.setVisible(showNormal); examplesScroll.setManaged(showNormal); }
            if (examplesMini != null) { examplesMini.setVisible(false); examplesMini.setManaged(false); }
            stopMiniMarquee();
        }
    }

    void stop() { stopAutoroll(); stopMiniMarquee(); }

    private boolean shouldAutoroll() {
        return Boolean.parseBoolean(com.memorizer.app.Config.get("app.ui.examples.autoroll", "true"))
                && current.size() > 1;
    }

    private void startAutoroll() {
        int interval = com.memorizer.app.Config.getInt("app.ui.examples.roll-interval-ms", 2200);
        stopAutoroll();
        roller = new Timeline(new KeyFrame(Duration.millis(Math.max(500, interval)), e -> {
            index = (index + 1) % current.size();
            renderNormalLines();
            renderMiniLine();
        }));
        roller.setCycleCount(Timeline.INDEFINITE);
        roller.play();
    }

    private void stopAutoroll() { if (roller != null) { roller.stop(); roller = null; } }

    private void renderNormalLines() {
        if (examplesBox == null) return;
        examplesBox.getChildren().clear();
        if (current.isEmpty()) return;
        int max = com.memorizer.app.Config.getInt("app.ui.examples.max-lines", 3);
        int lines = Math.max(1, Math.min(max, current.size()));
        for (int i = 0; i < lines; i++) {
            int idx = (index + i) % current.size();
            String txt = current.get(idx);
            Label line = new Label(txt);
            line.getStyleClass().add("example-line");
            line.setWrapText(true);
            Tooltip tp = new Tooltip(txt == null? "" : txt);
            line.setTooltip(tp);
            examplesBox.getChildren().add(line);
        }
    }

    private void renderMiniLine() {
        if (examplesMini == null) return;
        if (current.isEmpty()) { examplesMini.setText(""); return; }
        String txt = current.get(index == 0 ? 0 : index % current.size());
        examplesMini.setText(txt == null ? "" : txt);
    }

    private void startMiniMarquee() {
        startMiniMarqueeOn(examplesMini);
    }

    private void stopMiniMarquee() { if (marquee != null) { marquee.stop(); marquee = null; } if (marqueeTarget != null) { marqueeTarget.setTranslateX(0); } }

    // Expose helpers for StealthStage mini unified content area
    String currentSingleLine() {
        if (current.isEmpty()) return "";
        int idx = (index == 0 ? 0 : index % current.size());
        String txt = current.get(idx);
        return txt == null ? "" : txt;
    }

    void startMiniMarqueeOn(Label target) {
        if (target == null || !target.isVisible()) return;
        stopMiniMarquee();
        marqueeTarget = target;
        String txt = target.getText() == null ? "" : target.getText();
        if (txt.length() < 28) return; // short: no marquee
        Text meas = new Text(txt);
        meas.setFont(target.getFont());
        double w = meas.getLayoutBounds().getWidth();
        int msPerPx = com.memorizer.app.Config.getInt("app.ui.examples.marquee-ms-per-px", 100);
        marquee = new Timeline(
                new KeyFrame(Duration.ZERO, e -> marqueeTarget.setTranslateX(0)),
                new KeyFrame(Duration.millis(msPerPx * Math.max(50, w)), e -> marqueeTarget.setTranslateX(-w - 32))
        );
        marquee.setCycleCount(Timeline.INDEFINITE);
        marquee.play();
        target.setOnMouseEntered(e -> { if (marquee != null) marquee.pause(); });
        target.setOnMouseExited(e -> { if (marquee != null) marquee.play(); });
    }

    private void hideAll() {
        if (examplesBox != null) examplesBox.getChildren().clear();
        if (examplesScroll != null) { examplesScroll.setVisible(false); examplesScroll.setManaged(false); }
        if (examplesMini != null) { examplesMini.setVisible(false); examplesMini.setManaged(false); }
    }
}
