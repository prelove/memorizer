package com.memorizer.ui;

import com.memorizer.app.AppContext;
import com.memorizer.app.Config;
import com.memorizer.app.Scheduler;
import com.memorizer.app.TrayActions;
import com.memorizer.app.WebServerManager;
import com.memorizer.db.DeckRepository;
import com.memorizer.model.Deck;
import com.memorizer.service.StudyService;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.util.List;

/**
 * Builder for the main application menu bar.
 * Constructs File, Data, Study, View, and Help menus with all actions.
 */
public class MenuBarBuilder {
    private final Stage owner;
    private final StudyService studyService;
    private final Scheduler scheduler;
    private final Runnable refreshStatsCallback;
    private final Runnable reloadPlanCallback;

    public MenuBarBuilder(Stage owner, StudyService studyService, Scheduler scheduler,
                         Runnable refreshStatsCallback, Runnable reloadPlanCallback) {
        this.owner = owner;
        this.studyService = studyService;
        this.scheduler = scheduler;
        this.refreshStatsCallback = refreshStatsCallback;
        this.reloadPlanCallback = reloadPlanCallback;
    }

    /**
     * Build the complete menu bar.
     * @return configured MenuBar
     */
    public MenuBar build() {
        return new MenuBar(
            buildFileMenu(),
            buildDataMenu(),
            buildStudyMenu(),
            buildViewMenu(),
            buildHelpMenu()
        );
    }

    /**
     * Build File menu with import/export and exit options.
     */
    private Menu buildFileMenu() {
        Menu menu = new Menu("File");

        MenuItem miImport = new MenuItem("Import Excel...");
        miImport.setOnAction(e -> TrayActions.openImportDialog());

        MenuItem miTemplate = new MenuItem("Save Import Template...");
        miTemplate.setOnAction(e -> TrayActions.saveTemplateDialog());

        MenuItem miH2 = new MenuItem("Open H2 Console");
        miH2.setOnAction(e -> TrayActions.openH2Console());

        MenuItem miExit = new MenuItem("Exit");
        miExit.setOnAction(e -> System.exit(0));

        menu.getItems().addAll(
            miImport, miTemplate,
            new SeparatorMenuItem(),
            miH2,
            new SeparatorMenuItem(),
            miExit
        );

        return menu;
    }

    /**

     * Build Data menu for creating decks and entries.

     */

    private Menu buildDataMenu() {

        Menu menu = new Menu("Data");



        MenuItem miNewDeck = new MenuItem("New Deck...");

        miNewDeck.setOnAction(e -> DialogFactory.showNewDeckDialog(owner, this::showNotice));



        MenuItem miNewEntry = new MenuItem("New Entry...");

        miNewEntry.setOnAction(e -> DialogFactory.showNewEntryDialog(owner, this::showNotice));

        MenuItem miManageDecks = new MenuItem("Manage Decks...");
        miManageDecks.setOnAction(e -> {
            ManageDecksStage m = new ManageDecksStage(owner);
            m.show();
        });

        // Add sync server submenu

        Menu mSync = buildSyncMenu();



        menu.getItems().addAll(miNewDeck, miNewEntry, miManageDecks, new SeparatorMenuItem(), mSync);



        return menu;

    }

    /**
     * Build Sync submenu with server controls.
     */
    private Menu buildSyncMenu() {
        Menu menu = new Menu("Sync Server");

        MenuItem miEnable = new MenuItem("Enable Server");
        miEnable.setOnAction(e -> enableSyncServer());

        MenuItem miDisable = new MenuItem("Disable Server");
        miDisable.setOnAction(e -> disableSyncServer());

        MenuItem miPair = new MenuItem("Pair Mobile");
        miPair.setOnAction(e -> openPairingPage());

        MenuItem miStatus = new MenuItem("Server Status");
        miStatus.setOnAction(e -> showServerStatus());

        menu.getItems().addAll(miEnable, miDisable, miPair, new SeparatorMenuItem(), miStatus);

        return menu;
    }

