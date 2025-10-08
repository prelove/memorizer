package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.model.Rating;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Stealth banner window placed at the bottom.
 * Now wired to StudyService: showCard(front, back) + rating handlers.
 */
public class StealthStage extends Stage {
    private final Label frontLabel = new Label();
    private final Label backLabel  = new Label();
    private boolean showingFront = true;

    private StudyService study;

    public StealthStage() {
        initStyle(StageStyle.UNDECORATED);
        setAlwaysOnTop(true);
        setOpacity(Double.parseDouble(Config.get("app.window.opacity", "0.90")));

        Button flip = new Button("Flip (Space)");
        Button again = new Button("1 Again");
        Button hard  = new Button("2 Hard");
        Button good  = new Button("3 Good");
        Button easy  = new Button("4 Easy");
        Button close = new Button("×");

        close.setOnAction(e -> hide());

        flip.setOnAction(e -> toggleFace());
        again.setOnAction(e -> rateAndHide(Rating.AGAIN));
        hard.setOnAction(e -> rateAndHide(Rating.HARD));
        good.setOnAction(e -> rateAndHide(Rating.GOOD));
        easy.setOnAction(e -> rateAndHide(Rating.EASY));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12,
                new Label("Card:"),
                frontLabel,
                backLabel,
                spacer,
                flip, again, hard, good, easy, close
        );
        bar.setPadding(new Insets(8, 12, 8, 12));
        bar.setStyle("-fx-background-color: rgba(30,30,30,0.92); -fx-text-fill: white; -fx-background-radius: 10;");
        frontLabel.setStyle("-fx-text-fill: white;");
        backLabel.setStyle("-fx-text-fill: white;");
        backLabel.setVisible(false);

        StackPane root = new StackPane(bar);
        Scene scene = new Scene(root);
        scene.setFill(null);

        // Keyboard shortcuts
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.SPACE || ev.getCode() == KeyCode.ENTER) toggleFace();
            if (ev.getCode() == KeyCode.DIGIT1) rateAndHide(Rating.AGAIN);
            if (ev.getCode() == KeyCode.DIGIT2) rateAndHide(Rating.HARD);
            if (ev.getCode() == KeyCode.DIGIT3) rateAndHide(Rating.GOOD);
            if (ev.getCode() == KeyCode.DIGIT4) rateAndHide(Rating.EASY);
            if (ev.getCode() == KeyCode.ESCAPE) hide();
        });

        setScene(scene);

        // Size & position bottom
        double height = Double.parseDouble(Config.get("app.window.stealth.height", "56"));
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        setX(bounds.getMinX());
        setWidth(bounds.getWidth());
        setHeight(height);
        setY(bounds.getMaxY() - height - 2);
    }

    public void bindStudy(StudyService study) {
        this.study = study;
    }

    public void showCard(String front, String back) {
        frontLabel.setText("Front: " + front);
        backLabel.setText("Back: " + back);
        showingFront = true;
        frontLabel.setVisible(true);
        backLabel.setVisible(false);
    }

    private void toggleFace() {
        showingFront = !showingFront;
        frontLabel.setVisible(showingFront);
        backLabel.setVisible(!showingFront);
    }

    private void rateAndHide(Rating r) {
        if (study != null) {
            study.rate(r);
        }
        hide();
    }
}
