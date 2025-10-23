package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.app.Scheduler;
import com.memorizer.service.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Preferences window for runtime-configurable options. Writes to Config (persisted in data/prefs.properties),
 * applies changes immediately when possible, and logs each change.
 */
public class PreferencesStage extends Stage {
    private static final Logger log = LoggerFactory.getLogger(PreferencesStage.class);

    private final StudyService study;
    private final Scheduler scheduler;
    private final StealthStage stealth;

    // UI state refs
    private ToggleGroup tgTheme, tgMode, tgSched;
    private TextField tfBatch, tfMin, tfMax, tfDefer, tfSnooze;
    private TextField tfWidthN, tfWidthM, tfOpacity;
    private CheckBox cbForceFallback, cbSnoozeOnHide, cbOverlay;

    public PreferencesStage(StudyService study, Scheduler scheduler, StealthStage stealth) {
        this.study = study;
        this.scheduler = scheduler;
        this.stealth = stealth;

        setTitle("Preferences");
        setResizable(false);

        // 设置窗口图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                getIcons().add(icon);
            }
        } catch (Exception e) {
            // 记录日志但不中断
            System.out.println("Failed to load preferences stage icon: " + e.getMessage());
        }

        TabPane tabs = new TabPane();
        tabs.getTabs().add(new Tab("General", buildGeneralTab()));
        tabs.getTabs().add(new Tab("Scheduler", buildSchedulerTab()));
        tabs.getTabs().add(new Tab("Stealth", buildStealthTab()));
        for (Tab t : tabs.getTabs()) t.setClosable(false);

        BorderPane root = new BorderPane();
        root.setCenter(tabs);
        HBox actions = new HBox(8);
        actions.setPadding(new Insets(10));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnCancel = new Button("Cancel");
        Button btnSave = new Button("Save");
        actions.getChildren().addAll(spacer, btnCancel, btnSave);
        root.setBottom(actions);

        Scene sc = new Scene(root, 520, 420);
        setScene(sc);
        initModality(Modality.APPLICATION_MODAL);

        btnCancel.setOnAction(e -> close());
        btnSave.setOnAction(e -> {
            boolean needReschedule = false;
            boolean stealthPosUpdate = false;

            // General
            String themeSel = ((RadioButton) tgTheme.getSelectedToggle()).getText();
            String themeNew = "Light".equalsIgnoreCase(themeSel) ? "light" : "dark";
            needReschedule |= applyIfChanged("app.ui.theme", themeNew, () -> applyTheme(themeNew));

            String modeSel = ((RadioButton) tgMode.getSelectedToggle()).getText();
            String modeNew = "Mini".equalsIgnoreCase(modeSel) ? "mini" : "normal";
            needReschedule |= applyIfChanged("app.ui.mode", modeNew, () -> applyMode(modeNew));

            // Scheduler
            String schedSel = ((RadioButton) tgSched.getSelectedToggle()).getText();
            String schedNew = schedSel.startsWith("Due") ? "due" : "periodic";
            needReschedule |= applyIfChanged("app.study.scheduler.mode", schedNew, null);

            int batch = parseInt(tfBatch.getText(), Config.getInt("app.study.batch-size", 5));
            needReschedule |= applyIfChangedInt("app.study.batch-size", batch, null);

            int minI = parseInt(tfMin.getText(), Config.getInt("app.study.min-interval-minutes", 20));
            needReschedule |= applyIfChangedInt("app.study.min-interval-minutes", minI, null);

            int maxI = parseInt(tfMax.getText(), Config.getInt("app.study.max-interval-minutes", 60));
            needReschedule |= applyIfChangedInt("app.study.max-interval-minutes", maxI, null);

            int defBusy = parseInt(tfDefer.getText(), Config.getInt("app.study.defer-when-busy-minutes", 3));
            needReschedule |= applyIfChangedInt("app.study.defer-when-busy-minutes", defBusy, null);

            int snooze = parseInt(tfSnooze.getText(), Config.getInt("app.study.snooze-minutes", 10));
            applyIfChangedInt("app.study.snooze-minutes", snooze, null);

            boolean forceFallback = cbForceFallback.isSelected();
            applyIfChangedBool("app.study.force-show-when-empty", forceFallback, null);

            boolean snoozeHide = cbSnoozeOnHide.isSelected();
            applyIfChangedBool("app.study.snooze-on-hide-enabled", snoozeHide, null);

            // Stealth
            double fracN = parseDouble(tfWidthN.getText(), Double.parseDouble(Config.get("app.window.stealth.width-fraction", "0.98")));
            stealthPosUpdate |= applyIfChanged("app.window.stealth.width-fraction", String.valueOf(fracN), null);

            double fracM = parseDouble(tfWidthM.getText(), Double.parseDouble(Config.get("app.window.mini.width-fraction", "0.5")));
            stealthPosUpdate |= applyIfChanged("app.window.mini.width-fraction", String.valueOf(fracM), null);

            double opacity = parseDouble(tfOpacity.getText(), Double.parseDouble(Config.get("app.window.opacity", "0.90")));
            applyIfChanged("app.window.opacity", String.valueOf(opacity), () -> stealth.setOpacity(opacity));

            boolean overlay = cbOverlay.isSelected();
            stealthPosUpdate |= applyIfChangedBool("app.window.overlay-taskbar", overlay, null);

            if (stealthPosUpdate && stealth != null) {
                if (stealth.isShowing()) stealth.showAndFocus();
            }
            if (needReschedule) scheduler.rescheduleNow();
            close();
        });
    }

    private Pane buildGeneralTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(12));

        // Theme
        Label lTheme = new Label("Theme (Dark/Light)"); lTheme.setTooltip(new Tooltip("Drawer theme; applies immediately"));
        tgTheme = new ToggleGroup();
        RadioButton rbDark = new RadioButton("Dark"); rbDark.setToggleGroup(tgTheme);
        RadioButton rbLight = new RadioButton("Light"); rbLight.setToggleGroup(tgTheme);
        String curTheme = Config.get("app.ui.theme", "dark");
        if ("light".equalsIgnoreCase(curTheme)) rbLight.setSelected(true); else rbDark.setSelected(true);
        HBox themeRow = new HBox(10, rbDark, rbLight);

        // UI Mode
        Label lMode = new Label("Banner Mode"); lMode.setTooltip(new Tooltip("Normal or Mini"));
        tgMode = new ToggleGroup();
        RadioButton rbNormal = new RadioButton("Normal"); rbNormal.setToggleGroup(tgMode);
        RadioButton rbMini = new RadioButton("Mini"); rbMini.setToggleGroup(tgMode);
        String curMode = Config.get("app.ui.mode", "normal");
        if ("mini".equalsIgnoreCase(curMode)) rbMini.setSelected(true); else rbNormal.setSelected(true);
        HBox modeRow = new HBox(10, rbNormal, rbMini);

        box.getChildren().addAll(lTheme, themeRow, new Separator(), lMode, modeRow);
        return box;
    }

    private Pane buildSchedulerTab() {
        GridPane g = new GridPane();
        g.setPadding(new Insets(12));
        g.setHgap(10); g.setVgap(8);
        int r = 0;

        // Mode
        g.add(new Label("Mode"), 0, r);
        tgSched = new ToggleGroup();
        RadioButton rbDue = new RadioButton("Due-Driven (SRS)"); rbDue.setToggleGroup(tgSched);
        RadioButton rbPeriodic = new RadioButton("Periodic"); rbPeriodic.setToggleGroup(tgSched);
        String cur = Config.get("app.study.scheduler.mode", "due");
        if ("periodic".equalsIgnoreCase(cur)) rbPeriodic.setSelected(true); else rbDue.setSelected(true);
        g.add(new HBox(10, rbDue, rbPeriodic), 1, r++);

        // Batch size
        g.add(new Label("Batch size"), 0, r);
        tfBatch = new TextField(String.valueOf(Config.getInt("app.study.batch-size", 5))); tfBatch.setPrefColumnCount(6);
        tfBatch.setTooltip(new Tooltip("Cards per banner session"));
        g.add(tfBatch, 1, r++);

        // Intervals
        g.add(new Label("Min interval (min)"), 0, r);
        tfMin = new TextField(String.valueOf(Config.getInt("app.study.min-interval-minutes", 20))); tfMin.setPrefColumnCount(6);
        g.add(tfMin, 1, r++);

        g.add(new Label("Max interval (min)"), 0, r);
        tfMax = new TextField(String.valueOf(Config.getInt("app.study.max-interval-minutes", 60))); tfMax.setPrefColumnCount(6);
        g.add(tfMax, 1, r++);

        g.add(new Label("Defer when busy (min)"), 0, r);
        tfDefer = new TextField(String.valueOf(Config.getInt("app.study.defer-when-busy-minutes", 3))); tfDefer.setPrefColumnCount(6);
        g.add(tfDefer, 1, r++);

        g.add(new Label("Snooze (min)"), 0, r);
        tfSnooze = new TextField(String.valueOf(Config.getInt("app.study.snooze-minutes", 10))); tfSnooze.setPrefColumnCount(6);
        g.add(tfSnooze, 1, r++);

        cbForceFallback = new CheckBox("Force show when empty"); cbForceFallback.setSelected(Config.getBool("app.study.force-show-when-empty", true));
        g.add(cbForceFallback, 1, r++);

        cbSnoozeOnHide = new CheckBox("Snooze on Hide"); cbSnoozeOnHide.setSelected(Config.getBool("app.study.snooze-on-hide-enabled", true));
        g.add(cbSnoozeOnHide, 1, r++);

        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(180);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    private Pane buildStealthTab() {
        GridPane g = new GridPane();
        g.setPadding(new Insets(12));
        g.setHgap(10); g.setVgap(8);
        int r = 0;

        g.add(new Label("Normal width fraction"), 0, r);
        tfWidthN = new TextField(Config.get("app.window.stealth.width-fraction", "0.98"));
        tfWidthN.setTooltip(new Tooltip("0.0 ~ 1.0 of active screen width"));
        g.add(tfWidthN, 1, r++);

        g.add(new Label("Mini width fraction"), 0, r);
        tfWidthM = new TextField(Config.get("app.window.mini.width-fraction", "0.5"));
        g.add(tfWidthM, 1, r++);

        g.add(new Label("Opacity (0~1)"), 0, r);
        tfOpacity = new TextField(Config.get("app.window.opacity", "0.90"));
        g.add(tfOpacity, 1, r++);

        cbOverlay = new CheckBox("Overlay taskbar region"); cbOverlay.setSelected(Config.getBool("app.window.overlay-taskbar", false));
        g.add(cbOverlay, 1, r++);

        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(180);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    private boolean applyIfChanged(String key, String newVal, Runnable apply) {
        String cur = Config.get(key, newVal);
        if (!safeEq(cur, newVal)) {
            log.info("Pref change: {}: {} -> {}", key, cur, newVal);
            Config.set(key, newVal);
            if (apply != null) try { apply.run(); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    private boolean applyIfChangedInt(String key, int newVal, Runnable apply) {
        int cur = Config.getInt(key, newVal);
        if (cur != newVal) {
            log.info("Pref change: {}: {} -> {}", key, cur, newVal);
            Config.set(key, String.valueOf(newVal));
            if (apply != null) try { apply.run(); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    private boolean applyIfChangedBool(String key, boolean newVal, Runnable apply) {
        boolean cur = Config.getBool(key, newVal);
        if (cur != newVal) {
            log.info("Pref change: {}: {} -> {}", key, cur, newVal);
            Config.set(key, String.valueOf(newVal));
            if (apply != null) try { apply.run(); } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    private void applyTheme(String mode) {
        if (stealth == null) return;
        stealth.setTheme("light".equalsIgnoreCase(mode) ? StealthStage.ThemeMode.LIGHT : StealthStage.ThemeMode.DARK);
        try { if (com.memorizer.app.AppContext.getMain() != null)
            com.memorizer.app.AppContext.getMain().applyTheme("light".equalsIgnoreCase(mode)); } catch (Exception ignored) {}
    }

    private void applyMode(String mode) {
        if (stealth == null) return;
        stealth.setUIMode("mini".equalsIgnoreCase(mode) ? StealthStage.UIMode.MINI : StealthStage.UIMode.NORMAL);
    }

    private static boolean safeEq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }
    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }
    private static double parseDouble(String s, double def) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
