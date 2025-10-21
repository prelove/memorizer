package com.memorizer.ui;

import com.memorizer.db.CardRepository;
import com.memorizer.db.DeckRepository;
import com.memorizer.db.NoteRepository;
import com.memorizer.model.Deck;
import com.memorizer.model.Note;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * Factory for creating common dialogs used throughout the application.
 * Includes new deck, new entry, and user manual dialogs.
 */
public class DialogFactory {

    /**
     * Show dialog for creating a new deck.
     * @param owner parent window
     * @param noticeCallback callback for displaying notification message
     */
    public static void showNewDeckDialog(Stage owner, Consumer<String> noticeCallback) {
        Stage dialog = new Stage();
        dialog.setTitle("New Deck");

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        TextField tfName = new TextField();
        tfName.setPromptText("Deck name");

        HBox actions = new HBox(8);
        Button btnCancel = new Button("Cancel");
        Button btnSave = new Button("Create");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().addAll(spacer, btnCancel, btnSave);

        root.getChildren().addAll(new Label("Name"), tfName, actions);

        Scene scene = new Scene(root, 360, 120);
        dialog.setScene(scene);

        btnCancel.setOnAction(e -> dialog.close());

        Runnable create = () -> {
            String name = tfName.getText() == null ? "" : tfName.getText().trim();
            if (name.isEmpty()) {
                return;
            }
            try {
                new DeckRepository().getOrCreate(name);
                noticeCallback.accept("Deck created: " + name);
                dialog.close();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, 
                    "Failed to create deck: " + ex.getMessage(), 
                    ButtonType.OK).showAndWait();
            }
        };

        btnSave.setOnAction(e -> create.run());

        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ENTER) {
                create.run();
            }
            if (ev.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });

        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.showAndWait();
    }

    /**
     * Show dialog for creating a new entry (note + card).
     * @param owner parent window
     * @param noticeCallback callback for displaying notification message
     */
    public static void showNewEntryDialog(Stage owner, Consumer<String> noticeCallback) {
        Stage dialog = new Stage();
        dialog.setTitle("New Entry");

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));

        // Deck selection
        ComboBox<Deck> deckBox = new ComboBox<>();
        deckBox.setConverter(new javafx.util.StringConverter<Deck>() {
            @Override
            public String toString(Deck deck) {
                return deck == null ? "(No deck)" : deck.name;
            }

            @Override
            public Deck fromString(String s) {
                return null;
            }
        });

        List<Deck> decks = new DeckRepository().listAll();
        deckBox.getItems().setAll(decks);
        if (!decks.isEmpty()) {
            deckBox.getSelectionModel().select(0);
        }

        // Input fields
        TextField tfFront = new TextField();
        tfFront.setPromptText("Front");

        TextField tfReading = new TextField();
        tfReading.setPromptText("Reading");

        TextField tfPos = new TextField();
        tfPos.setPromptText("Pos");

        TextArea taBack = new TextArea();
        taBack.setPromptText("Back");
        taBack.setPrefRowCount(3);

        TextArea taExamples = new TextArea();
        taExamples.setPromptText("Examples");
        taExamples.setPrefRowCount(3);

        TextField tfTags = new TextField();
        tfTags.setPromptText("Tags");

        // Form layout
        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);

        int row = 0;
        form.add(new Label("Deck"), 0, row);
        form.add(deckBox, 1, row++);

        form.add(new Label("Front"), 0, row);
        form.add(tfFront, 1, row++);

        form.add(new Label("Reading/Pos"), 0, row);
        HBox readingPosBox = new HBox(8, tfReading, tfPos);
        HBox.setHgrow(tfReading, Priority.ALWAYS);
        form.add(readingPosBox, 1, row++);

        form.add(new Label("Back"), 0, row);
        form.add(taBack, 1, row++);

        form.add(new Label("Examples"), 0, row);
        form.add(taExamples, 1, row++);

        form.add(new Label("Tags"), 0, row);
        form.add(tfTags, 1, row++);

        // Column constraints
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(110);
        col0.setPrefWidth(120);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);

        form.getColumnConstraints().addAll(col0, col1);

        // Action buttons
        HBox actions = new HBox(8);
        Button btnCancel = new Button("Cancel");
        Button btnSave = new Button("Save");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.getChildren().addAll(spacer, btnCancel, btnSave);

        root.getChildren().addAll(form, actions);

        Scene scene = new Scene(root, 520, 360);
        dialog.setScene(scene);

        btnCancel.setOnAction(e -> dialog.close());

        Runnable save = () -> {
            Note note = new Note();
            Deck selectedDeck = deckBox.getValue();
            note.deckId = selectedDeck == null ? null : selectedDeck.id;
            note.front = tfFront.getText();
            note.back = taBack.getText();
            note.reading = tfReading.getText();
            note.pos = tfPos.getText();
            note.examples = taExamples.getText();
            note.tags = tfTags.getText();

            try {
                long noteId = new NoteRepository().insert(note);
                new CardRepository().insertForNote(noteId);
                
                String deckName = selectedDeck != null ? selectedDeck.name : "";
                noticeCallback.accept("Entry saved" + 
                    (selectedDeck != null ? " to " + deckName : ""));
                dialog.close();
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR, 
                    "Save failed: " + ex.getMessage(), 
                    ButtonType.OK).showAndWait();
            }
        };

        btnSave.setOnAction(e -> save.run());

        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
            if ((ev.getCode() == KeyCode.S || ev.getCode() == KeyCode.ENTER) 
                && ev.isControlDown()) {
                save.run();
            }
        });

        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.showAndWait();
    }

    /**
     * Show user manual dialog.
     * @param owner parent window
     */
    public static void showUserManual(Stage owner) {
        String text = loadUserManualText();

        TextArea textArea = new TextArea(text);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setPrefColumnCount(80);
        textArea.setPrefRowCount(24);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("User Manual");
        dialog.getDialogPane().setContent(textArea);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.initOwner(owner);
        dialog.showAndWait();
    }

    /**
     * Load user manual text from resources.
     * @return manual text content
     */
    private static String loadUserManualText() {
        try (InputStream is = DialogFactory.class.getResourceAsStream("/USER_MANUAL.txt")) {
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                return new String(baos.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }

        // Fallback manual text
        return buildFallbackManual();
    }

    /**
     * Build fallback manual text when resource file is not available.
     * @return fallback manual content
     */
    private static String buildFallbackManual() {
        return "Memorizer User Manual\n\n" +
               "- Stealth Banner: Normal/Mini modes (T to toggle theme, M to toggle mode).\n" +
               "- Flip cycle: Front → Back → Front+Back+Reading/Pos+Examples → Front.\n" +
               "- Rating: Again/Hard/Good/Easy (1/2/3/4).\n" +
               "- Progress: Today target bar with overlay text.\n" +
               "- Decks: View → Deck to filter; Data → New Deck/Entry to create.\n" +
               "- Shortcuts: SPACE/ENTER flip, F8 toggle banner, ESC hide.\n";
    }
}
