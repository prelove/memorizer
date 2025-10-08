package com.memorizer.app;

import com.memorizer.db.Database;
import com.memorizer.ui.StealthStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX application bootstrap.
 * Stage A: start DB, migrations, H2 console, tray, and a hidden stealth stage.
 */
public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private StealthStage stealthStage;
    private TrayManager trayManager;

    @Override
    public void start(Stage primaryStage) {
        // Init DB + migrations
        Database.start();
        // Start H2 console (localhost)
        H2ConsoleServer.startIfEnabled();

        // Prepare stealth stage (hidden by default)
        stealthStage = new StealthStage();

        // Add system tray
        trayManager = new TrayManager(stealthStage);

        // Do not show any primary window; we live in tray
        log.info("Memorizer started. Use tray menu to show/hide stealth banner.");
    }

    @Override
    public void stop() {
        try {
            H2ConsoleServer.stop();
            Database.stop();
        } catch (Exception ignored) {}
        Platform.exit();
    }

    public static void main(String[] args) {
        // For JDK8 + JavaFX, just launch
        launch(args);
    }
}
