package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.app.H2ConsoleServer;
import com.memorizer.app.Scheduler;
import com.memorizer.app.WebServerManager;
import com.memorizer.db.StatsRepository;
import com.memorizer.model.Rating;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.control.TableRow;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.PropertyValueFactory;

public class MainStage extends Stage {
    private final StudyService study;
    private final Scheduler scheduler;
    private final ObservableList<com.memorizer.service.PlanService.PlanRow> planRows = FXCollections.observableArrayList();
    private java.util.List<com.memorizer.service.PlanService.PlanRow> fullPlan = new java.util.ArrayList<>();
    private int planPageSize = 25;
    private Pagination planPagination;
    private TableView<com.memorizer.service.PlanService.PlanRow> planTable;
    private TableColumn<com.memorizer.service.PlanService.PlanRow, Integer> colOrder;
    private TableColumn<com.memorizer.service.PlanService.PlanRow, String> colKind;
    private TableColumn<com.memorizer.service.PlanService.PlanRow, String> colStatus;
    private TableColumn<com.memorizer.service.PlanService.PlanRow, String> colDeck;
    private TableColumn<com.memorizer.service.PlanService.PlanRow, String> colFront;


    // Dashboard labels
    private final Label lblDue = new Label("-");
    private final Label lblNew = new Label("-");
    private final Label lblTotalCards = new Label("-");
    private final Label lblTotalNotes = new Label("-");
    private final Label lblTodayReviews = new Label("-");
    private final Label lblScheduler = new Label("-");
    private final Label lblNotice = new Label("");
    // Plan stats
    private final Label lblPlanPending = new Label("-");
    private final Label lblPlanDone = new Label("-");
    private final Label lblPlanTotal = new Label("-");

    // Study tab
    private final Label lblFront = new Label();
    private final Label lblBack  = new Label();
    private boolean showingFront = true;

    // Exam tab state
    private final Label examFront = new Label();
    private final Label examBack  = new Label();
    private final Label examProgress = new Label("0/0");
    private final Label examScore = new Label("OK0 NG0");
    private ComboBox<String> examSourceBox;
    private CheckBox examShuffleBox;
    private Spinner<Integer> examBatchSpinner;
    private Button btnExamShow;
    private Button btnExamCorrect;
    private Button btnExamWrong;
    private Button btnExamNext;
    private Button btnExamRestart;
    private boolean examShowingAnswer = false;
    private final java.util.List<Long> examQueue = new java.util.ArrayList<>();
    private int examIndex = 0;
    private int examCorrect = 0;
    private int examWrong = 0;

