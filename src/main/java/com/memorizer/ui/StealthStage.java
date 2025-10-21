package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.db.StatsRepository;
import com.memorizer.model.Rating;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Stealth banner window (Normal/Mini). Small, always-on-top bar with flip/rate controls.
 *
 * Responsibilities kept minimal; delegates details to small helpers:
 * - FlipStateManager: flip cycle logic (Normal=3 states, Mini=4 states)
 * - ExamplesViewManager: examples rendering + autoroll/marquee
 * - StealthWindowPositioner: owner/geometry
 * - TodayProgressView: today progress refresh
 */
public class StealthStage extends Stage {
    public enum UIMode { NORMAL, MINI }
    public enum ThemeMode { DARK, LIGHT }

    // External services
    private StudyService study;

    // Mode/theme
    private UIMode mode = "mini".equalsIgnoreCase(Config.get("app.ui.mode", "normal")) ? UIMode.MINI : UIMode.NORMAL;
    private ThemeMode theme = "light".equalsIgnoreCase(Config.get("app.ui.theme", "dark")) ? ThemeMode.LIGHT : ThemeMode.DARK;

    // Session/batch
    private boolean sessionActive = false;
    private boolean inBatch = false;
    private int remainingInBatch = 1;
    private long currentCardId = -1;

    // Flip state and content managers
    private final FlipStateManager flip = new FlipStateManager();
    private ExamplesViewManager examplesMgr;
    private TodayProgressView progressView;

    // UI nodes
    private BorderPane root;
    private GridPane grid; // main content grid (C0..C7)

    // content cells
    private final Label front = new Label();
    private final Label back = new Label();
    private final Label readingPos = new Label();
    private final VBox examplesBox = new VBox(2);
    private final ScrollPane examplesScroll = new ScrollPane();
    private final Label examplesMini = new Label();
    private final Label batchInfo = new Label("");
    private final Label batchText = new Label("");
    private final ProgressBar batchBar = new ProgressBar(0);
    private final Button btnEdit = new Button("Edit");
    private final Button btnAdd  = new Button("Add");
    private final Button btnAgain = new Button("1");
    private final Button btnHard  = new Button("2");
    private final Button btnGood  = new Button("3");
    private final Button btnEasy  = new Button("4");
    private final Button btnFlip  = new Button("Flip");
    private final Label todayText = new Label("Today: 0/0");
    private final ProgressBar todayBar = new ProgressBar(0);
    private final Label miniContent = new Label("");

    // separators for toggling in mini
    private final java.util.List<Separator> separators = new java.util.ArrayList<>();

