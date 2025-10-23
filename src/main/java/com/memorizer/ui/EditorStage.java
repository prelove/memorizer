package com.memorizer.ui;

import com.memorizer.db.NoteRepository;
import com.memorizer.model.Note;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** Simple note editor window (modal utility) to edit front/back/reading/pos/examples/tags. */
public class EditorStage extends Stage {
    private final NoteRepository notes = new NoteRepository();
    private long cardId;
    private Note note;

    private TextField tfFront = new TextField();
    private TextArea taBack = new TextArea();
    private TextField tfReading = new TextField();
    private TextField tfPos = new TextField();
    private TextArea taExamples = new TextArea();
    private TextField tfTags = new TextField();

    public interface OnSaved { void handle(Note saved); }
    private OnSaved onSaved;

    public EditorStage() {
        initStyle(StageStyle.UTILITY);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Edit Note");
        setWidth(600); setHeight(480);

        // 设置窗口图标
        try {
            java.net.URL iconUrl = getClass().getResource("/icon.png");
            if (iconUrl != null) {
                javafx.scene.image.Image icon = new javafx.scene.image.Image(iconUrl.toString());
                getIcons().add(icon);
            }
        } catch (Exception e) {
            // 记录日志但不中断
            System.out.println("Failed to load editor stage icon: " + e.getMessage());
        }

        GridPane g = new GridPane();
        g.setPadding(new Insets(12));
        g.setHgap(10); g.setVgap(8);

        // 设置列约束：第一列（标签）使用固定宽度，第二列（输入控件）占据剩余空间
        javafx.scene.layout.ColumnConstraints labelCol = new javafx.scene.layout.ColumnConstraints();
        labelCol.setMinWidth(120); // 设置标签列最小宽度
        labelCol.setPrefWidth(120); // 设置标签列首选宽度
        javafx.scene.layout.ColumnConstraints inputCol = new javafx.scene.layout.ColumnConstraints();
        inputCol.setHgrow(javafx.scene.layout.Priority.ALWAYS); // 输入列占据剩余空间
        g.getColumnConstraints().addAll(labelCol, inputCol);

        int r = 0;
        g.add(new Label("Front:"), 0, r); g.add(tfFront, 1, r++);
        g.add(new Label("Back:"), 0, r); taBack.setPrefRowCount(5); g.add(taBack, 1, r++);
        g.add(new Label("Reading:"), 0, r); g.add(tfReading, 1, r++);
        g.add(new Label("Part of Speech:"), 0, r); g.add(tfPos, 1, r++);
        g.add(new Label("Examples:"), 0, r); taExamples.setPrefRowCount(5); g.add(taExamples, 1, r++);
        g.add(new Label("Tags:"), 0, r); g.add(tfTags, 1, r++);

        Button btnSave = new Button("Save");
        Button btnCancel = new Button("Cancel");
        btnSave.setOnAction(e -> save());
        btnCancel.setOnAction(e -> close());
        HBox buttons = new HBox(8, btnSave, btnCancel);
        g.add(buttons, 1, r);

        setScene(new Scene(g));
    }

    public void setOnSaved(OnSaved cb) { this.onSaved = cb; }

    public void loadByCardId(long cardId) {
        this.cardId = cardId;
        this.note = notes.findByCardId(cardId).orElse(null);
        if (note == null) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Note not found for card #" + cardId, ButtonType.OK);
            a.initOwner(this);
            a.showAndWait();
            close(); return;
        }
        tfFront.setText(note.front == null? "" : note.front);
        taBack.setText(note.back == null? "" : note.back);
        tfReading.setText(note.reading == null? "" : note.reading);
        tfPos.setText(note.pos == null? "" : note.pos);
        taExamples.setText(note.examples == null? "" : note.examples);
        tfTags.setText(note.tags == null? "" : note.tags);
    }

    private void save() {
        if (note == null) return;
        note.front = tfFront.getText();
        note.back = taBack.getText();
        note.reading = tfReading.getText();
        note.pos = tfPos.getText();
        note.examples = taExamples.getText();
        note.tags = tfTags.getText();
        new NoteRepository().update(note);
        if (onSaved != null) onSaved.handle(note);
        close();
    }
}

