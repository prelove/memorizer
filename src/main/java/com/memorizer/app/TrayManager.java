package com.memorizer.app;

import com.memorizer.importer.ExcelImportService;
import com.memorizer.importer.ExcelTemplateService;
import com.memorizer.service.StudyService;
import com.memorizer.ui.StealthStage;
import javafx.application.Platform;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TrayManager {
    private final TrayIcon trayIcon;
    private final StealthStage stealthStage;
    private final StudyService study;
    private final ExcelImportService importer = new ExcelImportService();
    private final ExcelTemplateService templater = new ExcelTemplateService();
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

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
        MenuItem miImport = new MenuItem("Import Excel...");
        MenuItem miTemplate = new MenuItem("Save Import Template...");
        MenuItem miH2   = new MenuItem("Open H2 Console");
        MenuItem miExit = new MenuItem("Exit");

        menu.add(miShow);
        menu.add(miHide);
        menu.addSeparator();
        menu.add(miImport);
        menu.add(miTemplate);
        menu.addSeparator();
        menu.add(miH2);
        menu.addSeparator();
        menu.add(miExit);

        trayIcon = new TrayIcon(image, "Memorizer", menu);
        trayIcon.setImageAutoSize(true);

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

        miImport.addActionListener(e -> doImportExcel());
        miTemplate.addActionListener(e -> saveTemplate());

        miH2.addActionListener(openH2());
        miExit.addActionListener(e -> {
            H2ConsoleServer.stop();
            Platform.runLater(stealthStage::close);
            tray.remove(trayIcon);
            bg.shutdownNow();
            System.exit(0);
        });

        try {
            tray.add(trayIcon);
        } catch (AWTException ex) {
            throw new RuntimeException("Failed to add tray icon", ex);
        }
    }

    private void doImportExcel() {
        FileDialog fd = new FileDialog((Frame) null, "Import Excel (.xlsx/.xls)", FileDialog.LOAD);
        fd.setFile("*.xlsx;*.xls");
        fd.setVisible(true);
        String file = fd.getFile();
        String dir = fd.getDirectory();
        if (file == null || dir == null) return;
        File f = new File(dir, file);

        trayIcon.displayMessage("Memorizer", "Importing " + f.getName() + "...", TrayIcon.MessageType.INFO);
        bg.submit(() -> {
            ExcelImportService.Report rpt = importer.importFile(f);
            trayIcon.displayMessage("Import Result",
                    rpt.message + "\n" + rpt.toString(),
                    "OK".equals(rpt.message) ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.WARNING);
        });
    }

    private void saveTemplate() {
        FileDialog fd = new FileDialog((Frame) null, "Save Import Template (.xlsx)", FileDialog.SAVE);
        fd.setFile("import_template.xlsx");
        fd.setVisible(true);
        String file = fd.getFile();
        String dir = fd.getDirectory();
        if (file == null || dir == null) return;
        File out = new File(dir, file);
        try {
            templater.saveTemplate(out);
            trayIcon.displayMessage("Memorizer", "Template saved: " + out.getAbsolutePath(),
                    TrayIcon.MessageType.INFO);
        } catch (Exception ex) {
            trayIcon.displayMessage("Template Error", ex.getMessage(), TrayIcon.MessageType.ERROR);
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