    public StealthStage() {
        setTitle("Memorizer — Stealth");
        initStyle(StageStyle.TRANSPARENT);
        buildUI();

        // position/flags
        StealthWindowPositioner.initOwnerIfHidden(this);
        StealthWindowPositioner.applyGeometry(this, mode);

        // key handling
        Scene sc = getScene();
        sc.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.SPACE || ev.getCode() == KeyCode.ENTER) { doFlip(); }
            else if (ev.getCode() == KeyCode.DIGIT1) rateAndMaybeNext(Rating.AGAIN);
            else if (ev.getCode() == KeyCode.DIGIT2) rateAndMaybeNext(Rating.HARD);
            else if (ev.getCode() == KeyCode.DIGIT3) rateAndMaybeNext(Rating.GOOD);
            else if (ev.getCode() == KeyCode.DIGIT4) rateAndMaybeNext(Rating.EASY);
            else if (ev.getCode() == KeyCode.ESCAPE) hideWithSnooze();
            else if (ev.getCode() == KeyCode.M) setUIMode(mode == UIMode.MINI ? UIMode.NORMAL : UIMode.MINI);
        });

        // init helpers
        examplesMgr = new ExamplesViewManager(examplesBox, examplesScroll, examplesMini);
        progressView = new TodayProgressView(todayText, todayBar);
        refreshTodayProgress();
        applyThemeClasses();
        applyModeLayout();
    }

    private void buildUI() {
        // Base roles/styles
        front.getStyleClass().add("cell-front");
        back.getStyleClass().add("cell-back");
        readingPos.getStyleClass().addAll("cell-reading","muted");
        examplesMini.getStyleClass().add("line-sub");
        todayBar.setMaxWidth(Double.MAX_VALUE);

        // Answers clusters (Normal with labels, Mini numeric)
        Button nAgain = new Button("1 Again");
        Button nHard  = new Button("2 Hard");
        Button nGood  = new Button("3 Good");
        Button nEasy  = new Button("4 Easy");
        HBox answersNormal = new HBox(6, nAgain, nHard, nGood, nEasy);
        answersNormal.getStyleClass().add("controls");
        HBox answersMini = new HBox(6, btnAgain, btnHard, btnGood, btnEasy);
        answersMini.getStyleClass().add("controls");
        // Styles
        for (Button b : new Button[]{nAgain,nHard,nGood,nEasy}) b.getStyleClass().addAll("controls","btn-answer");
        nAgain.getStyleClass().add("btn-again"); nHard.getStyleClass().add("btn-hard"); nGood.getStyleClass().add("btn-good"); nEasy.getStyleClass().add("btn-easy");
        btnAgain.getStyleClass().addAll("controls","btn-answer","btn-again");
        btnHard.getStyleClass().addAll("controls","btn-answer","btn-hard");
        btnGood.getStyleClass().addAll("controls","btn-answer","btn-good");
        btnEasy.getStyleClass().addAll("controls","btn-answer","btn-easy");
        btnFlip.getStyleClass().addAll("controls","btn-flip");
        // Left buttons match control height for consistent top alignment in Mini
        btnEdit.getStyleClass().addAll("controls","btn-edit");
        btnAdd.getStyleClass().addAll("controls","btn-add");

        // Examples container for Normal
        examplesScroll.setContent(examplesBox);
        examplesScroll.setFitToWidth(true);
        examplesScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Progress container (Today with inline batch suffix)
        VBox progressBox = new VBox(4, todayBar, todayText);
        progressBox.getStyleClass().add("cell-progress");

        // Grid layout per CHECKPOINT (C0..C7)
        grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(2);
        grid.setPadding(new Insets(6, 12, 6, 12));

        // Column constraints: C4 primary flex; C1/C3 sometimes; others fixed/minimal
        ColumnConstraints c0 = new ColumnConstraints(); // batch/edit
        ColumnConstraints c1 = new ColumnConstraints(); // front
        ColumnConstraints c2 = new ColumnConstraints(); // reading/pos
        ColumnConstraints c3 = new ColumnConstraints(); // back
        ColumnConstraints c4 = new ColumnConstraints(); // examples
        ColumnConstraints c5 = new ColumnConstraints(); // flip
        ColumnConstraints c6 = new ColumnConstraints(); // answers
        ColumnConstraints c7 = new ColumnConstraints(); // progress
        c1.setHgrow(Priority.SOMETIMES);
        c3.setHgrow(Priority.ALWAYS); // allow back to extend horizontally when space is available
        c4.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(c0,c1,c2,c3,c4,c5,c6,c7);
        // Center row vertically
        RowConstraints row = new RowConstraints();
        row.setValignment(VPos.CENTER);
        grid.getRowConstraints().add(row);

        int col = 0;
        // C0: Left command buttons (Edit/Add stacked). Mini only Edit.
        batchInfo.getStyleClass().add("batch-info");
        VBox leftBox = new VBox(6, btnEdit, btnAdd);
        grid.add(leftBox, col++, 0);
        GridPane.setValignment(leftBox, VPos.CENTER);
        addSeparator(col++);

        // C1: FRONT (wrap up to 2 lines in Normal)
        front.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(front, Priority.SOMETIMES);
        grid.add(front, col++, 0);
        addSeparator(col++);

        // C2: Reading/Pos (small, single-line ellipsis)
        readingPos.setMaxWidth(220);
        readingPos.setTextOverrun(OverrunStyle.ELLIPSIS);
        grid.add(readingPos, col++, 0);
        addSeparator(col++);

        // C3: BACK (wrap up to 2 lines in Normal)
        back.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(back, Priority.SOMETIMES);
        grid.add(back, col++, 0);
        addSeparator(col++);

        // C4: EXAMPLES (Normal: ScrollPane; Mini: single-line label)
        StackPane examplesCell = new StackPane(examplesScroll, examplesMini);
        GridPane.setHgrow(examplesCell, Priority.ALWAYS);
        grid.add(examplesCell, col++, 0);
        GridPane.setValignment(examplesCell, VPos.CENTER);
        addSeparator(col++);

        // C5: Flip button
        grid.add(btnFlip, col++, 0);
        GridPane.setValignment(btnFlip, VPos.CENTER);
        addSeparator(col++);

        // C6: Answers (stack normal/mini; toggle by mode)
        answersNormal.setAlignment(Pos.CENTER);
        answersMini.setAlignment(Pos.CENTER);
        StackPane answersCell = new StackPane(answersNormal, answersMini);
        grid.add(answersCell, col++, 0);
        GridPane.setValignment(answersCell, VPos.CENTER);
        addSeparator(col++);

        // C7: Progress
        grid.add(progressBox, col, 0);
        GridPane.setValignment(progressBox, VPos.CENTER);

        // Mini unified content (spans C1..C4)
        miniContent.getStyleClass().add("cell-front");
        miniContent.setMaxWidth(Double.MAX_VALUE);
        miniContent.setWrapText(false); // single-line, use marquee when long
        miniContent.setTextOverrun(OverrunStyle.CLIP);
        GridPane.setColumnIndex(miniContent, 1);
        GridPane.setColumnSpan(miniContent, 4);
        GridPane.setHgrow(miniContent, Priority.ALWAYS);
        grid.getChildren().add(miniContent);
        miniContent.setVisible(false);
        miniContent.setManaged(false);

        root = new BorderPane(grid);
        Scene sc = new Scene(root);
        sc.setFill(javafx.scene.paint.Color.TRANSPARENT);
        setScene(sc);

        // Actions
        btnFlip.setOnAction(e -> doFlip());
        // Rating handlers (both clusters)
        java.util.function.Consumer<Rating> rate = (r) -> rateAndMaybeNext(r);
        btnAgain.setOnAction(e -> rate.accept(Rating.AGAIN));
        btnHard.setOnAction(e -> rate.accept(Rating.HARD));
        btnGood.setOnAction(e -> rate.accept(Rating.GOOD));
        btnEasy.setOnAction(e -> rate.accept(Rating.EASY));
        nAgain.setOnAction(e -> rate.accept(Rating.AGAIN));
        nHard.setOnAction(e -> rate.accept(Rating.HARD));
        nGood.setOnAction(e -> rate.accept(Rating.GOOD));
        nEasy.setOnAction(e -> rate.accept(Rating.EASY));

        // Edit/Add actions
        btnEdit.setOnAction(e -> {
            if (currentCardId > 0) {
                EditorStage es = new EditorStage();
                es.initOwner(this);
                es.setOnSaved(n -> {
                    if (study != null && currentCardId > 0) {
                        study.viewCardById(currentCardId).ifPresent(cv -> {
                            renderForFlipState(cv);
                            examplesMgr.setExamples(cv.getExamples(), mode);
                            updateExamplesVisibility();
                        });
                    }
                });
                es.show();
                es.loadByCardId(currentCardId);
            }
        });
        btnAdd.setOnAction(e -> DialogFactory.showNewEntryDialog(this, msg -> {}));

        // Tooltips (show full content on hover)
        bindTooltipTo(front);
        bindTooltipTo(back);
        bindTooltipTo(readingPos);
        bindTooltipTo(miniContent);
    }

    // ===== Public API (used by scheduler/tray/UI) =====
    public void bindStudy(StudyService study) { this.study = study; }
    public boolean isSessionActive() { return sessionActive; }
    public void startBatch(int batchSize) {
        if (batchSize < 1) batchSize = 1;
        inBatch = batchSize > 1;
        remainingInBatch = batchSize;
        sessionActive = true;
        updateBatchInfo();
    }
    public void showCardView(StudyService.CardView v) {
        if (v == null) return;
        currentCardId = v.getCardId();
        // Reset flip state on each card
        flip.reset();
        renderForFlipState(v);

        // Examples
        examplesMgr.setExamples(v.getExamples(), mode);
        updateExamplesVisibility();

        // Focus capture to ensure keys go to scene root
        try { if (getScene() != null && getScene().getRoot() != null) getScene().getRoot().requestFocus(); } catch (Exception ignored) {}
        updateBatchInfo();
        refreshTodayProgress();
    }
    public void refreshTodayProgress() { progressView.refresh(); }
    public void setUIMode(UIMode m) { if (m != null && m != mode) { mode = m; Config.set("app.ui.mode", mode==UIMode.MINI?"mini":"normal"); applyModeLayout(); StealthWindowPositioner.applyGeometry(this, mode); updateExamplesVisibility(); } }
    public void setTheme(ThemeMode t) { if (t != null && t != theme) { theme = t; Config.set("app.ui.theme", theme==ThemeMode.LIGHT?"light":"dark"); applyThemeClasses(); } }
    public void showAndFocus() { if (!isShowing()) show(); toFront(); requestFocus(); setIconified(false); }
    /** Skip current card without rating and advance batch if applicable. */
    public void skipCurrent() {
        if (study != null) {
            long lastId = currentCardId;
            study.skipCurrent();
            if (inBatch) {
                remainingInBatch--;
                boolean allowFallback = Boolean.parseBoolean(Config.get("app.study.force-show-when-empty", "true"));
                if (remainingInBatch > 0) {
                    java.util.Optional<StudyService.CardView> next = study.nextForBatch(lastId, allowFallback);
                    if (next.isPresent()) { showCardView(next.get()); return; }
                }
            }
        }
        endBatchAndHide();
    }

    // ===== Internal behavior =====
    private void updateBatchInfo() {
        if (inBatch) {
            int total = remainingInBatch;
            int done = (currentCardId <= 0) ? 0 : Math.max(0, total - remainingInBatch);
            String suffix = "(" + done + "/" + total + ")";
            progressView.setSuffix(suffix);
        } else {
            progressView.setSuffix("");
        }
    }

    private void doFlip() {
        boolean isNormal = (mode == UIMode.NORMAL);
        flip.advance(isNormal);
        // Re-render if we have a card loaded
        if (study != null && currentCardId > 0) {
            java.util.Optional<StudyService.CardView> ov = study.viewCardById(currentCardId);
            ov.ifPresent(this::renderForFlipState);
        }
        updateExamplesVisibility();
    }

    private void renderForFlipState(StudyService.CardView v) {
        int state = flip.getFlipCount();
        boolean normal = (mode == UIMode.NORMAL);

        String reading = v.getReading();
        String pos = v.getPos();
        String rp = (reading == null ? "" : reading);
        if (pos != null && !pos.trim().isEmpty()) rp = rp.isEmpty()? ("["+pos+"]") : (rp + "  [" + pos + "]");
        readingPos.setText(rp);

        if (normal) {
            // 0: Front, 1: Back, 2: Both+Details
            boolean showFront = (state == 0) || (state == 2);
            boolean showBack  = (state == 1) || (state == 2);
            boolean showRP    = (state == 2);
            front.setText(showFront ? safe(v.getFront()) : ""); front.setVisible(showFront); front.setManaged(showFront);
            back.setText(showBack ? safe(v.getBack()) : "");   back.setVisible(showBack);   back.setManaged(showBack);
            readingPos.setVisible(showRP); readingPos.setManaged(showRP);
        } else {
            // Mini unified content spans center area; no ellipsis; marquee for long text
            String text;
            if (state == 0) text = safe(v.getFront());
            else if (state == 1) text = safe(v.getBack());
            else if (state == 2) text = rp;
            else text = examplesMgr.currentSingleLine();
            miniContent.setText(text);
            miniContent.setVisible(true); miniContent.setManaged(true);
            // Only examples state scrolls horizontally
            if (state == 3) {
                examplesMgr.startMiniMarqueeOn(miniContent);
            } else {
                examplesMgr.stop();
                miniContent.setTranslateX(0);
            }
            // Hide the normal centers in mini
            front.setVisible(false); front.setManaged(false);
            back.setVisible(false); back.setManaged(false);
            readingPos.setVisible(false); readingPos.setManaged(false);
        }
    }

    private void updateExamplesVisibility() {
        int state = flip.getFlipCount();
        if (mode == UIMode.NORMAL) {
            examplesMgr.updateForMode(mode, state);
        } else {
            if (examplesScroll != null) { examplesScroll.setVisible(false); examplesScroll.setManaged(false); }
            if (examplesMini != null) { examplesMini.setVisible(false); examplesMini.setManaged(false); }
        }
    }

    private void rateAndMaybeNext(Rating r) {
        if (study == null || currentCardId <= 0) return;
        long lastId = currentCardId;
        study.rate(r);
        if (inBatch) {
            remainingInBatch--;
            boolean allowFallback = Boolean.parseBoolean(Config.get("app.study.force-show-when-empty", "true"));
            if (remainingInBatch > 0) {
                java.util.Optional<StudyService.CardView> next = study.nextForBatch(lastId, allowFallback);
                if (next.isPresent()) { showCardView(next.get()); return; }
            }
        }
        endBatchAndHide();
    }

    private void endBatchAndHide() {
        inBatch = false; remainingInBatch = 1; currentCardId = -1; sessionActive = false; updateBatchInfo(); hide();
    }

    private void hideWithSnooze() {
        boolean snooze = Boolean.parseBoolean(Config.get("app.study.snooze-on-hide-enabled", "true"));
        int minutes = Config.getInt("app.study.snooze-on-hide-minutes", 10);
        if (snooze && study != null) study.dismissWithoutRating(true, minutes);
        endBatchAndHide();
    }

    private void applyModeLayout() {
        boolean mini = (mode == UIMode.MINI);
        // Toggle mini class for padding/height tweaks
        java.util.List<String> cls = root.getStyleClass();
        cls.remove("taskbar-mini");
        if (mini) cls.add("taskbar-mini");

        // Front/Back wrapping for Normal; Mini uses unified label without ellipsis
        front.setTextOverrun(OverrunStyle.ELLIPSIS);
        back.setTextOverrun(OverrunStyle.ELLIPSIS);
        readingPos.setTextOverrun(OverrunStyle.ELLIPSIS);
        front.setWrapText(!mini);
        back.setWrapText(!mini);

        boolean showNormalCenters = !mini;
        front.setVisible(showNormalCenters); front.setManaged(showNormalCenters);
        back.setVisible(showNormalCenters); back.setManaged(showNormalCenters);
        // readingPos is only shown dynamically in Normal details state
        readingPos.setVisible(false); readingPos.setManaged(false);
        if (examplesScroll != null) { examplesScroll.setVisible(showNormalCenters); examplesScroll.setManaged(showNormalCenters); }
        if (examplesMini != null) { examplesMini.setVisible(false); examplesMini.setManaged(false); }
        miniContent.setVisible(mini); miniContent.setManaged(mini);

        // Separators visibility per mode
        for (Separator s : separators) { s.setVisible(!mini); s.setManaged(!mini); }

        // Left buttons: mini requires Edit only; normal shows Edit + Add
        btnEdit.setVisible(true); btnEdit.setManaged(true);
        btnAdd.setVisible(!mini); btnAdd.setManaged(!mini);

        // Answers: toggle clusters
        // Find answers cell children and set visibility
        for (javafx.scene.Node n : grid.getChildren()) {
            if (n instanceof StackPane) {
                StackPane sp = (StackPane) n;
                if (sp.getChildren().size() == 2 && sp.getChildren().get(0) instanceof HBox && sp.getChildren().get(1) instanceof HBox) {
                    HBox answersNormal = (HBox) sp.getChildren().get(0);
                    HBox answersMini = (HBox) sp.getChildren().get(1);
                    answersNormal.setVisible(!mini); answersNormal.setManaged(!mini);
                    answersMini.setVisible(mini); answersMini.setManaged(mini);
                }
            }
        }
    }

    private void applyThemeClasses() {
        // Keep simple classes; external CSS can customize
        root.getStyleClass().removeAll("taskbar-dark","taskbar-light");
        root.getStyleClass().add(theme == ThemeMode.DARK ? "taskbar-dark" : "taskbar-light");
        getScene().getStylesheets().clear();
        String sel = theme == ThemeMode.DARK ? "/css/drawer-dark.css" : "/css/drawer-light.css";
        try { java.net.URL u = StealthStage.class.getResource(sel); if (u != null) getScene().getStylesheets().add(u.toExternalForm()); } catch (Exception ignored) {}
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private void bindTooltipTo(Label label) {
        if (label == null) return;
        Tooltip t = new Tooltip();
        t.textProperty().bind(label.textProperty());
        label.setTooltip(t);
    }

    private void addSeparator(int atCol) {
        Separator sep = new Separator();
        sep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        separators.add(sep);
        grid.add(sep, atCol, 0);
    }
}
