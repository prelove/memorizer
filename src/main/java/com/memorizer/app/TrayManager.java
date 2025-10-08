// src/main/java/com/memorizer/app/TrayManager.java
package com.memorizer.app;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.memorizer.service.StudyService;
import com.memorizer.ui.StealthStage;

import javafx.application.Platform;

public final class TrayManager {
    private final TrayIcon trayIcon;
    private final StealthStage stealthStage;
    private final StudyService study;

    public TrayManager(StealthStage stealthStage, StudyService study) {
        this.stealthStage = stealthStage;
        this.study = study;

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

        menu.add(miShow);
        menu.add(miHide);
        menu.addSeparator();
        menu.add(miH2);
        menu.addSeparator();
        menu.add(miExit);

        trayIcon = new TrayIcon(image, "Memorizer", menu);
        trayIcon.setImageAutoSize(true);

        // ---- use FX thread for UI ops ----
        miShow.addActionListener(e -> Platform.runLater(() -> {
            java.util.Optional<com.memorizer.service.StudyService.CardView> opt = study.nextCard();
            if (opt.isPresent()) {
                com.memorizer.service.StudyService.CardView v = opt.get();
                stealthStage.showCard(v.front, v.back);
                stealthStage.show();
            } else {
                trayIcon.displayMessage("Memorizer", "No due/new cards.", TrayIcon.MessageType.INFO);
            }
        }));


        miHide.addActionListener(e -> Platform.runLater(stealthStage::hide));
        miH2.addActionListener(openH2());
        miExit.addActionListener(e -> {
            H2ConsoleServer.stop();
            Platform.runLater(stealthStage::close);
            tray.remove(trayIcon);
            System.exit(0);
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException ex) {
            throw new RuntimeException("Failed to add tray icon", ex);
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
        int sz = 16;
        Image img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) img.getGraphics();
        g.setColor(new Color(60, 180, 75));
        g.fillOval(0,0,sz,sz);
        g.dispose();
        return img;
    }
}
