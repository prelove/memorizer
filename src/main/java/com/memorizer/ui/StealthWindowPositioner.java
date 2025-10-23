package com.memorizer.ui;

import com.memorizer.util.ScreenUtil;
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
        // Determine active screen via AWT pointer
        ScreenUtil.TaskbarInfo tb = ScreenUtil.taskbarFor(ScreenUtil.activeDevice());
        ScreenUtil.Rect visAwt = ScreenUtil.visibleRect(tb.device);
        // Map to doubles
        double screenX = visAwt.x;
        double screenY = visAwt.y;
        double screenW = visAwt.w;
        double screenH = visAwt.h;

        boolean overlay = Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.overlay-taskbar", "false"));
        double scale = 1.0;
        try { int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution(); scale = Math.max(1.0, dpi / 96.0); } catch (Throwable ignored) {}

        double gap = 4.0; // distance from taskbar edge when not overlaying

        if (mode == StealthStage.UIMode.MINI) {
            double h = 44 * scale;
            double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.mini.width-fraction", "0.5"));
            double w = Math.max(320, screenW * frac);
            stage.setWidth(w); stage.setHeight(h);

            switch (tb.edge) {
                case BOTTOM:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(tb.rect.y - h - gap);
                    break;
                case TOP:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(screenY + gap);
                    break;
                case LEFT:
                    stage.setX(screenX + gap);
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                case RIGHT:
                    stage.setX(tb.rect.x - w - gap);
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                default:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(screenY + screenH - h - gap);
            }
        } else {
            double h = 76 * scale;
            double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.stealth.width-fraction", "0.98"));
            double w = Math.max(480, screenW * frac);
            stage.setWidth(w); stage.setHeight(h);

            switch (tb.edge) {
                case BOTTOM:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(overlay ? tb.rect.y : (tb.rect.y - h - 2));
                    break;
                case TOP:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(overlay ? tb.rect.y : (screenY + 2));
                    break;
                case LEFT:
                    // For left taskbar, keep banner near left edge but centered vertically
                    stage.setX(overlay ? tb.rect.x : (screenX + 2));
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                case RIGHT:
                    stage.setX(overlay ? (tb.rect.x + tb.rect.w - w) : (tb.rect.x - w - 2));
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                default:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(screenY + screenH - h - 2);
            }
        }
    }
}

