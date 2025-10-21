package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.app.Scheduler;
import com.memorizer.app.WebServerManager;
import com.memorizer.db.StatsRepository;
import com.memorizer.service.PlanService;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

/**
 * Dashboard panel displaying statistics and sync server controls.
 * Shows card counts, review stats, plan progress, and mobile sync options.
 */
public class DashboardPanel {
    private final StudyService studyService;
    private final Scheduler scheduler;
    
    // Statistics labels
    private final Label lblDue = new Label("-");
    private final Label lblNew = new Label("-");
    private final Label lblTotalCards = new Label("-");
    private final Label lblTotalNotes = new Label("-");
    private final Label lblTodayReviews = new Label("-");
    private final Label lblPlanPending = new Label("-");
    private final Label lblPlanDone = new Label("-");
    private final Label lblPlanTotal = new Label("-");
    
    // Sync server controls
    private Label lblSyncStatus;
    private Button btnToggleSync;

    public DashboardPanel(StudyService studyService, Scheduler scheduler) {
        this.studyService = studyService;
        this.scheduler = scheduler;
    }

    /**
     * Build the dashboard UI pane.
     * @return the constructed dashboard pane
     */
    public Pane build() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(16));
        grid.setHgap(16);
        grid.setVgap(10);

        // Add statistics rows
        int row = 0;
        grid.add(new Label("Due:"), 0, row); 
        grid.add(lblDue, 1, row++);
        grid.add(new Label("New:"), 0, row); 
        grid.add(lblNew, 1, row++);
        grid.add(new Label("Total Cards:"), 0, row); 
        grid.add(lblTotalCards, 1, row++);
        grid.add(new Label("Total Notes:"), 0, row); 
        grid.add(lblTotalNotes, 1, row++);
        grid.add(new Label("Today's Reviews:"), 0, row); 
        grid.add(lblTodayReviews, 1, row++);
        grid.add(new Label("Plan Pending:"), 0, row); 
        grid.add(lblPlanPending, 1, row++);
        grid.add(new Label("Plan Done:"), 0, row); 
        grid.add(lblPlanDone, 1, row++);
        grid.add(new Label("Plan Total:"), 0, row); 
        grid.add(lblPlanTotal, 1, row++);

        // Refresh button
        Button btnRefresh = new Button("Refresh (F5)");
        btnRefresh.getStyleClass().addAll("btn", "btn-primary");
        btnRefresh.setOnAction(e -> refresh());
        grid.add(btnRefresh, 0, row, 2, 1);

        // Mobile sync server controls
        row++;
        grid.add(buildSyncControls(), 0, row, 2, 1);

        return grid;
    }

    /**
     * Build sync server control row.
     */
    private HBox buildSyncControls() {
        Label lblSync = new Label("Sync Server:");
        lblSyncStatus = new Label(WebServerManager.get().isRunning() ? "Running" : "Stopped");
        
        btnToggleSync = new Button(WebServerManager.get().isRunning() ? "Disable" : "Enable");
        btnToggleSync.setOnAction(e -> toggleSyncServer());
        
        Button btnPair = new Button("Pair Mobile");
        btnPair.setOnAction(e -> openPairingPage());
        
        return new HBox(10, lblSync, lblSyncStatus, btnToggleSync, btnPair);
    }

    /**
     * Toggle the sync server on/off.
     */
    private void toggleSyncServer() {
        try {
            WebServerManager manager = WebServerManager.get();
            if (manager.isRunning()) {
                manager.stop();
            } else {
                manager.start();
            }
            updateSyncStatus();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, 
                "Sync server error: " + ex.getMessage(), 
                ButtonType.OK).showAndWait();
        }
    }

    /**
     * Update sync server status display.
     */
    private void updateSyncStatus() {
        WebServerManager manager = WebServerManager.get();
        lblSyncStatus.setText(manager.isRunning() ? "Running" : "Stopped");
        btnToggleSync.setText(manager.isRunning() ? "Disable" : "Enable");
    }

    /**
     * Open the mobile pairing page in browser.
     */
    private void openPairingPage() {
        try {
            WebServerManager manager = WebServerManager.get();
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
            new Alert(Alert.AlertType.ERROR, 
                "Open pairing page failed: " + ex.getMessage(), 
                ButtonType.OK).showAndWait();
        }
    }

    /**
     * Refresh all dashboard statistics.
     */
    public void refresh() {
        // Load statistics from repository
        StatsRepository.Stats stats = new StatsRepository().load();
        lblDue.setText(String.valueOf(stats.dueCount));
        lblNew.setText(String.valueOf(stats.newCount));
        lblTotalCards.setText(String.valueOf(stats.totalCards));
        lblTotalNotes.setText(String.valueOf(stats.totalNotes));
        lblTodayReviews.setText(String.valueOf(stats.todayReviews));
        
        // Load plan counts
        PlanService.Counts planCounts = studyService.planCounts();
        lblPlanPending.setText(String.valueOf(planCounts.pending));
        lblPlanDone.setText(String.valueOf(planCounts.done));
        lblPlanTotal.setText(String.valueOf(planCounts.total));
    }

    /**
     * Get scheduler status label text.
     */
    public String getSchedulerStatus() {
        String mode = Config.get("app.study.scheduler.mode", "due");
        String modeLabel = "due".equalsIgnoreCase(mode) ? "Due" : "Periodic";
        return (scheduler.isPaused() ? "Paused" : "Running") + " (" + modeLabel + ")";
    }
}
