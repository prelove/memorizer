package com.memorizer.ui;

import com.memorizer.util.ScreenUtil;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Taskbar-top Anki-like flashcard drawer.
 * Java 8 compatible; no external theme engine required.
 */
public class AnkiDrawerStage extends Stage {

    private final BorderPane root = new BorderPane();
    private final SplitPane split = new SplitPane();

    private final VBox cardBox = new VBox();
    private final TextFlow frontFlow = new TextFlow();
    private final TextFlow backFlow = new TextFlow();
    private boolean showingFront = true;

    private final VBox rightBox = new VBox(8);
    private final HBox answers = new HBox(8);
    private final Button btnAgain = new Button("Again");
    private final Button btnHard  = new Button("Hard");
    private final Button btnGood  = new Button("Good");
    private final Button btnEasy  = new Button("Easy");

    private Timeline autoHideT;

    public AnkiDrawerStage() {
        initStyle(StageStyle.UNDECORATED);
        setAlwaysOnTop(true);
        buildUi();
        Scene scene = new Scene(root);
        scene.setFill(null);
        try { scene.getStylesheets().add(AnkiDrawerStage.class.getResource("/app.css").toExternalForm()); } catch (Exception ignored) {}
        setScene(scene);

        // Keys: Space flip, 1-4 answers, Esc hide
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) { flip(); }
            else if (e.getCode() == KeyCode.DIGIT1) onAnswer(1);
            else if (e.getCode() == KeyCode.DIGIT2) onAnswer(2);
            else if (e.getCode() == KeyCode.DIGIT3) onAnswer(3);
            else if (e.getCode() == KeyCode.DIGIT4) onAnswer(4);
            else if (e.getCode() == KeyCode.ESCAPE) hideWithAnim();
        });

        // Auto-hide when mouse leaves for 800ms
        root.addEventFilter(MouseEvent.MOUSE_EXITED_TARGET, ev -> startAutoHide());
        root.addEventFilter(MouseEvent.MOUSE_ENTERED_TARGET, ev -> cancelAutoHide());
    }

    private void buildUi() {
        root.setId("ankiDrawer");
        root.setPadding(new Insets(12));

        // Card area
        frontFlow.getStyleClass().add("card-text");
        backFlow.getStyleClass().add("card-text");
        frontFlow.setPrefWidth(640);
        backFlow.setPrefWidth(640);
        frontFlow.setMaxWidth(Double.MAX_VALUE);
        backFlow.setMaxWidth(Double.MAX_VALUE);
        frontFlow.setVisible(true); backFlow.setVisible(false);
        frontFlow.setManaged(true); backFlow.setManaged(false);

        VBox frontCard = new VBox(frontFlow); frontCard.getStyleClass().add("card");
        VBox backCard  = new VBox(backFlow);  backCard.getStyleClass().add("card");
        cardBox.getChildren().addAll(frontCard, backCard);
        VBox.setVgrow(frontCard, Priority.ALWAYS);
        VBox.setVgrow(backCard, Priority.ALWAYS);

        // Right answers
        styleBtn(btnAgain, "btn-again");
        styleBtn(btnHard,  "btn-hard");
        styleBtn(btnGood,  "btn-good");
        styleBtn(btnEasy,  "btn-easy");
        btnAgain.setOnAction(e -> onAnswer(1));
        btnHard.setOnAction(e -> onAnswer(2));
        btnGood.setOnAction(e -> onAnswer(3));
        btnEasy.setOnAction(e -> onAnswer(4));
        answers.getChildren().addAll(btnAgain, btnHard, btnGood, btnEasy);
        rightBox.getChildren().addAll(answers);

        split.setOrientation(Orientation.HORIZONTAL);
        split.getItems().addAll(cardBox, rightBox);
        split.setDividerPositions(0.8); // card takes most space
        root.setCenter(split);
    }

    private void styleBtn(Button b, String clazz) {
        b.getStyleClass().addAll("btn", clazz);
        b.setFocusTraversable(true);
    }

    public void setCardTexts(String front, String back) {
        frontFlow.getChildren().clear();
        backFlow.getChildren().clear();
        Text f = new Text(front == null ? "" : front); f.getStyleClass().add("card-text");
        Text b = new Text(back  == null ? "" : back);  b.getStyleClass().add("card-text");
        frontFlow.getChildren().add(f);
        backFlow.getChildren().add(b);
        // wrappingWidth adjusts at runtime with width listener
        root.widthProperty().addListener((o,ov,nv)->{
            double wrap = Math.max(300, nv.doubleValue() * 0.75);
            f.wrappingWidthProperty().set(wrap);
            b.wrappingWidthProperty().set(wrap);
        });
    }

    public void showDrawer() {
        applyGeometry();
        // Prepare animation
        Region content = (Region) root;
        content.setOpacity(0);
        content.setTranslateY(getHeight() + 30);
        if (!isShowing()) show();

        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(content.opacityProperty(), 0),
                        new KeyValue(content.translateYProperty(), getHeight() + 30, Interpolator.EASE_OUT)
                ),
                new KeyFrame(Duration.millis(200),
                        new KeyValue(content.opacityProperty(), 1),
                        new KeyValue(content.translateYProperty(), 0, Interpolator.EASE_OUT)
                )
        );
        t.play();
        root.requestFocus();
    }

    public void hideWithAnim() {
        Region content = (Region) root;
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(content.opacityProperty(), 1),
                        new KeyValue(content.translateYProperty(), 0, Interpolator.EASE_IN)
                ),
                new KeyFrame(Duration.millis(180),
                        new KeyValue(content.opacityProperty(), 0),
                        new KeyValue(content.translateYProperty(), getHeight() + 30, Interpolator.EASE_IN)
                )
        );
        t.setOnFinished(e -> hide());
        t.play();
    }

    private void startAutoHide() {
        cancelAutoHide();
        autoHideT = new Timeline(new KeyFrame(Duration.millis(800), e -> hideWithAnim()));
        autoHideT.play();
    }

    private void cancelAutoHide() {
        if (autoHideT != null) { autoHideT.stop(); autoHideT = null; }
    }

    private void flip() {
        showingFront = !showingFront;
        frontFlow.setVisible(showingFront);
        frontFlow.setManaged(showingFront);
        backFlow.setVisible(!showingFront);
        backFlow.setManaged(!showingFront);
    }

    private void onAnswer(int rating) {
        // Hook to StudyService here if needed; for now just hide smoothly
        hideWithAnim();
    }

    private void applyGeometry() {
        // Use the screen under the pointer (correct on multi-monitor / HiDPI setups).
        // jfxVisualBounds returns logical pixels — same coordinate space as Stage.setX/Y/Width/Height.
        java.awt.GraphicsDevice activeDevice = ScreenUtil.activeDevice();
        Rectangle2D vis = ScreenUtil.jfxVisualBounds(activeDevice);
        double screenW = vis.getWidth();
        double screenH = vis.getHeight();

        // Scale base heights by user font-scale so the drawer feels proportionate
        // on displays running without OS DPI scaling.
        double fs = com.memorizer.app.Config.getFontScale();
        double prefH = Math.min(260 * fs, screenH * 0.35);
        double widthFrac = 0.9;
        double w = Math.max(480, screenW * widthFrac);

        setWidth(w);
        setHeight(prefH);
        setMinHeight(180 * fs);
        setMaxHeight(screenH * 0.40);

        // Position above the taskbar on the active screen.
        double gap = 10; // logical-px gap to taskbar
        try {
            ScreenUtil.TaskbarInfo tb = ScreenUtil.taskbarFor(activeDevice);
            // Convert AWT taskbar rect to JavaFX logical coords via AWT/JFX bounds ratio
            java.awt.Rectangle awtBounds = activeDevice.getDefaultConfiguration().getBounds();
            double hRatio = (awtBounds.width  > 0) ? screenW / awtBounds.width  : 1.0;
            double vRatio = (awtBounds.height > 0) ? screenH / awtBounds.height : 1.0;
            double tbX = tb.rect.x * hRatio;
            double tbY = tb.rect.y * vRatio;
            double tbW = tb.rect.w * hRatio;
            setX(tbX + (tbW - w) / 2.0);
            setY(tbY - prefH - gap);
        } catch (Throwable t) {
            setX(vis.getMinX() + (screenW - w) / 2.0);
            setY(vis.getMinY() + screenH - prefH - gap);
        }
    }
}

