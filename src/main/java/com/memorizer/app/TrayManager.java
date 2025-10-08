package com.memorizer.app;

import com.memorizer.service.StudyService;
import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;
import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class TrayManager {
    private final TrayIcon trayIcon;
    private final StealthStage stealthStage;
    private final MainStage mainStage;
    private final StudyService study;
    private final Scheduler scheduler;

    private final MenuItem miPause = new MenuItem("Pause Reminders");
    private final MenuItem miResume = new MenuItem("Resume Reminders");

    public TrayManager(StealthStage stealthStage, MainStage mainStage, StudyService study, Scheduler scheduler) {
        this.stealthStage = stealthStage;
        this.mainStage = mainStage;
        this.study = study;
        this.scheduler = scheduler;

        if (!SystemTray.isSupported()) throw new IllegalStateException("System tray not supported on this platform.");
        final SystemTray tray = SystemTray.getSystemTray();

        Image image = loadIcon();
        PopupMenu menu = new PopupMenu();

        MenuItem miOpenMain = new MenuItem("Open Main Window");
        MenuItem miShow = new MenuItem("Show Stealth Now");
        MenuItem miSnooze = new MenuItem("Snooze 10 min");
        MenuItem miImport = new MenuItem("Import Excel...");
        MenuItem miTemplate = new MenuItem("Save Import Template...");
        MenuItem miH2   = new MenuItem("Open H2 Console");
        MenuItem miExit = new MenuItem("Exit");

        menu.add(miOpenMain);
        menu.add(miShow);
        menu.addSeparator();
        menu.add(miPause);
        menu.add(miResume);
        menu.add(miSnooze);
        menu.addSeparator();
        menu.add(miImport);
        menu.add(miTemplate);
        menu.addSeparator();
        menu.add(miH2);
        menu.addSeparator();
        menu.add(miExit);

        trayIcon = new TrayIcon(image, "Memorizer", menu);
        trayIcon.setImageAutoSize(true);
        TrayActions.attachTrayIcon(trayIcon);

        miOpenMain.addActionListener(e -> Platform.runLater(mainStage::showAndFocus));

        miShow.addActionListener(e -> Platform.runLater(() -> {
            TrayActions.showStealthNow(study);
        }));

        miPause.addActionListener(e -> { scheduler.pause(); updatePauseMenu(); });
        miResume.addActionListener(e -> { scheduler.resume(); updatePauseMenu(); });

        miSnooze.addActionListener(e -> scheduler.snooze(Config.getInt("app.study.snooze-minutes", 10)));

        miImport.addActionListener(e -> TrayActions.openImportDialog());
        miTemplate.addActionListener(e -> TrayActions.saveTemplateDialog());

        miH2.addActionListener(openH2());
        miExit.addActionListener(e -> {
            H2ConsoleServer.stop();
            Platform.runLater(() -> {
                stealthStage.close();
                if (mainStage != null) mainStage.close();
            });
            tray.remove(trayIcon);
            scheduler.stop();
            System.exit(0);
        });

        updatePauseMenu();
        try { tray.add(trayIcon); } catch (AWTException ex) { throw new RuntimeException("Failed to add tray icon", ex); }
    }

    private void updatePauseMenu() {
        boolean paused = scheduler.isPaused();
        miPause.setEnabled(!paused);
        miResume.setEnabled(paused);
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
