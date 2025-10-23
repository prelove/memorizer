package com.memorizer.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.memorizer.db.Database;
import com.memorizer.service.StudyService;
import com.memorizer.ui.MainStage;
import com.memorizer.ui.StealthStage;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;


/**
 * JavaFX application entrypoint.
 * Wires database/migrations, console, tray, scheduler, and stages.
 */
public class MainApp extends Application {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);
    private StealthStage stealthStage;
    private MainStage mainStage;
    private TrayManager trayManager;
    private Scheduler scheduler;
    private StudyService studyService;
    
    private Stage toolOwner;

    @Override
    public void start(Stage primaryStage) {
        Platform.setImplicitExit(false);

        Database.start();
        H2ConsoleServer.startIfEnabled();

        // 设置应用图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                primaryStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            log.warn("Failed to load application icon", e);
        }

        // Invisible owner to keep child windows off the taskbar
        toolOwner = new Stage(StageStyle.UTILITY);
        toolOwner.setOpacity(0);
        toolOwner.setWidth(1); toolOwner.setHeight(1);
        toolOwner.setX(-10000); toolOwner.setY(-10000);
        toolOwner.setIconified(true);
        // 为工具窗口也设置图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                toolOwner.getIcons().add(icon);
            }
        } catch (Exception e) {
            log.warn("Failed to load tool owner icon", e);
        }
        toolOwner.show();
        AppContext.setOwner(toolOwner);

        stealthStage = new StealthStage();                  // it will use owner if enabled
        // 为 stealth 窗口设置图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                stealthStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            log.warn("Failed to load stealth stage icon", e);
        }
        studyService = new StudyService();
        com.memorizer.service.PlanService planService = new com.memorizer.service.PlanService();
        studyService.bindPlan(planService);
        stealthStage.bindStudy(studyService);

        scheduler = new Scheduler(studyService, stealthStage);
        scheduler.start();

        mainStage = new MainStage(studyService, scheduler);
        // 为主窗口设置图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                mainStage.getIcons().add(icon);
            }
        } catch (Exception e) {
            log.warn("Failed to load main stage icon", e);
        }

        AppContext.setStealth(stealthStage);
        AppContext.setMain(mainStage);

        trayManager = new TrayManager(stealthStage, mainStage, studyService, scheduler);
        AppContext.setTray(trayManager);
        log.info("Memorizer started.");
    }

    @Override
    public void stop() {
        try {
            if (trayManager != null) trayManager.shutdown();
            try { com.memorizer.app.WebServerManager.get().stop(); } catch (Exception ignored) {}
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
