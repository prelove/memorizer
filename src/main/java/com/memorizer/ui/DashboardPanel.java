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
    
    // Task progress indicator
    private HBox taskProgressContainer;

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
        
        // Left side: Summary statistics
        VBox summarySection = createSummarySection();
        summarySection.setStyle("-fx-background-color: #f0f0f0; -fx-background-radius: 8px; -fx-padding: 15px;");
        
        // Right side: Charts
        VBox chartsSection = createChartsSection();
        
        // Add sections to main container
        mainContainer.getChildren().addAll(summarySection, chartsSection);
        
        // Set proportional widths (40% for summary, 60% for charts)
        HBox.setHgrow(summarySection, javafx.scene.layout.Priority.NEVER);
        HBox.setHgrow(chartsSection, javafx.scene.layout.Priority.ALWAYS);
        summarySection.setPrefWidth(300);
        
        return mainContainer;
    }
    
    /**
     * Create summary statistics section.
     * @return VBox containing summary statistics
     */
    private VBox createSummarySection() {
        VBox summaryContainer = new VBox(8);
        summaryContainer.setPadding(new Insets(10));
        
        // Add title
        Label titleLabel = new Label("Summary Statistics");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        summaryContainer.getChildren().add(titleLabel);
        
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
        
        // Add refresh button
        Button btnRefresh = new Button("Refresh (F5)");
        btnRefresh.getStyleClass().addAll("btn", "btn-primary");
        btnRefresh.setOnAction(e -> refresh());
        btnRefresh.setPrefWidth(120);
        
        HBox buttonContainer = new HBox();
        buttonContainer.getChildren().add(btnRefresh);
        buttonContainer.setPadding(new Insets(10, 0, 0, 0));
        
        summaryContainer.getChildren().add(buttonContainer);
        
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
        taskProgressContainer = new HBox(2);
        taskProgressContainer.setPrefHeight(30);
        chartsContainer.getChildren().addAll(taskProgressTitle, taskProgressContainer);
        
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
        
        // Add data to series
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd");
        for (ChartRepository.DailyReviewCount item : data) {
            String dateLabel = item.date.format(formatter);
            series.getData().add(new XYChart.Data<>(dateLabel, item.count));
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
        // Clear existing indicators
        taskProgressContainer.getChildren().clear();
        
        // Get today's plan
        PlanService.Counts planCounts = studyService.planCounts();
        int totalTasks = planCounts.total;
        int doneTasks = planCounts.done;
        int pendingTasks = planCounts.pending;
        
        // Create progress indicators
        for (int i = 0; i < totalTasks; i++) {
            Region indicator = new Region();
            indicator.setPrefSize(10, 20);
            indicator.setStyle("-fx-background-color: #cccccc; -fx-background-radius: 2;");
            
            // Color coding:
            // Gray - Not started
            // Green - Done
            // Orange - Pending/Current
            if (i < doneTasks) {
                indicator.setStyle("-fx-background-color: #4CAF50; -fx-background-radius: 2;"); // Green
            } else if (i < doneTasks + pendingTasks) {
                indicator.setStyle("-fx-background-color: #FF9800; -fx-background-radius: 2;"); // Orange
            }
            
            taskProgressContainer.getChildren().add(indicator);
        }
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
