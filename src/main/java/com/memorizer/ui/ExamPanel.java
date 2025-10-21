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
    
    // Exam state
    private boolean examShowingAnswer = false;
    private final List<Long> examQueue = new ArrayList<>();
    private int examIndex = 0;
    private int examCorrect = 0;
    private int examWrong = 0;

    public ExamPanel(StudyService studyService) {
        this.studyService = studyService;
    }

    /**
     * Build the exam panel UI.
     * @return the constructed exam pane
     */
    public Pane build() {
        BorderPane root = new BorderPane();
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        // Style front and back labels
        examFront.setStyle("-fx-font-size: 22px;");
        examBack.setStyle("-fx-font-size: 20px; -fx-text-fill: #444;");
        examBack.setVisible(false);

        HBox row1 = new HBox(10, new Label("Front:"), examFront);
        HBox row2 = new HBox(10, new Label("Back:"), examBack);

        // Create control buttons
        createButtons();
        
        // Create configuration controls
        HBox controls = new HBox(10,
            new Label("Source:"), examSourceBox,
            examShuffleBox,
            new Label("Batch:"), examBatchSpinner,
            new Separator(),
            btnExamShow, btnExamCorrect, btnExamWrong, btnExamNext, btnExamRestart,
            new Separator(), 
            new Label("Progress:"), examProgress, 
            new Label("Score:"), examScore
        );
        controls.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(row1, row2, new Separator(), controls);
        root.setCenter(box);

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
        examBack.setVisible(true);
        btnExamCorrect.setDisable(false);
        btnExamWrong.setDisable(false);
    }

    /**
     * Mark current answer as correct.
     */
    private void markCorrect() {
        examCorrect++;
        examScore.setText("OK" + examCorrect + " NG" + examWrong);
        gotoNext();
    }

    /**
     * Mark current answer as wrong.
     */
    private void markWrong() {
        examWrong++;
        examScore.setText("OK" + examCorrect + " NG" + examWrong);
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
        examScore.setText("OK0 NG0");

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

        examFront.setText(front);
        examBack.setText(back);
        examShowingAnswer = false;
        examBack.setVisible(false);

        btnExamShow.setDisable(false);
        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);
        btnExamNext.setDisable(false);

        examProgress.setText((examIndex + 1) + "/" + examQueue.size());
    }

    /**
     * Show no cards available message.
     */
    private void showNoCards() {
        examFront.setText("(No exam items)");
        examBack.setText("");
        examBack.setVisible(false);
        btnExamShow.setDisable(true);
        btnExamCorrect.setDisable(true);
        btnExamWrong.setDisable(true);
        btnExamNext.setDisable(true);
        examProgress.setText("0/0");
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
        
        examFront.setText("(Exam finished)");
        examBack.setText("");
        examBack.setVisible(false);
        examProgress.setText(examQueue.size() + "/" + examQueue.size());
    }
}
