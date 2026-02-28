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
     *
     * <p>Window dimensions are set in <em>JavaFX logical pixels</em> — the
     * rendering pipeline scales to physical pixels automatically when HiDPI is
     * active ({@code prism.allowhidpi=true}).  We therefore use
     * {@link ScreenUtil#jfxVisualBounds} for screen metrics (consistent
     * coordinate space) and do NOT multiply by any DPI ratio for width/height.
     * The user-controlled {@code app.ui.font-scale} adjusts the banner height
     * so that users on very-high-DPI screens running at 100% OS scaling can
     * still get a comfortably-sized banner.</p>
     */
    static void applyGeometry(Stage stage, StealthStage.UIMode mode) {
        // AWT: edge detection + identify which device the pointer is on
        ScreenUtil.TaskbarInfo tb = ScreenUtil.taskbarFor(ScreenUtil.activeDevice());

        // JavaFX visual bounds — logical pixels, same coordinate space as Stage.setX/Y/Width/Height
        Rectangle2D vis = ScreenUtil.jfxVisualBounds(tb.device);
        double screenX = vis.getMinX();
        double screenY = vis.getMinY();
        double screenW = vis.getWidth();
        double screenH = vis.getHeight();

        // AWT taskbar rect is also in logical pixels on Java 9+ DPI-aware JVMs.
        // Derive a scale ratio between AWT device bounds and JavaFX bounds to
        // safely convert the AWT taskbar coordinates to JavaFX logical space.
        double hRatio = 1.0, vRatio = 1.0;
        try {
            java.awt.Rectangle awtBounds = tb.device.getDefaultConfiguration().getBounds();
            if (awtBounds.width > 0) hRatio = screenW / awtBounds.width;
            if (awtBounds.height > 0) vRatio = screenH / awtBounds.height;
        } catch (Throwable ignored) {}
        double tbX = tb.rect.x * hRatio;
        double tbY = tb.rect.y * vRatio;
        double tbW = tb.rect.w * hRatio;
        double tbH = tb.rect.h * vRatio;

        boolean overlay = Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.overlay-taskbar", "false"));

        // User-controlled font scale: adjusts banner height (and therefore the font
        // scale applied by StealthStage.applyFontScale).  Does NOT interact with OS
        // DPI scaling — those are handled transparently by JavaFX.
        double fontScale = com.memorizer.app.Config.getFontScale();

        double gap = 4.0; // distance from taskbar edge when not overlaying

        if (mode == StealthStage.UIMode.MINI) {
            double h = Math.round(44 * fontScale);
            double frac = parseDouble(com.memorizer.app.Config.get("app.window.mini.width-fraction", "0.5"), 0.5);
            double w = Math.max(320, screenW * frac);
            stage.setWidth(w); stage.setHeight(h);

            switch (tb.edge) {
                case BOTTOM:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(tbY - h - gap);
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
                    stage.setX(tbX - w - gap);
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                default:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(screenY + screenH - h - gap);
            }
        } else {
            double h = Math.round(76 * fontScale);
            double frac = parseDouble(com.memorizer.app.Config.get("app.window.stealth.width-fraction", "0.98"), 0.98);
            double w = Math.max(480, screenW * frac);
            stage.setWidth(w); stage.setHeight(h);

            switch (tb.edge) {
                case BOTTOM:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(overlay ? tbY : (tbY - h - 2));
                    break;
                case TOP:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(overlay ? tbY : (screenY + 2));
                    break;
                case LEFT:
                    stage.setX(overlay ? tbX : (screenX + 2));
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                case RIGHT:
                    stage.setX(overlay ? (tbX + tbW - w) : (tbX - w - 2));
                    stage.setY(screenY + (screenH - h) / 2.0);
                    break;
                default:
                    stage.setX(screenX + (screenW - w) / 2.0);
                    stage.setY(screenY + screenH - h - 2);
            }
        }
    }

    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Throwable ignored) { return def; }
    }
}

