package com.memorizer.ui;

import com.memorizer.app.AppContext;
import com.memorizer.app.Config;
import com.memorizer.service.PlanService;
import com.memorizer.service.StudyService;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plan panel displaying today's study plan in a paginated table.
 * Users can view, sort, and interact with planned cards.
 */
public class PlanPanel {
    private final StudyService studyService;
    
    // Table and data
    private TableView<PlanService.PlanRow> planTable;
    private final ObservableList<PlanService.PlanRow> planRows = FXCollections.observableArrayList();
    private List<PlanService.PlanRow> fullPlan = new ArrayList<>();
    
    // Table columns
    private TableColumn<PlanService.PlanRow, Integer> colOrder;
    private TableColumn<PlanService.PlanRow, String> colKind;
    private TableColumn<PlanService.PlanRow, String> colStatus;
    private TableColumn<PlanService.PlanRow, String> colDeck;
    private TableColumn<PlanService.PlanRow, String> colFront;
    
    // Pagination
    private Pagination planPagination;
    private int planPageSize;

    public PlanPanel(StudyService studyService) {
        this.studyService = studyService;
        this.planPageSize = Config.getInt("app.ui.plan.page-size", 25);
    }

    /**
     * Build the plan panel UI.
     * @return the constructed plan pane
     */
    public Pane build() {
        BorderPane root = new BorderPane();
        
        // Build table
        planTable = new TableView<>(planRows);
        buildTableColumns();
        setupTableBehavior();
        
        // Control buttons
        Button btnRefresh = new Button("Refresh");
        btnRefresh.getStyleClass().addAll("btn", "btn-primary");
        btnRefresh.setOnAction(e -> reload());
        
        Button btnPrev = new Button("Prev");
        Button btnNext = new Button("Next");
        btnPrev.getStyleClass().addAll("btn", "btn-default");
        btnNext.getStyleClass().addAll("btn", "btn-default");
        btnPrev.setOnAction(e -> changePage(-1));
        btnNext.setOnAction(e -> changePage(1));
        
        // Pagination control
        planPagination = new Pagination(1, 0);
        planPagination.currentPageIndexProperty().addListener((obs, ov, nv) -> 
            applyPage(nv.intValue()));
        
        // Layout
        HBox topBar = new HBox(8, btnRefresh, btnPrev, btnNext);
        topBar.setPadding(new Insets(8));
        
        root.setTop(topBar);
        root.setCenter(planTable);
        root.setBottom(new HBox(8, planPagination));
        
        // Load initial data
        reload();
        
        return root;
    }

