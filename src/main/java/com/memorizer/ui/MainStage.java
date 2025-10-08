package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.app.H2ConsoleServer;
import com.memorizer.app.Scheduler;
import com.memorizer.db.StatsRepository;
import com.memorizer.model.Rating;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class MainStage extends Stage {
    private final StudyService study;
    private final Scheduler scheduler;

    // Dashboard labels
    private final Label lblDue = new Label("-");
    private final Label lblNew = new Label("-");
    private final Label lblTotalCards = new Label("-");
    private final Label lblTotalNotes = new Label("-");
    private final Label lblTodayReviews = new Label("-");
    private final Label lblScheduler = new Label("-");

    // Study tab
    private final Label lblFront = new Label();
    private final Label lblBack  = new Label();
    private boolean showingFront = true;

    public MainStage(StudyService study, Scheduler scheduler) {
        this.study = study;
        this.scheduler = scheduler;

        setTitle("Memorizer");
        setMinWidth(720);
        setMinHeight(480);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setCenter(buildTabs());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root);
        // shortcuts on main window
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.F5) refreshStats();
            if (ev.getCode() == KeyCode.SPACE) toggleFace();
            if (ev.getCode() == KeyCode.DIGIT1) rate(Rating.AGAIN);
            if (ev.getCode() == KeyCode.DIGIT2) rate(Rating.HARD);
            if (ev.getCode() == KeyCode.DIGIT3) rate(Rating.GOOD);
            if (ev.getCode() == KeyCode.DIGIT4) rate(Rating.EASY);
        });
        setScene(scene);

        setOnCloseRequest(e -> hide());
        refreshStats();
        loadNextForStudy();
    }

    private MenuBar buildMenuBar() {
        Menu mFile = new Menu("File");
        MenuItem miImport = new MenuItem("Import Excel...");
        miImport.setOnAction(e -> com.memorizer.app.TrayActions.openImportDialog());
        MenuItem miTemplate = new MenuItem("Save Import Template...");
        miTemplate.setOnAction(e -> com.memorizer.app.TrayActions.saveTemplateDialog());
        MenuItem miH2 = new MenuItem("Open H2 Console");
        miH2.setOnAction(e -> H2ConsoleServer.startIfEnabled());
        MenuItem miExit = new MenuItem("Exit");
        miExit.setOnAction(e -> {
            // delegate to tray exit (if any), else System.exit
            System.exit(0);
        });
        mFile.getItems().addAll(miImport, miTemplate, new SeparatorMenuItem(), miH2, new SeparatorMenuItem(), miExit);

        Menu mStudy = new Menu("Study");
        MenuItem miShowNow = new MenuItem("Show Now");
        miShowNow.setOnAction(e -> com.memorizer.app.TrayActions.showStealthNow(study));
        MenuItem miPause = new MenuItem("Pause Reminders");
        miPause.setOnAction(e -> scheduler.pause());
        MenuItem miResume = new MenuItem("Resume Reminders");
        miResume.setOnAction(e -> scheduler.resume());
        MenuItem miSnooze = new MenuItem("Snooze 10 min");
        miSnooze.setOnAction(e -> scheduler.snooze(Config.getInt("app.study.snooze-minutes", 10)));
        mStudy.getItems().addAll(miShowNow, new SeparatorMenuItem(), miPause, miResume, miSnooze);

        Menu mHelp = new Menu("Help");
        MenuItem miAbout = new MenuItem("About");
        miAbout.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Memorizer\nSimple spaced repetition helper.\n© You").showAndWait());
        mHelp.getItems().addAll(miAbout);

        return new MenuBar(mFile, mStudy, mHelp);
    }

    private TabPane buildTabs() {
        TabPane tabs = new TabPane();

        Tab tabDash = new Tab("Dashboard", buildDashboard());
        tabDash.setClosable(false);

        Tab tabStudy = new Tab("Study", buildStudyPane());
        tabStudy.setClosable(false);

        tabs.getTabs().addAll(tabDash, tabStudy);
        return tabs;
    }

    private Pane buildDashboard() {
        GridPane g = new GridPane();
        g.setPadding(new Insets(16));
        g.setHgap(16);
        g.setVgap(10);

        int r = 0;
        g.add(new Label("Due:"), 0, r); g.add(lblDue, 1, r++);
        g.add(new Label("New:"), 0, r); g.add(lblNew, 1, r++);
        g.add(new Label("Total Cards:"), 0, r); g.add(lblTotalCards, 1, r++);
        g.add(new Label("Total Notes:"), 0, r); g.add(lblTotalNotes, 1, r++);
        g.add(new Label("Today's Reviews:"), 0, r); g.add(lblTodayReviews, 1, r++);

        Button btnRefresh = new Button("Refresh (F5)");
        btnRefresh.setOnAction(e -> refreshStats());
        g.add(btnRefresh, 0, r, 2, 1);

        return g;
    }

    private Pane buildStudyPane() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        lblFront.setStyle("-fx-font-size: 20px;");
        lblBack.setStyle("-fx-font-size: 20px; -fx-text-fill: #555;");
        lblBack.setVisible(false);

        HBox row1 = new HBox(10, new Label("Front:"), lblFront);
        HBox row2 = new HBox(10, new Label("Back:"), lblBack);

        HBox buttons = new HBox(10);
        Button btnFlip = new Button("Flip (Space)");
        Button btnAgain = new Button("1 Again");
        Button btnHard  = new Button("2 Hard");
        Button btnGood  = new Button("3 Good");
        Button btnEasy  = new Button("4 Easy");
        Button btnNext  = new Button("Next");

        btnFlip.setOnAction(e -> toggleFace());
        btnAgain.setOnAction(e -> { rate(Rating.AGAIN); loadNextForStudy(); });
        btnHard.setOnAction(e -> { rate(Rating.HARD);  loadNextForStudy(); });
        btnGood.setOnAction(e -> { rate(Rating.GOOD);  loadNextForStudy(); });
        btnEasy.setOnAction(e -> { rate(Rating.EASY);  loadNextForStudy(); });
        btnNext.setOnAction(e -> loadNextForStudy());

        buttons.getChildren().addAll(btnFlip, btnAgain, btnHard, btnGood, btnEasy, btnNext);
        buttons.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(row1, row2, new Separator(), buttons);
        return box;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(16, new Label("Scheduler:"), lblScheduler);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: #f2f2f2;");
        return bar;
    }

    // ---- public helpers ----
    public void showAndFocus() {
        if (!isShowing()) show();
        toFront(); requestFocus(); setIconified(false);
        refreshStats();
    }

    // ---- internals ----
    private void refreshStats() {
        StatsRepository.Stats s = new StatsRepository().load();
        lblDue.setText(String.valueOf(s.dueCount));
        lblNew.setText(String.valueOf(s.newCount));
        lblTotalCards.setText(String.valueOf(s.totalCards));
        lblTotalNotes.setText(String.valueOf(s.totalNotes));
        lblTodayReviews.setText(String.valueOf(s.todayReviews));
        lblScheduler.setText(scheduler.isPaused() ? "Paused" : "Running");
    }

    private void loadNextForStudy() {
        java.util.Optional<com.memorizer.service.StudyService.CardView> opt = study.currentOrNextOrFallback();
        if (opt.isPresent()) {
            com.memorizer.service.StudyService.CardView v = opt.get();
            lblFront.setText(v.front);
            lblBack.setText(v.back);
            showingFront = true;
            lblFront.setVisible(true);
            lblBack.setVisible(false);
        } else {
            lblFront.setText("(No cards. Use File → Import Excel...)");
            lblBack.setText("");
            showingFront = true;
            lblBack.setVisible(false);
        }
    }

    private void toggleFace() {
        showingFront = !showingFront;
        lblFront.setVisible(showingFront);
        lblBack.setVisible(!showingFront);
    }

    private void rate(Rating r) {
        study.rate(r);
        refreshStats();
    }
}
