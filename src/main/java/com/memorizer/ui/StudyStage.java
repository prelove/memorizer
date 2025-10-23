package com.memorizer.ui;

import com.memorizer.model.Rating;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.MenuBar;
import javafx.scene.control.Menu;
import javafx.scene.control.CheckMenuItem;
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
    private final Label lblMode  = new Label();
    private boolean showingFront = true;

    public StudyStage(StudyService study) {
        this.study = study;
        setTitle("Study");
        setMinWidth(560);
        setMinHeight(320);

        // 设置窗口图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                getIcons().add(icon);
            }
        } catch (Exception e) {
            // 记录日志但不中断
            System.out.println("Failed to load study stage icon: " + e.getMessage());
        }

        VBox root = new VBox(12);
        root.setAlignment(Pos.TOP_LEFT);
        root.setPadding(new Insets(16));

        // Keep default Modena look for Study window (no external CSS roles)
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
        btnFlip.getStyleClass().addAll("btn","btn-info");
        btnAgain.getStyleClass().addAll("btn","btn-danger");
        btnHard.getStyleClass().addAll("btn","btn-warning");
        btnGood.getStyleClass().addAll("btn","btn-success");
        btnEasy.getStyleClass().addAll("btn","btn-primary");
        btnNext.getStyleClass().addAll("btn","btn-default");

        btnFlip.setOnAction(e -> toggleFace());
        btnAgain.setOnAction(e -> { rate(Rating.AGAIN); loadNext(); });
        btnHard.setOnAction(e -> { rate(Rating.HARD);  loadNext(); });
        btnGood.setOnAction(e -> { rate(Rating.GOOD);  loadNext(); });
        btnEasy.setOnAction(e -> { rate(Rating.EASY);  loadNext(); });
        btnNext.setOnAction(e -> loadNext());

        buttons.getChildren().addAll(btnFlip, btnAgain, btnHard, btnGood, btnEasy, btnNext);
        buttons.setAlignment(Pos.CENTER_LEFT);

        // Menu bar (Theme toggle) + Mode indicator
        MenuBar menuBar = buildMenuBar();
        lblMode.getStyleClass().add("batch-info");
        refreshModeIndicatorFromConfig();
        HBox topBar = new HBox(8, menuBar);
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        topBar.getChildren().addAll(spacer, lblMode);

        root.getChildren().addAll(topBar, row1, row2, new Separator(), buttons);

        // Wrap in a scrolling container to avoid clipping when content exceeds height
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(root);
        sp.setFitToWidth(true); sp.setFitToHeight(false);
        sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        Scene scene = new Scene(sp);
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.SPACE) toggleFace();
            if (ev.getCode() == KeyCode.DIGIT1) { rate(Rating.AGAIN); loadNext(); }
            if (ev.getCode() == KeyCode.DIGIT2) { rate(Rating.HARD);  loadNext(); }
            if (ev.getCode() == KeyCode.DIGIT3) { rate(Rating.GOOD);  loadNext(); }
            if (ev.getCode() == KeyCode.DIGIT4) { rate(Rating.EASY);  loadNext(); }
        });
        setScene(scene);

        // Size to OS work area when showing so bottom edge is flush with taskbar
        setOnShown(e -> {
            javafx.geometry.Rectangle2D vb = Screen.getPrimary().getVisualBounds();
            setX(Math.floor(vb.getMinX()));
            setY(Math.floor(vb.getMinY()));
            setWidth(Math.ceil(vb.getWidth()));
            setHeight(Math.ceil(vb.getHeight() + 1));
        });

        // Stage will be sized to work area in showAndFocus()

        loadNext();
    }

    public void showAndFocus() {
        // Fit to OS work area so bottom edge is flush with taskbar
        javafx.geometry.Rectangle2D vb = Screen.getPrimary().getVisualBounds();
        setX(Math.floor(vb.getMinX()));
        setY(Math.floor(vb.getMinY()));
        setWidth(Math.ceil(vb.getWidth()));
        setHeight(Math.ceil(vb.getHeight() + 1));
        if (!isShowing()) show();
        toFront(); requestFocus(); setIconified(false);
    }

    // Allow others (Main/Tray) to update theme live
    public void applyTheme(boolean light) { /* no-op: Study window stays default Modena */ }

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

    public void refreshModeIndicatorFromConfig() {
        boolean mini = "mini".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.mode","normal"));
        lblMode.setText("Mode: " + (mini?"Mini":"Normal"));
    }

    private MenuBar buildMenuBar() {
        Menu mView = new Menu("View");
        Menu mTheme = new Menu("Theme");
        CheckMenuItem miDark = new CheckMenuItem("Dark");
        CheckMenuItem miLight = new CheckMenuItem("Light");
        boolean light = "light".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.theme","dark"));
        miLight.setSelected(light); miDark.setSelected(!light);
        miDark.setOnAction(e -> {
            if (miDark.isSelected()) {
                miLight.setSelected(false);
                com.memorizer.app.Config.set("app.ui.theme", "dark");
                // Update all windows
                applyTheme(false);
                try { com.memorizer.ui.StealthStage s = com.memorizer.app.AppContext.getStealth(); if (s != null) s.setTheme(com.memorizer.ui.StealthStage.ThemeMode.DARK); } catch (Exception ignored) {}
                try { com.memorizer.ui.MainStage m = com.memorizer.app.AppContext.getMain(); if (m != null) m.applyTheme(false); } catch (Exception ignored) {}
            } else if (!miLight.isSelected()) { miDark.setSelected(true); }
        });
        miLight.setOnAction(e -> {
            if (miLight.isSelected()) {
                miDark.setSelected(false);
                com.memorizer.app.Config.set("app.ui.theme", "light");
                // Update all windows
                applyTheme(true);
                try { com.memorizer.ui.StealthStage s = com.memorizer.app.AppContext.getStealth(); if (s != null) s.setTheme(com.memorizer.ui.StealthStage.ThemeMode.LIGHT); } catch (Exception ignored) {}
                try { com.memorizer.ui.MainStage m = com.memorizer.app.AppContext.getMain(); if (m != null) m.applyTheme(true); } catch (Exception ignored) {}
            } else if (!miDark.isSelected()) { miLight.setSelected(true); }
        });
        mTheme.getItems().addAll(miDark, miLight);
        mView.getItems().addAll(mTheme);
        return new MenuBar(mView);
    }
}
