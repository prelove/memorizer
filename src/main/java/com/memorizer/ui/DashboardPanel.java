package com.memorizer.ui;

import com.memorizer.app.Config;
import com.memorizer.app.Scheduler;
import com.memorizer.db.ChartRepository;
import com.memorizer.db.StatsRepository;
import com.memorizer.service.PlanService;
import com.memorizer.service.StudyService;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.control.ProgressBar;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard panel displaying statistics and sync server controls.
 * Shows card counts, review stats, plan progress, and mobile sync options.
 */
public class DashboardPanel {
    private final StudyService studyService;
    private final Scheduler scheduler;
    
    // Statistics labels
    private final Label lblDue = new Label("-");
    private final Label lblNew = new Label("-");
    private final Label lblTotalCards = new Label("-");
    private final Label lblTotalNotes = new Label("-");
    private final Label lblTodayReviews = new Label("-");
    private final Label lblPlanPending = new Label("-");
    private final Label lblPlanDone = new Label("-");
    private final Label lblPlanTotal = new Label("-");
    
    // Chart components
    private BarChart<String, Number> dailyReviewsChart;
    private BarChart<String, Number> ratingDistributionChart;
    private BarChart<String, Number> cardStatusChart;
    
    // Task progress indicator (spheres grid)
    private GridPane taskProgressContainer;
    private HBox taskProgressRow;
    private HBox taskProgressBarRow;
    private final ProgressBar taskProgressBar = new ProgressBar(0);
    private final Label taskProgressText = new Label("0/0");
    private final VBox taskProgressStatusStack = new VBox(1);
    private final VBox taskProgressDeckStack = new VBox(1);
    private final VBox taskProgressStatusStackBar = new VBox(1);
    private final VBox taskProgressDeckStackBar = new VBox(1);
    private final Label taskProgressTitleCount = new Label("");
    private javafx.scene.control.TableView<HistoryRow> historyTable;
    private java.time.LocalDate selectedHistoryDate;

    private static class HistoryRow {
        java.time.LocalDate date;
        int done;
        int target;
        boolean completed;
        boolean inStreak;
    }

    public DashboardPanel(StudyService studyService, Scheduler scheduler) {
        this.studyService = studyService;
        this.scheduler = scheduler;
    }

    /**
     * Build the dashboard UI pane.
     * @return the constructed dashboard pane
     */
    public Pane build() {
        // Create main container with horizontal layout
        HBox mainContainer = new HBox(20);
        mainContainer.setPadding(new Insets(16));
        
        // Left side: Summary statistics (fixed width)
        VBox summarySection = createSummarySection();
        summarySection.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 8px; -fx-padding: 15px;");
        
        // Right side: Charts
        VBox chartsSection = createChartsSection();
        
        // Add sections to main container
        mainContainer.getChildren().addAll(summarySection, chartsSection);
        
        // Set fixed width for summary (prevent squeeze) and make charts flexible
        HBox.setHgrow(summarySection, javafx.scene.layout.Priority.NEVER);
        HBox.setHgrow(chartsSection, javafx.scene.layout.Priority.ALWAYS);
        summarySection.setMinWidth(320);
        summarySection.setPrefWidth(320);
        
        return mainContainer;
    }
    