    public MainStage(StudyService study, Scheduler scheduler) {
        this.study = study;
        this.scheduler = scheduler;

        setTitle("Memorizer");
        setMinWidth(720);
        setMinHeight(480);

        BorderPane root = new BorderPane();
        root.setTop(buildMenuBar());
        // Wrap center in a scroll container to avoid clipping and keep content aligned to top
        javafx.scene.control.ScrollPane centerScroll = new javafx.scene.control.ScrollPane(buildTabs());
        centerScroll.setFitToWidth(true); centerScroll.setFitToHeight(false);
        centerScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        root.setCenter(centerScroll);
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

        // Ensure we size to the OS work area the moment this window is shown
        setOnShown(e -> {
            javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
            setX(Math.floor(vb.getMinX()));
            setY(Math.floor(vb.getMinY()));
            setWidth(Math.ceil(vb.getWidth()));
            setHeight(Math.ceil(vb.getHeight() + 1));
        });

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
        miH2.setOnAction(e -> com.memorizer.app.TrayActions.openH2Console());
        MenuItem miExit = new MenuItem("Exit");
        miExit.setOnAction(e -> {
            // delegate to tray exit (if any), else System.exit
            System.exit(0);
        });
        mFile.getItems().addAll(miImport, miTemplate, new SeparatorMenuItem(), miH2, new SeparatorMenuItem(), miExit);

        Menu mData = new Menu("Data");
        MenuItem miNewDeck = new MenuItem("New Deck...");
        miNewDeck.setOnAction(e -> openNewDeckDialog());
        MenuItem miNewEntry = new MenuItem("New Entry...");
        miNewEntry.setOnAction(e -> openNewEntryDialog());
        mData.getItems().addAll(miNewDeck, miNewEntry);

        Menu mStudy = new Menu("Study");
        MenuItem miShowNow = new MenuItem("Show Now (Banner)");
        miShowNow.setOnAction(e -> com.memorizer.app.TrayActions.showStealthNow(study));
        MenuItem miOpenStudy = new MenuItem("Open Study Window");
        miOpenStudy.setOnAction(e -> openStudyWindow());
        MenuItem miPause = new MenuItem("Pause Reminders");
        miPause.setOnAction(e -> scheduler.pause());
        MenuItem miResume = new MenuItem("Resume Reminders");
        miResume.setOnAction(e -> scheduler.resume());
        MenuItem miSnooze = new MenuItem("Snooze 10 min");
        miSnooze.setOnAction(e -> scheduler.snooze(Config.getInt("app.study.snooze-minutes", 10)));
        mStudy.getItems().addAll(miOpenStudy, miShowNow, new SeparatorMenuItem(), miPause, miResume, miSnooze);

        // View: Theme toggle (Dark/Light)
        Menu mView = new Menu("View");
        Menu mTheme = new Menu("Theme");
        CheckMenuItem miDark = new CheckMenuItem("Dark");
        CheckMenuItem miLight = new CheckMenuItem("Light");
        boolean light = "light".equalsIgnoreCase(Config.get("app.ui.theme","dark"));
        miLight.setSelected(light); miDark.setSelected(!light);
        // mutual exclusion
        miDark.setOnAction(e -> {
            if (miDark.isSelected()) { miLight.setSelected(false); setThemeDark(); }
            else if (!miLight.isSelected()) { miDark.setSelected(true); }
        });
        miLight.setOnAction(e -> {
            if (miLight.isSelected()) { miDark.setSelected(false); setThemeLight(); }
            else if (!miDark.isSelected()) { miLight.setSelected(true); }
        });
        mTheme.getItems().addAll(miDark, miLight);
        // Deck filter
        Menu mDeck = new Menu("Deck");
        ToggleGroup tg = new ToggleGroup();
        RadioMenuItem miAll = new RadioMenuItem("All Decks"); miAll.setToggleGroup(tg);
        String sel = Config.get("app.deck.filter","all");
        if ("all".equalsIgnoreCase(sel)) miAll.setSelected(true);
        miAll.setOnAction(e -> { Config.set("app.deck.filter","all"); reloadPlan(); refreshStats(); try { com.memorizer.ui.StealthStage s = com.memorizer.app.AppContext.getStealth(); if (s!=null) s.refreshTodayProgress(); } catch (Exception ignored) {} });
        mDeck.getItems().add(miAll);
        java.util.List<com.memorizer.model.Deck> decks = new com.memorizer.db.DeckRepository().listAll();
        for (com.memorizer.model.Deck d : decks) {
            RadioMenuItem item = new RadioMenuItem(d.name + " (#"+d.id+")");
            item.setToggleGroup(tg);
            if (String.valueOf(d.id).equals(sel)) item.setSelected(true);
            item.setOnAction(ev -> { Config.set("app.deck.filter", String.valueOf(d.id)); reloadPlan(); refreshStats(); try { com.memorizer.ui.StealthStage s = com.memorizer.app.AppContext.getStealth(); if (s!=null) s.refreshTodayProgress(); } catch (Exception ignored) {} });
            mDeck.getItems().add(item);
        }
        mView.getItems().addAll(mTheme, new SeparatorMenuItem(), mDeck);

        Menu mHelp = new Menu("Help");
        MenuItem miAbout = new MenuItem("About");
        miAbout.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("About Memorizer");
            a.setHeaderText("Memorizer");
            a.setContentText("Simple spaced repetition helper.\n   You");
            a.showAndWait();
        });
        MenuItem miManual = new MenuItem("User Manual...");
        miManual.setOnAction(e -> openUserManual());
        mHelp.getItems().addAll(miManual, new SeparatorMenuItem(), miAbout);

        return new MenuBar(mFile, mData, mStudy, mView, mHelp);
    }

    private void openUserManual() {
        String text = loadUserManual();
        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefColumnCount(80);
        ta.setPrefRowCount(24);
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("User Manual");
        dlg.getDialogPane().setContent(ta);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.initOwner(this);
        dlg.showAndWait();
    }

    private String loadUserManual() {
        try (java.io.InputStream is = getClass().getResourceAsStream("/USER_MANUAL.txt")) {
            if (is != null) {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] tmp = new byte[4096];
                int n;
                while ((n = is.read(tmp)) != -1) bos.write(tmp, 0, n);
                return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
        return "Memorizer User Manual\n\n" +
               "- Stealth Banner: Normal/Mini modes (T to toggle theme, M to toggle mode).\n" +
               "- Flip cycle: Front → Back → Front+Back+Reading/Pos+Examples → Front.\n" +
               "- Rating: Again/Hard/Good/Easy (1/2/3/4).\n" +
               "- Progress: Today target bar with overlay text.\n" +
               "- Decks: View → Deck to filter; Data → New Deck/Entry to create.\n" +
               "- Shortcuts: SPACE/ENTER flip, F8 toggle banner, ESC hide.\n";
    }

    private void openNewDeckDialog() {
        javafx.stage.Stage d = new javafx.stage.Stage();
        d.setTitle("New Deck");
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        TextField tfName = new TextField();
        tfName.setPromptText("Deck name");
        HBox actions = new HBox(8);
        Button btnCancel = new Button("Cancel");
        Button btnSave = new Button("Create");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().addAll(spacer, btnCancel, btnSave);
        root.getChildren().addAll(new Label("Name"), tfName, actions);
        Scene sc = new Scene(root, 360, 120);
        d.setScene(sc);
        btnCancel.setOnAction(e -> d.close());
        Runnable create = () -> {
            String name = tfName.getText()==null?"":tfName.getText().trim();
            if (name.isEmpty()) return;
            try { new com.memorizer.db.DeckRepository().getOrCreate(name); } catch (Exception ignored) {}
            showNotice("Deck created: " + name);
            d.close();
        };
        btnSave.setOnAction(e -> create.run());
        sc.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ENTER) create.run(); if (ev.getCode()== KeyCode.ESCAPE) d.close(); });
        d.initOwner(this);
        d.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        d.showAndWait();
    }

    private void openNewEntryDialog() {
        javafx.stage.Stage d = new javafx.stage.Stage();
        d.setTitle("New Entry");
        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        ComboBox<com.memorizer.model.Deck> deckBox = new ComboBox<>();
        deckBox.setConverter(new javafx.util.StringConverter<com.memorizer.model.Deck>() {
            @Override public String toString(com.memorizer.model.Deck dd) { return dd==null?"(No deck)":dd.name; }
            @Override public com.memorizer.model.Deck fromString(String s) { return null; }
        });
        java.util.List<com.memorizer.model.Deck> decks = new com.memorizer.db.DeckRepository().listAll();
        deckBox.getItems().setAll(decks);
        if (!decks.isEmpty()) deckBox.getSelectionModel().select(0);

        TextField tfFront = new TextField(); tfFront.setPromptText("Front");
        TextField tfReading = new TextField(); tfReading.setPromptText("Reading");
        TextField tfPos = new TextField(); tfPos.setPromptText("Pos");
        TextArea taBack = new TextArea(); taBack.setPromptText("Back"); taBack.setPrefRowCount(3);
        TextArea taExamples = new TextArea(); taExamples.setPromptText("Examples"); taExamples.setPrefRowCount(3);
        TextField tfTags = new TextField(); tfTags.setPromptText("Tags");

        GridPane form = new GridPane(); form.setHgap(8); form.setVgap(8);
        int r=0; form.add(new Label("Deck"),0,r); form.add(deckBox,1,r++);
        form.add(new Label("Front"),0,r); form.add(tfFront,1,r++);
        form.add(new Label("Reading/Pos"),0,r); HBox rp = new HBox(8, tfReading, tfPos); HBox.setHgrow(tfReading, Priority.ALWAYS); form.add(rp,1,r++);
        form.add(new Label("Back"),0,r); form.add(taBack,1,r++);
        form.add(new Label("Examples"),0,r); form.add(taExamples,1,r++);
        form.add(new Label("Tags"),0,r); form.add(tfTags,1,r++);
        ColumnConstraints cc0 = new ColumnConstraints(); cc0.setMinWidth(110); cc0.setPrefWidth(120);
        ColumnConstraints cc1 = new ColumnConstraints(); cc1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(cc0, cc1);

        HBox actions = new HBox(8);
        Button btnCancel = new Button("Cancel");
        Button btnSave = new Button("Save");
        Region spacer2 = new Region(); HBox.setHgrow(spacer2, Priority.ALWAYS);
        actions.getChildren().addAll(spacer2, btnCancel, btnSave);

        root.getChildren().addAll(form, actions);
        Scene sc = new Scene(root, 520, 360);
        d.setScene(sc);
        btnCancel.setOnAction(e -> d.close());
        Runnable save = () -> {
            com.memorizer.model.Note n = new com.memorizer.model.Note();
            com.memorizer.model.Deck sel = deckBox.getValue();
            n.deckId = sel == null ? null : sel.id;
            n.front = tfFront.getText();
            n.back = taBack.getText();
            n.reading = tfReading.getText();
            n.pos = tfPos.getText();
            n.examples = taExamples.getText();
            n.tags = tfTags.getText();
            try {
                long nid = new com.memorizer.db.NoteRepository().insert(n);
                new com.memorizer.db.CardRepository().insertForNote(nid);
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Save failed: " + ex.getMessage(), ButtonType.OK).showAndWait();
                return;
            }
            showNotice("Entry saved" + (sel!=null? (" to " + sel.name): ""));
            d.close();
        };
        btnSave.setOnAction(e -> save.run());
        sc.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) d.close();
            if (ev.getCode() == KeyCode.S && ev.isControlDown()) save.run();
            if (ev.getCode() == KeyCode.ENTER && ev.isControlDown()) save.run();
        });
        d.initOwner(this);
        d.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        d.showAndWait();
    }

    private void setThemeDark() {
        Config.set("app.ui.theme", "dark");
        try { com.memorizer.ui.StealthStage s = com.memorizer.app.AppContext.getStealth(); if (s != null) s.setTheme(com.memorizer.ui.StealthStage.ThemeMode.DARK); } catch (Exception ignored) {}
        // Keep main/study default Modena background (no theme application here)
    }
    private void setThemeLight() {
        Config.set("app.ui.theme", "light");
        try { com.memorizer.ui.StealthStage s = com.memorizer.app.AppContext.getStealth(); if (s != null) s.setTheme(com.memorizer.ui.StealthStage.ThemeMode.LIGHT); } catch (Exception ignored) {}
        // Keep main/study default Modena background (no theme application here)
    }
    private void applyThemeForMain(Region root, boolean light) { /* no-op: use default Modena */ }

    /** Allow external callers (e.g., Tray) to live-update the main/study themes. */
    public void applyTheme(boolean light) { /* no-op for main + study backgrounds */ }

    /** Update Mode indicator label in Study window, if open. */
    public void refreshModeIndicatorInStudy() {
        try { if (studyStage != null) studyStage.refreshModeIndicatorFromConfig(); } catch (Exception ignored) {}
    }

    private TabPane buildTabs() {
        TabPane tabs = new TabPane();

        Tab tabDash = new Tab("Dashboard", buildDashboard());
        tabDash.setClosable(false);

        planPageSize = Config.getInt("app.ui.plan.page-size", 25);
        Tab tabPlan = new Tab("Plan", buildPlanPane());
        tabPlan.setClosable(false);
        Tab tabExam = new Tab("Exam", buildExamPane());
        tabExam.setClosable(false);
        tabs.getTabs().addAll(tabDash, tabPlan, tabExam);
        return tabs;
    }

    // ---- study window ----
    private StudyStage studyStage;
    private void openStudyWindow() {
        if (studyStage == null) studyStage = new StudyStage(study);
        studyStage.showAndFocus();
    }

    private Pane buildExamPane() {
        BorderPane root = new BorderPane();
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        examFront.setStyle("-fx-font-size: 22px;");
        examBack.setStyle("-fx-font-size: 20px; -fx-text-fill: #444;");
        examBack.setVisible(false);

        HBox row1 = new HBox(10, new Label("Front:"), examFront);
        HBox row2 = new HBox(10, new Label("Back:"), examBack);

        btnExamShow = new Button("Show Answer");
        btnExamCorrect = new Button("  Correct");
        btnExamWrong = new Button("  Wrong");
        btnExamNext = new Button("Next");
        btnExamRestart = new Button("Restart");

        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);

        btnExamShow.setOnAction(e -> {
            examShowingAnswer = true;
            examBack.setVisible(true);
            btnExamCorrect.setDisable(false);
            btnExamWrong.setDisable(false);
        });

        btnExamCorrect.setOnAction(e -> {
            examCorrect++;
            examScore.setText("OK" + examCorrect + " NG" + examWrong);
            gotoNextExam();
        });

        btnExamWrong.setOnAction(e -> {
            examWrong++;
            examScore.setText("OK" + examCorrect + " NG" + examWrong);
            gotoNextExam();
        });

        btnExamNext.setOnAction(e -> gotoNextExam());
        btnExamRestart.setOnAction(e -> { prepareExamQueue(); showExamCurrent(); });

        // source/options
        examSourceBox = new ComboBox<>();
        examSourceBox.getItems().setAll("Plan", "Due", "New");
        String srcPref = Config.get("app.ui.exam.source", "Plan");
        if (!examSourceBox.getItems().contains(srcPref)) srcPref = "Plan";
        examSourceBox.getSelectionModel().select(srcPref);
        examSourceBox.valueProperty().addListener((o,ov,nv) -> { Config.set("app.ui.exam.source", nv==null?"Plan":nv); prepareExamQueue(); showExamCurrent(); });

        examShuffleBox = new CheckBox("Shuffle");
        boolean shPref = Config.getBool("app.ui.exam.shuffle", true);
        examShuffleBox.setSelected(shPref);
        examShuffleBox.selectedProperty().addListener((o,ov,nv) -> { Config.set("app.ui.exam.shuffle", String.valueOf(nv)); prepareExamQueue(); showExamCurrent(); });

        int batchPref = Config.getInt("app.ui.exam.batch-size", 20);
        examBatchSpinner = new Spinner<>(1, 500, Math.max(1, batchPref));
        examBatchSpinner.setEditable(true);
        examBatchSpinner.valueProperty().addListener((o,ov,nv) -> { if (nv!=null) { Config.set("app.ui.exam.batch-size", String.valueOf(nv)); prepareExamQueue(); showExamCurrent(); }});

        HBox controls = new HBox(10,
                new Label("Source:"), examSourceBox,
                examShuffleBox,
                new Label("Batch:"), examBatchSpinner,
                new Separator(),
                btnExamShow, btnExamCorrect, btnExamWrong, btnExamNext, btnExamRestart,
                new Separator(), new Label("Progress:"), examProgress, new Label("Score:"), examScore);
        controls.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(row1, row2, new Separator(), controls);
        root.setCenter(box);

        // Prepare first session
        prepareExamQueue();
        showExamCurrent();

        // Keyboard shortcuts on exam pane
        box.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                if (!examShowingAnswer) { btnExamShow.fire(); } else { btnExamNext.fire(); }
            } else if (ev.getCode() == KeyCode.A) {
                if (!btnExamCorrect.isDisabled()) btnExamCorrect.fire();
            } else if (ev.getCode() == KeyCode.S) {
                if (!btnExamWrong.isDisabled()) btnExamWrong.fire();
            } else if (ev.getCode() == KeyCode.ESCAPE) {
                btnExamRestart.fire();
            }
        });

        return root;
    }

    private void prepareExamQueue() {
        examQueue.clear();
        examIndex = 0;
        examCorrect = 0; examWrong = 0;
        examScore.setText("OK0 NG0");

        java.util.List<com.memorizer.service.PlanService.PlanRow> rows = study.planListToday();
        int limit = Math.max(1, Config.getInt("app.ui.exam.batch-size", 20));
        String src = Config.get("app.ui.exam.source", "Plan");
        boolean useShuffle = Config.getBool("app.ui.exam.shuffle", true);
        java.util.List<Long> pool = new java.util.ArrayList<>();
        for (com.memorizer.service.PlanService.PlanRow r : rows) {
            if (r == null) continue;
            if ("Plan".equalsIgnoreCase(src)) pool.add(r.getCardId());
            else if ("Due".equalsIgnoreCase(src)) { if (r.getKind() == 0) pool.add(r.getCardId()); }
            else if ("New".equalsIgnoreCase(src)) { if (r.getKind() == 2) pool.add(r.getCardId()); }
        }
        if (useShuffle) java.util.Collections.shuffle(pool);
        for (Long id : pool) { examQueue.add(id); if (examQueue.size() >= limit) break; }

        if (examQueue.isEmpty()) {
            java.util.Optional<com.memorizer.model.Card> any = new com.memorizer.db.CardRepository().findAnyAvailable();
            if (any.isPresent()) examQueue.add(any.get().id);
        }
        examProgress.setText(examQueue.isEmpty()?"0/0":"1/" + examQueue.size());
    }

    private void showExamCurrent() {
        if (examQueue.isEmpty() || examIndex < 0 || examIndex >= examQueue.size()) {
            examFront.setText("(No exam items)");
            examBack.setText("");
            examBack.setVisible(false);
            btnExamShow.setDisable(true);
            btnExamCorrect.setDisable(true);
            btnExamWrong.setDisable(true);
            btnExamNext.setDisable(true);
            examProgress.setText("0/0");
            return;
        }
        long cardId = examQueue.get(examIndex);
        com.memorizer.db.NoteRepository repo = new com.memorizer.db.NoteRepository();
        java.util.Optional<com.memorizer.model.Note> on = repo.findByCardId(cardId);
        String f = on.isPresent() ? (on.get().front == null ? "" : on.get().front) : "";
        String b = on.isPresent() ? (on.get().back == null ? "" : on.get().back) : "";
        examFront.setText(f);
        examBack.setText(b);
        examShowingAnswer = false; examBack.setVisible(false);
        btnExamShow.setDisable(false);
        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);
        btnExamNext.setDisable(false);
        examProgress.setText((examIndex + 1) + "/" + examQueue.size());
    }

    private void gotoNextExam() {
        if (examQueue.isEmpty()) { showExamCurrent(); return; }
        examIndex++;
        if (examIndex >= examQueue.size()) {
            btnExamShow.setDisable(true);
            btnExamCorrect.setDisable(true);
            btnExamWrong.setDisable(true);
            btnExamNext.setDisable(true);
            examFront.setText("(Exam finished)");
            examBack.setText("");
            examBack.setVisible(false);
            examProgress.setText(examQueue.size() + "/" + examQueue.size());
            return;
        }
        showExamCurrent();
    }

    private Pane buildPlanPane() {
        BorderPane root = new BorderPane();
        planTable = new TableView<>(planRows);

        colOrder = new TableColumn<>("Order");
        colOrder.setCellValueFactory(new PropertyValueFactory<>("orderNo"));
        colOrder.setPrefWidth(60);

        colKind = new TableColumn<>("Kind");
        colKind.setCellValueFactory(c -> {
            int k = c.getValue().getKind();
            String s = k==0?"DUE":k==1?"LEECH":k==2?"NEW":k==3?"CHAL":String.valueOf(k);
            return javafx.beans.property.SimpleStringProperty.stringExpression(new javafx.beans.property.SimpleStringProperty(s));
        });
        colKind.setPrefWidth(70);
        colKind.setCellFactory(col -> new TableCell<com.memorizer.service.PlanService.PlanRow, String>(){
            @Override protected void updateItem(String item, boolean empty){
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                com.memorizer.service.PlanService.PlanRow r = (com.memorizer.service.PlanService.PlanRow) getTableRow().getItem();
                if (r == null) { setStyle(""); return; }
                int k = r.getKind();
                String color = k==0?"#d7e8ff":k==1?"#ffd0d0":k==2?"#c7f3e6":"#ead6ff";
                setStyle("-fx-text-fill: "+color+";");
            }
        });

        colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> {
            int st = c.getValue().getStatus();
            String s = st==0?"PENDING":st==1?"DONE":st==2?"ROLLED":st==3?"SKIPPED":String.valueOf(st);
            return javafx.beans.property.SimpleStringProperty.stringExpression(new javafx.beans.property.SimpleStringProperty(s));
        });
        colStatus.setPrefWidth(90);

        colDeck = new TableColumn<>("Deck");
        colDeck.setCellValueFactory(new PropertyValueFactory<>("deckName"));
        colDeck.setPrefWidth(140);

        colFront = new TableColumn<>("Front");
        colFront.setCellValueFactory(new PropertyValueFactory<>("front"));
        colFront.setPrefWidth(420);
        colFront.setCellFactory(col -> new TableCell<com.memorizer.service.PlanService.PlanRow, String>(){
            @Override protected void updateItem(String item, boolean empty){
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                com.memorizer.service.PlanService.PlanRow r = (com.memorizer.service.PlanService.PlanRow) getTableRow().getItem();
                if (r == null) { setStyle(""); return; }
                int k = r.getKind();
                String color = k==0?"#d7e8ff":k==1?"#ffd0d0":k==2?"#c7f3e6":"#ead6ff";
                setStyle("-fx-text-fill: "+color+";");
            }
        });

        planTable.getColumns().addAll(colOrder, colKind, colStatus, colDeck, colFront);

        // Ensure sorting applies to the full dataset (not only the current page)
        planTable.setSortPolicy(tv -> {
            applyPlanPage(planPagination == null ? 0 : planPagination.getCurrentPageIndex());
            return true;
        });

        // double-click to show this card now in banner
        planTable.setRowFactory(tv -> {
            TableRow<com.memorizer.service.PlanService.PlanRow> row = new TableRow<>();
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    com.memorizer.service.PlanService.PlanRow r = row.getItem();
                    java.util.Optional<com.memorizer.service.StudyService.CardView> v = study.viewCardById(r.getCardId());
                    if (v.isPresent()) {
                        com.memorizer.ui.StealthStage st = com.memorizer.app.AppContext.getStealth();
                        int batch = Config.getInt("app.study.batch-size", 1);
                        st.startBatch(batch);
                        st.showCardView(v.get());
                        st.showAndFocus();
                    }
                }
            });
            // context menu: mark done / skip
            ContextMenu cm = new ContextMenu();
            MenuItem miDone = new MenuItem("Mark Done");
            MenuItem miSkip = new MenuItem("Skip");
            miDone.setOnAction(e -> {
                if (!row.isEmpty()) {
                    com.memorizer.service.PlanService.PlanRow r = row.getItem();
                    try { new com.memorizer.service.PlanService().markDone(r.getCardId()); } catch (Exception ignored) {}
                    reloadPlan();
                    try { com.memorizer.app.TrayManager tm = com.memorizer.app.AppContext.getTray(); if (tm != null) tm.updatePlanTooltip(); } catch (Exception ignored) {}
                }
            });
            miSkip.setOnAction(e -> {
                if (!row.isEmpty()) {
                    com.memorizer.service.PlanService.PlanRow r = row.getItem();
                    try { new com.memorizer.service.PlanService().markSkipped(r.getCardId()); } catch (Exception ignored) {}
                    reloadPlan();
                    try { com.memorizer.app.TrayManager tm = com.memorizer.app.AppContext.getTray(); if (tm != null) tm.updatePlanTooltip(); } catch (Exception ignored) {}
                }
            });
            cm.getItems().addAll(miDone, miSkip);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(cm));
            return row;
        });


        Button btnRefresh = new Button("Refresh");
        btnRefresh.getStyleClass().addAll("btn","btn-primary");
        btnRefresh.setOnAction(e -> reloadPlan());
        Button btnPrev = new Button("Prev");
        Button btnNext = new Button("Next");
        btnPrev.getStyleClass().addAll("btn","btn-default");
        btnNext.getStyleClass().addAll("btn","btn-default");
        btnPrev.setOnAction(e -> changePlanPage(-1));
        btnNext.setOnAction(e -> changePlanPage(1));
        planPagination = new Pagination(1, 0);
        planPagination.currentPageIndexProperty().addListener((obs,ov,nv) -> applyPlanPage(nv.intValue()));
        HBox top = new HBox(8, btnRefresh, btnPrev, btnNext);
        top.setPadding(new Insets(8));

        root.setTop(top);
        root.setCenter(planTable);
        root.setBottom(new HBox(8, planPagination));
        reloadPlan();
        return root;
    }

    private void reloadPlan() {
        fullPlan = study.planListToday();
        int pages = Math.max(1, (int) Math.ceil(fullPlan.size() / (double) planPageSize));
        planPagination.setPageCount(pages);
        applyPlanPage(0);
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
        g.add(new Label("Plan Pending:"), 0, r); g.add(lblPlanPending, 1, r++);
        g.add(new Label("Plan Done:"), 0, r); g.add(lblPlanDone, 1, r++);
        g.add(new Label("Plan Total:"), 0, r); g.add(lblPlanTotal, 1, r++);

        Button btnRefresh = new Button("Refresh (F5)");
        btnRefresh.getStyleClass().addAll("btn","btn-primary");
        btnRefresh.setOnAction(e -> refreshStats());
        g.add(btnRefresh, 0, r, 2, 1);

        // Mobile Sync (PWA Server)
        r++;
        Label lblSync = new Label("Sync Server:");
        Label lblSyncStatus = new Label(com.memorizer.app.WebServerManager.get().isRunning()?"Running":"Stopped");
        Button btnToggleSync = new Button(com.memorizer.app.WebServerManager.get().isRunning()?"Disable":"Enable");
        Button btnPair = new Button("Pair Mobile");
        btnToggleSync.setOnAction(e -> {
            try {
                if (com.memorizer.app.WebServerManager.get().isRunning()) {
                    com.memorizer.app.WebServerManager.get().stop();
                } else {
                    com.memorizer.app.WebServerManager.get().start();
                }
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Sync server error: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
            lblSyncStatus.setText(com.memorizer.app.WebServerManager.get().isRunning()?"Running":"Stopped");
            btnToggleSync.setText(com.memorizer.app.WebServerManager.get().isRunning()?"Disable":"Enable");
        });
        btnPair.setOnAction(e -> {
            try {
                if (!com.memorizer.app.WebServerManager.get().isRunning()) com.memorizer.app.WebServerManager.get().start();
                int p = com.memorizer.app.WebServerManager.get().getPort();
                int port = (p==0? com.memorizer.app.Config.getInt("app.web.port",7070):p);
                String httpsUrl = "https://localhost:" + port + "/pair";
                String httpUrl  =  "http://localhost:" + port + "/pair";
                boolean ok = com.memorizer.util.Browse.open(httpsUrl);
                if (!ok) com.memorizer.util.Browse.open(httpUrl);
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Open pairing page failed: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });
        HBox syncRow = new HBox(10, lblSync, lblSyncStatus, btnToggleSync, btnPair);
        g.add(syncRow, 0, r, 2, 1);

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
        btnFlip.getStyleClass().addAll("btn","btn-info");
        btnAgain.getStyleClass().addAll("btn","btn-danger");
        btnHard.getStyleClass().addAll("btn","btn-warning");
        btnGood.getStyleClass().addAll("btn","btn-success");
        btnEasy.getStyleClass().addAll("btn","btn-primary");
        btnNext.getStyleClass().addAll("btn","btn-default");

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
        HBox bar = new HBox(16);
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: #f2f2f2;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        lblNotice.getStyleClass().add("batch-info");
        bar.getChildren().addAll(new Label("Scheduler:"), lblScheduler, spacer, lblNotice);
        return bar;
    }

    private void showNotice(String msg) {
        lblNotice.setText(msg == null ? "" : msg);
        if (msg == null || msg.trim().isEmpty()) return;
        PauseTransition pt = new PauseTransition(Duration.seconds(3));
        pt.setOnFinished(e -> { if (lblNotice.getText().equals(msg)) lblNotice.setText(""); });
        pt.playFromStart();
    }

    // ---- public helpers ----
    public void showAndFocus() {
        // Fit to OS work area so bottom edge is flush with taskbar
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        setX(Math.floor(vb.getMinX()));
        setY(Math.floor(vb.getMinY()));
        // add a tiny fudge on height to avoid a 1px gap due to DPI rounding
        setWidth(Math.ceil(vb.getWidth()));
        setHeight(Math.ceil(vb.getHeight() + 1));
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
        com.memorizer.service.PlanService.Counts pc = study.planCounts();
        lblPlanPending.setText(String.valueOf(pc.pending));
        lblPlanDone.setText(String.valueOf(pc.done));
        lblPlanTotal.setText(String.valueOf(pc.total));
    }

    private void loadNextForStudy() {
        java.util.Optional<com.memorizer.service.StudyService.CardView> opt = study.currentOrNextOrFallback();
        if (opt.isPresent()) {
            com.memorizer.service.StudyService.CardView v = opt.get();
            lblFront.setText(v.getFront());
            lblBack.setText(v.getBack());
            showingFront = true;
            lblFront.setVisible(true);
            lblBack.setVisible(false);
        } else {
            lblFront.setText("(No cards. Use File -> Import Excel...)");
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

    private void applyPlanPage(int pageIndex) {
        if (pageIndex < 0) pageIndex = 0;
        int pages = Math.max(1, (int) Math.ceil(fullPlan.size() / (double) planPageSize));
        if (pageIndex >= pages) pageIndex = pages - 1;
        if (planPagination != null) {
            planPagination.setCurrentPageIndex(pageIndex);
        }
        java.util.List<TableColumn<com.memorizer.service.PlanService.PlanRow, ?>> order = new java.util.ArrayList<>(planTable.getSortOrder());
        if (!order.isEmpty()) {
            java.util.Comparator<com.memorizer.service.PlanService.PlanRow> cmp = null;
            for (TableColumn<com.memorizer.service.PlanService.PlanRow, ?> tc : order) {
                java.util.Comparator<com.memorizer.service.PlanService.PlanRow> part = null;
                if (tc == colOrder) part = java.util.Comparator.comparingInt(com.memorizer.service.PlanService.PlanRow::getOrderNo);
                else if (tc == colKind) part = java.util.Comparator.comparingInt(com.memorizer.service.PlanService.PlanRow::getKind);
                else if (tc == colStatus) part = java.util.Comparator.comparingInt(com.memorizer.service.PlanService.PlanRow::getStatus);
                else if (tc == colDeck) part = java.util.Comparator.comparing(r -> r.getDeckName() == null ? "" : r.getDeckName());
                else if (tc == colFront) part = java.util.Comparator.comparing(r -> r.getFront() == null ? "" : r.getFront());
                if (part != null) {
                    if (tc.getSortType() == TableColumn.SortType.DESCENDING) part = part.reversed();
                    cmp = (cmp == null) ? part : cmp.thenComparing(part);
                }
            }
            if (cmp != null) fullPlan.sort(cmp);
        }
        int from = pageIndex * planPageSize;
        int to = Math.min(fullPlan.size(), from + planPageSize);
        planRows.setAll(fullPlan.subList(from, to));
    }

    private void changePlanPage(int delta) {
        int idx = planPagination.getCurrentPageIndex() + delta;
        applyPlanPage(idx);
    }

}

