package com.memorizer.ui;

import com.memorizer.db.DeckRepository;
import com.memorizer.model.Deck;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Simple Manage Decks window: list, rename, delete. */
public class ManageDecksStage extends Stage {
    private final DeckRepository repo = new DeckRepository();
    private final TableView<Deck> table = new TableView<>();
    private final ObservableList<Deck> data = FXCollections.observableArrayList();

    public ManageDecksStage(Stage owner) {
        initOwner(owner);
        initStyle(StageStyle.UTILITY);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Manage Decks");
        setWidth(520); setHeight(420);

        TableColumn<Deck, String> colId = new TableColumn<>("ID");
        colId.setPrefWidth(80);
        colId.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().id)));

        TableColumn<Deck, String> colName = new TableColumn<>("Name");
        colName.setPrefWidth(360);
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().name));

        table.getColumns().addAll(colId, colName);
        table.setItems(data);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button btnRefresh = new Button("Refresh");
        Button btnRename = new Button("Rename...");
        Button btnDelete = new Button("Delete");
        Button btnClose = new Button("Close");
        btnClose.setOnAction(e -> close());
        btnRefresh.setOnAction(e -> reload());
        btnRename.setOnAction(e -> renameSelected());
        btnDelete.setOnAction(e -> deleteSelected());

        HBox controls = new HBox(8, btnRefresh, btnRename, btnDelete);
        HBox right = new HBox(btnClose); HBox.setHgrow(right, Priority.ALWAYS); right.setSpacing(8);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, controls, spacer, btnClose);
        bottom.setPadding(new Insets(8));

        BorderPane root = new BorderPane();
        root.setCenter(table);
        root.setBottom(bottom);
        BorderPane.setMargin(table, new Insets(8));

        setScene(new Scene(root));
        reload();
    }

    private void reload() {
        data.clear();
        data.addAll(repo.listAll());
        if (!data.isEmpty()) table.getSelectionModel().select(0);
    }

    private void renameSelected() {
        Deck sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        TextInputDialog d = new TextInputDialog(sel.name);
        d.initOwner(this);
        d.setTitle("Rename Deck");
        d.setHeaderText(null);
        d.setContentText("New name:");
        d.showAndWait().ifPresent(nm -> {
            String name = nm == null ? "" : nm.trim();
            if (name.isEmpty() || name.equals(sel.name)) return;
            try {
                // simple rename via SQL
                try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("UPDATE deck SET name=? WHERE id=?")) {
                    ps.setString(1, name); ps.setLong(2, sel.id); ps.executeUpdate();
                }
                reload();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Rename failed: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });
    }

    private void deleteSelected() {
        Deck sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete deck '" + sel.name + "'? This will remove its cards as per FK rules.", ButtonType.YES, ButtonType.NO);
        a.initOwner(this);
        a.setHeaderText("Confirm Delete");
        a.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.YES) return;
            try {
                try (java.sql.PreparedStatement ps = com.memorizer.db.Database.get().prepareStatement("DELETE FROM deck WHERE id=?")) {
                    ps.setLong(1, sel.id); ps.executeUpdate();
                }
                reload();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, "Delete failed: " + ex.getMessage(), ButtonType.OK).showAndWait();
            }
        });
    }
}
