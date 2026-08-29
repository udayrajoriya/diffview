package com.comparetool.ui;

import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Selection bar for the main shell — lets the user choose left and right paths, then
 * click <em>Compare</em> to start a comparison.
 *
 * <h3>Enable/disable logic</h3>
 * <p>The Compare button is enabled only when both text fields contain non-empty text.
 * Actual path validation (existence, type matching) happens on Compare click.
 *
 * <h3>Type-mismatch messaging</h3>
 * <p>If the user points one side at a file and the other at a directory (or either path
 * does not exist), a red error label is shown beneath the text fields.
 *
 * <h3>Drag-and-drop</h3>
 * <p>Both text fields accept file / directory drops from the OS shell or another panel.
 * Dropping a path pre-fills the field.
 */
public class SelectionBar extends VBox {

    // ── controls ──────────────────────────────────────────────────────────────
    private final TextField leftField    = new TextField();
    private final TextField rightField   = new TextField();
    private final Button    leftBrowse   = new Button("…");
    private final Button    rightBrowse  = new Button("…");
    private final Button    compareButton = new Button("Compare");
    private final Label     messageLabel  = new Label();

    // ── mode ──────────────────────────────────────────────────────────────────
    /** When {@code true} the browse buttons always open a {@link DirectoryChooser}. */
    private boolean folderMode = false;

    // ── callback ──────────────────────────────────────────────────────────────
    private Consumer<CompareRequest> onCompare;
    /** F5 scene-level handler — fires Compare when both fields are populated (REQ-015.1). */
    private final EventHandler<KeyEvent> f5Handler = e -> {
        if (e.getCode() == KeyCode.F5 && !compareButton.isDisabled()) {
            handleCompare();
            e.consume();
        }
    };
    // ── constructor ───────────────────────────────────────────────────────────

    public SelectionBar() {
        // Assign IDs for TestFX lookup and CSS targeting
        leftField.setId("leftField");
        leftField.setPromptText("Left path…");
        leftField.setAccessibleText("Left comparison path");
        rightField.setId("rightField");
        rightField.setPromptText("Right path\u2026");
        rightField.setAccessibleText("Right comparison path");
        leftBrowse.setId("leftBrowse");
        leftBrowse.setAccessibleText("Browse for left file or folder");
        rightBrowse.setId("rightBrowse");
        rightBrowse.setAccessibleText("Browse for right file or folder");
        compareButton.setId("compareButton");
        compareButton.setAccessibleText("Compare the two selected paths (F5)");
        messageLabel.setId("messageLabel");

        // Compare button enabled iff both fields are non-empty
        compareButton.disableProperty().bind(
                Bindings.isEmpty(leftField.textProperty())
                        .or(Bindings.isEmpty(rightField.textProperty())));

        // Style the message label
        messageLabel.setStyle("-fx-text-fill: #cc0000;");
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);

        // Layout: two rows
        HBox.setHgrow(leftField,  Priority.ALWAYS);
        HBox.setHgrow(rightField, Priority.ALWAYS);

        HBox topRow = new HBox(6,
                leftField, leftBrowse,
                rightField, rightBrowse,
                compareButton);
        topRow.setAlignment(Pos.CENTER_LEFT);

        setSpacing(4);
        setPadding(new Insets(8));
        getChildren().addAll(topRow, messageLabel);

        // Wire actions
        leftBrowse.setOnAction(e -> browse(true));
        rightBrowse.setOnAction(e -> browse(false));
        compareButton.setOnAction(e -> handleCompare());

