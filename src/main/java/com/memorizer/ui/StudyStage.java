package com.memorizer.ui;

import com.memorizer.model.Rating;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Centered, standard study window (non-stealth) for daily practice. */
public class StudyStage extends Stage {
    private final StudyService study;
    private final Label lblFront = new Label();
    private final Label lblBack  = new Label();
    private boolean showingFront = true;

    public StudyStage(StudyService study) {
        this.study = study;
        setTitle("Study");
        setMinWidth(560);
        setMinHeight(320);

        VBox root = new VBox(12);
        root.setPadding(new Insets(16));

        lblFront.setStyle("-fx-font-size: 22px;");
        lblBack.setStyle("-fx-font-size: 22px; -fx-text-fill: #555;");
        lblBack.setVisible(false);

        HBox row1 = new HBox(10, new Label("Front:"), lblFront);
        HBox row2 = new HBox(10, new Label("Back:"), lblBack);

        HBox buttons = new HBox(10);
        Button btnFlip = new Button("Flip (Space)");
        Button btnAgain = new Button("1 Again");
        Button btnHard  = new Button("2 Hard");
        Button btnGood  = new Button("3 Good");
        Button btnEasy  = new Button("4 Easy)");
        Button btnNext  = new Button("Next");

        btnFlip.setOnAction(e -> toggleFace());
        btnAgain.setOnAction(e -> { rate(Rating.AGAIN); loadNext(); });
        btnHard.setOnAction(e -> { rate(Rating.HARD);  loadNext(); });
        btnGood.setOnAction(e -> { rate(Rating.GOOD);  loadNext(); });
        btnEasy.setOnAction(e -> { rate(Rating.EASY);  loadNext(); });
        btnNext.setOnAction(e -> loadNext());

        buttons.getChildren().addAll(btnFlip, btnAgain, btnHard, btnGood, btnEasy, btnNext);
        buttons.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().addAll(row1, row2, new Separator(), buttons);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.SPACE) toggleFace();
            if (ev.getCode() == KeyCode.DIGIT1) { rate(Rating.AGAIN); loadNext(); }
            if (ev.getCode() == KeyCode.DIGIT2) { rate(Rating.HARD);  loadNext(); }
            if (ev.getCode() == KeyCode.DIGIT3) { rate(Rating.GOOD);  loadNext(); }
            if (ev.getCode() == KeyCode.DIGIT4) { rate(Rating.EASY);  loadNext(); }
        });
        setScene(scene);

        // center on primary screen
        javafx.geometry.Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        setX(vb.getMinX() + (vb.getWidth() - getMinWidth())/2.0);
        setY(vb.getMinY() + (vb.getHeight() - getMinHeight())/2.0);

        loadNext();
    }

    public void showAndFocus() {
        if (!isShowing()) show();
        toFront(); requestFocus(); setIconified(false);
    }

    private void loadNext() {
        java.util.Optional<StudyService.CardView> opt = study.currentOrNextOrFallback();
        if (opt.isPresent()) {
            StudyService.CardView v = opt.get();
            lblFront.setText(v.getFront());
            lblBack.setText(v.getBack());
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
        // no toast here; rely on immediate next load
    }
}

