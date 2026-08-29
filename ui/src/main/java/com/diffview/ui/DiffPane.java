package com.comparetool.ui;

import com.comparetool.model.DiffRow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A single pane (left or right) in the side-by-side file comparison view.
 *
 * <p>Structure (task 12.4 extended):
 * <pre>
 * VBox (DiffPane)
 * ├── HBox  titleBar      — filename + dirty indicator "● filename"
 * └── StackPane content
 *     ├── ListView        — virtualized diff rows (default / read mode)
 *     └── TextArea        — in-place text editor (edit mode)
 * </pre>
 *
 * <h3>Edit mode (REQ-005)</h3>
 * <p>Call {@link #setEditMode(boolean) setEditMode(true)} to switch the pane from the
 * virtualized list to a {@link TextArea}.  The TextArea is pre-populated with the
 * plain-text content of the current rows (skipping placeholder rows).  Call
 * {@link #setEditMode(boolean) setEditMode(false)} to switch back; the caller is
 * responsible for reading {@link #getEditContent()} and pushing it to the ViewModel
 * <em>before</em> calling {@code setEditMode(false)}.
 *
 * <h3>Virtualization</h3>
 * <p>The ListView uses JavaFX's internal {@code VirtualFlow} so that only the visible
 * rows are realized as scene-graph nodes.  A fixed cell size is set to give VirtualFlow
 * the information it needs to avoid measuring every cell individually.
 */
public class DiffPane extends VBox {

    /** Fixed height of each diff row in pixels. */
    static final double CELL_HEIGHT = 24.0;

    private final boolean          isLeft;
    private final ListView<DiffRow> listView  = new ListView<>();
    private final Label             titleLabel;
    private final TextArea          editArea;

    private String  baseTitle;
    private boolean editMode  = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public DiffPane(boolean isLeft) {
        this.isLeft    = isLeft;
        this.baseTitle = isLeft ? "Left" : "Right";

        // ── ListView ──
        listView.setId(isLeft ? "leftDiffList" : "rightDiffList");
        listView.setFixedCellSize(CELL_HEIGHT);      // critical for VirtualFlow perf
        listView.setCellFactory(lv -> new DiffLineCell(isLeft));

        // ── Edit TextArea ──
        editArea = new TextArea();
        editArea.setId(isLeft ? "leftEditArea" : "rightEditArea");
        editArea.setWrapText(false);
        editArea.setVisible(false);
        editArea.setManaged(false);

        // ── Content pane: ListView and TextArea overlaid ──
        StackPane content = new StackPane(listView, editArea);
        VBox.setVgrow(content, Priority.ALWAYS);

        // ── Title bar ──
        titleLabel = new Label(baseTitle);
        titleLabel.setId(isLeft ? "leftPaneTitle" : "rightPaneTitle");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-padding: 2 6 2 6;");

        HBox titleBar = new HBox(titleLabel);
        titleBar.setId(isLeft ? "leftPaneHeader" : "rightPaneHeader");
        titleBar.setStyle("-fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");

        getChildren().addAll(titleBar, content);
    }

    // ── Data binding ──────────────────────────────────────────────────────────

    /**
     * Replaces the displayed rows.  Both panes should receive the same list;
     * each cell renders only its own side.
     */
    public void setRows(List<DiffRow> rows) {
        listView.setItems(FXCollections.observableArrayList(rows));
        if (editMode) {
            editArea.setText(buildEditText(listView.getItems()));
        }
    }

    // ── Title / dirty indicator (REQ-005) ─────────────────────────────────────

    /**
     * Sets the base title shown in the pane header (e.g. a filename).
     */
    public void setTitle(String title) {
        this.baseTitle = title != null ? title : (isLeft ? "Left" : "Right");
        titleLabel.setText(baseTitle);
    }

    /**
     * Shows or hides the unsaved-changes indicator ("● ") in the pane title (REQ-005).
     */
    public void setDirty(boolean dirty) {
        titleLabel.setText(dirty ? "\u25cf " + baseTitle : baseTitle);
    }

    // ── Edit mode (REQ-005) ───────────────────────────────────────────────────

    /**
     * Switches between read-only ListView mode and editable TextArea mode.
     *
     * <p>When switching to edit mode the TextArea is populated from the current
     * list content.  When leaving edit mode, the caller should read
     * {@link #getEditContent()} and push the changes to the ViewModel
     * <em>before</em> calling this method with {@code false}.
     */
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
        listView.setVisible(!editMode);
        listView.setManaged(!editMode);
        editArea.setVisible(editMode);
        editArea.setManaged(editMode);
        if (editMode) {
            editArea.setText(buildEditText(listView.getItems()));
            editArea.requestFocus();
        }
    }

    /** Returns {@code true} when the pane is in text-editing mode. */
    public boolean isEditMode() { return editMode; }

    /**
     * Returns the current text in the edit TextArea.  Read this before calling
     * {@link #setEditMode(boolean) setEditMode(false)} to apply edits.
     */
    public String getEditContent() { return editArea.getText(); }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the items currently shown in this pane. */
    public ObservableList<DiffRow> getItems()  { return listView.getItems(); }

    /** Returns the underlying {@link ListView} (used for scroll-binding in task 12.2). */
    public ListView<DiffRow> getListView()     { return listView; }

    /** Returns the edit {@link TextArea} (visible only in edit mode). */
    public TextArea getEditArea()              { return editArea; }

    /** {@code true} if this pane renders the left side of each diff row. */
    public boolean isLeft()                    { return isLeft; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildEditText(ObservableList<DiffRow> items) {
        if (items == null || items.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (DiffRow row : items) {
            if (isLeft) {
                if (!row.isLeftPlaceholder())  sb.append(row.leftText()).append('\n');
            } else {
                if (!row.isRightPlaceholder()) sb.append(row.rightText()).append('\n');
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '\n') sb.deleteCharAt(len - 1);
        return sb.toString();
    }
}
