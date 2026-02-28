package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.db.CardRepository;
import com.memorizer.db.NoteRepository;
import com.memorizer.model.Note;
import com.memorizer.service.PlanService;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.control.ProgressBar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Exam panel for testing knowledge without affecting SRS.
 * Supports different question sources (Plan/Due/New) and shuffle mode.
 */
public class ExamPanel {
    private final StudyService studyService;

    // UI components
    private final Label examFront = new Label();
    private final Label examBack = new Label();
    private final Label examReadingPos = new Label("");
    private final Label examDeck = new Label("");
    private final Label examTags = new Label("");
    private final TextArea examExamples = new TextArea();
    private final Label examProgress = new Label("0/0");
    private final Label examScore = new Label("Score: 0%");
    private final Label examOkLabel = new Label("OK: 0");
    private final Label examNgLabel = new Label("NG: 0");
    private final ProgressBar examProgressBar = new ProgressBar(0);

    // Extra card-panel labels (managed visibility together with answer)
    private Label answerBadge;
    private Separator cardDivider;
    private HBox cardMetaRow;
    private Label examplesHeaderLabel;

    private ComboBox<String> examSourceBox;
    private CheckBox examShuffleBox;
    private Spinner<Integer> examBatchSpinner;
    private CheckBox examRepeatWrongsBox;
    private CheckBox examCompactBox;
    private CheckBox examBackFirstBox;
    private Button btnExport;

    private Button btnExamShow;
    private Button btnExamCorrect;
    private Button btnExamWrong;
    private Button btnExamNext;
    private Button btnExamRestart;

    // Exam state
    private boolean examShowingAnswer = false;
    private final List<Long> examQueue = new ArrayList<>();
    private int examIndex = 0;
    private int examCorrect = 0;
    private int examWrong = 0;

    // Results log for export
    private static class ExamResult { long cardId; boolean correct; }
    private final List<ExamResult> results = new ArrayList<>();

    public ExamPanel(StudyService studyService) {
        this.studyService = studyService;
    }

    // ---- compact mode ref (kept for applySizing) ----
    private VBox bottomSection;

    public Pane build() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f0f4f8;");

        // ── Create all buttons and config controls first ──────────────────────
        createButtons();

        // ── TOP: settings bar + progress bar ──────────────────────────────────
        VBox topSection = new VBox(8);
        topSection.setPadding(new Insets(10, 16, 10, 16));
        topSection.setStyle("-fx-background-color: #ffffff;"
                + "-fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");

        // Settings row
        HBox settingsLeft = new HBox(10,
                new Label("Source:"), examSourceBox,
                examShuffleBox,
                new Label("Batch:"), examBatchSpinner,
                examBackFirstBox,
                examRepeatWrongsBox
        );
        settingsLeft.setAlignment(Pos.CENTER_LEFT);
        // Compact toggle appended into settingsLeft
        boolean compactPref = Config.getBool("app.ui.exam.compact", false);
        ensureCompactToggle(settingsLeft, compactPref);

        Region settingsSpacer = new Region();
        HBox.setHgrow(settingsSpacer, Priority.ALWAYS);
        Button btnEditTop = new Button("Edit…");
        btnEditTop.setStyle("-fx-font-size: 12px;");
        btnEditTop.setOnAction(e -> openEditorForCurrent());
        HBox settingsRow = new HBox(10, settingsLeft, settingsSpacer, btnEditTop);
        settingsRow.setAlignment(Pos.CENTER_LEFT);

        // Progress row
        examProgressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(examProgressBar, Priority.ALWAYS);
        examProgressBar.setPrefHeight(10);
        examProgressBar.setStyle("-fx-accent: #3fb950; -fx-background-radius: 5;");

        examProgress.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #4a5568;");
        examOkLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #38a169; -fx-font-weight: bold;");
        examNgLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #e53e3e; -fx-font-weight: bold;");
        examScore.setStyle("-fx-font-size: 12px; -fx-text-fill: #4a5568; -fx-font-weight: bold;");

        HBox progressRow = new HBox(10,
                examProgress, examProgressBar,
                examOkLabel, examNgLabel, examScore
        );
        progressRow.setAlignment(Pos.CENTER_LEFT);

