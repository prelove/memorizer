package com.memorizer.ui;

import com.memorizer.app.Config;
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
 * Minimal stealth banner window placed at the bottom of primary screen.
 * For Stage A: shows a dummy card (Front/Back) and rating buttons.
 */
public class StealthStage extends Stage {
    private final Label frontLabel = new Label("Front: 愛おしい");
    private final Label backLabel  = new Label("Back: lovable; dear; adorable (いとおしい)");
    private boolean showingFront = true;

    public StealthStage() {
        initStyle(StageStyle.UNDECORATED);
        setAlwaysOnTop(true);
        setOpacity(Double.parseDouble(Config.get("app.window.opacity", "0.90")));

        // Content layout
        Button flip = new Button("Flip (Space)");
        Button again = new Button("1 Again");
        Button hard  = new Button("2 Hard");
        Button good  = new Button("3 Good");
        Button easy  = new Button("4 Easy");
        Button close = new Button("×");

        close.setOnAction(e -> hide());

        flip.setOnAction(e -> toggleFace());
        again.setOnAction(e -> acknowledge("AGAIN"));
        hard.setOnAction(e -> acknowledge("HARD"));
        good.setOnAction(e -> acknowledge("GOOD"));
        easy.setOnAction(e -> acknowledge("EASY"));

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
        backLabel.setVisible(false); // start with front

        StackPane root = new StackPane(bar);
        Scene scene = new Scene(root);
        scene.setFill(null);

        // Keyboard shortcuts
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.SPACE || ev.getCode() == KeyCode.ENTER) toggleFace();
            if (ev.getCode() == KeyCode.DIGIT1) acknowledge("AGAIN");
            if (ev.getCode() == KeyCode.DIGIT2) acknowledge("HARD");
            if (ev.getCode() == KeyCode.DIGIT3) acknowledge("GOOD");
            if (ev.getCode() == KeyCode.DIGIT4) acknowledge("EASY");
            if (ev.getCode() == KeyCode.ESCAPE) hide();
        });

        setScene(scene);

        // Size & position bottom
        double height = Double.parseDouble(Config.get("app.window.stealth.height", "56"));
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        setX(bounds.getMinX());
        setWidth(bounds.getWidth());
        setHeight(height);
        setY(bounds.getMaxY() - height - 2); // just above taskbar
    }

    private void toggleFace() {
        showingFront = !showingFront;
        frontLabel.setVisible(showingFront);
        backLabel.setVisible(!showingFront);
    }

    private void acknowledge(String rating) {
        // Stage A: just hide; Stage B: wire to SRS + DB
        hide();
        // TODO: publish rating event for scheduler/SRS
    }
}
