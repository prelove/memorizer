package com.memorizer.app;

import com.memorizer.importer.ExcelImportService;
import com.memorizer.importer.ExcelTemplateService;
import com.memorizer.service.StudyService;
import com.memorizer.ui.StealthStage;
import javafx.application.Platform;

import java.awt.*;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TrayActions {
    private static final ExcelImportService importer = new ExcelImportService();
    private static final ExcelTemplateService templater = new ExcelTemplateService();
    private static final ExecutorService bg = Executors.newSingleThreadExecutor();

    private static TrayIcon trayIconRef;

    static void attachTrayIcon(TrayIcon icon) {
        trayIconRef = icon;
    }

    public static void showStealthNow(StudyService study) {
        java.util.Optional<com.memorizer.service.StudyService.CardView> opt = study.currentOrNextOrFallback();
        if (opt.isPresent()) {
            com.memorizer.service.StudyService.CardView v = opt.get();
            Platform.runLater(() -> {
                StealthStage stage = com.memorizer.app.AppContext.getStealth();
                stage.showCard(v.front, v.back);
                stage.showAndFocus();
            });
        } else {
            if (trayIconRef != null) trayIconRef.displayMessage("Memorizer", "No cards.", TrayIcon.MessageType.INFO);
        }
    }
    
    public static void openH2Console() {
        com.memorizer.app.H2ConsoleServer.startIfEnabled();
        String url = "http://127.0.0.1:" + com.memorizer.app.Config.get("app.h2.console.port", "8082") + "/";
        boolean ok = com.memorizer.util.Browse.open(url);
        if (!ok && trayIconRef != null) {
            trayIconRef.displayMessage("H2 Console", "Open " + url + " in your browser.", TrayIcon.MessageType.INFO);
        }
    }

    public static void openImportDialog() {
        FileDialog fd = new FileDialog((Frame) null, "Import Excel (.xlsx/.xls)", FileDialog.LOAD);
        fd.setFile("*.xlsx;*.xls");
        fd.setVisible(true);
        if (fd.getFile() == null || fd.getDirectory() == null) return;
        File f = new File(fd.getDirectory(), fd.getFile());
        if (trayIconRef != null) trayIconRef.displayMessage("Memorizer", "Importing " + f.getName() + "...", TrayIcon.MessageType.INFO);
        bg.submit(() -> {
            ExcelImportService.Report rpt = importer.importFile(f);
            if (trayIconRef != null) trayIconRef.displayMessage("Import Result",
                    rpt.message + "\n" + rpt.toString(),
                    "OK".equals(rpt.message) ? TrayIcon.MessageType.INFO : TrayIcon.MessageType.WARNING);
        });
    }

    public static void saveTemplateDialog() {
        FileDialog fd = new FileDialog((Frame) null, "Save Import Template (.xlsx)", FileDialog.SAVE);
        fd.setFile("import_template.xlsx");
        fd.setVisible(true);
        if (fd.getFile() == null || fd.getDirectory() == null) return;
        File out = new File(fd.getDirectory(), fd.getFile());
        try {
            templater.saveTemplate(out);
            if (trayIconRef != null)
                trayIconRef.displayMessage("Memorizer", "Template saved: " + out.getAbsolutePath(), TrayIcon.MessageType.INFO);
        } catch (Exception ex) {
            if (trayIconRef != null)
                trayIconRef.displayMessage("Template Error", ex.getMessage(), TrayIcon.MessageType.ERROR);
        }
    }

    private TrayActions(){}
}
