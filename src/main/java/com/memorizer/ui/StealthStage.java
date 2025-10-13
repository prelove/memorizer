package com.memorizer.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import com.memorizer.db.StatsRepository;
import com.memorizer.service.StudyService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class StealthStage extends Stage {

 public enum UIMode { NORMAL, MINI }
 public enum ThemeMode { DARK, LIGHT }

 // === 会话状态（用于调度串行化） ===
 private volatile boolean sessionActive;
 public boolean isSessionActive() { return sessionActive; }

 // === 模式/根节点 ===
 private UIMode currentMode = "mini".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.mode","normal"))
         ? UIMode.MINI : UIMode.NORMAL;
 private BorderPane root;              // 作为舞台根
 private ThemeMode currentTheme = "light".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.theme","dark"))
         ? ThemeMode.LIGHT : ThemeMode.DARK;
 private Pane normalBar;               // Normal 模式根条
 private Pane miniBar;                 // Mini 模式根条

 // === 共享控件（两种模式都会用到） ===
 private final Label batchInfo = new Label();     // (2/3)
 private final Button btnEdit = new Button("✎");
 private final Button btnFlip = new Button("Flip");
 private final Button btn1 = new Button("1");
 private final Button btn2 = new Button("2");
 private final Button btn3 = new Button("3");
 private final Button btn4 = new Button("4");
 private final Button btnAdd = new Button("Add");

 private final Label frontLabel = new Label(); // legacy
 private final Label backLabel  = new Label(); // legacy
 private final Label readingLabel = new Label(); // legacy
 private final Label posLabel = new Label();
 private int flipPressCount = 0;
 private boolean readingShown = false;
 private final Label kindLabel = new Label();

 private final VBox examplesBox = new VBox(2);    // 简易例句容器（滚动动效下步做）
 private final javafx.scene.control.ScrollPane examplesScroll = new javafx.scene.control.ScrollPane();
 private final Label todayLabel = new Label("Today: 0/0");
 private final ProgressBar todayProgress = new ProgressBar(0);
 private ProgressBar todayProgressMini; // clone for mini row
 private final Label planLabel = new Label("");

 private boolean showingFront = true;
 private long currentCardId = -1;
 
 private boolean inBatch = false;
 private int remainingInBatch = 1; // how many cards left in this session
 
 private StudyService study;
 private Timeline examplesTimeline;
 private java.util.List<String> currentExamples;
 private int examplesIndex = 0;
 private final Button btnSkip = new Button("Skip");
 private final Button btnSnooze = new Button("Snooze");

 // --- New drawer GridPane layout ---
 private GridPane drawerRoot;
 private VBox examplesCell; // C4 container
 private Label leftBadge = new Label();
 private Separator leftSep = new Separator(javafx.geometry.Orientation.VERTICAL);
 private Separator rightSep = new Separator(javafx.geometry.Orientation.VERTICAL);
 private VBox centerBox = new VBox(2);
 private TextFlow lineA = new TextFlow();
 private Label lineB = new Label(); // reading
 private Label lineC = new Label(); // example (first)
 private Label readingPosLabel = new Label();
 private Label examplesMini = new Label();
 private HBox controlsMini;
 private VBox controlsNormal;
 private HBox rowFlip;
 private HBox rowKeys;
 private GridPane answersNormal;
 private HBox answersMini;
 private VBox progressBox;
 private java.util.List<Separator> allVerticalSeps = new java.util.ArrayList<>();
 private Timeline examplesMiniMarquee;
 private ColumnConstraints colC2; // Reading/Pos
 private ColumnConstraints colC4; // Examples

 public StealthStage() {
     super();
     initWindowFlags();

     // 样式
     // 让主文本可分配宽度 & 高对比 & 省略
     frontLabel.setMaxWidth(Double.MAX_VALUE);
     backLabel.setMaxWidth(Double.MAX_VALUE);
     readingLabel.setMaxWidth(Double.MAX_VALUE);

     HBox.setHgrow(frontLabel, Priority.ALWAYS);
     HBox.setHgrow(backLabel, Priority.ALWAYS);

     frontLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
     backLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
     readingLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
    frontLabel.setWrapText(false);
    backLabel.setWrapText(false);
    readingLabel.setWrapText(false);
    // avoid focus sticking to labels
    frontLabel.setFocusTraversable(false);
    backLabel.setFocusTraversable(false);
    readingLabel.setFocusTraversable(false);

    // 使用 CSS 类来控制视觉效果（避免内联覆盖）
    frontLabel.getStyleClass().add("front");
    backLabel.getStyleClass().add("back");
    readingLabel.getStyleClass().add("reading");
    posLabel.getStyleClass().add("pos-pill");
    batchInfo.getStyleClass().addAll("batch-info","muted");

    // 按钮样式（项目 CSS + app.css）
    // buttons styling classes (32px height via CSS .controls)
    btnFlip.getStyleClass().addAll("controls","btn-flip");
    btnEdit.getStyleClass().addAll("controls","btn-flip");
    btnAdd.getStyleClass().addAll("controls","btn-flip");
    // Normal mode uses text labels on main buttons
    btn1.setText("Again (1)");
    btn2.setText("Hard (2)");
    btn3.setText("Good (3)");
    btn4.setText("Easy (4)");
    btn1.getStyleClass().addAll("controls","btn-answer","btn-again");
    btn2.getStyleClass().addAll("controls","btn-answer","btn-hard");
    btn3.getStyleClass().addAll("controls","btn-answer","btn-good");
    btn4.getStyleClass().addAll("controls","btn-answer","btn-easy");

    todayLabel.getStyleClass().addAll("today","muted");
    todayProgress.getStyleClass().add("today-progress");
     todayProgress.setPrefWidth(120);
     todayProgress.setMaxWidth(120);
     todayProgress.setMinWidth(120);

     btnEdit.setTooltip(new Tooltip("Edit… (E)"));
     btnAdd.setTooltip(new Tooltip("Add… (A)"));
     batchInfo.setTooltip(new Tooltip("Batch progress"));
     btn1.setTooltip(new Tooltip("Again (1)"));
     btn2.setTooltip(new Tooltip("Hard (2)"));
     btn3.setTooltip(new Tooltip("Good (3)"));
     btn4.setTooltip(new Tooltip("Easy (4)"));

     // 评分按钮动作（沿用你的 rateAndHide）
     btnFlip.setOnAction(e -> flipPressed());
     btn1.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.AGAIN));
     btn2.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.HARD));
     btn3.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.GOOD));
     btn4.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.EASY));
     btnSkip.setOnAction(e -> skipCurrent());
     btnSnooze.setOnAction(e -> hideWithSnooze());

    btnEdit.setOnAction(e -> openEditPopup());
    btnAdd.setOnAction(e -> openAddPopup());
    btnSkip.setTooltip(new Tooltip("Skip (S)"));
    btnSnooze.setTooltip(new Tooltip("Snooze & Hide"));
    // Context menu: Add on right-click of Edit
    javafx.scene.control.ContextMenu cm = new javafx.scene.control.ContextMenu();
    javafx.scene.control.MenuItem miAdd = new javafx.scene.control.MenuItem("Add…");
    miAdd.setOnAction(e -> openAddPopup());
    cm.getItems().add(miAdd);
    btnEdit.setOnContextMenuRequested(e -> cm.show(btnEdit, e.getScreenX(), e.getScreenY()));

    // 根容器
    root = new BorderPane();
    root.setId("taskbarDrawer");
    root.getStyleClass().add("taskbar-dark");
    Scene sc = new Scene(root);
    sc.setFill(javafx.scene.paint.Color.TRANSPARENT);
    setScene(sc);
    root.setSnapToPixel(true);
    // apply theme stylesheet (dark by default)
    applyDrawerTheme(currentTheme == ThemeMode.DARK);
    // rounded clip with shadow
    final javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
    clip.setArcWidth(16); clip.setArcHeight(16);
    root.setClip(clip);
    root.layoutBoundsProperty().addListener((o,ov,nv)->{
        if (nv != null) { clip.setWidth(nv.getWidth()); clip.setHeight(nv.getHeight()); }
    });
    root.setEffect(new javafx.scene.effect.DropShadow(18, javafx.scene.paint.Color.rgb(0,0,0,0.35)));
    // 保持 Java 8 兼容，不加载需要更高 JRE 的样式库

    // 主题初始化
    applyTheme(currentTheme);

    buildDrawerGrid();
    applyMode(currentMode);

    // Responsive shrink policy thresholds for center area and progress
    root.widthProperty().addListener((o, oldW, w) -> {
        double ww = w == null ? 0 : w.doubleValue();
        boolean mini = currentMode == UIMode.MINI;
        // hide progress text under ~1180px in Normal
        boolean tight1 = (!mini) && ww > 0 && ww < 1180;
        // Progress text visibility handled in applyMode (by swapping normal/mini rows)
        // clamp FRONT/BACK to 1 line under ~1060px in Normal
        boolean tight2 = (!mini) && ww > 0 && ww < 1060;
        limitLabelLines(frontLabel, tight2 ? 1 : (mini ? 1 : 2));
        limitLabelLines(backLabel,  tight2 ? 1 : (mini ? 1 : 2));
        // collapse EXAMPLES under ~980px (hide it first); only relevant in state 2
        boolean tight3 = ww > 0 && ww < 980;
        int state = flipPressCount % 3;
        if (examplesScroll != null) {
            boolean showNormal = (state == 2) && (!mini) && !tight3;
            examplesScroll.setVisible(showNormal);
            examplesScroll.setManaged(showNormal);
        }
        if (examplesMini != null) {
            boolean showMini = (state == 2) && mini && !tight3;
            examplesMini.setVisible(showMini);
            examplesMini.setManaged(showMini);
        }
        // reading/pos width pressure: squeeze to ~100px when tight
        if (colC2 != null) {
            if (ww > 1200) { colC2.setMinWidth(110); colC2.setPrefWidth(130); }
            else if (ww > 1050) { colC2.setMinWidth(105); colC2.setPrefWidth(120); }
            else { colC2.setMinWidth(100); colC2.setPrefWidth(110); }
        }
        // examples max width priority vs center
        if (colC4 != null) {
            if (ww > 1280) { colC4.setPrefWidth(Region.USE_COMPUTED_SIZE); }
            else if (ww > 1100) { colC4.setPrefWidth(360); }
            else if (ww > 980) { colC4.setPrefWidth(280); }
            else { colC4.setPrefWidth(160); }
        }
        adjustExamplesLinesByWidth(ww, mini);
    });

    // Mouse interactions: clicking front/back triggers flip behavior
    installClickHandlers();

     // 键盘
     getScene().setOnKeyPressed(ev -> {
         switch (ev.getCode()) {
             case SPACE:
             case ENTER: flipPressed(); break;
             case DIGIT1: rateAndHide(com.memorizer.model.Rating.AGAIN); break;
             case DIGIT2: rateAndHide(com.memorizer.model.Rating.HARD); break;
             case DIGIT3: rateAndHide(com.memorizer.model.Rating.GOOD); break;
             case DIGIT4: rateAndHide(com.memorizer.model.Rating.EASY); break;
             case F8:
                 if (isShowing()) hide(); else showAndFocus();
                 break;
             case ESCAPE: hideWithSnooze(); break;
            case S: skipCurrent(); break;
            case M: toggleMode(); break; // 新增：切换 Normal/Mini
            case T: toggleTheme(); break; // 切换 黑/白 主题
             default: break;
         }
     });

 }

 private void installClickHandlers() {
     frontLabel.setOnMouseClicked(ev -> {
         if (ev.getClickCount() >= 2) {
             // ensure reading/pos appear (equivalent to two flips)
             if (flipPressCount < 2) flipPressCount = 2;
             updateFaceVisibility();
         } else {
             flipPressed();
         }
     });
     backLabel.setOnMouseClicked(ev -> {
         if (ev.getClickCount() >= 2) {
             if (flipPressCount < 2) flipPressCount = 2;
             updateFaceVisibility();
         } else {
             flipPressed();
         }
     });
 }

 // === 模式切换 ===
 public void setUIMode(UIMode mode) {
     if (mode == null || mode == currentMode) return;
     currentMode = mode;
     // responsive: toggle 'narrow' class if width < 900px
     getScene().widthProperty().addListener((o,ov,nv) -> {
         if (nv != null) {
             boolean narrow = nv.doubleValue() < 900;
             if (narrow && !root.getStyleClass().contains("narrow")) root.getStyleClass().add("narrow");
             if (!narrow) root.getStyleClass().remove("narrow");
         }
     });

     buildDrawerGrid();
     applyMode(currentMode);
     // cache content during anims to avoid jitter
     root.setCache(true);
    applyPositionForMode();
    // persist preference
    com.memorizer.app.Config.set("app.ui.mode", currentMode == UIMode.MINI ? "mini" : "normal");
     // notify main/study windows to refresh indicators if present
     try { com.memorizer.ui.MainStage m = com.memorizer.app.AppContext.getMain(); if (m != null) m.refreshModeIndicatorInStudy(); } catch (Exception ignored) {}
 }
 public void toggleMode() {
     setUIMode(currentMode == UIMode.NORMAL ? UIMode.MINI : UIMode.NORMAL);
 }

 // === 主题切换 ===
 public void setTheme(ThemeMode theme) {
     if (theme == null || theme == currentTheme) return;
     currentTheme = theme;
     applyDrawerTheme(currentTheme == ThemeMode.DARK);
     com.memorizer.app.Config.set("app.ui.theme", currentTheme == ThemeMode.LIGHT ? "light" : "dark");
     // Reapply mode visibility to ensure nodes become visible/managed after swap
     applyMode(currentMode);
 }
 public void toggleTheme() { setTheme(currentTheme == ThemeMode.DARK ? ThemeMode.LIGHT : ThemeMode.DARK); }
 private void applyTheme(ThemeMode theme) { /* legacy no-op */ }

 private void applyDrawerTheme(boolean dark) {
     String sel = dark ? "/css/drawer-dark.css" : "/css/drawer-light.css";
     try {
         java.net.URL u = StealthStage.class.getResource(sel);
         if (u != null) getScene().getStylesheets().setAll(u.toExternalForm());
     } catch (Exception ignored) {}
     root.getStyleClass().removeAll("taskbar-dark","taskbar-light");
     root.getStyleClass().add(dark?"taskbar-dark":"taskbar-light");
 }

 private void applyMode(UIMode mode) {
     boolean mini = (mode == UIMode.MINI);
     // center lines visibility (Normal shows B/C; Mini hides B/C)
     if (frontLabel != null) {
         frontLabel.setWrapText(!mini);
         limitLabelLines(frontLabel, mini ? 1 : 2);
     }
     if (backLabel != null) {
         backLabel.setWrapText(!mini);
         limitLabelLines(backLabel, mini ? 1 : 2);
         backLabel.setVisible(true); backLabel.setManaged(true);
     }
    if (readingPosLabel != null) {
        int state = flipPressCount % 3;
        boolean showRP = (state == 2);
        readingPosLabel.setVisible(showRP); readingPosLabel.setManaged(showRP);
    }
    if (examplesScroll != null && examplesMini != null) {
        int state = flipPressCount % 3;
        boolean showRP = (state == 2);
        examplesScroll.setVisible(showRP && !mini); examplesScroll.setManaged(showRP && !mini);
        examplesMini.setVisible(showRP && mini);    examplesMini.setManaged(showRP && mini);
    }
    // Edit/Add: show ADD only in normal (mini uses context menu)
    if (btnAdd != null) { btnAdd.setVisible(!mini); btnAdd.setManaged(!mini); }
    if (mini) startMiniExamplesMarquee(); else stopMiniExamplesMarquee();
    // right clusters (answers)
    if (answersMini != null) { answersMini.setVisible(mini); answersMini.setManaged(mini); }
    if (answersNormal != null) { answersNormal.setVisible(!mini); answersNormal.setManaged(!mini); }
     // progress text: hide in Mini (bar only)
    if (progressBox != null) {
        if (progressBox.getChildren().size() >= 2) {
            Node normalCol = progressBox.getChildren().get(0);
            Node miniRow = progressBox.getChildren().get(1);
            normalCol.setVisible(!mini); normalCol.setManaged(!mini);
            miniRow.setVisible(mini);   miniRow.setManaged(mini);
        }
        // Ensure progress bar is visible in both modes
        todayProgress.setVisible(true); todayProgress.setManaged(true);
    }
     // separators
     if (allVerticalSeps != null) {
         for (Separator s : allVerticalSeps) { s.setVisible(!mini); s.setManaged(!mini); }
     }
    // strict heights by DPI
    double scale = 1.0; try { int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution(); scale = Math.max(1.0, dpi / 96.0); } catch (Throwable ignored) {}
    double h = mini ? 44*scale : 76*scale;
    setHeight(h);
    // style class for padding differences and CSS overrides
    root.getStyleClass().removeAll("taskbar-mini","taskbar-normal","mini");
    root.getStyleClass().add(mini ? "taskbar-mini" : "taskbar-normal");
    refreshTodayProgress();
}

 // === Normal 布局 ===
 private Pane buildNormalRoot() {
     // 左侧控制列
    VBox left = new VBox(6, btnEdit, batchInfo);
    left.setAlignment(Pos.TOP_CENTER);
     left.setPadding(new Insets(6, 10, 6, 10));
     left.setMinWidth(60);

    // 主显示列（横向排列：Front | Back(含 Reading)）
    VBox backBox = new VBox(2, backLabel, readingLabel);
    backBox.setAlignment(Pos.TOP_LEFT);
    HBox main = new HBox(16, frontLabel, backBox);
    main.setAlignment(Pos.TOP_LEFT);
    main.setPadding(new Insets(8, 8, 8, 8));
    HBox.setHgrow(main, Priority.ALWAYS);
    HBox.setHgrow(frontLabel, Priority.ALWAYS);
    HBox.setHgrow(backBox, Priority.ALWAYS);
    frontLabel.setAlignment(Pos.CENTER_LEFT);
    backLabel.setAlignment(Pos.CENTER_LEFT);
    readingLabel.setAlignment(Pos.CENTER_LEFT);

    // 标签列（词性 + 计划类型）
    kindLabel.getStyleClass().add("pos-pill");
    kindLabel.setVisible(false);
    VBox tags = new VBox(6, posLabel, kindLabel);
    tags.setAlignment(Pos.TOP_CENTER);
    tags.setPadding(new Insets(6, 10, 6, 10));

    // 例句区（滚动容器，超出显示滚动条）
    examplesBox.setPadding(new Insets(6, 10, 6, 10));
    examplesBox.setFillWidth(true);
    examplesScroll.setFitToWidth(true);
    examplesScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
    examplesScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
    examplesScroll.setContent(examplesBox);
    examplesScroll.setPrefViewportHeight(64);
    examplesScroll.setFocusTraversable(false);
    VBox examples = new VBox(new Label(" "), examplesScroll); // 顶部留一点空
    examples.setPrefWidth(360);
    examples.setMinWidth(240);
    examples.setMaxWidth(480);

    // 右侧操作列（Flip + 评分 + Today）
    HBox ratings = new HBox(8, btn1, btn2, btn3, btn4);
    HBox controlsRow = new HBox(10, btnFlip, ratings, todayLabel);
    controlsRow.getStyleClass().add("controls");
    HBox.setHgrow(controlsRow, Priority.NEVER);
    controlsRow.setFillHeight(true);
    controlsRow.setAlignment(Pos.BASELINE_RIGHT);
    VBox right = new VBox(8, controlsRow);
     right.setAlignment(Pos.CENTER_RIGHT);
     right.setPadding(new Insets(8, 10, 8, 10));
     right.setMinWidth(260);

     // 当日进度
     planLabel.getStyleClass().add("batch-info");
    VBox today = new VBox(4, todayProgress, planLabel);
     today.setAlignment(Pos.CENTER_RIGHT);
     today.setPadding(new Insets(6, 10, 6, 10));

  // 整行
    // small vertical separators between sections for clearer partitioning
    javafx.scene.control.Separator s1 = vSep(); s1.getStyleClass().add("splitter");
    javafx.scene.control.Separator s2 = vSep(); s2.getStyleClass().add("splitter");
    javafx.scene.control.Separator s3 = vSep(); s3.getStyleClass().add("splitter");
    javafx.scene.control.Separator s4 = vSep(); s4.getStyleClass().add("splitter");
    javafx.scene.control.Separator s5 = vSep(); s5.getStyleClass().add("splitter");
    HBox bar = new HBox(12, left, s1, main, s2, tags, s3, examples, s4, right, s5, today);
    bar.setAlignment(Pos.TOP_LEFT);
    bar.setPadding(new Insets(8, 12, 8, 12));

     // 让 examples 可被压缩（避免把主文本挤没了）
     examples.setMaxWidth(480);
     HBox.setHgrow(examples, Priority.SOMETIMES);
     
     return bar;
 }

 // === Mini 布局 ===
 private Pane buildMiniRoot() {
        Region leftSpring = new Region();
        Region rightSpring = new Region();
        HBox.setHgrow(leftSpring, Priority.ALWAYS);
        HBox.setHgrow(rightSpring, Priority.ALWAYS);

        // Mini：显示正/背面与发音（发音根据翻面可见），不显示 pos/examples
        posLabel.setVisible(false);
        examplesBox.getChildren().clear();

        // Mini: single row toolbar + one-line front text with ellipsis
        frontLabel.setWrapText(false);
        frontLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox buttons = new HBox(6, btnFlip, btn1, btn2, btn3, btn4);
        buttons.getStyleClass().add("controls");
        HBox bar = new HBox(10,
                btnEdit,
                batchInfo,
                leftSpring,
                frontLabel,
                rightSpring,
                buttons,
                todayLabel
        );
        HBox.setHgrow(frontLabel, Priority.ALWAYS);
        bar.getStyleClass().add("mini");
        bar.setAlignment(Pos.TOP_LEFT);
        bar.setPadding(new Insets(8, 12, 8, 12));
        return bar;
    }


 public void bindStudy(StudyService study) {
     this.study = study;
 }
 
 private Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }

 // === 显示卡片内容（DTO） ===
 public void showCardView(StudyService.CardView v) {
	    this.currentCardId = v.getCardId();
        String primary = v.getFront() != null ? v.getFront() : "";
        String secondary = v.getBack() != null ? v.getBack() : "";
        String reading = v.getReading() != null ? v.getReading() : "";
        	frontLabel.setText(primary);
	    backLabel.setText(secondary);
	    readingLabel.setText(reading);
    posLabel.setText(v.getPos() != null ? v.getPos() : "");
    String pos = v.getPos() != null ? v.getPos() : "";
    String rp = reading.isEmpty() ? pos : (pos.isEmpty() ? reading : reading + "  " + pos);
    readingPosLabel.setText(rp);
    // map plan kind to badge text + css class
    String ktxt = null;
    String kclass = null;
    if (v.getPlanKind() != null) {
        switch (v.getPlanKind()) {
            case 0: ktxt = "DUE"; kclass = "kind-due"; break;
            case 1: ktxt = "LEECH"; kclass = "kind-leech"; break;
            case 2: ktxt = "NEW"; kclass = "kind-new"; break;
            case 3: ktxt = "CHAL"; kclass = "kind-chal"; break;
            default: break;
        }
    }
    // clear previous kind-* classes
    kindLabel.getStyleClass().removeAll("kind-due","kind-leech","kind-new","kind-chal");
    frontLabel.getStyleClass().removeAll("kind-due","kind-leech","kind-new","kind-chal");
    backLabel.getStyleClass().removeAll("kind-due","kind-leech","kind-new","kind-chal");
    if (ktxt != null && !ktxt.isEmpty()) {
        kindLabel.setText(ktxt);
        if (kclass != null) {
            kindLabel.getStyleClass().add(kclass);
            frontLabel.getStyleClass().add(kclass);
            backLabel.getStyleClass().add(kclass);
        }
        kindLabel.setVisible(true);
    } else {
        kindLabel.setText("");
        kindLabel.setVisible(false);
    }
        // Populate center lines
        lineA.getChildren().clear();
        lineA.getChildren().add(new Text(primary));
        lineB.setText(reading == null ? "" : reading);
        String example = null;
        try {
            java.util.List<String> exs = v.getExamples();
            if (exs != null && !exs.isEmpty()) example = exs.get(0);
        } catch (Throwable ignored) {}
        lineC.setText(example == null ? "" : example);
        examplesMini.setText(example == null ? "" : example);
        boolean bEmpty = lineB.getText() == null || lineB.getText().trim().isEmpty();
        boolean cEmpty = lineC.getText() == null || lineC.getText().trim().isEmpty();
        if (currentMode == UIMode.NORMAL) {
            lineB.setVisible(!bEmpty); lineB.setManaged(!bEmpty);
            lineC.setVisible(!cEmpty); lineC.setManaged(!cEmpty);
        }

	    setExamples(v.getExamples());

	    showingFront = true;
        flipPressCount = 0;
        readingShown = false;
        updateFaceVisibility();
        // ensure key events land on the scene root (avoid space triggering focused buttons or selections)
        try { if (getScene() != null && getScene().getRoot() != null) getScene().getRoot().requestFocus(); } catch (Exception ignored) {}

	    updateBatchInfoLabel();
	    refreshTodayProgress();
	}


 private void setExamples(java.util.List<String> items) {
    // stop previous timeline
    if (examplesTimeline != null) { examplesTimeline.stop(); examplesTimeline = null; }
    currentExamples = (items == null) ? java.util.Collections.emptyList() : new java.util.ArrayList<>(items);
    examplesIndex = 0;
    examplesBox.getChildren().clear();
    int max = com.memorizer.app.Config.getInt("app.ui.examples.max-lines", 3);
    boolean autoroll = Boolean.parseBoolean(com.memorizer.app.Config.get("app.ui.examples.autoroll", "true"));
    long intervalMs = com.memorizer.app.Config.getInt("app.ui.examples.roll-interval-ms", 2200);

    if (currentExamples.isEmpty()) {
        // hide examples containers when empty
        examplesBox.getChildren().clear();
        if (examplesCell != null) { examplesCell.setVisible(false); examplesCell.setManaged(false); }
        if (examplesMini != null) { examplesMini.setVisible(false); examplesMini.setManaged(false); }
        return;
    }

    // show first item
    Label first = new Label(currentExamples.get(0));
    first.getStyleClass().add("example-line");
    examplesBox.getChildren().add(first);
    if (examplesCell != null) { examplesCell.setVisible(true); examplesCell.setManaged(true); }
    if (examplesMini != null) { examplesMini.setVisible(currentMode == UIMode.MINI); examplesMini.setManaged(currentMode == UIMode.MINI); }

    if (autoroll && currentExamples.size() > 1) {
        examplesTimeline = new Timeline(new KeyFrame(Duration.millis(Math.max(500, intervalMs)), e -> {
            examplesIndex = (examplesIndex + 1) % currentExamples.size();
            examplesBox.getChildren().clear();
            int lines = Math.max(1, Math.min(max, currentExamples.size()));
            for (int i = 0; i < lines; i++) {
                int idx = (examplesIndex + i) % currentExamples.size();
                Label line = new Label(currentExamples.get(idx));
                line.getStyleClass().add("example-line");
                examplesBox.getChildren().add(line);
            }
        }));
        examplesTimeline.setCycleCount(Timeline.INDEFINITE);
        examplesTimeline.play();

        // pause on hover
        examplesBox.setOnMouseEntered(e -> { if (examplesTimeline != null) examplesTimeline.pause(); });
        examplesBox.setOnMouseExited(e -> { if (examplesTimeline != null) examplesTimeline.play(); });
    } else {
        // static mode: cap to max lines
        examplesBox.getChildren().clear();
        int count = 0;
        for (String s : currentExamples) {
            if (s == null || s.trim().isEmpty()) continue;
            Label line = new Label(s.trim());
            line.getStyleClass().add("example-line");
            examplesBox.getChildren().add(line);
            count++; if (count >= max) break;
        }
    }

    // update mini marquee label text
    if (!currentExamples.isEmpty()) {
        String miniText = currentExamples.get(0);
        examplesMini.setText(miniText == null ? "" : miniText);
        startMiniExamplesMarquee();
    }
 }

 private void startMiniExamplesMarquee() {
     if (currentMode != UIMode.MINI) { stopMiniExamplesMarquee(); return; }
     if (examplesMini == null) return;
     String txt = examplesMini.getText();
     if (txt == null) txt = "";
     examplesMini.setTranslateX(0);
     stopMiniExamplesMarquee();
     if (txt.length() < 28) return; // short text: no marquee
     Text meas = new Text(txt);
     meas.setFont(examplesMini.getFont());
     double textW = meas.getLayoutBounds().getWidth();
     examplesMiniMarquee = new Timeline(
             new KeyFrame(Duration.ZERO, e -> examplesMini.setTranslateX(0)),
             new KeyFrame(Duration.millis(80 * textW), e -> examplesMini.setTranslateX(-textW - 32))
     );
     examplesMiniMarquee.setCycleCount(Timeline.INDEFINITE);
     examplesMiniMarquee.play();
     examplesMini.setOnMouseEntered(e -> { if (examplesMiniMarquee != null) examplesMiniMarquee.pause(); });
     examplesMini.setOnMouseExited(e -> { if (examplesMiniMarquee != null) examplesMiniMarquee.play(); });
 }

 private void stopMiniExamplesMarquee() {
     if (examplesMiniMarquee != null) { examplesMiniMarquee.stop(); examplesMiniMarquee = null; }
 }

 private void refreshTodayProgress() {
     int target = com.memorizer.app.Config.getInt("app.study.daily-target", 50);
     int done = 0;
     try { done = new StatsRepository().load().todayReviews; } catch (Exception ignored) {}
     int shown = (target > 0) ? Math.min(done, target) : done;
     todayLabel.setText("Today: " + shown + "/" + target);
     todayProgress.setProgress(target > 0 ? Math.min(1.0, done / (double) target) : 0);
     // plan counts
     try {
         com.memorizer.service.PlanService.Counts pc = (study != null) ? study.planCounts() : null;
         String next = (study != null) ? study.previewNextFromPlanFront().orElse("") : "";
         if (next.length() > 24) next = next.substring(0,24) + "…";
         if (pc != null) planLabel.setText("Plan " + pc.pending + "/" + pc.total + (next.isEmpty()?"":" — Next: " + next));
     } catch (Exception ignored) {}
 }

 // === 位置/窗口标志（你在 S1 已加入，保留） ===
 private void initWindowFlags() {
     if (Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.hide-from-taskbar","true"))) {
         Stage owner = com.memorizer.app.AppContext.getOwner();
         if (owner != null) initOwner(owner);
     }
     initStyle(StageStyle.TRANSPARENT);
     setAlwaysOnTop(true);
     setOpacity(Double.parseDouble(com.memorizer.app.Config.get("app.window.opacity","0.90")));
 }

    private void applyPositionForMode() {
        String mode = currentMode == UIMode.MINI ? "mini" : "normal";
        boolean overlay = Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.overlay-taskbar", "false"));
        Rectangle2D vis = Screen.getPrimary().getVisualBounds();
        double screenW = vis.getWidth(), screenH = vis.getHeight(), screenX = vis.getMinX(), screenY = vis.getMinY();

     if ("mini".equalsIgnoreCase(mode)) {
         double scale = 1.0;
         try { int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution(); scale = Math.max(1.0, dpi / 96.0); } catch (Throwable ignored) {}
         double h = 44 * scale; // strict mini height
         int pad = 0;
         double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.mini.width-fraction","0.5"));
         double w = Math.max(320, screenW * frac);
         setWidth(w); setHeight(h);
         setX(screenX + (screenW - w)/2.0);
         setY(screenY + screenH - h - pad);
     } else {
         double scale = 1.0;
         try { int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution(); scale = Math.max(1.0, dpi / 96.0); } catch (Throwable ignored) {}
         double h = 76 * scale; // strict normal 2-row height
         int pad = 0;
         double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.stealth.width-fraction","0.98"));
         double w = Math.max(480, screenW * frac);

         if (overlay) {
             com.memorizer.util.ScreenUtil.Rect tb = com.memorizer.util.ScreenUtil.taskbarRect();
             setX(tb.x); setY(tb.y);
             setWidth(tb.w); setHeight(Math.min(tb.h, h));
         } else {
             setWidth(w); setHeight(h);
             setX(screenX + (screenW - w)/2.0);
             setY(screenY + screenH - h - pad);
         }
     }
 }

 public void showAndFocus() {
     applyPositionForMode();
     if (!isShowing()) show();
     toFront(); requestFocus(); setIconified(false);
 }

 // === 批次/评分/翻面/结束 ===
 private void updateBatchInfoLabel() {
     if (inBatch) {
         int total = com.memorizer.app.Config.getInt("app.study.batch-size", 1);
         int done = Math.max(0, total - remainingInBatch + 1); // 当前这张算第几张
         if (currentCardId <= 0) done = Math.max(0, total - remainingInBatch);
         batchInfo.setText("(" + done + "/" + total + ")");
         batchInfo.setVisible(true);
     } else {
         batchInfo.setText("");
         batchInfo.setVisible(false);
     }
 }
 
 private void rateAndHide(com.memorizer.model.Rating r) {
     if (study != null) {
         long lastId = currentCardId;
         study.rate(r);
         if (inBatch) {
             remainingInBatch--;
             boolean allowFallback = Boolean.parseBoolean(com.memorizer.app.Config.get("app.study.force-show-when-empty", "true"));
             if (remainingInBatch > 0) {
                 java.util.Optional<com.memorizer.service.StudyService.CardView> next =
                         study.nextForBatch(lastId, allowFallback);
                 if (next.isPresent()) {
                     showCardView(next.get());
                     return; // 继续批次，不隐藏
                 }
             }
         }
     }
     endBatchAndHide();
 }

 private void hideWithSnooze() {
     boolean snoozeEnabled = Boolean.parseBoolean(com.memorizer.app.Config.get("app.study.snooze-on-hide-enabled", "true"));
     int mins = com.memorizer.app.Config.getInt("app.study.snooze-on-hide-minutes", 10);
     if (study != null) {
         study.dismissWithoutRating(snoozeEnabled, mins);
     }
     endBatchAndHide();
 }
 
 

 private void flipPressed() {
     // Cycle: 0 -> 1 -> 2 -> 0
     flipPressCount = (flipPressCount + 1) % 3;
     updateFaceVisibility();
 }

    private void updateFaceVisibility() {
        int state = flipPressCount % 3; // 0: Front only, 1: Back only, 2: Front+Back+Reading/Pos(+Examples)
        boolean showFront = (state == 0) || (state == 2);
        boolean showBack  = (state == 1) || (state == 2);
        boolean showRP    = (state == 2);

        frontLabel.setVisible(showFront); frontLabel.setManaged(showFront);
        backLabel.setVisible(showBack);   backLabel.setManaged(showBack);
        readingLabel.setVisible(showRP);  readingLabel.setManaged(showRP);
        if (readingPosLabel != null) { readingPosLabel.setVisible(showRP); readingPosLabel.setManaged(showRP); }

        // Examples visibility by state + mode; width listener may further hide under tight width
        boolean mini = (currentMode == UIMode.MINI);
        if (examplesScroll != null) { boolean vis = showRP && !mini; examplesScroll.setVisible(vis); examplesScroll.setManaged(vis); }
        if (examplesMini != null)   { boolean vis = showRP && mini;  examplesMini.setVisible(vis);   examplesMini.setManaged(vis); }
    }

 private javafx.scene.control.Separator vSep() {
     javafx.scene.control.Separator sp = new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL);
     sp.getStyleClass().add("v-sep");
     sp.setMinWidth(1);
     sp.setPrefWidth(1);
     sp.setMaxWidth(1);
     return sp;
 }

 // 将 Label 限制为 N 行（通过剪裁实现粗略行数限制）
    private void limitLabelLines(Label label, int lines) {
        if (label == null || lines < 1) return;
        label.setWrapText(true);
        label.applyCss();
        double fs = label.getFont() == null ? 14 : label.getFont().getSize();
        double lh = fs * 1.3;
        double maxH = lh * lines;
        label.setMaxHeight(maxH);
        label.setPrefHeight(Region.USE_COMPUTED_SIZE);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        label.layoutBoundsProperty().addListener((obs, oldB, newB) -> {
            clip.setWidth(newB.getWidth());
            clip.setHeight(maxH);
        });
        label.setClip(clip);
    }

    // Build GridPane drawer root with center 3 lines and right/left clusters
    private void buildDrawerGrid() {
        if (drawerRoot != null) { root.setCenter(drawerRoot); return; }
        drawerRoot = new GridPane();
        drawerRoot.setHgap(0); drawerRoot.setVgap(0);

        // Build 15 columns: C0 S C1 S C2 S C3 S C4 S C5 S C6 S C7
        java.util.List<ColumnConstraints> cols = new java.util.ArrayList<>();
        ColumnConstraints c0 = new ColumnConstraints(); c0.setMinWidth(84); c0.setPrefWidth(92); c0.setHgrow(Priority.NEVER); cols.add(c0);
        ColumnConstraints s01 = new ColumnConstraints(); s01.setMinWidth(1); s01.setPrefWidth(1); cols.add(s01);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setHgrow(Priority.SOMETIMES); cols.add(c1);
        ColumnConstraints s12 = new ColumnConstraints(); s12.setMinWidth(1); s12.setPrefWidth(1); cols.add(s12);
        ColumnConstraints c2 = new ColumnConstraints(); c2.setMinWidth(110); c2.setPrefWidth(130); cols.add(c2); this.colC2 = c2;
        ColumnConstraints s23 = new ColumnConstraints(); s23.setMinWidth(1); s23.setPrefWidth(1); cols.add(s23);
        ColumnConstraints c3 = new ColumnConstraints(); c3.setHgrow(Priority.SOMETIMES); cols.add(c3);
        ColumnConstraints s34 = new ColumnConstraints(); s34.setMinWidth(1); s34.setPrefWidth(1); cols.add(s34);
        ColumnConstraints c4 = new ColumnConstraints(); c4.setHgrow(Priority.ALWAYS); cols.add(c4); this.colC4 = c4;
        ColumnConstraints s45 = new ColumnConstraints(); s45.setMinWidth(1); s45.setPrefWidth(1); cols.add(s45);
        ColumnConstraints c5 = new ColumnConstraints(); c5.setMinWidth(76); c5.setPrefWidth(84); cols.add(c5);
        ColumnConstraints s56 = new ColumnConstraints(); s56.setMinWidth(1); s56.setPrefWidth(1); cols.add(s56);
        ColumnConstraints c6 = new ColumnConstraints(); c6.setMinWidth(180); c6.setPrefWidth(188); cols.add(c6);
        ColumnConstraints s67 = new ColumnConstraints(); s67.setMinWidth(1); s67.setPrefWidth(1); cols.add(s67);
        ColumnConstraints c7 = new ColumnConstraints(); c7.setMinWidth(180); c7.setPrefWidth(200); cols.add(c7);
        drawerRoot.getColumnConstraints().addAll(cols);

        // C0: Edit/Add buttons container
        VBox leftButtons = new VBox(8);
        leftButtons.getStyleClass().add("cell-edit");
        leftButtons.setAlignment(Pos.CENTER);
        leftButtons.getChildren().addAll(btnEdit, btnAdd);
        GridPane.setValignment(leftButtons, VPos.CENTER);
        drawerRoot.add(leftButtons, 0, 0);

        // separators storage helper
        java.util.function.Function<Integer, Separator> sepAt = idx -> {
            Separator sp = new Separator(javafx.geometry.Orientation.VERTICAL);
            GridPane.setValignment(sp, VPos.CENTER);
            allVerticalSeps.add(sp); return sp;
        };

        // add seps at correct columns
        drawerRoot.add(sepAt.apply(1), 1, 0);
        drawerRoot.add(sepAt.apply(3), 3, 0);
        drawerRoot.add(sepAt.apply(5), 5, 0);
        drawerRoot.add(sepAt.apply(7), 7, 0);
        drawerRoot.add(sepAt.apply(9), 9, 0);
        drawerRoot.add(sepAt.apply(11), 11, 0);
        drawerRoot.add(sepAt.apply(13), 13, 0);

        // C1: FRONT (Label, wraps up to 2 lines in Normal)
        frontLabel.getStyleClass().add("cell-front");
        frontLabel.setWrapText(true);
        GridPane.setValignment(frontLabel, VPos.CENTER);
        drawerRoot.add(frontLabel, 2, 0);

        // C2: Reading/Pos (small, ellipsis)
        readingPosLabel.getStyleClass().addAll("cell-reading","muted");
        readingPosLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        GridPane.setValignment(readingPosLabel, VPos.CENTER);
        drawerRoot.add(readingPosLabel, 4, 0);

        // C3: BACK (Label, wraps up to 2 lines in Normal)
        backLabel.getStyleClass().add("cell-back");
        backLabel.setWrapText(true);
        GridPane.setValignment(backLabel, VPos.CENTER);
        drawerRoot.add(backLabel, 6, 0);

        // C4: EXAMPLES (Normal: TextFlow wrapped; Mini: single-line marquee)
        examplesCell = new VBox(2);
        examplesCell.getStyleClass().add("cell-examples");
        examplesCell.setFillWidth(true);
        // Normal flow (reuse examplesBox/examplesScroll)
        examplesScroll.setFitToWidth(true);
        examplesScroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        examplesScroll.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        examplesScroll.setContent(examplesBox);
        examplesCell.getChildren().add(examplesScroll);
        // Mini single-line label inside clipped wrapper (for marquee)
        examplesMini.setTextOverrun(OverrunStyle.ELLIPSIS);
        examplesMini.getStyleClass().add("line-sub");
        StackPane miniWrap = new StackPane(examplesMini);
        javafx.scene.shape.Rectangle miniClip = new javafx.scene.shape.Rectangle();
        miniWrap.layoutBoundsProperty().addListener((oo,ov,nv)->{
            if (nv != null) { miniClip.setWidth(nv.getWidth()); miniClip.setHeight(nv.getHeight()); }
        });
        miniWrap.setClip(miniClip);
        examplesCell.getChildren().add(miniWrap);
        GridPane.setValignment(examplesCell, VPos.CENTER);
        drawerRoot.add(examplesCell, 8, 0);

        // C5: Flip (single button)
        btnFlip.getStyleClass().add("cell-flip");
        GridPane.setValignment(btnFlip, VPos.CENTER);
        drawerRoot.add(btnFlip, 10, 0);

        // C6: Answers (Normal: 2x2 grid; Mini: 1 row HBox)
        answersNormal = new GridPane(); answersNormal.setHgap(8); answersNormal.setVgap(6);
        answersNormal.getStyleClass().add("cell-answers");
        // equal column widths for 2x2 grid
        ColumnConstraints a0 = new ColumnConstraints(); a0.setPercentWidth(50); a0.setHgrow(Priority.ALWAYS);
        ColumnConstraints a1 = new ColumnConstraints(); a1.setPercentWidth(50); a1.setHgrow(Priority.ALWAYS);
        answersNormal.getColumnConstraints().addAll(a0, a1);
        answersNormal.add(btn1, 0, 0); answersNormal.add(btn2, 1, 0);
        answersNormal.add(btn3, 0, 1); answersNormal.add(btn4, 1, 1);
        GridPane.setHgrow(btn1, Priority.ALWAYS); GridPane.setHgrow(btn2, Priority.ALWAYS);
        GridPane.setHgrow(btn3, Priority.ALWAYS); GridPane.setHgrow(btn4, Priority.ALWAYS);
        btn1.setMaxWidth(Double.MAX_VALUE); btn2.setMaxWidth(Double.MAX_VALUE);
        btn3.setMaxWidth(Double.MAX_VALUE); btn4.setMaxWidth(Double.MAX_VALUE);
        // Create dedicated mini buttons to avoid reparenting
        Button m1 = new Button("1"); m1.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.AGAIN));  m1.getStyleClass().addAll("controls","btn-answer","btn-again"); m1.setTooltip(new Tooltip("Again (1)"));
        Button m2 = new Button("2"); m2.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.HARD));   m2.getStyleClass().addAll("controls","btn-answer","btn-hard");  m2.setTooltip(new Tooltip("Hard (2)"));
        Button m3 = new Button("3"); m3.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.GOOD));   m3.getStyleClass().addAll("controls","btn-answer","btn-good");  m3.setTooltip(new Tooltip("Good (3)"));
        Button m4 = new Button("4"); m4.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.EASY));   m4.getStyleClass().addAll("controls","btn-answer","btn-easy");  m4.setTooltip(new Tooltip("Easy (4)"));
        answersMini = new HBox(8, m1, m2, m3, m4); answersMini.getStyleClass().add("cell-answers");
        answersMini.setAlignment(Pos.BASELINE_CENTER);
        HBox.setHgrow(m1, Priority.ALWAYS); HBox.setHgrow(m2, Priority.ALWAYS);
        HBox.setHgrow(m3, Priority.ALWAYS); HBox.setHgrow(m4, Priority.ALWAYS);
        m1.setMaxWidth(Double.MAX_VALUE); m2.setMaxWidth(Double.MAX_VALUE);
        m3.setMaxWidth(Double.MAX_VALUE); m4.setMaxWidth(Double.MAX_VALUE);
        VBox answersCell = new VBox(8, answersMini, answersNormal);
        answersCell.setAlignment(Pos.CENTER);
        GridPane.setValignment(answersCell, VPos.CENTER);
        drawerRoot.add(answersCell, 12, 0);

        // C7: Progress (Normal: bar over text; Mini: bar only)
        progressBox = new VBox(4);
        progressBox.getStyleClass().add("cell-progress");
        // Normal: ProgressBar with text overlay (Today + (n/m)) on top, aligned right
        HBox progressTextInline = new HBox(6, todayLabel, batchInfo);
        progressTextInline.setAlignment(Pos.CENTER_RIGHT);
        progressTextInline.setMouseTransparent(true);
        StackPane progressStack = new StackPane(todayProgress, progressTextInline);
        StackPane.setAlignment(progressTextInline, Pos.CENTER_RIGHT);
        StackPane.setMargin(progressTextInline, new Insets(0,8,0,0));
        VBox progressNormal = new VBox(progressStack);
        progressNormal.setAlignment(Pos.CENTER_RIGHT);
        // Mini: badge left of bar, right-aligned
        Label batchMini = new Label(); batchMini.getStyleClass().addAll("batch-info","muted"); batchMini.textProperty().bind(batchInfo.textProperty());
        todayProgressMini = new ProgressBar(); todayProgressMini.getStyleClass().add("today-progress");
        todayProgressMini.progressProperty().bind(todayProgress.progressProperty());
        todayProgressMini.setPrefWidth(todayProgress.getPrefWidth());
        todayProgressMini.setMinWidth(todayProgress.getMinWidth());
        todayProgressMini.setMaxWidth(todayProgress.getMaxWidth());
        todayProgressMini.getStyleClass().add("progress-large");
        HBox progressMini = new HBox(6, batchMini, todayProgressMini);
        progressMini.setAlignment(Pos.CENTER_RIGHT);
        progressBox.getChildren().addAll(progressNormal, progressMini);
        progressBox.setAlignment(Pos.CENTER_RIGHT);
        progressBox.setPadding(new Insets(0,8,0,0));
        GridPane.setValignment(progressBox, VPos.CENTER);
        drawerRoot.add(progressBox, 14, 0);
        todayProgress.getStyleClass().add("progress-large");
        // Ensure answers visible/managed (never hidden by legacy code)
        java.util.stream.Stream.of(btn1, btn2, btn3, btn4).forEach(b -> { b.setVisible(true); b.setManaged(true); });

        root.setCenter(drawerRoot);
        // Vertical centering on all children in the single row
        for (Node n : drawerRoot.getChildren()) { GridPane.setValignment(n, VPos.CENTER); }
        // baseline align for answers row in mini
        if (answersMini != null) answersMini.setAlignment(Pos.BASELINE_CENTER);
    }

    private void adjustExamplesLinesByWidth(double ww, boolean mini) {
        if (mini) {
            // in mini, optionally marquee handled separately
            return;
        }
        // adjust visible lines by viewport height
        if (examplesScroll != null) {
            if (ww > 1280) examplesScroll.setPrefViewportHeight(64);
            else if (ww > 1100) examplesScroll.setPrefViewportHeight(48);
            else if (ww > 980) examplesScroll.setPrefViewportHeight(34);
            else examplesScroll.setPrefViewportHeight(24);
        }
    }

 public void skipCurrent() {
     if (study != null) {
         long lastId = currentCardId;
         study.skipCurrent();
         if (inBatch) {
             remainingInBatch--;
             boolean allowFallback = Boolean.parseBoolean(com.memorizer.app.Config.get("app.study.force-show-when-empty", "true"));
             if (remainingInBatch > 0) {
                 java.util.Optional<com.memorizer.service.StudyService.CardView> next =
                         study.nextForBatch(lastId, allowFallback);
                 if (next.isPresent()) { showCardView(next.get()); return; }
             }
         }
     }
     endBatchAndHide();
 }
