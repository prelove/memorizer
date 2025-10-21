package com.memorizer.ui;

import com.memorizer.app.Scheduler;
import com.memorizer.service.StudyService;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Main application window that hosts the dashboard, plan, and exam panels.
 * Responsible for wiring together UI components and reacting to high-level actions
 * triggered via the menu bar or the system tray.
 */
public class MainStage extends Stage {
    private final StudyService studyService;
    private final Scheduler scheduler;

    private final DashboardPanel dashboardPanel;
    private final PlanPanel planPanel;
    private final ExamPanel examPanel;

    private final Label lblScheduler = new Label("-");
    private final Label lblNotice = new Label("");
    private StudyStage studyStage;

    public MainStage(StudyService studyService, Scheduler scheduler) {
        this.studyService = studyService;
        this.scheduler = scheduler;
        this.dashboardPanel = new DashboardPanel(studyService, scheduler);
        this.planPanel = new PlanPanel(studyService);
        this.examPanel = new ExamPanel(studyService);

        setTitle("Memorizer");
        setMinWidth(720);
        setMinHeight(480);

        BorderPane root = new BorderPane();
        MenuBarBuilder menuBuilder = new MenuBarBuilder(
                this,
                studyService,
                scheduler,
                this::refreshStats,
                this::reloadPlan
        );
        MenuBar menuBar = menuBuilder.build();
        root.setTop(menuBar);

        ScrollPane centerScroll = new ScrollPane(buildTabs());
        centerScroll.setFitToWidth(true);
        centerScroll.setFitToHeight(false);
        centerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setCenter(centerScroll);
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.F5) {
                refreshStats();
                reloadPlan();
            }
        });
        setScene(scene);

        setOnShown(e -> sizeToHalfScreen());

        refreshStats();
    }

    private TabPane buildTabs() {
        TabPane tabs = new TabPane();

        Tab dashboardTab = new Tab("Dashboard", dashboardPanel.build());
        dashboardTab.setClosable(false);

        Tab planTab = new Tab("Plan", planPanel.build());
        planTab.setClosable(false);

        Tab examTab = new Tab("Exam", examPanel.build());
        examTab.setClosable(false);

        tabs.getTabs().addAll(dashboardTab, planTab, examTab);
        return tabs;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(12);
        bar.setPadding(new Insets(8, 12, 8, 12));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
                new Label("Scheduler:"),
                lblScheduler,
                spacer,
                lblNotice
        );
        return bar;
    }

    private void sizeToHalfScreen() {
        Rectangle2D visualBounds = Screen.getPrimary().getVisualBounds();
        double width = Math.ceil(visualBounds.getWidth() / 2.0);
        double height = Math.ceil(visualBounds.getHeight() / 2.0);
        double x = Math.floor(visualBounds.getMinX() + (visualBounds.getWidth() - width) / 2.0);
        double y = Math.floor(visualBounds.getMinY() + (visualBounds.getHeight() - height) / 2.0);
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
    }

    private void refreshStats() {
        dashboardPanel.refresh();
        lblScheduler.setText(dashboardPanel.getSchedulerStatus());
    }

    private void reloadPlan() {
        planPanel.reload();
    }

    public void showNotice(String message) {
        String text = message == null ? "" : message;
        lblNotice.setText(text);
        if (text.trim().isEmpty()) {
            return;
        }

        PauseTransition pt = new PauseTransition(Duration.seconds(3));
        pt.setOnFinished(e -> {
            if (lblNotice.getText().equals(text)) {
                lblNotice.setText("");
            }
        });
        pt.playFromStart();
    }

    public void openStudyWindow() {
        if (studyStage == null) {
            studyStage = new StudyStage(studyService);
        }
        studyStage.showAndFocus();
    }

    public void refreshModeIndicatorInStudy() {
        if (studyStage != null) {
            studyStage.refreshModeIndicatorFromConfig();
        }
        lblScheduler.setText(dashboardPanel.getSchedulerStatus());
    }

    public void applyTheme(boolean light) {
        // Main window uses default Modena theme; nothing to change yet.
    }

    public void showAndFocus() {
        if (!isShowing()) {
            sizeToHalfScreen();
            show();
        }
        toFront();
        requestFocus();
        setIconified(false);
        refreshStats();
    }
}
