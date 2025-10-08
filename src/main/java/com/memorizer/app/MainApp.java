package com.memorizer.app;

import com.memorizer.db.Database;
import com.memorizer.service.StudyService;
import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private StealthStage stealthStage;
    private MainStage mainStage;
    private TrayManager trayManager;
    private Scheduler scheduler;
    private StudyService studyService;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        Database.start();
        H2ConsoleServer.startIfEnabled();

        stealthStage = new StealthStage();
        studyService = new StudyService();
        stealthStage.bindStudy(studyService);

        scheduler = new Scheduler(studyService, stealthStage);
        scheduler.start();

        mainStage = new MainStage(studyService, scheduler);

        // expose to context for TrayActions
        AppContext.setStealth(stealthStage);
        AppContext.setMain(mainStage);

        trayManager = new TrayManager(stealthStage, mainStage, studyService, scheduler);

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