        // F5 = Compare shortcut: active whenever SelectionBar is in a scene (REQ-015.1)
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, f5Handler);
            if (newScene != null) newScene.addEventFilter(KeyEvent.KEY_PRESSED, f5Handler);
        });

        // Drag-and-drop
        setupDragAndDrop(leftField,  true);
        setupDragAndDrop(rightField, false);
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Forces the browse buttons to always open a {@link DirectoryChooser}.
     * Pass {@code true} when this bar is being used for a folder comparison tab.
     */
    public void setFolderMode(boolean folderMode) {
        this.folderMode = folderMode;
    }

    /** Registers the callback invoked when the user clicks Compare with valid, matching paths. */
    public void setOnCompare(Consumer<CompareRequest> handler) {
        this.onCompare = handler;
    }

    /** Returns the left path text field. */
    public TextField getLeftField()    { return leftField;  }

    /** Returns the right path text field. */
    public TextField getRightField()   { return rightField; }

    /** Returns the Compare button. */
    public Button getCompareButton()   { return compareButton; }

    /** Returns the message label (error / info text). */
    public Label getMessageLabel()     { return messageLabel; }

    // ── package-private for testing ───────────────────────────────────────────

    /**
     * Sets the path in the specified field.  Called internally by the drag-and-drop
     * handler, and also by tests to simulate a file drop without constructing a full
     * {@code DragEvent}.
     *
     * @param left {@code true} to set the left field; {@code false} for the right field
     * @param path the path string to populate
     */
    void setPathFromDrop(boolean left, String path) {
        (left ? leftField : rightField).setText(path);
        clearMessage();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void handleCompare() {
        String leftText  = leftField.getText().trim();
        String rightText = rightField.getText().trim();
        if (leftText.isEmpty() || rightText.isEmpty()) return;

        Path left;
        Path right;
        try {
            left  = Path.of(leftText);
            right = Path.of(rightText);
        } catch (Exception ex) {
            showMessage("Invalid path: " + ex.getMessage());
            return;
        }

        if (!Files.exists(left)) {
            showMessage("Left path does not exist: " + left);
            return;
        }
        if (!Files.exists(right)) {
            showMessage("Right path does not exist: " + right);
            return;
        }

        boolean leftIsDir  = Files.isDirectory(left);
        boolean rightIsDir = Files.isDirectory(right);

        if (leftIsDir != rightIsDir) {
            showMessage("Type mismatch: cannot compare a "
                    + (leftIsDir ? "folder" : "file") + " with a "
                    + (rightIsDir ? "folder" : "file") + ".");
            return;
        }

        clearMessage();
        if (onCompare != null) {
            onCompare.accept(new CompareRequest(left, right, leftIsDir));
        }
    }

    private void browse(boolean left) {
        Path currentPath = parsePath(left ? leftField.getText() : rightField.getText());
        Path otherPath   = parsePath(left ? rightField.getText() : leftField.getText());

        // Use DirectoryChooser if: folderMode is set, or the current field already
        // holds a directory, or the other field already holds a directory.
        boolean useDirectory = folderMode
                || (currentPath != null && Files.isDirectory(currentPath))
                || (currentPath == null && otherPath != null && Files.isDirectory(otherPath));

        File result;
        if (useDirectory) {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle(left ? "Select left folder" : "Select right folder");
            if (currentPath != null && Files.isDirectory(currentPath)) {
                dc.setInitialDirectory(currentPath.toFile());
            } else if (otherPath != null && Files.isDirectory(otherPath)) {
                dc.setInitialDirectory(otherPath.toFile());
            }
            result = dc.showDialog(getScene() != null ? getScene().getWindow() : null);
        } else {
            FileChooser fc = new FileChooser();
            fc.setTitle(left ? "Select left file" : "Select right file");
            if (currentPath != null && currentPath.getParent() != null) {
                fc.setInitialDirectory(currentPath.getParent().toFile());
            }
            result = fc.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        }
        if (result != null) {
            setPathFromDrop(left, result.getAbsolutePath());
        }
    }

    private void setupDragAndDrop(TextField field, boolean isLeft) {
        field.setOnDragOver(event -> {
            if (event.getGestureSource() != field && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY, TransferMode.LINK);
            }
            event.consume();
        });
        field.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    setPathFromDrop(isLeft, files.get(0).getAbsolutePath());
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void showMessage(String msg) {
        messageLabel.setText(msg);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void clearMessage() {
        messageLabel.setText("");
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }

    private static Path parsePath(String text) {
        if (text == null || text.isBlank()) return null;
        try { return Path.of(text.trim()); } catch (Exception e) { return null; }
    }
}