private void toggleFace() {
     showingFront = !showingFront;
     frontLabel.setVisible(showingFront);
     backLabel.setVisible(!showingFront);
 }

 public void startBatch(int batchSize) {
     if (batchSize < 1) batchSize = 1;
     this.inBatch = batchSize > 1;
     this.remainingInBatch = batchSize;
     this.sessionActive = true;
     updateBatchInfoLabel();
 }

 private void endBatchAndHide() {
     this.inBatch = false;
     this.remainingInBatch = 1;
     this.currentCardId = -1;
     this.sessionActive = false;
     updateBatchInfoLabel();
     hide();
 }

 // === Lightweight Edit/Add Popup ===
 private void openEditPopup() {
     long cardId = this.currentCardId;
     if (cardId <= 0) return;
     java.util.Optional<com.memorizer.model.Note> on = new com.memorizer.db.NoteRepository().findByCardId(cardId);
     if (!on.isPresent()) return;
     showNotePopup(on.get(), /*isAdd*/ false);
 }

 private void openAddPopup() { showNotePopup(new com.memorizer.model.Note(), /*isAdd*/ true); }

 private void showNotePopup(com.memorizer.model.Note note, boolean isAdd) {
     Stage pop = new Stage(StageStyle.TRANSPARENT);
     pop.setAlwaysOnTop(true);
     pop.initOwner(this);
     // DPI scale
     double scale = 1.0; try { int dpi = java.awt.Toolkit.getDefaultToolkit().getScreenResolution(); scale = Math.max(1.0, dpi/96.0); } catch (Throwable ignored) {}
     double w = 480 * scale, h = 380 * scale;
     // UI
     BorderPane bp = new BorderPane();
     bp.getStyleClass().add(root.getStyleClass().contains("taskbar-dark")?"taskbar-dark":"taskbar-light");
     bp.getStyleClass().add("popup");
     bp.setPadding(new Insets(12));
     javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle(); clip.setArcWidth(12); clip.setArcHeight(12); bp.setClip(clip);
     bp.layoutBoundsProperty().addListener((o,ov,nv)->{ if (nv!=null) { clip.setWidth(nv.getWidth()); clip.setHeight(nv.getHeight()); }});
     bp.setEffect(new javafx.scene.effect.DropShadow(16, javafx.scene.paint.Color.rgb(0,0,0,0.35)));

     // Fields
     javafx.scene.control.TextField fFront = new javafx.scene.control.TextField(note.front == null?"":note.front);
     javafx.scene.control.TextField fReading = new javafx.scene.control.TextField(note.reading == null?"":note.reading);
     javafx.scene.control.TextField fPos = new javafx.scene.control.TextField(note.pos == null?"":note.pos);
     javafx.scene.control.TextArea fBack = new javafx.scene.control.TextArea(note.back == null?"":note.back);
     javafx.scene.control.TextArea fExamples = new javafx.scene.control.TextArea(note.examples == null?"":note.examples);
     fBack.setPrefRowCount(3); fExamples.setPrefRowCount(4);
     javafx.scene.control.Label t1 = new javafx.scene.control.Label("Front");
     javafx.scene.control.Label t2 = new javafx.scene.control.Label("Reading / Pos");
     javafx.scene.control.Label t3 = new javafx.scene.control.Label("Back");
     javafx.scene.control.Label t4 = new javafx.scene.control.Label("Examples");

     GridPane form = new GridPane(); form.setHgap(8); form.setVgap(8);
     int r=0; form.add(t1,0,r); form.add(fFront,1,r++);
     form.add(t2,0,r); HBox rp = new HBox(8, fReading, fPos); HBox.setHgrow(fReading, Priority.ALWAYS); HBox.setHgrow(fPos, Priority.SOMETIMES); form.add(rp,1,r++);
     form.add(t3,0,r); form.add(fBack,1,r++);
     form.add(t4,0,r); form.add(fExamples,1,r++);
     ColumnConstraints cc0 = new ColumnConstraints(); cc0.setMinWidth(110); cc0.setPrefWidth(120);
     ColumnConstraints cc1 = new ColumnConstraints(); cc1.setHgrow(Priority.ALWAYS);
     form.getColumnConstraints().addAll(cc0, cc1);

     HBox actions = new HBox(8);
     Button bSave = new Button("Save"); bSave.getStyleClass().addAll("controls","btn-flip");
     Button bCancel = new Button("Cancel"); bCancel.getStyleClass().addAll("controls","btn-flip");
     actions.getChildren().addAll(spacer(), bCancel, bSave); actions.setAlignment(Pos.CENTER_RIGHT);

     VBox content = new VBox(12, form, actions);
     bp.setCenter(content);
     Scene sc = new Scene(bp, w, h);
     sc.setFill(javafx.scene.paint.Color.TRANSPARENT);
     sc.getStylesheets().setAll(getScene().getStylesheets());
     pop.setScene(sc);

     // Position above drawer, centered horizontally
     double px = getX() + (getWidth() - w)/2.0; if (px < 0) px = getX();
     double py = Math.max(getY() - h - 12, 20);
     pop.setX(px); pop.setY(py);

     // Handlers
     Runnable doSave = () -> {
         try {
             com.memorizer.db.NoteRepository nr = new com.memorizer.db.NoteRepository();
             if (isAdd) {
                 com.memorizer.model.Note n = new com.memorizer.model.Note();
                 n.front = fFront.getText(); n.back = fBack.getText(); n.reading = fReading.getText(); n.pos = fPos.getText(); n.examples = fExamples.getText();
                 long nid = nr.insert(n);
                 new com.memorizer.db.CardRepository().insertForNote(nid);
             } else {
                 com.memorizer.model.Note n = note;
                 n.front = fFront.getText(); n.back = fBack.getText(); n.reading = fReading.getText(); n.pos = fPos.getText(); n.examples = fExamples.getText();
                 nr.update(n);
                 // refresh view if editing current
                 if (currentCardId > 0 && study != null) {
                     java.util.Optional<com.memorizer.service.StudyService.CardView> ov = study.viewCardById(currentCardId);
                     ov.ifPresent(this::showCardView);
                 }
             }
         } catch (Exception ignored) {}
         pop.close();
         try { if (getScene()!=null && getScene().getRoot()!=null) getScene().getRoot().requestFocus(); } catch (Exception ignored) {}
     };
     bSave.setOnAction(e -> doSave.run());
     bCancel.setOnAction(e -> { pop.close(); try { getScene().getRoot().requestFocus(); } catch (Exception ignored) {} });
     sc.setOnKeyPressed(ev -> {
         switch (ev.getCode()) {
             case ESCAPE: pop.close(); try { getScene().getRoot().requestFocus(); } catch (Exception ignored) {} break;
             case S: if (ev.isControlDown()) { doSave.run(); } break;
             default: break;
         }
     });
     pop.show();
     fFront.requestFocus(); fFront.selectAll();
 }
}
