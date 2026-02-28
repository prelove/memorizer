package com.memorizer.ui;

import com.memorizer.app.Scheduler;
import com.memorizer.service.StudyService;
import com.memorizer.util.ScreenUtil;
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
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
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
        setMinHeight(660);

        // 设置窗口图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                getIcons().add(icon);
            }
        } catch (Exception e) {
            // 记录日志但不中断
            System.out.println("Failed to load main stage icon: " + e.getMessage());
        }

        BorderPane root = new BorderPane();
        MenuBarBuilder menuBuilder = new MenuBarBuilder(
                this,
                studyService,
                scheduler,
                this::refreshStats,
                this::reloadPlan
        );
        MenuBar menuBar = menuBuilder.build();
        HBox crumbs = new HBox(8);
        crumbs.setPadding(new Insets(6, 12, 6, 12));
        Label crumbLabel = new Label("Home / Dashboard");
        crumbs.getChildren().add(crumbLabel);
        VBox top = new VBox(menuBar, crumbs);
        root.setTop(top);

        TabPane tabsVar = buildTabs();
        ScrollPane centerScroll = new ScrollPane(tabsVar);
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
            // Ctrl+= / Ctrl+- to adjust font scale
            if (event.isControlDown()) {
                if (event.getCode() == KeyCode.EQUALS || event.getCode() == KeyCode.PLUS) {
                    adjustFontScale(scene, +0.1);
                } else if (event.getCode() == KeyCode.MINUS) {
                    adjustFontScale(scene, -0.1);
                }
            }
        });
        setScene(scene);

        setOnShown(e -> sizeToHalfScreen());

        refreshStats();

        // Update breadcrumbs on tab selection
        tabsVar.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            String name = (nv == null || nv.getText() == null) ? "Dashboard" : nv.getText();
            crumbLabel.setText("Home / " + name);
        });
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
        // Use the screen the pointer is on (correct on multi-monitor / HiDPI setups).
        Rectangle2D visualBounds = ScreenUtil.jfxVisualBounds(ScreenUtil.activeDevice());
        double width = Math.ceil(visualBounds.getWidth() / 2.0);
        // Height: slightly taller than half so the window feels spacious
        double height = Math.ceil(visualBounds.getHeight() / 2.0 * 1.52);
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

    /** Open the Study window and show a specific card id. */
    public void showCardInStudy(long cardId) {
        if (studyStage == null) {
            studyStage = new StudyStage(studyService);
        }
        studyStage.showCard(cardId);
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

    /** Adjust font scale by delta (clamped to 0.7–1.5), persist to Config, and apply to scene. */
    private void adjustFontScale(javafx.scene.Scene scene, double delta) {
        double current = com.memorizer.app.Config.getFontScale();
        double next = Math.round((current + delta) * 10.0) / 10.0;
        if (next < 0.7) next = 0.7;
        if (next > 1.5) next = 1.5;
        com.memorizer.app.Config.set("app.ui.font-scale", String.valueOf(next));
        scene.getRoot().setStyle("-fx-font-size: " + next + "em;");
        showNotice("Font scale: " + next);
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
