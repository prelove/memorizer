package com.memorizer.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.control.OverrunStyle;
import com.memorizer.db.StatsRepository;
import com.memorizer.service.StudyService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

public class StealthStage extends Stage {

 public enum UIMode { NORMAL, MINI }

 // === 会话状态（用于调度串行化） ===
 private volatile boolean sessionActive;
 public boolean isSessionActive() { return sessionActive; }

 // === 模式/根节点 ===
 private UIMode currentMode = "mini".equalsIgnoreCase(com.memorizer.app.Config.get("app.ui.mode","normal"))
         ? UIMode.MINI : UIMode.NORMAL;
 private BorderPane root;              // 作为舞台根
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

 private final Label frontLabel = new Label();
 private final Label backLabel  = new Label();
 private final Label readingLabel = new Label();
 private final Label posLabel = new Label();

 private final VBox examplesBox = new VBox(2);    // 简易例句容器（滚动动效下步做）
 private final Label todayLabel = new Label("Today: 0/0");
 private final ProgressBar todayProgress = new ProgressBar(0);

 private boolean showingFront = true;
 private long currentCardId = -1;
 
 private boolean inBatch = false;
 private int remainingInBatch = 1; // how many cards left in this session
 
 private StudyService study;
 private Timeline examplesTimeline;
 private java.util.List<String> currentExamples;
 private int examplesIndex = 0;

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

    // 使用 CSS 类来控制视觉效果（避免内联覆盖）
    frontLabel.getStyleClass().add("front");
    backLabel.getStyleClass().add("back");
    readingLabel.getStyleClass().add("reading");
    posLabel.getStyleClass().add("pos-pill");
    batchInfo.getStyleClass().add("batch-info");

    // 评分按钮样式交由 CSS 控制（统一尺寸/圆角等）
    btnFlip.getStyleClass().add("btn-flip");
    btn1.getStyleClass().add("btn-rating");
    btn2.getStyleClass().add("btn-rating");
    btn3.getStyleClass().add("btn-rating");
    btn4.getStyleClass().add("btn-rating");

    todayLabel.getStyleClass().add("today-label");
    todayProgress.getStyleClass().add("today-progress");
     todayProgress.setPrefWidth(120);
     todayProgress.setMaxWidth(120);
     todayProgress.setMinWidth(120);

     btnEdit.setTooltip(new Tooltip("Edit current card (open main window)"));
     batchInfo.setTooltip(new Tooltip("Batch progress"));
     btn1.setTooltip(new Tooltip("Again (1)"));
     btn2.setTooltip(new Tooltip("Hard (2)"));
     btn3.setTooltip(new Tooltip("Good (3)"));
     btn4.setTooltip(new Tooltip("Easy (4)"));