    /**
     * Build all table columns with proper cell factories.
     */
    private void buildTableColumns() {
        // Order column
        colOrder = new TableColumn<>("Order");
        colOrder.setCellValueFactory(new PropertyValueFactory<>("orderNo"));
        colOrder.setPrefWidth(60);

        // Kind column with color coding
        colKind = new TableColumn<>("Kind");
        colKind.setCellValueFactory(c -> {
            int kind = c.getValue().getKind();
            String text = kind == 0 ? "DUE" : 
                         kind == 1 ? "LEECH" : 
                         kind == 2 ? "NEW" : 
                         kind == 3 ? "CHAL" : 
                         String.valueOf(kind);
            return javafx.beans.property.SimpleStringProperty.stringExpression(
                new javafx.beans.property.SimpleStringProperty(text));
        });
        colKind.setPrefWidth(70);
        colKind.setCellFactory(col -> new TableCell<PlanService.PlanRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                javafx.scene.control.TableRow<PlanService.PlanRow> tableRow = getTableRow();
                PlanService.PlanRow row = tableRow == null ? null : tableRow.getItem();
                if (row == null) {
                    setStyle("");
                    return;
                }
                String color = getKindColor(row.getKind());
                setStyle("-fx-text-fill: " + color + ";");
            }
        });

        // Status column
        colStatus = new TableColumn<>("Status");
        colStatus.setCellValueFactory(c -> {
            int status = c.getValue().getStatus();
            String text = status == 0 ? "PENDING" : 
                         status == 1 ? "DONE" : 
                         status == 2 ? "ROLLED" : 
                         status == 3 ? "SKIPPED" : 
                         String.valueOf(status);
            return javafx.beans.property.SimpleStringProperty.stringExpression(
                new javafx.beans.property.SimpleStringProperty(text));
        });
        colStatus.setPrefWidth(90);

        // Deck column
        colDeck = new TableColumn<>("Deck");
        colDeck.setCellValueFactory(new PropertyValueFactory<>("deckName"));
        colDeck.setPrefWidth(140);

        // Front column with color coding
        colFront = new TableColumn<>("Front");
        colFront.setCellValueFactory(new PropertyValueFactory<>("front"));
        colFront.setPrefWidth(420);
        colFront.setCellFactory(col -> new TableCell<PlanService.PlanRow, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                javafx.scene.control.TableRow<PlanService.PlanRow> tableRow = getTableRow();
                PlanService.PlanRow row = tableRow == null ? null : tableRow.getItem();
                if (row == null) {
                    setStyle("");
                    return;
                }
                String color = getKindColor(row.getKind());
                setStyle("-fx-text-fill: " + color + ";");
            }
        });

        planTable.getColumns().addAll(colOrder, colKind, colStatus, colDeck, colFront);
    }

    /**
     * Get color for card kind.
     */
    private String getKindColor(int kind) {
        return kind == 0 ? "#d7e8ff" :  // DUE - blue
               kind == 1 ? "#ffd0d0" :  // LEECH - red
               kind == 2 ? "#c7f3e6" :  // NEW - green
               "#ead6ff";               // CHALLENGE - purple
    }

    /**
     * Setup table sorting and row interaction behavior.
     */
    private void setupTableBehavior() {
        // Custom sort policy to sort full dataset
        planTable.setSortPolicy(tv -> {
            applyPage(planPagination == null ? 0 : planPagination.getCurrentPageIndex());
            return true;
        });

        // Double-click to show card in stealth banner
        planTable.setRowFactory(tv -> {
            TableRow<PlanService.PlanRow> row = new TableRow<>();
            
            row.setOnMouseClicked(ev -> {
                if (ev.getClickCount() == 2 && !row.isEmpty()) {
                    showCardInBanner(row.getItem());
                }
            });
            
            // Context menu for marking done/skipped
            ContextMenu contextMenu = createRowContextMenu(row);
            row.contextMenuProperty().bind(
                javafx.beans.binding.Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );
            
            return row;
        });
    }

    /**
     * Create context menu for table rows.
     */
    private ContextMenu createRowContextMenu(TableRow<PlanService.PlanRow> row) {
        ContextMenu menu = new ContextMenu();
        
        MenuItem miDone = new MenuItem("Mark Done");
        miDone.setOnAction(e -> {
            if (!row.isEmpty()) {
                markCardDone(row.getItem().getCardId());
            }
        });
        
        MenuItem miSkip = new MenuItem("Skip");
        miSkip.setOnAction(e -> {
            if (!row.isEmpty()) {
                markCardSkipped(row.getItem().getCardId());
            }
        });
        
        menu.getItems().addAll(miDone, miSkip);
        return menu;
    }

    /**
     * Show card in stealth banner window.
     */
    private void showCardInBanner(PlanService.PlanRow planRow) {
        java.util.Optional<StudyService.CardView> cardView = 
            studyService.viewCardById(planRow.getCardId());
        
        if (cardView.isPresent()) {
            com.memorizer.ui.StealthStage stealth = AppContext.getStealth();
            if (stealth != null) {
                int batchSize = Config.getInt("app.study.batch-size", 1);
                stealth.startBatch(batchSize);
                stealth.showCardView(cardView.get());
                stealth.showAndFocus();
            }
        }
    }

    /**
     * Mark a card as done in the plan.
     */
    private void markCardDone(long cardId) {
        try {
            new PlanService().markDone(cardId);
            reload();
            updateTrayTooltip();
        } catch (Exception ignored) {
        }
    }

    /**
     * Mark a card as skipped in the plan.
     */
    private void markCardSkipped(long cardId) {
        try {
            new PlanService().markSkipped(cardId);
            reload();
            updateTrayTooltip();
        } catch (Exception ignored) {
        }
    }

    /**
     * Update system tray tooltip with plan counts.
     */
    private void updateTrayTooltip() {
        try {
            com.memorizer.app.TrayManager tray = AppContext.getTray();
            if (tray != null) {
                tray.updatePlanTooltip();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Reload plan data from service.
     */
    public void reload() {
        fullPlan = studyService.planListToday();
        int pageCount = Math.max(1, (int) Math.ceil(fullPlan.size() / (double) planPageSize));
        planPagination.setPageCount(pageCount);
        applyPage(0);
    }

    /**
     * Apply a specific page of data to the table.
     */
    private void applyPage(int pageIndex) {
        if (pageIndex < 0) {
            pageIndex = 0;
        }
        
        int pageCount = Math.max(1, (int) Math.ceil(fullPlan.size() / (double) planPageSize));
        if (pageIndex >= pageCount) {
            pageIndex = pageCount - 1;
        }
        
        if (planPagination != null) {
            planPagination.setCurrentPageIndex(pageIndex);
        }
        
        // Apply sorting to full plan
        applySorting();
        
        // Extract page data
        int fromIndex = pageIndex * planPageSize;
        int toIndex = Math.min(fullPlan.size(), fromIndex + planPageSize);
        planRows.setAll(fullPlan.subList(fromIndex, toIndex));
    }

    /**
     * Apply current table sort order to full plan.
     */
    private void applySorting() {
        List<TableColumn<PlanService.PlanRow, ?>> sortOrder = 
            new ArrayList<>(planTable.getSortOrder());
        
        if (sortOrder.isEmpty()) {
            return;
        }
        
        Comparator<PlanService.PlanRow> comparator = null;
        
        for (TableColumn<PlanService.PlanRow, ?> column : sortOrder) {
            Comparator<PlanService.PlanRow> columnComparator = getColumnComparator(column);
            
            if (columnComparator != null) {
                if (column.getSortType() == TableColumn.SortType.DESCENDING) {
                    columnComparator = columnComparator.reversed();
                }
                comparator = (comparator == null) ? columnComparator : 
                            comparator.thenComparing(columnComparator);
            }
        }
        
        if (comparator != null) {
            fullPlan.sort(comparator);
        }
    }

    /**
     * Get comparator for a specific column.
     */
    private Comparator<PlanService.PlanRow> getColumnComparator(
            TableColumn<PlanService.PlanRow, ?> column) {
        
        if (column == colOrder) {
            return Comparator.comparingInt(PlanService.PlanRow::getOrderNo);
        } else if (column == colKind) {
            return Comparator.comparingInt(PlanService.PlanRow::getKind);
        } else if (column == colStatus) {
            return Comparator.comparingInt(PlanService.PlanRow::getStatus);
        } else if (column == colDeck) {
            return Comparator.comparing(r -> r.getDeckName() == null ? "" : r.getDeckName());
        } else if (column == colFront) {
            return Comparator.comparing(r -> r.getFront() == null ? "" : r.getFront());
        }
        
        return null;
    }

    /**
     * Change page by delta (e.g., +1 for next, -1 for previous).
     */
    private void changePage(int delta) {
        int newIndex = planPagination.getCurrentPageIndex() + delta;
        applyPage(newIndex);
    }
}