    /**
     * Enable sync server.
     */
    private void enableSyncServer() {
        try {
            com.memorizer.app.WebServerManager manager = com.memorizer.app.WebServerManager.get();
            if (!manager.isRunning()) {
                manager.start();
                // Copy base URL to clipboard for convenience
                String base = manager.getBaseUrl();
                if (base != null) {
                    try {
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                                .setContents(new java.awt.datatransfer.StringSelection(base), null);
                    } catch (Exception ignored) {}
                    showNotice("Sync server enabled: " + base + " (copied)");
                } else {
                    showNotice("Sync server enabled");
                }
            } else {
                showNotice("Sync server is already running");
            }
        } catch (Exception ex) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                "Failed to enable sync server: " + ex.getMessage(), 
                javafx.scene.control.ButtonType.OK);
            alert.showAndWait();
        }
    }

    /**
     * Disable sync server.
     */
    private void disableSyncServer() {
        try {
            com.memorizer.app.WebServerManager manager = com.memorizer.app.WebServerManager.get();
            if (manager.isRunning()) {
                manager.stop();
                showNotice("Sync server disabled");
            } else {
                showNotice("Sync server is not running");
            }
        } catch (Exception ex) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                "Failed to disable sync server: " + ex.getMessage(), 
                javafx.scene.control.ButtonType.OK);
            alert.showAndWait();
        }
    }

    /**
     * Show server status.
     */
    private void showServerStatus() {
        try {
            com.memorizer.app.WebServerManager manager = com.memorizer.app.WebServerManager.get();
            String status = manager.isRunning() ? "Running on port " + manager.getPort() : "Stopped";
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION, 
                "Sync Server Status: " + status, 
                javafx.scene.control.ButtonType.OK);
            alert.showAndWait();
        } catch (Exception ex) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                "Failed to get server status: " + ex.getMessage(), 
                javafx.scene.control.ButtonType.OK);
            alert.showAndWait();
        }
    }

    /**
     * Build Study menu with scheduler controls.
     */
    private Menu buildStudyMenu() {
        Menu menu = new Menu("Study");

        MenuItem miOpenStudy = new MenuItem("Open Study Window");
        miOpenStudy.setOnAction(e -> openStudyWindow());

        MenuItem miShowNow = new MenuItem("Show Now (Banner)");
        miShowNow.setOnAction(e -> TrayActions.showStealthNow(studyService));

        MenuItem miPause = new MenuItem("Pause Reminders");
        miPause.setOnAction(e -> scheduler.pause());

        MenuItem miResume = new MenuItem("Resume Reminders");
        miResume.setOnAction(e -> scheduler.resume());

        MenuItem miSnooze = new MenuItem("Snooze 10 min");
        miSnooze.setOnAction(e -> scheduler.snooze(Config.getInt("app.study.snooze-minutes", 10)));

        // Immediate rebuild action
        MenuItem miRebuildPlan = new MenuItem("Rebuild Today's Plan Now");
        miRebuildPlan.setOnAction(e -> {
            studyService.rebuildTodayPlan();
            // Refresh panels
            reloadPlanCallback.run();
            refreshStatsCallback.run();
            // Update banner to reflect new plan immediately
            try {
                StealthStage stealth = AppContext.getStealth();
                if (stealth != null) {
                    java.util.Optional<com.memorizer.service.StudyService.CardView> ov = studyService.nextFromPlanPreferred(false);
                    if (ov.isPresent()) {
                        int batch = Config.getInt("app.study.batch-size", 3);
                        stealth.startBatch(batch);
                        stealth.showCardView(ov.get());
                        stealth.showAndFocus();
                    }
                }
            } catch (Exception ignored) {}
        });

        // Scheduler mode submenu
        Menu mSchedMode = buildSchedulerModeMenu();

        menu.getItems().addAll(
            miOpenStudy, miShowNow,
            new SeparatorMenuItem(),
            miRebuildPlan,
            new SeparatorMenuItem(),
            mSchedMode,
            new SeparatorMenuItem(),
            miPause, miResume, miSnooze
        );

        return menu;
    }

    /**
     * Build scheduler mode submenu (Due-Driven vs Periodic).
     */
    private Menu buildSchedulerModeMenu() {
        Menu menu = new Menu("Scheduler Mode");
        ToggleGroup toggleGroup = new ToggleGroup();

        RadioMenuItem miDue = new RadioMenuItem("Due-Driven (SRS)");
        RadioMenuItem miPeriodic = new RadioMenuItem("Periodic");
        miDue.setToggleGroup(toggleGroup);
        miPeriodic.setToggleGroup(toggleGroup);

        String mode = Config.get("app.study.scheduler.mode", "due");
        boolean isDueMode = "due".equalsIgnoreCase(mode);
        miDue.setSelected(isDueMode);
        miPeriodic.setSelected(!isDueMode);

        miDue.setOnAction(e -> {
            if (miDue.isSelected()) {
                Config.set("app.study.scheduler.mode", "due");
                scheduler.rescheduleNow();
                refreshStatsCallback.run();
            }
        });

        miPeriodic.setOnAction(e -> {
            if (miPeriodic.isSelected()) {
                Config.set("app.study.scheduler.mode", "periodic");
                scheduler.rescheduleNow();
                refreshStatsCallback.run();
            }
        });

        menu.getItems().addAll(miDue, miPeriodic);
        return menu;
    }

    /**
     * Build View menu with theme and deck filter options.
     */
    private Menu buildViewMenu() {
        Menu menu = new Menu("View");

        // Theme submenu
        Menu mTheme = buildThemeMenu();

        // Deck filter submenu
        Menu mDeck = buildDeckFilterMenu();

        MenuItem miPrefs = new MenuItem("Preferences...");
        miPrefs.setOnAction(e -> openPreferences());

        menu.getItems().addAll(
            mTheme,
            new SeparatorMenuItem(),
            mDeck,
            new SeparatorMenuItem(),
            miPrefs
        );

        return menu;
    }

    /**
     * Build theme selection submenu.
     */
    private Menu buildThemeMenu() {
        Menu menu = new Menu("Theme");

        CheckMenuItem miDark = new CheckMenuItem("Dark");
        CheckMenuItem miLight = new CheckMenuItem("Light");

        boolean isLight = "light".equalsIgnoreCase(Config.get("app.ui.theme", "dark"));
        miLight.setSelected(isLight);
        miDark.setSelected(!isLight);

        // Mutual exclusion
        miDark.setOnAction(e -> {
            if (miDark.isSelected()) {
                miLight.setSelected(false);
                setTheme(false);
            } else if (!miLight.isSelected()) {
                miDark.setSelected(true);
            }
        });

        miLight.setOnAction(e -> {
            if (miLight.isSelected()) {
                miDark.setSelected(false);
                setTheme(true);
            } else if (!miDark.isSelected()) {
                miLight.setSelected(true);
            }
        });

        menu.getItems().addAll(miDark, miLight);
        return menu;
    }

    /**
     * Build deck filter submenu.
     */
    private Menu buildDeckFilterMenu() {
        Menu menu = new Menu("Deck");
        ToggleGroup toggleGroup = new ToggleGroup();

        RadioMenuItem miAll = new RadioMenuItem("All Decks");
        miAll.setToggleGroup(toggleGroup);

        String selectedFilter = Config.get("app.deck.filter", "all");
        if ("all".equalsIgnoreCase(selectedFilter)) {
            miAll.setSelected(true);
        }

        miAll.setOnAction(e -> {
            Config.set("app.deck.filter", "all");
            applyDeckFilter();
        });

        menu.getItems().add(miAll);

        // Add deck-specific filters
        List<Deck> decks = new DeckRepository().listAll();
        for (Deck deck : decks) {
            RadioMenuItem item = new RadioMenuItem(deck.name + " (#" + deck.id + ")");
            item.setToggleGroup(toggleGroup);

            if (String.valueOf(deck.id).equals(selectedFilter)) {
                item.setSelected(true);
            }

            item.setOnAction(ev -> {
                Config.set("app.deck.filter", String.valueOf(deck.id));
                applyDeckFilter();
            });

            menu.getItems().add(item);
        }

        return menu;
    }

    /**
     * Build Help menu.
     */
    private Menu buildHelpMenu() {
        Menu menu = new Menu("Help");

        MenuItem miManual = new MenuItem("User Manual...");
        miManual.setOnAction(e -> DialogFactory.showUserManual(owner));

        MenuItem miAbout = new MenuItem("About");
        miAbout.setOnAction(e -> showAboutDialog());

        menu.getItems().addAll(miManual, new SeparatorMenuItem(), miAbout);

        return menu;
    }

    /**
     * Set application theme (light/dark).
     */
    private void setTheme(boolean light) {
        Config.set("app.ui.theme", light ? "light" : "dark");
        try {
            StealthStage stealth = AppContext.getStealth();
            if (stealth != null) {
                stealth.setTheme(light ? StealthStage.ThemeMode.LIGHT : StealthStage.ThemeMode.DARK);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Apply deck filter and refresh views.
     */
    private void applyDeckFilter() {
        studyService.rebuildTodayPlan();
        reloadPlanCallback.run();
        refreshStatsCallback.run();
        
        try {
            StealthStage stealth = AppContext.getStealth();
            if (stealth != null) {
                stealth.refreshTodayProgress();
                boolean autoStart = Config.getBool("app.deck.switch.autostart-batch", true);
                java.util.Optional<com.memorizer.service.StudyService.CardView> ov = studyService.nextFromPlanPreferred(false);
                if (ov.isPresent()) {
                    if (autoStart) {
                        int batch = Config.getInt("app.study.batch-size", 3);
                        stealth.startBatch(batch);
                        stealth.showCardView(ov.get());
                        stealth.showAndFocus();
                    } else {
                        // Only switch the displayed card; no new batch or focus steal
                        stealth.showCardView(ov.get());
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Open study window.
     */
    private void openStudyWindow() {
        // This will be handled by MainStage
        if (owner instanceof MainStage) {
            ((MainStage) owner).openStudyWindow();
        }
    }

    /**
     * Open preferences dialog.
     */
    private void openPreferences() {
        StealthStage stealth = AppContext.getStealth();
        PreferencesStage dialog = new PreferencesStage(studyService, scheduler, stealth);
        dialog.initOwner(owner);
        dialog.showAndWait();
        refreshStatsCallback.run();
    }

    /**
     * Show about dialog.
     */
    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Memorizer");
        alert.setHeaderText("Memorizer");
        alert.setContentText("Simple spaced repetition helper.\n   You");
        alert.showAndWait();
    }

    /**
     * Open pairing page in browser.
     */
    private void openPairingPage() {
        try {
            com.memorizer.app.WebServerManager manager = com.memorizer.app.WebServerManager.get();
            if (!manager.isRunning()) {
                manager.start();
            }
            
            int port = manager.getPort();
            if (port == 0) {
                port = Config.getInt("app.web.port", 7070);
            }
            
            String httpsUrl = "https://localhost:" + port + "/pair";
            String httpUrl = "http://localhost:" + port + "/pair";
            
            boolean success = com.memorizer.util.Browse.open(httpsUrl);
            if (!success) {
                com.memorizer.util.Browse.open(httpUrl);
            }
        } catch (Exception ex) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR, 
                "Open pairing page failed: " + ex.getMessage(), 
                javafx.scene.control.ButtonType.OK);
            alert.showAndWait();
        }
    }

    /**
     * Show notification message (to be implemented by MainStage).
     */
    private void showNotice(String message) {
        if (owner instanceof MainStage) {
            ((MainStage) owner).showNotice(message);
        }
    }
}