     // 评分按钮动作（沿用你的 rateAndHide）
     btnFlip.setOnAction(e -> toggleFace());
     btn1.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.AGAIN));
     btn2.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.HARD));
     btn3.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.GOOD));
     btn4.setOnAction(e -> rateAndHide(com.memorizer.model.Rating.EASY));

     btnEdit.setOnAction(e -> {
         // 先打开主窗（具体“定位到编辑”下步实现）
         com.memorizer.app.AppContext.getMain().showAndFocus();
     });

    // 根容器
    root = new BorderPane();
    root.getStyleClass().add("stealth-root");
     setScene(new Scene(root));
     try {
         java.net.URL css = StealthStage.class.getResource("/stealth.css");
         if (css != null) getScene().getStylesheets().add(css.toExternalForm());
     } catch (Exception ignored) {}
    try {
        java.net.URL css = StealthStage.class.getResource("/stealth.css");
        if (css != null) getScene().getStylesheets().add(css.toExternalForm());
    } catch (Exception ignored) {}

     // 键盘
     getScene().setOnKeyPressed(ev -> {
         switch (ev.getCode()) {
             case SPACE:
             case ENTER: toggleFace(); break;
             case DIGIT1: rateAndHide(com.memorizer.model.Rating.AGAIN); break;
             case DIGIT2: rateAndHide(com.memorizer.model.Rating.HARD); break;
             case DIGIT3: rateAndHide(com.memorizer.model.Rating.GOOD); break;
             case DIGIT4: rateAndHide(com.memorizer.model.Rating.EASY); break;
             case ESCAPE: hideWithSnooze(); break;
             case M: toggleMode(); break; // 新增：切换 Normal/Mini
             default: break;
         }
     });

     applyMode(currentMode);
 }

 // === 模式切换 ===
 public void setUIMode(UIMode mode) {
     if (mode == null || mode == currentMode) return;
     currentMode = mode;
     applyMode(currentMode);
     applyPositionForMode();
     // persist preference
     com.memorizer.app.Config.set("app.ui.mode", currentMode == UIMode.MINI ? "mini" : "normal");
 }
 public void toggleMode() {
     setUIMode(currentMode == UIMode.NORMAL ? UIMode.MINI : UIMode.NORMAL);
 }

 private void applyMode(UIMode mode) {
     if (mode == UIMode.MINI) {
         // entering MINI: hide secondary fields and stop examples roller
         if (examplesTimeline != null) { examplesTimeline.stop(); examplesTimeline = null; }
         readingLabel.setVisible(false);
         posLabel.setVisible(false);
         examplesBox.setVisible(false);
         // compact button texts for mini
         btn1.setText("1"); btn2.setText("2"); btn3.setText("3"); btn4.setText("4");
         // rebuild mini bar fresh to ensure nodes are correctly parented
         miniBar = buildMiniRoot();
         root.setCenter(miniBar);
     } else {
         // entering NORMAL: restore visibility and ensure examples area is repopulated
         readingLabel.setVisible(true);
         posLabel.setVisible(true);
         examplesBox.setVisible(true);
         // descriptive button texts for normal
         btn1.setText("Again"); btn2.setText("Hard"); btn3.setText("Good"); btn4.setText("Easy");
         // rebuild normal bar fresh to restore full layout
         normalBar = buildNormalRoot();
         root.setCenter(normalBar);
         // restart examples display if we have cached items
         if (currentExamples != null && !currentExamples.isEmpty()) {
             setExamples(currentExamples);
         }
     }
     // keep front/back consistent with current side
     frontLabel.setVisible(showingFront);
     backLabel.setVisible(!showingFront);
     refreshTodayProgress();
 }

 // === Normal 布局 ===
 private Pane buildNormalRoot() {
     // 左侧控制列
     VBox left = new VBox(6, btnEdit, batchInfo);
     left.setAlignment(Pos.CENTER);
     left.setPadding(new Insets(6, 10, 6, 10));
     left.setMinWidth(60);

     // 主显示列（front/back + reading）
     VBox main = new VBox(2, frontLabel, readingLabel, backLabel);
     main.setAlignment(Pos.CENTER_LEFT);
     main.setPadding(new Insets(8, 8, 8, 8));
     HBox.setHgrow(main, Priority.ALWAYS);      // 关键：主区吃满
     frontLabel.setAlignment(Pos.CENTER_LEFT);
     backLabel.setAlignment(Pos.CENTER_LEFT);
     readingLabel.setAlignment(Pos.CENTER_LEFT);

     // 标签列（词性）
     VBox tags = new VBox(posLabel);
     tags.setAlignment(Pos.CENTER);
     tags.setPadding(new Insets(6, 10, 6, 10));

     // 例句区（先静态展示若干行）
     examplesBox.setPadding(new Insets(6, 10, 6, 10));
     examplesBox.setFillWidth(true);
    VBox examples = new VBox(new Label(" "), examplesBox); // 顶部留一点空
    examples.setPrefWidth(360);
     examples.setMinWidth(240);
     examples.setMaxWidth(480);

     // 右侧操作列（Flip + 评分）
     HBox ratings = new HBox(6, btn1, btn2, btn3, btn4);
     VBox right = new VBox(8, btnFlip, ratings);
     right.setAlignment(Pos.CENTER);
     right.setPadding(new Insets(6, 10, 6, 10));
     right.setMinWidth(220);

     // 当日进度
     VBox today = new VBox(4, todayLabel, todayProgress);
     today.setAlignment(Pos.CENTER);
     today.setPadding(new Insets(6, 10, 6, 10));

  // 整行
     HBox bar = new HBox(8, left, main, tags, examples, right, today);
     bar.setAlignment(Pos.CENTER_LEFT);
     bar.setPadding(new Insets(0, 8, 0, 8));

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

	    // Mini：只显示正/背面主文本，不显示 reading/pos/examples
	    readingLabel.setVisible(false);
	    posLabel.setVisible(false);
	    examplesBox.getChildren().clear();

	    HBox bar = new HBox(10,
	            btnEdit,
	            batchInfo,
	            leftSpring,                // 左弹簧
	            frontLabel,                // 主文本在中间
	            rightSpring,               // 右弹簧
	            btnFlip,
	            new HBox(6, btn1, btn2, btn3, btn4),
	            todayLabel
	    );
	    bar.setAlignment(Pos.CENTER);
	    bar.setPadding(new Insets(6, 10, 6, 10));

	    // 关键：让主文本尽可能居中且可被压缩
	    frontLabel.setAlignment(Pos.CENTER);
	    frontLabel.setMaxWidth(Double.MAX_VALUE);
	    HBox.setHgrow(frontLabel, Priority.SOMETIMES);

	    return bar;
	}


 public void bindStudy(StudyService study) {
     this.study = study;
 }
 
 private Region spacer() { Region r = new Region(); HBox.setHgrow(r, Priority.ALWAYS); return r; }

 // === 显示卡片内容（DTO） ===
 public void showCardView(StudyService.CardView v) {
	    this.currentCardId = v.getCardId();
	    frontLabel.setText(v.getFront() != null ? v.getFront() : "");
	    backLabel.setText(v.getBack() != null ? v.getBack() : "");
	    readingLabel.setText(v.getReading() != null ? v.getReading() : "");
	    posLabel.setText(v.getPos() != null ? v.getPos() : "");
	    setExamples(v.getExamples());

	    showingFront = true;
	    frontLabel.setVisible(true);
	    backLabel.setVisible(false);

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

    if (currentExamples.isEmpty()) return;

    // show first item
    Label first = new Label(currentExamples.get(0));
    first.getStyleClass().add("example-line");
    examplesBox.getChildren().add(first);

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
 }

 private void refreshTodayProgress() {
     int target = com.memorizer.app.Config.getInt("app.study.daily-target", 50);
     int done = 0;
     try { done = new StatsRepository().load().todayReviews; } catch (Exception ignored) {}
     todayLabel.setText("Today: " + done + "/" + target);
     todayProgress.setProgress(target > 0 ? Math.min(1.0, done / (double) target) : 0);
 }

 // === 位置/窗口标志（你在 S1 已加入，保留） ===
 private void initWindowFlags() {
     if (Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.hide-from-taskbar","true"))) {
         Stage owner = com.memorizer.app.AppContext.getOwner();
         if (owner != null) initOwner(owner);
     }
     initStyle(StageStyle.UNDECORATED);
     setAlwaysOnTop(true);
     setOpacity(Double.parseDouble(com.memorizer.app.Config.get("app.window.opacity","0.90")));
 }

 private void applyPositionForMode() {
     String mode = currentMode == UIMode.MINI ? "mini" : "normal";
     boolean overlay = Boolean.parseBoolean(com.memorizer.app.Config.get("app.window.overlay-taskbar", "false"));
     Rectangle2D vis = Screen.getPrimary().getVisualBounds();
     double screenW = vis.getWidth(), screenH = vis.getHeight(), screenX = vis.getMinX(), screenY = vis.getMinY();

     if ("mini".equalsIgnoreCase(mode)) {
         double h = com.memorizer.app.Config.getInt("app.window.mini.height", 40);
         double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.mini.width-fraction","0.5"));
         double w = Math.max(320, screenW * frac);
         setWidth(w); setHeight(h);
         setX(screenX + (screenW - w)/2.0);
         setY(screenY + screenH - h - 2);
     } else {
         double h = com.memorizer.app.Config.getInt("app.window.stealth.height", 64);
         double frac = Double.parseDouble(com.memorizer.app.Config.get("app.window.stealth.width-fraction","0.98"));
         double w = Math.max(480, screenW * frac);

         if (overlay) {
             com.memorizer.util.ScreenUtil.Rect tb = com.memorizer.util.ScreenUtil.taskbarRect();
             setX(tb.x); setY(tb.y);
             setWidth(tb.w); setHeight(Math.min(tb.h, h));
         } else {
             setWidth(w); setHeight(h);
             setX(screenX + (screenW - w)/2.0);
             setY(screenY + screenH - h - 2);
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

}
