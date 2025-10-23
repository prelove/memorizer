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

    /**
     * Build the exam panel UI.
     * @return the constructed exam pane
     */
    // hold rows to adjust spacing in compact mode
    private HBox ctrlRow1;
    private HBox ctrlRow2;
    private HBox ctrlRow3;

    public Pane build() {
        BorderPane root = new BorderPane();
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        // Style front and back labels
        examFront.setStyle("-fx-font-size: 22px;");
        examBack.setStyle("-fx-font-size: 20px; -fx-text-fill: #444;");
        examBack.setVisible(false);
        examReadingPos.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");
        examReadingPos.setVisible(false);
        examExamples.setEditable(false);
        examExamples.setWrapText(true);
        examExamples.setVisible(false);
        examExamples.setPrefRowCount(3);

        HBox frontRow = new HBox(10, new Label("Front:"), examFront);
        HBox backRow = new HBox(10, new Label("Back:"), examBack);
        HBox rpRow = new HBox(10, new Label("Reading/POS:"), examReadingPos);
        HBox rowDeck = new HBox(10, new Label("Deck:"), examDeck);
        HBox rowTags = new HBox(10, new Label("Tags:"), examTags);

        // Create control buttons
        createButtons();
        
        // Create three-line controls
        // Row 1: configuration (left) + Edit/Show Answer (right)
        HBox controlsRow1 = new HBox(10,
            new Label("Source:"), examSourceBox,
            examShuffleBox,
            new Label("Batch:"), examBatchSpinner,
            examBackFirstBox,
            examRepeatWrongsBox
        );
        controlsRow1.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.Region spacerTop = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerTop, Priority.ALWAYS);
        Button btnEditTop = new Button("Edit…");
        btnEditTop.setOnAction(e -> openEditorForCurrent());
        // Bigger Show Answer button on the right
        ctrlRow1 = new HBox(10, controlsRow1, spacerTop, btnEditTop, btnExamShow);
        ctrlRow1.setAlignment(Pos.CENTER_LEFT);

        // Row 2: big Correct/Wrong, then Next/Restart on the right
        ctrlRow2 = new HBox(10);
        ctrlRow2.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.Region spacerMid = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerMid, Priority.ALWAYS);
        ctrlRow2.getChildren().addAll(
            btnExamCorrect, btnExamWrong,
            spacerMid,
            btnExamNext, btnExamRestart
        );

        // Row 3: progress on the left; counters and score on the right
        ctrlRow3 = new HBox(10);
        ctrlRow3.setAlignment(Pos.CENTER_LEFT);
        examProgressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(examProgressBar, Priority.ALWAYS);
        examProgressBar.setStyle("-fx-accent: #3fb950;");
        javafx.scene.layout.Region spacerBottom = new javafx.scene.layout.Region();
        HBox.setHgrow(spacerBottom, Priority.ALWAYS);
        // Export button inline on bottom row (init before adding)
        btnExport = new Button("Export Results…");
        btnExport.setOnAction(e -> exportResults());
        ctrlRow3.getChildren().addAll(
            new Label("Progress:"), examProgress, examProgressBar,
            spacerBottom,
            examOkLabel, examNgLabel, examScore, btnExport
        );

        box.getChildren().addAll(
            frontRow, backRow, rpRow, rowDeck, rowTags,
            new Label("Examples:"), examExamples,
            new Separator(),
            ctrlRow1,
            ctrlRow2,
            ctrlRow3
        );
        root.setCenter(box);

        // Apply initial sizing/colors and compact mode
        boolean compactPref = Config.getBool("app.ui.exam.compact", false);
        ensureCompactToggle(controlsRow1, compactPref);
        applySizing(compactPref);

        // Setup keyboard shortcuts
        setupKeyboardShortcuts(box);

        // Initialize first exam session
        prepareExamQueue();
        showCurrentCard();

        return root;
    }

    /**
     * Create and configure all control buttons.
     */
    private void createButtons() {
        btnExamShow = new Button("Show Answer");
        btnExamCorrect = new Button("  Correct");
        btnExamWrong = new Button("  Wrong");
        btnExamNext = new Button("Next");
        btnExamRestart = new Button("Restart");

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
        // Row spacings
        if (ctrlRow1 != null) ctrlRow1.setSpacing(compact ? 8 : 10);
        if (ctrlRow2 != null) ctrlRow2.setSpacing(compact ? 8 : 10);
        if (ctrlRow3 != null) ctrlRow3.setSpacing(compact ? 8 : 10);

        // Examples area height
        examExamples.setPrefRowCount(compact ? 2 : 3);

        // Button styles
        styleButtons(compact);
    }

    private void styleButtons(boolean compact) {
        // Show Answer prominent
        String showSz = compact ? "14px" : "16px";
        String showPad = compact ? "6 12" : "8 16";
        btnExamShow.setStyle("-fx-font-size: " + showSz + "; -fx-font-weight: bold; -fx-padding: " + showPad + ";");
        btnExamShow.setMinHeight(compact ? 30 : 36);

        // Correct (green) and Wrong (red)
        String cwSz = compact ? "14px" : "15px";
        String cwPad = compact ? "6 14" : "8 18";
        String base = "-fx-font-weight: 700; -fx-text-fill: white; -fx-background-radius: 6;";
        btnExamCorrect.setStyle("-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base +
                " -fx-background-color: linear-gradient(#38a169, #2f855a);");
        btnExamWrong.setStyle("-fx-font-size: " + cwSz + "; -fx-padding: " + cwPad + "; " + base +
                " -fx-background-color: linear-gradient(#e53e3e, #c53030);");
        btnExamCorrect.setMinHeight(compact ? 30 : 34);
        btnExamWrong.setMinHeight(compact ? 30 : 34);
    }

    @Override
    public String toString() { return "ExamPanel"; }
}