        topSection.getChildren().addAll(settingsRow, progressRow);
        root.setTop(topSection);

        // ── CENTER: card content ───────────────────────────────────────────────
        // Front / question side
        examFront.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #1a202c;");
        examFront.setWrapText(true);
        examFront.setMaxWidth(Double.MAX_VALUE);

        // Answer / back side – hidden until "Show Answer"
        examBack.setStyle("-fx-font-size: 22px; -fx-text-fill: #2d3748;");
        examBack.setVisible(false);
        examBack.setWrapText(true);
        examBack.setMaxWidth(Double.MAX_VALUE);

        examReadingPos.setStyle("-fx-font-size: 14px; -fx-text-fill: #718096; -fx-font-style: italic;");
        examReadingPos.setVisible(false);
        examReadingPos.setWrapText(true);
        examReadingPos.setMaxWidth(Double.MAX_VALUE);

        examDeck.setStyle("-fx-font-size: 12px; -fx-text-fill: #ffffff;"
                + "-fx-background-color: #667eea; -fx-background-radius: 10;"
                + "-fx-padding: 2 10 2 10;");
        examDeck.setVisible(false);

        examTags.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096;");
        examTags.setVisible(false);

        Label questionBadge = new Label("QUESTION");
        questionBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;"
                + "-fx-text-fill: #718096; -fx-letter-spacing: 1;");

        answerBadge = new Label("ANSWER");
        answerBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;"
                + "-fx-text-fill: #2f855a; -fx-letter-spacing: 1;");
        answerBadge.setVisible(false);

        cardDivider = new Separator();
        cardDivider.setVisible(false);

        cardMetaRow = new HBox(8, examDeck, examTags);
        cardMetaRow.setAlignment(Pos.CENTER_LEFT);
        cardMetaRow.setVisible(false);

        VBox cardPane = new VBox(10,
                questionBadge,
                examFront,
                cardDivider,
                answerBadge,
                examBack,
                examReadingPos,
                cardMetaRow
        );
        cardPane.setPadding(new Insets(24, 28, 24, 28));
        cardPane.setStyle("-fx-background-color: #ffffff;"
                + "-fx-background-radius: 12;"
                + "-fx-border-color: #e2e8f0;"
                + "-fx-border-radius: 12;"
                + "-fx-border-width: 1;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 3);");
        cardPane.setMaxWidth(Double.MAX_VALUE);

        // Examples section
        examExamples.setEditable(false);
        examExamples.setWrapText(true);
        examExamples.setVisible(false);
        examExamples.setPrefRowCount(3);
        examExamples.setStyle("-fx-font-size: 13px;"
                + "-fx-control-inner-background: #f7fafc;"
                + "-fx-border-color: #e2e8f0; -fx-border-radius: 6;");

        examplesHeaderLabel = new Label("EXAMPLES");
        examplesHeaderLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;"
                + "-fx-text-fill: #718096; -fx-letter-spacing: 1;");
        examplesHeaderLabel.setVisible(false);

        VBox examplesBox = new VBox(6, examplesHeaderLabel, examExamples);
        examplesBox.setMaxWidth(Double.MAX_VALUE);

        VBox centerBox = new VBox(16, cardPane, examplesBox);
        centerBox.setPadding(new Insets(20, 24, 16, 24));
        centerBox.setAlignment(Pos.TOP_CENTER);
        centerBox.setMaxWidth(Double.MAX_VALUE);

        ScrollPane centerScroll = new ScrollPane(centerBox);
        centerScroll.setFitToWidth(true);
        centerScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        centerScroll.setStyle("-fx-background-color: #f0f4f8; -fx-background: #f0f4f8;");
        root.setCenter(centerScroll);

        // ── BOTTOM: action buttons ─────────────────────────────────────────────
        bottomSection = new VBox(10);
        bottomSection.setPadding(new Insets(12, 16, 16, 16));
        bottomSection.setStyle("-fx-background-color: #ffffff;"
                + "-fx-border-color: #e2e8f0; -fx-border-width: 1 0 0 0;");

        btnExamShow.setMaxWidth(Double.MAX_VALUE);

        btnExamCorrect.setMaxWidth(Double.MAX_VALUE);
        btnExamWrong.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(btnExamCorrect, Priority.ALWAYS);
        HBox.setHgrow(btnExamWrong, Priority.ALWAYS);
        HBox cwRow = new HBox(12, btnExamCorrect, btnExamWrong);
        cwRow.setAlignment(Pos.CENTER);

        btnExport = new Button("Export…");
        btnExport.setStyle("-fx-font-size: 12px;");
        btnExport.setOnAction(e -> exportResults());
        Region navSpacer = new Region();
        HBox.setHgrow(navSpacer, Priority.ALWAYS);
        HBox navRow = new HBox(10, btnExamNext, btnExamRestart, navSpacer, btnExport);
        navRow.setAlignment(Pos.CENTER_LEFT);

        bottomSection.getChildren().addAll(btnExamShow, cwRow, navRow);
        root.setBottom(bottomSection);

        // Apply initial button sizing
        applySizing(compactPref);

        // Setup keyboard shortcuts
        setupKeyboardShortcuts(root);

        // Initialize first exam session
        prepareExamQueue();
        showCurrentCard();

        return root;
    }

    /**
     * Create and configure all control buttons.
     */
    private void createButtons() {
        btnExamShow = new Button("▶  Show Answer  (Enter)");
        btnExamCorrect = new Button("✓  Correct  (A)");
        btnExamWrong = new Button("✗  Wrong  (S)");
        btnExamNext = new Button("Next →");
        btnExamRestart = new Button("↺  Restart");

        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);

        btnExamShow.setOnAction(e -> showAnswer());
        btnExamCorrect.setOnAction(e -> markCorrect());
        btnExamWrong.setOnAction(e -> markWrong());
        btnExamNext.setOnAction(e -> gotoNext());
        btnExamRestart.setOnAction(e -> restart());

        createConfigurationControls();
    }

    /**
     * Create source, shuffle, and batch size controls.
     */
    private void createConfigurationControls() {
        // Source selection
        examSourceBox = new ComboBox<>();
        examSourceBox.getItems().setAll("Plan", "Due", "New");
        String sourcePref = Config.get("app.ui.exam.source", "Plan");
        if (!examSourceBox.getItems().contains(sourcePref)) {
            sourcePref = "Plan";
        }
        examSourceBox.getSelectionModel().select(sourcePref);
        examSourceBox.valueProperty().addListener((o, ov, nv) -> {
            Config.set("app.ui.exam.source", nv == null ? "Plan" : nv);
            restart();
        });

        // Shuffle checkbox
        examShuffleBox = new CheckBox("Shuffle");
        boolean shufflePref = Config.getBool("app.ui.exam.shuffle", true);
        examShuffleBox.setSelected(shufflePref);
        examShuffleBox.selectedProperty().addListener((o, ov, nv) -> {
            Config.set("app.ui.exam.shuffle", String.valueOf(nv));
            restart();
        });

        // Batch size spinner
        int batchPref = Config.getInt("app.ui.exam.batch-size", 20);
        examBatchSpinner = new Spinner<>(1, 500, Math.max(1, batchPref));
        examBatchSpinner.setEditable(true);
        examBatchSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (nv != null) {
                Config.set("app.ui.exam.batch-size", String.valueOf(nv));
                restart();
            }
        });

        // Repeat wrongs at end
        boolean repPref = Config.getBool("app.ui.exam.repeat-wrongs", true);
        examRepeatWrongsBox = new CheckBox("Repeat wrongs at end");
        examRepeatWrongsBox.setSelected(repPref);
        examRepeatWrongsBox.selectedProperty().addListener((o, ov, nv) -> {
            Config.set("app.ui.exam.repeat-wrongs", String.valueOf(nv));
        });

        // Back first (reverse) toggle
        boolean backFirstPref = Config.getBool("app.ui.exam.back-first", false);
        examBackFirstBox = new CheckBox("Back first");
        examBackFirstBox.setSelected(backFirstPref);
        examBackFirstBox.selectedProperty().addListener((o, ov, nv) -> {
            Config.set("app.ui.exam.back-first", String.valueOf(nv));
            restart();
        });
    }

    /**
     * Setup keyboard shortcuts for exam navigation.
     */
    private void setupKeyboardShortcuts(Pane pane) {
        pane.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                if (!examShowingAnswer) {
                    btnExamShow.fire();
                } else {
                    btnExamNext.fire();
                }
            } else if (ev.getCode() == KeyCode.A) {
                if (!btnExamCorrect.isDisabled()) {
                    btnExamCorrect.fire();
                }
            } else if (ev.getCode() == KeyCode.S) {
                if (!btnExamWrong.isDisabled()) {
                    btnExamWrong.fire();
                }
            } else if (ev.getCode() == KeyCode.ESCAPE) {
                btnExamRestart.fire();
            }
        });
    }

    /**
     * Show the answer for current card.
     */
    private void showAnswer() {
        examShowingAnswer = true;
        // Reveal both sides when answer is shown
        examBack.setVisible(true);
        examFront.setVisible(true);
        examReadingPos.setVisible(true);
        examDeck.setVisible(true);
        examTags.setVisible(true);
        examExamples.setVisible(true);
        // Extra card-panel decorators
        if (answerBadge != null) answerBadge.setVisible(true);
        if (cardDivider != null) cardDivider.setVisible(true);
        if (cardMetaRow != null) cardMetaRow.setVisible(true);
        if (examplesHeaderLabel != null) examplesHeaderLabel.setVisible(
                examExamples.getText() != null && !examExamples.getText().isEmpty());
        btnExamCorrect.setDisable(false);
        btnExamWrong.setDisable(false);
    }

    /**
     * Mark current answer as correct.
     */
    private void markCorrect() {
        examCorrect++;
        updateCountersAndScore();
        recordResult(true);
        gotoNext();
    }

    /**
     * Mark current answer as wrong.
     */
    private void markWrong() {
        examWrong++;
        updateCountersAndScore();
        if (examRepeatWrongsBox != null && examRepeatWrongsBox.isSelected()) {
            enqueueWrongCurrent();
        }
        recordResult(false);
        gotoNext();
    }

    /**
     * Restart exam with fresh queue.
     */
    private void restart() {
        prepareExamQueue();
        showCurrentCard();
    }

    /**
     * Prepare exam queue based on current settings.
     */
    private void prepareExamQueue() {
        examQueue.clear();
        examIndex = 0;
        examCorrect = 0;
        examWrong = 0;
        updateCountersAndScore();
        wrongQueue.clear();
        repeatingWrongs = false;
        results.clear();

        List<PlanService.PlanRow> rows = studyService.planListToday();
        int limit = Math.max(1, Config.getInt("app.ui.exam.batch-size", 20));
        String source = Config.get("app.ui.exam.source", "Plan");
        boolean useShuffle = Config.getBool("app.ui.exam.shuffle", true);

        // Filter cards based on source
        List<Long> pool = new ArrayList<>();
        for (PlanService.PlanRow row : rows) {
            if (row == null) continue;
            
            if ("Plan".equalsIgnoreCase(source)) {
                pool.add(row.getCardId());
            } else if ("Due".equalsIgnoreCase(source)) {
                if (row.getKind() == 0) { // DUE cards only
                    pool.add(row.getCardId());
                }
            } else if ("New".equalsIgnoreCase(source)) {
                if (row.getKind() == 2) { // NEW cards only
                    pool.add(row.getCardId());
                }
            }
        }

        // Shuffle if enabled
        if (useShuffle) {
            Collections.shuffle(pool);
        }

        // Limit to batch size
        for (Long cardId : pool) {
            examQueue.add(cardId);
            if (examQueue.size() >= limit) {
                break;
            }
        }

        // Fallback: if no cards found, get any available card
        if (examQueue.isEmpty()) {
            Optional<com.memorizer.model.Card> anyCard = 
                new CardRepository().findAnyAvailable();
            if (anyCard.isPresent()) {
                examQueue.add(anyCard.get().id);
            }
        }

        examProgress.setText(examQueue.isEmpty() ? "0/0" : "1/" + examQueue.size());
        examProgressBar.setProgress(examQueue.isEmpty() ? 0 : 0.0);
    }

    /**
     * Display current card in the queue.
     */
    private void showCurrentCard() {
        if (examQueue.isEmpty() || examIndex < 0 || examIndex >= examQueue.size()) {
            showNoCards();
            return;
        }

        long cardId = examQueue.get(examIndex);
        NoteRepository noteRepo = new NoteRepository();
        Optional<Note> noteOpt = noteRepo.findByCardId(cardId);

        String front = noteOpt.isPresent() && noteOpt.get().front != null ? 
                      noteOpt.get().front : "";
        String back = noteOpt.isPresent() && noteOpt.get().back != null ? 
                     noteOpt.get().back : "";
        String reading = noteOpt.isPresent() && noteOpt.get().reading != null ? noteOpt.get().reading : "";
        String pos = noteOpt.isPresent() && noteOpt.get().pos != null ? noteOpt.get().pos : "";
        String examples = noteOpt.isPresent() && noteOpt.get().examples != null ? noteOpt.get().examples : "";
        String tags = noteOpt.isPresent() && noteOpt.get().tags != null ? noteOpt.get().tags : "";
        String deckName = "";
        if (noteOpt.isPresent() && noteOpt.get().deckId != null) {
            try {
                deckName = new com.memorizer.db.DeckRepository().findNameById(noteOpt.get().deckId);
                if (deckName == null) deckName = "";
            } catch (Exception ignored) {}
        }

        examFront.setText(front);
        examBack.setText(back);
        String rp = (reading == null ? "" : reading.trim());
        if (!rp.isEmpty() && pos != null && !pos.trim().isEmpty()) rp = rp + "  •  " + pos.trim();
        else if (rp.isEmpty()) rp = pos == null ? "" : pos.trim();
        examReadingPos.setText(rp);
        examExamples.setText(examples);
        examDeck.setText(deckName);
        examTags.setText(tags);
        examShowingAnswer = false;
        boolean backFirst = examBackFirstBox != null && examBackFirstBox.isSelected();
        examBack.setVisible(backFirst);
        examFront.setVisible(!backFirst);
        examReadingPos.setVisible(false);
        examDeck.setVisible(false);
        examTags.setVisible(false);
        examExamples.setVisible(false);
        // Extra card-panel decorators
        if (answerBadge != null) answerBadge.setVisible(false);
        if (cardDivider != null) cardDivider.setVisible(false);
        if (cardMetaRow != null) cardMetaRow.setVisible(false);
        if (examplesHeaderLabel != null) examplesHeaderLabel.setVisible(false);

        btnExamShow.setDisable(false);
        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);
        btnExamNext.setDisable(false);

        examProgress.setText((examIndex + 1) + "/" + examQueue.size());
        if (examQueue.size() > 0) {
            double p = Math.max(0, Math.min(1.0, (double) examIndex / (double) examQueue.size()));
            examProgressBar.setProgress(p);
        } else {
            examProgressBar.setProgress(0);
        }
    }

    /**
     * Show no cards available message.
     */
    private void showNoCards() {
        examFront.setText("(No exam items)");
        examBack.setText("");
        examReadingPos.setText("");
        examDeck.setText("");
        examTags.setText("");
        examExamples.setText("");
        examBack.setVisible(false);
        examFront.setVisible(true);
        examReadingPos.setVisible(false);
        examDeck.setVisible(false);
        examTags.setVisible(false);
        examExamples.setVisible(false);
        if (answerBadge != null) answerBadge.setVisible(false);
        if (cardDivider != null) cardDivider.setVisible(false);
        if (cardMetaRow != null) cardMetaRow.setVisible(false);
        if (examplesHeaderLabel != null) examplesHeaderLabel.setVisible(false);
        btnExamShow.setDisable(true);
        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);
        btnExamNext.setDisable(true);
        examProgress.setText("0/0");
        examProgressBar.setProgress(0);
        updateCountersAndScore();
    }

    /**
     * Go to next card in exam queue.
     */
    private void gotoNext() {
        if (examQueue.isEmpty()) {
            showCurrentCard();
            return;
        }

        examIndex++;

        if (examIndex >= examQueue.size()) {
            if (examRepeatWrongsBox.isSelected() && !wrongQueue.isEmpty() && !repeatingWrongs) {
                // Start a second pass of wrong answers
                examQueue.clear();
                examQueue.addAll(wrongQueue);
                wrongQueue.clear();
                examIndex = 0;
                repeatingWrongs = true;
                examProgressBar.setProgress(0);
                showCurrentCard();
                return;
            }
            showExamFinished();
            return;
        }

        showCurrentCard();
    }

    /**
     * Show exam finished state.
     */
    private void showExamFinished() {
        btnExamShow.setDisable(true);
        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);
        btnExamNext.setDisable(true);
        
        int total = examCorrect + examWrong;
        String pct = total == 0 ? "" : String.format("  %.0f%%", (100.0 * examCorrect / total));
        examFront.setText("(Exam finished)" + pct);
        examBack.setText("");
        examReadingPos.setText("");
        examDeck.setText("");
        examTags.setText("");
        examExamples.setText("");
        examBack.setVisible(false);
        examFront.setVisible(true);
        examReadingPos.setVisible(false);
        examDeck.setVisible(false);
        examTags.setVisible(false);
        examExamples.setVisible(false);
        if (answerBadge != null) answerBadge.setVisible(false);
        if (cardDivider != null) cardDivider.setVisible(false);
        if (cardMetaRow != null) cardMetaRow.setVisible(false);
        if (examplesHeaderLabel != null) examplesHeaderLabel.setVisible(false);
        examProgress.setText(examQueue.size() + "/" + examQueue.size());
        examProgressBar.setProgress(1.0);
        updateCountersAndScore();
    }

    // ---- editor integration ----
    private void openEditorForCurrent() {
        if (examQueue.isEmpty() || examIndex < 0 || examIndex >= examQueue.size()) return;
        long cardId = examQueue.get(examIndex);
        EditorStage ed = new EditorStage();
        javafx.stage.Stage owner = com.memorizer.app.AppContext.getOwner();
        if (owner == null) owner = com.memorizer.app.AppContext.getMain();
        if (owner != null) ed.initOwner(owner);
        ed.setOnSaved(n -> {
            // reload current view with updated content
            showCurrentCard();
        });
        ed.loadByCardId(cardId);
        ed.show();
    }

    // ---- wrong answers repeat ----
    private final List<Long> wrongQueue = new ArrayList<>();
    private boolean repeatingWrongs = false;

    private void enqueueWrongCurrent() {
        if (examQueue.isEmpty() || examIndex < 0 || examIndex >= examQueue.size()) return;
        long cardId = examQueue.get(examIndex);
        wrongQueue.add(cardId);
    }

    private void recordResult(boolean correct) {
        if (examQueue.isEmpty() || examIndex < 0 || examIndex >= examQueue.size()) return;
        ExamResult r = new ExamResult();
        r.cardId = examQueue.get(examIndex);
        r.correct = correct;
        results.add(r);
    }

    private void exportResults() {
        if (results.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No results to export yet.", ButtonType.OK).showAndWait();
            return;
        }
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Exam Results");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        java.io.File out = fc.showSaveDialog(com.memorizer.app.AppContext.getMain());
        if (out == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("cardId,deck,front,back,reading,pos,tags,result\n");
        com.memorizer.db.NoteRepository nr = new com.memorizer.db.NoteRepository();
        for (ExamResult r : results) {
            java.util.Optional<com.memorizer.model.Note> on = nr.findByCardId(r.cardId);
            String deck = ""; String front = ""; String back = ""; String reading = ""; String pos = ""; String tags = "";
            if (on.isPresent()) {
                com.memorizer.model.Note n = on.get();
                front = safe(n.front); back = safe(n.back); reading = safe(n.reading); pos = safe(n.pos); tags = safe(n.tags);
                if (n.deckId != null) {
                    try { String dn = new com.memorizer.db.DeckRepository().findNameById(n.deckId); if (dn != null) deck = dn; } catch (Exception ignored) {}
                }
            }
            sb.append(r.cardId).append(',')
              .append(csv(deck)).append(',')
              .append(csv(front)).append(',')
              .append(csv(back)).append(',')
              .append(csv(reading)).append(',')
              .append(csv(pos)).append(',')
              .append(csv(tags)).append(',')
              .append(r.correct ? "OK" : "NG").append('\n');
        }
        try {
            java.nio.file.Files.write(out.toPath(), sb.toString().getBytes("UTF-8"));
            new Alert(Alert.AlertType.INFORMATION, "Exported to: " + out.getAbsolutePath(), ButtonType.OK).showAndWait();
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Export failed: " + ex.getMessage(), ButtonType.OK).showAndWait();
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String csv(String s) {
        if (s == null) return "";
        String t = s.replace("\r", " ").replace("\n", " ");
        if (t.contains(",") || t.contains("\"")) {
            t = t.replace("\"", "\"\"");
            return '"' + t + '"';
        }
        return t;
    }

    private void updateCountersAndScore() {
        examOkLabel.setText("OK: " + examCorrect);
        examNgLabel.setText("NG: " + examWrong);
        int total = examCorrect + examWrong;
        String pct = total == 0 ? "0%" : String.format("%.0f%%", (100.0 * examCorrect / total));
        examScore.setText("Score: " + pct);
    }

    // ---- compact mode and button styles ----
    private void ensureCompactToggle(HBox controlsRow1Left, boolean initial) {
        examCompactBox = new CheckBox("Compact");
        examCompactBox.setSelected(initial);
        examCompactBox.selectedProperty().addListener((o, ov, nv) -> {
            Config.set("app.ui.exam.compact", String.valueOf(nv));
            applySizing(nv);
        });
        // Insert at the end of the left group
        controlsRow1Left.getChildren().add(examCompactBox);
    }

    private void applySizing(boolean compact) {
        // Bottom section spacing
        if (bottomSection != null) bottomSection.setSpacing(compact ? 6 : 10);
        // Examples area height
        examExamples.setPrefRowCount(compact ? 2 : 3);
        // Button styles
        styleButtons(compact);
    }

    private void styleButtons(boolean compact) {
        // Show Answer: full width, prominent blue-ish
        String showSz = compact ? "14px" : "16px";
        String showPad = compact ? "8 16" : "12 20";
        btnExamShow.setStyle("-fx-font-size: " + showSz + "; -fx-font-weight: bold;"
                + "-fx-padding: " + showPad + ";"
                + "-fx-background-color: linear-gradient(#667eea, #5a67d8);"
                + "-fx-text-fill: white; -fx-background-radius: 8;");
        btnExamShow.setMinHeight(compact ? 34 : 42);

        // Correct (green) and Wrong (red)
        String cwSz = compact ? "14px" : "15px";
        String cwPad = compact ? "8 16" : "10 20";
        String base = "-fx-font-weight: 700; -fx-text-fill: white; -fx-background-radius: 8;";
        String greenN = "-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base
                + " -fx-background-color: linear-gradient(#38a169, #2f855a);";
        String greenH = "-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base
                + " -fx-background-color: linear-gradient(#48bb78, #2f855a);";
        String greenP = "-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base
                + " -fx-background-color: linear-gradient(#2f855a, #276749);";
        String redN = "-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base
                + " -fx-background-color: linear-gradient(#e53e3e, #c53030);";
        String redH = "-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base
                + " -fx-background-color: linear-gradient(#f56565, #c53030);";
        String redP = "-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base
                + " -fx-background-color: linear-gradient(#c53030, #9b2c2c);";

        btnExamCorrect.setStyle(greenN);
        btnExamCorrect.setOnMouseEntered(e -> btnExamCorrect.setStyle(greenH));
        btnExamCorrect.setOnMouseExited(e -> btnExamCorrect.setStyle(greenN));
        btnExamCorrect.setOnMousePressed(e -> btnExamCorrect.setStyle(greenP));
        btnExamCorrect.setOnMouseReleased(e -> btnExamCorrect.setStyle(greenH));

        btnExamWrong.setStyle(redN);
        btnExamWrong.setOnMouseEntered(e -> btnExamWrong.setStyle(redH));
        btnExamWrong.setOnMouseExited(e -> btnExamWrong.setStyle(redN));
        btnExamWrong.setOnMousePressed(e -> btnExamWrong.setStyle(redP));
        btnExamWrong.setOnMouseReleased(e -> btnExamWrong.setStyle(redH));
        btnExamCorrect.setMinHeight(compact ? 32 : 38);
        btnExamWrong.setMinHeight(compact ? 32 : 38);
    }

    @Override
    public String toString() { return "ExamPanel"; }
}
