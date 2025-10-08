package com.memorizer.app;

import com.memorizer.ui.StealthStage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage; // <-- add this import
import java.io.IOException;
import java.io.InputStream;

/**
 * System tray integration. Minimal controls:
 * - Show/Hide Stealth
 * - Start/Stop H2 Console
 * - Exit
 */
public final class TrayManager {
    private final TrayIcon trayIcon;
    private final StealthStage stealthStage;

    public TrayManager(StealthStage stealthStage) {
        this.stealthStage = stealthStage;

        if (!SystemTray.isSupported()) {
            throw new IllegalStateException("System tray not supported on this platform.");
        }
        final SystemTray tray = SystemTray.getSystemTray();

        Image image = loadIcon();
        PopupMenu menu = new PopupMenu();

        MenuItem miShow = new MenuItem("Show Stealth");
        MenuItem miHide = new MenuItem("Hide Stealth");
        MenuItem miH2   = new MenuItem("Open H2 Console");
        MenuItem miExit = new MenuItem("Exit");

        // Build menu first (listeners will be wired AFTER trayIcon is assigned)
        menu.add(miShow);
        menu.add(miHide);
        menu.addSeparator();
        menu.add(miH2);
        menu.addSeparator();
        menu.add(miExit);

        // Now create the tray icon (so the field is definitely assigned)
        trayIcon = new TrayIcon(image, "Memorizer", menu);
        trayIcon.setImageAutoSize(true);

        // Wire listeners AFTER trayIcon is initialized to avoid "blank final" error
        miShow.addActionListener(e -> stealthStage.show());
        miHide.addActionListener(e -> stealthStage.hide());
        miH2.addActionListener(openH2());
        miExit.addActionListener(e -> {
            H2ConsoleServer.stop();
            stealthStage.close();
            tray.remove(trayIcon); // use the local 'tray' we already obtained
            System.exit(0);
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            throw new RuntimeException("Failed to add tray icon", e);
        }
    }

    private ActionListener openH2() {
        return e -> {
            H2ConsoleServer.startIfEnabled();
            trayIcon.displayMessage("H2 Console",
                    "Open http://127.0.0.1:" + Config.get("app.h2.console.port", "8082") + "/",
                    TrayIcon.MessageType.INFO);
        };
    }

    private Image loadIcon() {
        try (InputStream in = TrayManager.class.getResourceAsStream("/icon-16.png")) {
            if (in != null) return ImageIO.read(in);
        } catch (IOException ignored) {}
        // Fallback generic image
        int sz = 16;
        Image img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setColor(new Color(60, 180, 75));
        g.fillOval(0,0,sz,sz);
        g.dispose();
        return img;
    }
}