    /**
     * Create summary statistics section.
     * @return VBox containing summary statistics
     */
    private VBox createSummarySection() {
        VBox summaryContainer = new VBox(8);
        summaryContainer.setPadding(new Insets(10));
        
        // Add title with right-aligned refresh
        Label titleLabel = new Label("Summary Statistics");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        Button btnRefresh = new Button("Refresh (F5)");
        btnRefresh.getStyleClass().addAll("btn", "btn-primary");
        btnRefresh.setOnAction(e -> refresh());
        btnRefresh.setPrefWidth(120);
        HBox titleRow = new HBox(10, titleLabel);
        Region tSpacer = new Region(); HBox.setHgrow(tSpacer, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().addAll(tSpacer, btnRefresh);
        summaryContainer.getChildren().add(titleRow);
        
        // Create grid for statistics
        GridPane statsGrid = new GridPane();
        statsGrid.setHgap(10);
        statsGrid.setVgap(6);
        
        // Add statistics rows
        int row = 0;
        addStatRow(statsGrid, "Due:", lblDue, row++);
        addStatRow(statsGrid, "New:", lblNew, row++);
        addStatRow(statsGrid, "Total Cards:", lblTotalCards, row++);
        addStatRow(statsGrid, "Total Notes:", lblTotalNotes, row++);
        addStatRow(statsGrid, "Today's Reviews:", lblTodayReviews, row++);
        addStatRow(statsGrid, "Plan Pending:", lblPlanPending, row++);
        addStatRow(statsGrid, "Plan Done:", lblPlanDone, row++);
        addStatRow(statsGrid, "Plan Total:", lblPlanTotal, row++);
        
        summaryContainer.getChildren().add(statsGrid);
        
        // Recent progress history
        Label historyTitle = new Label("Recent Progress (14 days)");
        historyTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        historyTable = buildHistoryTable();
        
        summaryContainer.getChildren().addAll(historyTitle, historyTable);
        VBox.setVgrow(historyTable, javafx.scene.layout.Priority.ALWAYS);
        
        return summaryContainer;
    }
    
    /**
     * Add a statistic row to the grid.
     */
    private void addStatRow(GridPane grid, String label, Label valueLabel, int row) {
        grid.add(new Label(label), 0, row);
        grid.add(valueLabel, 1, row);
        valueLabel.setStyle("-fx-font-weight: bold;");
    }

    private javafx.scene.control.TableView<HistoryRow> buildHistoryTable() {
        javafx.scene.control.TableView<HistoryRow> tv = new javafx.scene.control.TableView<>();
        tv.setPrefHeight(220);
        tv.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);

        javafx.scene.control.TableColumn<HistoryRow, String> cDate = new javafx.scene.control.TableColumn<>("Date");
        cDate.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().date.toString()));
        cDate.setPrefWidth(100);

        javafx.scene.control.TableColumn<HistoryRow, String> cDone = new javafx.scene.control.TableColumn<>("Done?");
        cDone.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().completed ? "Yes" : (cd.getValue().done > 0 ? "Partial" : "No")));
        cDone.setPrefWidth(80);

        javafx.scene.control.TableColumn<HistoryRow, String> cProg = new javafx.scene.control.TableColumn<>("Progress");
        cProg.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().done + "/" + cd.getValue().target));
        cProg.setStyle("-fx-alignment: CENTER-RIGHT;");

        tv.getColumns().addAll(cDate, cDone, cProg);

        tv.setRowFactory(table -> new javafx.scene.control.TableRow<HistoryRow>() {
            @Override protected void updateItem(HistoryRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setStyle(""); return; }
                String bg;
                if (item.inStreak && item.completed) bg = "#fff3cd"; // soft yellow
                else if (item.completed) bg = "#d4edda";              // soft green
                else if (item.done > 0) bg = "#ffe8cc";                // soft orange
                else bg = "#f1f3f5";                                   // soft gray
                setStyle("-fx-background-color: " + bg + ";");
            }
        });

        // Optional: click to filter daily chart to that day
        tv.setOnMouseClicked(ev -> {
            HistoryRow r = tv.getSelectionModel().getSelectedItem();
            if (r != null) {
                selectedHistoryDate = r.date;
                updateCharts();
            }
        });
        return tv;
    }
    
    /**
     * Create charts section with daily reviews, rating distribution, and card status charts.
     * @return VBox containing all charts
     */
    private VBox createChartsSection() {
        // Initialize charts
        initializeCharts();
        
        // Create a container for all charts
        VBox chartsContainer = new VBox(15);
        
        // Add chart titles and charts
        Label dailyReviewsTitle = new Label("Daily Reviews (Last 7 Days)");
        dailyReviewsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        chartsContainer.getChildren().addAll(dailyReviewsTitle, dailyReviewsChart);
        
        Label ratingDistributionTitle = new Label("Rating Distribution");
        ratingDistributionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        chartsContainer.getChildren().addAll(ratingDistributionTitle, ratingDistributionChart);
        
        Label cardStatusTitle = new Label("Card Status Distribution");
        cardStatusTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        chartsContainer.getChildren().addAll(cardStatusTitle, cardStatusChart);
        
        // Add task progress indicator
        Label taskProgressTitle = new Label("Today's Task Progress");
        taskProgressTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        taskProgressTitleCount.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #333;");
        HBox titleRow = new HBox(8, taskProgressTitle);
        javafx.scene.layout.Region titleSpacer = new javafx.scene.layout.Region();
        HBox.setHgrow(titleSpacer, javafx.scene.layout.Priority.ALWAYS);
        titleRow.getChildren().addAll(titleSpacer, taskProgressTitleCount);
        Label taskProgressLegend = new Label("Legend: Done (green) • Pending (orange) • Queued (gray). Hover or click items.");
        taskProgressLegend.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        taskProgressContainer = new GridPane();
        taskProgressContainer.setHgap(8);
        taskProgressContainer.setVgap(8);
        taskProgressContainer.setAlignment(Pos.CENTER_LEFT);
        // 20 columns, evenly spaced
        for (int i = 0; i < 20; i++) {
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setPercentWidth(5.0);
            taskProgressContainer.getColumnConstraints().add(cc);
        }
        taskProgressText.setStyle("-fx-font-weight: bold;");
        // Mini stacked bar showing proportions (done/pending/queued)
        taskProgressStatusStack.setPrefWidth(10);
        taskProgressStatusStack.setMaxHeight(22);
        taskProgressStatusStack.setAlignment(Pos.BOTTOM_CENTER);
        taskProgressDeckStack.setPrefWidth(10);
        taskProgressDeckStack.setMaxHeight(22);
        taskProgressDeckStack.setAlignment(Pos.BOTTOM_CENTER);
        taskProgressStatusStackBar.setPrefWidth(10);
        taskProgressStatusStackBar.setMaxHeight(22);
        taskProgressStatusStackBar.setAlignment(Pos.BOTTOM_CENTER);
        taskProgressDeckStackBar.setPrefWidth(10);
        taskProgressDeckStackBar.setMaxHeight(22);
        taskProgressDeckStackBar.setAlignment(Pos.BOTTOM_CENTER);
        // Row for small totals: indicators + "x/y" + mini stack
        HBox miniStacksRow = new HBox(4, taskProgressStatusStack, taskProgressDeckStack);
        taskProgressRow = new HBox(8, taskProgressContainer, taskProgressText, miniStacksRow);
        taskProgressRow.setAlignment(Pos.CENTER_LEFT);

        // Fallback progress bar row for large totals
        taskProgressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(taskProgressBar, javafx.scene.layout.Priority.ALWAYS);
        HBox miniStacksBar = new HBox(4, taskProgressStatusStackBar, taskProgressDeckStackBar);
        taskProgressBarRow = new HBox(8, taskProgressBar, taskProgressText, miniStacksBar);
        taskProgressBarRow.setAlignment(Pos.CENTER_LEFT);
        taskProgressBarRow.setVisible(false);
        taskProgressBarRow.setManaged(false);

        chartsContainer.getChildren().addAll(titleRow, taskProgressLegend, taskProgressRow, taskProgressBarRow);
        
        return chartsContainer;
    }
    
    /**
     * Initialize all charts with basic configuration.
     */
    private void initializeCharts() {
        // Daily reviews chart (Bar chart)
        CategoryAxis xAxis1 = new CategoryAxis();
        NumberAxis yAxis1 = new NumberAxis();
        dailyReviewsChart = new BarChart<>(xAxis1, yAxis1);
        dailyReviewsChart.setPrefHeight(180);
        dailyReviewsChart.setLegendVisible(false);
        dailyReviewsChart.setTitle("Daily Reviews");
        
        // Rating distribution chart (Bar chart)
        CategoryAxis xAxis2 = new CategoryAxis();
        NumberAxis yAxis2 = new NumberAxis();
        ratingDistributionChart = new BarChart<>(xAxis2, yAxis2);
        ratingDistributionChart.setPrefHeight(180);
        ratingDistributionChart.setLegendVisible(false);
        ratingDistributionChart.setTitle("Rating Distribution");
        
        // Card status chart (Bar chart)
        CategoryAxis xAxis3 = new CategoryAxis();
        NumberAxis yAxis3 = new NumberAxis();
        cardStatusChart = new BarChart<>(xAxis3, yAxis3);
        cardStatusChart.setPrefHeight(180);
        cardStatusChart.setLegendVisible(false);
        cardStatusChart.setTitle("Card Status Distribution");
    }
    
    /**
     * Update all charts with fresh data.
     */
    private void updateCharts() {
        ChartRepository chartRepo = new ChartRepository();
        
        // Update daily reviews chart
        updateDailyReviewsChart(chartRepo);
        
        // Update rating distribution chart
        updateRatingDistributionChart(chartRepo);
        
        // Update card status chart
        updateCardStatusChart(chartRepo);
    }
    
    /**
     * Update daily reviews chart with data from the last 7 days.
     */
    private void updateDailyReviewsChart(ChartRepository chartRepo) {
        // Clear existing data
        dailyReviewsChart.getData().clear();
        
        // Create data series
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Reviews");
        
        // Get data for last 7 days
        List<ChartRepository.DailyReviewCount> data = chartRepo.getDailyReviewCounts(7);

        // Add data to series (optionally filter to selected date)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
        for (ChartRepository.DailyReviewCount item : data) {
            if (selectedHistoryDate == null || selectedHistoryDate.equals(item.date)) {
                String dateLabel = item.date.format(formatter);
                series.getData().add(new XYChart.Data<>(dateLabel, item.count));
            }
        }
        
        // Add series to chart then style once node is created
        dailyReviewsChart.getData().add(series);
        if (series.getNode() != null) {
            series.getNode().setStyle("-fx-bar-fill: #3fb950;");
        } else {
            series.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) newNode.setStyle("-fx-bar-fill: #3fb950;");
            });
        }
    }
    
    /**
     * Update rating distribution chart.
     */
    private void updateRatingDistributionChart(ChartRepository chartRepo) {
        // Clear existing data
        ratingDistributionChart.getData().clear();
        
        // Create data series
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Ratings");
        
        // Get rating distribution data
        List<ChartRepository.RatingDistribution> data = chartRepo.getRatingDistribution();
        
        // Add data to series (rating 1=Again, 2=Hard, 3=Good, 4=Easy)
        String[] ratingLabels = {"Again", "Hard", "Good", "Easy"};
        for (ChartRepository.RatingDistribution item : data) {
            if (item.rating >= 1 && item.rating <= 4) {
                series.getData().add(new XYChart.Data<>(ratingLabels[item.rating - 1], item.count));
            }
        }
        
        // Add series to chart then style once node is created
        ratingDistributionChart.getData().add(series);
        if (series.getNode() != null) {
            series.getNode().setStyle("-fx-bar-fill: #8a2be2;");
        } else {
            series.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) newNode.setStyle("-fx-bar-fill: #8a2be2;");
            });
        }
    }
    
    /**
     * Update card status chart.
     */
    private void updateCardStatusChart(ChartRepository chartRepo) {
        // Clear existing data
        cardStatusChart.getData().clear();
        
        // Create data series
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Status");
        
        // Get card status distribution data
        List<ChartRepository.CardStatusDistribution> data = chartRepo.getCardStatusDistribution();
        
        // Add data to series
        for (ChartRepository.CardStatusDistribution item : data) {
            series.getData().add(new XYChart.Data<>(item.status, item.count));
        }
        
        // Add series to chart then style once node is created
        cardStatusChart.getData().add(series);
        if (series.getNode() != null) {
            series.getNode().setStyle("-fx-bar-fill: #4285f4;");
        } else {
            series.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) newNode.setStyle("-fx-bar-fill: #4285f4;");
            });
        }
    }
    
    /**
     * Update task progress indicator with today's plan.
     */
    private void updateTaskProgressIndicator() {
        if (taskProgressContainer == null) return;
        // Clear existing indicators
        taskProgressContainer.getChildren().clear();
        
        // Get today's plan
        PlanService.Counts planCounts = studyService.planCounts();
        int totalTasks = Math.max(0, planCounts.total);
        int doneTasks = Math.max(0, planCounts.done);
        int pendingTasks = Math.max(0, planCounts.pending);

        // Simplified: always show spheres (no bar fallback)
        taskProgressBarRow.setVisible(false);
        taskProgressBarRow.setManaged(false);
        taskProgressContainer.setVisible(true);
        taskProgressContainer.setManaged(true);

        // Build indicators using today's plan list for richer hover info
        java.util.List<com.memorizer.service.PlanService.PlanRow> rows = studyService.planListToday();
        taskProgressText.setText(doneTasks + "/" + totalTasks);
        taskProgressTitleCount.setText(doneTasks + "/" + totalTasks);
        int dotRadius = com.memorizer.app.Config.getInt("app.ui.progress.dot-radius", 8);
        int perRow = Math.max(5, com.memorizer.app.Config.getInt("app.ui.progress.per-row", 20));

        // Rebuild column constraints based on per-row capacity
        taskProgressContainer.getColumnConstraints().clear();
        for (int i = 0; i < perRow; i++) {
            javafx.scene.layout.ColumnConstraints cc = new javafx.scene.layout.ColumnConstraints();
            cc.setPercentWidth(100.0 / perRow);
            taskProgressContainer.getColumnConstraints().add(cc);
        }
        taskProgressContainer.getChildren().clear();

        for (int i = 0; i < rows.size(); i++) {
            com.memorizer.service.PlanService.PlanRow row = rows.get(i);
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle();
            double radius = Math.max(3.0, (double) dotRadius);
            dot.setRadius(radius);

            int st = row.getStatus();
            if (st == 1) dot.setFill(javafx.scene.paint.Color.web("#4CAF50"));
            else if (st == 0) dot.setFill(javafx.scene.paint.Color.web("#FF9800"));
            else dot.setFill(javafx.scene.paint.Color.web("#BDBDBD"));

            long cardId = row.getCardId();
            String base = "Deck: " + safe(row.getDeckName()) + "\n" + "Front: " + summarize(row.getFront()) + "\n(click to open)";
            Tooltip tp = new Tooltip(base);
            // Lazy-enrich tooltip on first hover
            dot.setOnMouseEntered(e -> enrichTooltipAsync(tp, cardId));
            dot.setOnMouseClicked(e -> openStudyForCard(cardId));
            Tooltip.install(dot, tp);
            int col = i % perRow;
            int r = i / perRow;
            GridPane.setColumnIndex(dot, col);
            GridPane.setRowIndex(dot, r);
            taskProgressContainer.getChildren().add(dot);
        }
        taskProgressRow.setVisible(true);
        taskProgressRow.setManaged(true);
        taskProgressBarRow.setVisible(false);
        taskProgressBarRow.setManaged(false);
        // Hide stacked bars in simplified view
        for (javafx.scene.Node n : taskProgressRow.getChildren()) {
            // ensure row remains visible but stacks may be empty; keep as-is
        }
    }

    // ---- Helpers for progress hover ----
    private static String safe(String s) { return s == null ? "" : s; }

    private void enrichTooltipAsync(Tooltip tp, long cardId) {
        // Avoid blocking UI: fetch details on a background thread, then apply to tooltip
        new Thread(() -> {
            String text = buildTooltipText(cardId);
            if (text == null || text.trim().isEmpty()) return;
            javafx.application.Platform.runLater(() -> tp.setText(text));
        }, "tp-enrich").start();
    }

    private String buildTooltipText(long cardId) {
        StringBuilder sb = new StringBuilder();
        try {
            java.util.Optional<com.memorizer.model.Note> on = new com.memorizer.db.NoteRepository().findByCardId(cardId);
            if (on.isPresent()) {
                com.memorizer.model.Note n = on.get();
                String deck = "";
                try { if (n.deckId != null) deck = new com.memorizer.db.DeckRepository().findNameById(n.deckId); } catch (Exception ignored) {}
                if (!deck.isEmpty()) sb.append("Deck: ").append(deck).append('\n');
                sb.append("Front: ").append(summarize(n.front)).append('\n');
                if (n.reading != null && !n.reading.trim().isEmpty()) sb.append("Reading: ").append(n.reading).append('\n');
                if (n.pos != null && !n.pos.trim().isEmpty()) sb.append("POS: ").append(n.pos).append('\n');
            }
        } catch (Exception ignored) {}

        // Mastery status (previous -> current)
        String mastery = masteryLine(cardId);
        if (!mastery.isEmpty()) sb.append(mastery).append('\n');

        // Recent review logs
        java.util.List<String> recents = recentReviewsOf(cardId, 5);
        if (!recents.isEmpty()) {
            sb.append("Reviews:\n");
            for (String r : recents) sb.append("  • ").append(r).append('\n');
        }
        return sb.toString().trim();
    }

    private void openStudyForCard(long cardId) {
        try {
            com.memorizer.ui.MainStage main = com.memorizer.app.AppContext.getMain();
            if (main != null) {
                javafx.application.Platform.runLater(() -> main.showCardInStudy(cardId));
            }
        } catch (Exception ignored) {}
    }

    private String summarize(String s) {
        if (s == null) return "";
        String t = s.replace('\n',' ').trim();
        if (t.length() <= 120) return t;
        return t.substring(0, 117) + "...";
    }

    private java.util.List<String> recentReviewsOf(long cardId, int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        String sql = "SELECT reviewed_at, rating, latency_ms FROM review_log WHERE card_id=? ORDER BY reviewed_at DESC LIMIT ?";
        try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
            ps.setLong(1, cardId);
            ps.setInt(2, Math.max(1, limit));
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Timestamp ts = rs.getTimestamp(1);
                    int rating = rs.getInt(2);
                    Object latObj = rs.getObject(3);
                    Integer lat = (latObj == null ? null : ((Number)latObj).intValue());
                    String tsStr = (ts == null ? "" : new java.text.SimpleDateFormat("MM-dd HH:mm").format(ts));
                    String r = tsStr + "  rating=" + rating + (lat == null ? "" : ("  " + lat + "ms"));
                    out.add(r);
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    private String masteryLine(long cardId) {
        double curr = masteryFor(cardId);
        Double prev = lastPrevMastery(cardId);
        String currPct = String.format("%.0f%%", curr * 100.0);
        if (prev == null) return "Mastery: " + currPct;
        String prevPct = String.format("%.0f%%", prev * 100.0);
        return "Mastery: " + prevPct + " → " + currPct;
    }

    private Double lastPrevMastery(long cardId) {
        String sql = "SELECT prev_interval FROM review_log WHERE card_id=? ORDER BY reviewed_at DESC LIMIT 1";
        try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
            ps.setLong(1, cardId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object ivl = rs.getObject(1);
                    if (ivl == null) return null;
                    double d = ((Number)ivl).doubleValue();
                    return normalizeInterval(d);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private double masteryFor(long cardId) {
        String sql = "SELECT interval_days, ease, reps, status FROM card WHERE id=?";
        try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement(sql)) {
            ps.setLong(1, cardId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Object ivlObj = rs.getObject(1);
                    Double ivl = (ivlObj == null ? null : ((Number)ivlObj).doubleValue());
                    double ease = rs.getDouble(2);
                    int reps = rs.getInt(3);
                    int status = rs.getInt(4);
                    double mIvl = (ivl == null ? 0.1 : normalizeInterval(ivl));
                    double mReps = clamp01(reps / 10.0);
                    double mEase = clamp01((ease - 1.3) / (3.0 - 1.3));
                    double base = 0.15 + 0.55 * mIvl + 0.2 * mReps + 0.1 * mEase;
                    if (status == 0) base = Math.min(base, 0.6); // new cards shouldn't be shown as 100%
                    return clamp01(base);
                }
            }
        } catch (Exception ignored) {}
        return 0.2;
    }

    private double normalizeInterval(double days) {
        // Normalize interval to ~0..1 over a 30-day window (tunable)
        return clamp01(days / 30.0);
    }

    private double clamp01(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    /** Update the mini stacked bars (done/pending/queued) for both rows. */
    private void updateStatusStacks(int done, int pending, int queued) {
        int total = Math.max(1, done + pending + queued);
        taskProgressStatusStack.getChildren().clear();
        taskProgressStatusStackBar.getChildren().clear();
        // Heights proportional to counts (cap minimum for visibility)
        double maxH = 22.0;
        double hDone = Math.max(2.0, maxH * (done / (double) total));
        double hPend = Math.max(2.0, maxH * (pending / (double) total));
        double hQueue = Math.max(2.0, maxH * (queued / (double) total));
        Region rDone = new Region(); rDone.setPrefSize(10, hDone); rDone.setStyle("-fx-background-color:#4CAF50;");
        Region rPend = new Region(); rPend.setPrefSize(10, hPend); rPend.setStyle("-fx-background-color:#FF9800;");
        Region rQueue = new Region(); rQueue.setPrefSize(10, hQueue); rQueue.setStyle("-fx-background-color:#CCCCCC;");
        Tooltip.install(rDone, new Tooltip("Done: " + done));
        Tooltip.install(rPend, new Tooltip("Pending: " + pending));
        Tooltip.install(rQueue, new Tooltip("Queued: " + queued));
        taskProgressStatusStack.getChildren().addAll(rQueue, rPend, rDone);
        Region rDone2 = new Region(); rDone2.setPrefSize(10, hDone); rDone2.setStyle("-fx-background-color:#4CAF50;");
        Region rPend2 = new Region(); rPend2.setPrefSize(10, hPend); rPend2.setStyle("-fx-background-color:#FF9800;");
        Region rQueue2 = new Region(); rQueue2.setPrefSize(10, hQueue); rQueue2.setStyle("-fx-background-color:#CCCCCC;");
        Tooltip.install(rDone2, new Tooltip("Done: " + done));
        Tooltip.install(rPend2, new Tooltip("Pending: " + pending));
        Tooltip.install(rQueue2, new Tooltip("Queued: " + queued));
        taskProgressStatusStackBar.getChildren().addAll(rQueue2, rPend2, rDone2);
    }

    /** Update the deck composition stacked bars using per-deck counts and stable colors. */
    private void updateDeckStacks() {
        taskProgressDeckStack.getChildren().clear();
        taskProgressDeckStackBar.getChildren().clear();
        java.util.List<com.memorizer.service.PlanService.DeckShare> shares = new com.memorizer.service.PlanService().deckSharesToday();
        int total = shares.stream().mapToInt(s -> s.count).sum();
        if (total <= 0) return;
        double maxH = 22.0;
        for (int i = shares.size() - 1; i >= 0; i--) { // bottom-up
            com.memorizer.service.PlanService.DeckShare s = shares.get(i);
            double h = Math.max(2.0, maxH * (s.count / (double) total));
            Region r = new Region();
            r.setPrefSize(10, h);
            r.setStyle("-fx-background-color:" + deckColorFor(s.deckName) + ";");
            Tooltip.install(r, new Tooltip(s.deckName + ": " + s.count));
            taskProgressDeckStack.getChildren().add(r);
            Region r2 = new Region();
            r2.setPrefSize(10, h);
            r2.setStyle("-fx-background-color:" + deckColorFor(s.deckName) + ";");
            Tooltip.install(r2, new Tooltip(s.deckName + ": " + s.count));
            taskProgressDeckStackBar.getChildren().add(r2);
        }
    }

    /** Map deck name to a stable, visually distinct color. */
    private String deckColorFor(String name) {
        if (name == null) name = "(No Deck)";
        int hash = Math.abs(name.hashCode());
        // Pick from a small palette to keep colors consistent and high-contrast
        String[] palette = new String[]{
                "#1f77b4", // blue
                "#ff7f0e", // orange
                "#2ca02c", // green
                "#d62728", // red
                "#9467bd", // purple
                "#8c564b", // brown
                "#e377c2", // pink
                "#17becf"  // cyan
        };
        return palette[hash % palette.length];
    }

    /**
     * Refresh all dashboard statistics and charts.
     */
    public void refresh() {
        // Load statistics from repository
        StatsRepository.Stats stats = new StatsRepository().load();
        lblDue.setText(String.valueOf(stats.dueCount));
        lblNew.setText(String.valueOf(stats.newCount));
        lblTotalCards.setText(String.valueOf(stats.totalCards));
        lblTotalNotes.setText(String.valueOf(stats.totalNotes));
        lblTodayReviews.setText(String.valueOf(stats.todayReviews));
        
        // Load plan counts
        PlanService.Counts planCounts = studyService.planCounts();
        lblPlanPending.setText(String.valueOf(planCounts.pending));
        lblPlanDone.setText(String.valueOf(planCounts.done));
        lblPlanTotal.setText(String.valueOf(planCounts.total));
        
        // Update charts
        updateCharts();
        
        // Update task progress indicator
        updateTaskProgressIndicator();

        // Update history table
        updateHistoryTable();
    }

    private void updateHistoryTable() {
        if (historyTable == null) return;
        int days = 14;
        java.time.LocalDate today = java.time.LocalDate.now();
        java.util.Map<java.time.LocalDate, Integer> doneMap = new java.util.HashMap<>();
        java.util.List<com.memorizer.db.ChartRepository.DailyReviewCount> list = new com.memorizer.db.ChartRepository().getDailyReviewCounts(days);
        for (com.memorizer.db.ChartRepository.DailyReviewCount d : list) doneMap.put(d.date, d.count);

        int target = com.memorizer.app.Config.getInt("app.study.daily-target", 50);
        java.util.List<HistoryRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < days; i++) {
            java.time.LocalDate d = today.minusDays(i);
            HistoryRow r = new HistoryRow();
            r.date = d;
            r.done = doneMap.getOrDefault(d, 0);
            r.target = target;
            r.completed = target > 0 && r.done >= target;
            rows.add(r);
        }
        for (int i = 0; i < rows.size(); i++) {
            HistoryRow r = rows.get(i);
            boolean prevCompleted = (i + 1 < rows.size()) && rows.get(i + 1).completed; // reverse chronological order
            boolean nextCompleted = (i - 1 >= 0) && rows.get(i - 1).completed;
            r.inStreak = r.completed && (prevCompleted || nextCompleted);
        }
        javafx.collections.ObservableList<HistoryRow> data = javafx.collections.FXCollections.observableArrayList(rows);
        historyTable.setItems(data);
    }

    /**
     * Get scheduler status label text.
     */
    public String getSchedulerStatus() {
        String mode = Config.get("app.study.scheduler.mode", "due");
        String modeLabel = "due".equalsIgnoreCase(mode) ? "Due" : "Periodic";
        return (scheduler.isPaused() ? "Paused" : "Running") + " (" + modeLabel + ")";
    }
}
