package com.memorizer.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Handles taskbar owner/visibility and window geometry for the stealth banner.
 * Keeps logic isolated from StealthStage to simplify responsibilities.
 */
final class StealthWindowPositioner {
    private StealthWindowPositioner() {}

    /**
     * Initialize owner to hide the window from taskbar when enabled in config.
     */
    static void initOwnerIfHidden(Stage stage) {
        if (Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.hide-from-taskbar", "true"))) {
            Stage owner = com.memorizer.app.AppContext.getOwner();
            if (owner != null) stage.initOwner(owner);
        }
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        stage.setOpacity(Double.parseDouble(com.memorizer.app.Config.get("app.window.opacity", "0.90")));
    }

    /**
     * Apply Normal/Mini geometry. Mini uses strict height; Normal is taller.
     * Honors overlay-taskbar flag for Normal mode.
     */
    static void applyGeometry(Stage stage, StealthStage.UIMode mode) {
        Rectangle2D vis = Screen.getPrimary().getVisualBounds();
        double screenW = vis.getWidth(), screenH = vis.getHeight();
        double screenX = vis.getMinX(), screenY = vis.getMinY();

        boolean overlay = Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.overlay-taskbar", "false"));
        double scale = 1.0;
        try { int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution(); scale = Math.max(1.0, dpi / 96.0); } catch (Throwable ignored) {}

        if (mode == StealthStage.UIMode.MINI) {
            double h = 44 * scale;
            double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.mini.width-fraction", "0.5"));
            double w = Math.max(320, screenW * frac);
            stage.setWidth(w); stage.setHeight(h);
            stage.setX(screenX + (screenW - w) / 2.0);
            stage.setY(screenY + screenH - h - 4); // 从任务栏往上移4像素
        } else {
            double h = 76 * scale;
            double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.stealth.width-fraction", "0.98"));
            double w = Math.max(480, screenW * frac);
            stage.setWidth(w); stage.setHeight(h);
            if (overlay) {
                // Best-effort: snap to bottom edge; some OSes may disallow true overlay
                stage.setX(screenX + (screenW - w) / 2.0);
                stage.setY(screenY + screenH - h);
            } else {
                stage.setX(screenX + (screenW - w) / 2.0);
                stage.setY(screenY + screenH - h - 2);
            }
        }
    }
}

