package com.memorizer.app;

import com.memorizer.db.Database;
import com.memorizer.service.StudyService;
import com.memorizer.ui.StealthStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX application bootstrap.
 * Stage A: DB + H2 console + tray.
 * Step 2: StudyService + Scheduler wired to stealth banner.
 */
public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private StealthStage stealthStage;
    private TrayManager trayManager;
    private Scheduler scheduler;
    private StudyService studyService;

    @Override
    public void start(Stage primaryStage) {
        // Init DB + migrations
        Database.start();
        // Start H2 console (localhost)
        H2ConsoleServer.startIfEnabled();

        // Prepare stealth stage
        stealthStage = new StealthStage();

        // Study service + bind to stage
        studyService = new StudyService();
        stealthStage.bindStudy(studyService);

        // System tray
        trayManager = new TrayManager(stealthStage, studyService);
        
        // Scheduler (randomized interval)
        scheduler = new Scheduler(studyService, stealthStage);
        scheduler.start();

        log.info("Memorizer started. Use tray menu to show/hide stealth banner.");
    }

    @Override
    public void stop() {
        try {
            if (scheduler != null) scheduler.stop();
            H2ConsoleServer.stop();
            Database.stop();
        } catch (Exception ignored) {}
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
