package com.diffview.ui;

import com.diffview.model.DiffBlock;
import com.diffview.model.DiffModel;
import com.diffview.model.MergeDirection;
import com.diffview.viewmodel.FileComparisonViewModel;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Side-by-side file comparison view (task 12.1).
 *
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │  [⇅ Sync Scroll ▣]   toolbar (top)                           │
 * ├──────────────────────────┬─────────────────────────────────────┤
 * │  DiffPane (left)         │   DiffPane (right)                  │
 * │  VirtualFlow ListView    │   VirtualFlow ListView              │
 * └──────────────────────────┴─────────────────────────────────────┘
 *                    "Files are identical" overlay (StackPane center)
 * </pre>
 *
 * <h3>Usage</h3>
 * <ol>
 *   <li>Create the view and add it to the scene.</li>
 *   <li>Call {@link #setModel(DiffModel)} to display a comparison result.</li>
 *   <li>The left and right {@link DiffPane}s are populated with the same
 *       {@link com.diffview.model.DiffRow} list; each cell renders its own
 *       side (left or right).</li>
 *   <li>When {@link DiffModel#identical()} is {@code true}, an overlay label
 *       is shown in the center of the view.</li>
 * </ol>
 *
 * <p>Synchronized scrolling and navigation controls are added in tasks 12.2
 * and 12.3.  ViewModel wiring happens in task 17.x.
 */
public class FileComparisonView extends BorderPane {

    // ── Controls ──────────────────────────────────────────────────────────────
    private final DiffPane          leftPane          = new DiffPane(true);
    private final DiffPane          rightPane         = new DiffPane(false);
    private final Label             identicalLabel    = new Label("Files are identical");
    private final Label             diffCountLabel    = new Label("0 / 0");
    private final ToggleButton      syncScrollButton;
    private final Button            prevButton;
    private final Button            nextButton;
    private final StackPane         contentStack;
    private final ScrollSyncManager scrollSyncManager;
    private final DiffNavigator     navigator         = new DiffNavigator();

    // ── Merge toolbar controls (task 12.4) ───────────────────────────────────────
    private final Button       copyBlockLToR   = new Button("Block →");
    private final Button       copyBlockRToL   = new Button("← Block");
    private final Button       copyAllLToR     = new Button("All →");
    private final Button       copyAllRToL     = new Button("← All");
    private final Button       undoButton      = new Button("↩ Undo");
    private final Button       redoButton      = new Button("Redo ↪");
    private final ToggleButton editToggleBtn   = new ToggleButton("Edit");
    private final Button       saveLeftBtn     = new Button("Save L");
    private final Button       saveRightBtn    = new Button("Save R");
    private final Button       saveAllBtn      = new Button("Save All");

    // ── Options toolbar (task 12.5) ──────────────────────────────────────────────
    private final OptionsBar optionsBar = new OptionsBar();

    /** Bound ViewModel; {@code null} until {@link #bindViewModel} is called. */
    private FileComparisonViewModel boundViewModel;

    /** Keyboard handler — stored so it can be removed when the view leaves a scene. */
    private final EventHandler<KeyEvent> keyHandler = e -> handleKeyEvent(e);

    // ── Constructor ───────────────────────────────────────────────────────────

    public FileComparisonView() {
        VBox.setVgrow(leftPane,  Priority.ALWAYS);
        VBox.setVgrow(rightPane, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setId("diffSplitPane");
        splitPane.setDividerPositions(0.5);

        // "Files are identical" indicator — overlaid in the center, hidden by default
        identicalLabel.setId("identicalLabel");
        identicalLabel.getStyleClass().add("identical-indicator");
        identicalLabel.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; "
                + "-fx-text-fill: #2e7d32; "
                + "-fx-background-color: rgba(232,245,233,0.92); "
                + "-fx-background-radius: 6; "
                + "-fx-padding: 12 28 12 28; "
                + "-fx-border-color: #a5d6a7; -fx-border-radius: 6; -fx-border-width: 1;");
        identicalLabel.setVisible(false);
        identicalLabel.setManaged(false);

        contentStack = new StackPane(splitPane, identicalLabel);
        StackPane.setAlignment(identicalLabel, Pos.CENTER);

        // ── Navigation toolbar ────────────────────────────────────────────────
        prevButton = new Button("\u2190 Prev");
        prevButton.setId("prevDiffButton");
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> navigatePrevious());

        nextButton = new Button("Next \u2192");
        nextButton.setId("nextDiffButton");
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> navigateNext());

        diffCountLabel.setId("diffCountLabel");
        diffCountLabel.setMinWidth(64);
        diffCountLabel.setAlignment(Pos.CENTER);

        // Wire navigator observable state → counter label + button enable/disable
        navigator.currentIndexProperty().addListener((obs, o, n) -> updateNavState());
        navigator.blockCountProperty().addListener((obs, o, n) -> updateNavState());

        syncScrollButton = new ToggleButton("\u21c5 Sync Scroll");
        syncScrollButton.setId("syncScrollButton");
        syncScrollButton.setSelected(true);       // synced by default

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(prevButton, diffCountLabel, nextButton, spacer, syncScrollButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 8, 4, 8));
        toolbar.setSpacing(8);

        // ── Scroll sync ───────────────────────────────────────────────────────
        scrollSyncManager = new ScrollSyncManager(
                leftPane.getListView(), rightPane.getListView(), true);

        syncScrollButton.selectedProperty().addListener(
                (obs, wasSelected, isSelected) -> scrollSyncManager.setSynced(isSelected));

        // ── Keyboard shortcuts: F7 = next diff, Shift+F7 = prev diff (REQ-003) ─
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
            }
        });

        // ── Merge toolbar (task 12.4) ───────────────────────────────────────────
        // All merge controls start disabled — enabled when a ViewModel is bound
        copyBlockLToR.setId("copyBlockLToRButton");   copyBlockLToR.setDisable(true);
        copyBlockRToL.setId("copyBlockRToLButton");   copyBlockRToL.setDisable(true);
        copyAllLToR  .setId("copyAllLToRButton");     copyAllLToR  .setDisable(true);
        copyAllRToL  .setId("copyAllRToLButton");     copyAllRToL  .setDisable(true);
        undoButton   .setId("undoButton");            undoButton   .setDisable(true);
        redoButton   .setId("redoButton");            redoButton   .setDisable(true);
        editToggleBtn.setId("editToggleButton");      editToggleBtn.setDisable(true);
        saveLeftBtn  .setId("saveLeftButton");        saveLeftBtn  .setDisable(true);
        saveRightBtn .setId("saveRightButton");       saveRightBtn .setDisable(true);
        saveAllBtn   .setId("saveAllButton");         saveAllBtn   .setDisable(true);

        // ── Accessible names (REQ-015.3) ──────────────────────────────────────
        prevButton   .setAccessibleText("Previous difference (Shift+F7)");
        nextButton   .setAccessibleText("Next difference (F7)");
        syncScrollButton.setAccessibleText("Synchronise scrolling");
        copyBlockLToR.setAccessibleText("Copy current block left to right (Ctrl+Right)");
        copyBlockRToL.setAccessibleText("Copy current block right to left (Ctrl+Left)");
        copyAllLToR  .setAccessibleText("Copy all changes left to right (Ctrl+Shift+Right)");
        copyAllRToL  .setAccessibleText("Copy all changes right to left (Ctrl+Shift+Left)");
        undoButton   .setAccessibleText("Undo last merge (Ctrl+Z)");
        redoButton   .setAccessibleText("Redo last undone merge (Ctrl+Y)");
        editToggleBtn.setAccessibleText("Toggle edit mode");
        saveLeftBtn  .setAccessibleText("Save left document");
        saveRightBtn .setAccessibleText("Save right document");
        saveAllBtn   .setAccessibleText("Save both documents (Ctrl+S)");

        // Edit toggle: switch both panes in/out of edit mode
        editToggleBtn.selectedProperty().addListener((obs, wasOn, isOn) -> {
            if (!isOn && boundViewModel != null) {
                // Leaving edit mode — push edits to ViewModel before switching back
                List<String> leftLines  = Arrays.asList(
                        leftPane.getEditContent().split("\n", -1));
                List<String> rightLines = Arrays.asList(
                        rightPane.getEditContent().split("\n", -1));
                boundViewModel.editDocument(true,  leftLines);
                boundViewModel.editDocument(false, rightLines);
            }
            leftPane .setEditMode(isOn);
            rightPane.setEditMode(isOn);
        });

        HBox mergeToolbar = new HBox(
                copyBlockRToL, copyBlockLToR,
                new Separator(Orientation.VERTICAL),
                copyAllRToL, copyAllLToR,
                new Separator(Orientation.VERTICAL),
                undoButton, redoButton,
                new Separator(Orientation.VERTICAL),
                editToggleBtn,
                new Separator(Orientation.VERTICAL),
                saveLeftBtn, saveRightBtn, saveAllBtn);
        mergeToolbar.setAlignment(Pos.CENTER_LEFT);
        mergeToolbar.setPadding(new Insets(4, 8, 4, 8));
        mergeToolbar.setSpacing(6);

        setTop(new VBox(toolbar, mergeToolbar, optionsBar));
        setCenter(contentStack);
    }

    // ── Model binding ─────────────────────────────────────────────────────────

    /**
     * Populates both diff panes from the given {@link DiffModel} and shows or
     * hides the "Files are identical" indicator accordingly.
     * Pass {@code null} to clear the view.
     */
    public void setModel(DiffModel model) {
        if (model == null) {
            leftPane.setRows(List.of());
            rightPane.setRows(List.of());
            navigator.setBlocks(List.of());
            showIdenticalIndicator(false);
            return;
        }
        // Both panes share the same DiffRow list; each cell renders its own side.
        leftPane.setRows(model.rows());
        rightPane.setRows(model.rows());
        navigator.setBlocks(model.blocks());
        showIdenticalIndicator(model.identical());
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the left diff pane. */
    public DiffPane getLeftPane()  { return leftPane; }

    /** Returns the right diff pane. */
    public DiffPane getRightPane() { return rightPane; }

    /** Returns the "Files are identical" label. */
    public Label getIdenticalLabel() { return identicalLabel; }

    /** Returns the sync-scroll toggle button. */
    public ToggleButton getSyncScrollButton() { return syncScrollButton; }

    /** Returns the {@link ScrollSyncManager}. */
    public ScrollSyncManager getScrollSyncManager() { return scrollSyncManager; }

    /** Returns the diff-block navigator. */
    public DiffNavigator getNavigator() { return navigator; }

    /** Returns the encoding/whitespace options bar. */
    public OptionsBar getOptionsBar() { return optionsBar; }

    /**
     * Binds this view to a {@link FileComparisonViewModel}, enabling all merge
     * controls (task 12.4).  May only be called once per view instance.
     *
     * <p>After binding:
     * <ul>
     *   <li>The diff model is kept in sync via {@code vm.diffModelProperty()}.</li>
     *   <li>The merge/undo/redo/save buttons are enabled / disabled reactively.</li>
     *   <li>Left/right pane titles show the unsaved-changes indicator (●) when dirty.</li>
     * </ul>
     *
     * @param vm the ViewModel to bind; must not be {@code null}
     * @throws IllegalStateException if a ViewModel is already bound
     */
    public void bindViewModel(FileComparisonViewModel vm) {
        if (this.boundViewModel != null) {
            throw new IllegalStateException("ViewModel already bound");
        }
        this.boundViewModel = vm;

        // Sync model → view
        vm.diffModelProperty().addListener((obs, o, model) -> {
            if (model != null) setModel(model);
            else               setModel(null);
        });

        // Block-copy buttons: enabled only when a navigator block is selected
        navigator.currentIndexProperty().addListener((obs, o, n) -> updateBlockCopyButtons());
        vm.differenceCountProperty().addListener((obs, o, count) -> {
            boolean hasDiffs = count.intValue() > 0;
            copyAllLToR.setDisable(!hasDiffs);
            copyAllRToL.setDisable(!hasDiffs);
            updateBlockCopyButtons();
            updateSaveAllButton();
        });

        // Undo / redo
        vm.canUndoProperty().addListener((obs, o, v) -> undoButton.setDisable(!v));
        vm.canRedoProperty().addListener((obs, o, v) -> redoButton.setDisable(!v));

        // Dirty indicators and save buttons
        vm.leftDirtyProperty().addListener((obs, o, v) -> {
            leftPane.setDirty(v);
            saveLeftBtn.setDisable(!v);
            updateSaveAllButton();
        });
        vm.rightDirtyProperty().addListener((obs, o, v) -> {
            rightPane.setDirty(v);
            saveRightBtn.setDisable(!v);
            updateSaveAllButton();
        });

        // Merge button actions
        copyBlockLToR.setOnAction(e -> {
            DiffBlock block = navigator.currentBlock();
            if (block != null) vm.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
        });
        copyBlockRToL.setOnAction(e -> {
            DiffBlock block = navigator.currentBlock();
            if (block != null) vm.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);
        });
        copyAllLToR.setOnAction(e -> vm.copyAll(MergeDirection.LEFT_TO_RIGHT));
        copyAllRToL.setOnAction(e -> vm.copyAll(MergeDirection.RIGHT_TO_LEFT));
        undoButton .setOnAction(e -> vm.undo());
        redoButton .setOnAction(e -> vm.redo());
        saveLeftBtn.setOnAction(e -> vm.saveLeft());
        saveRightBtn.setOnAction(e -> vm.saveRight());
        saveAllBtn .setOnAction(e -> vm.saveAll());

        // Enable edit toggle
        editToggleBtn.setDisable(false);

        // ── Options bar wiring (task 12.5) ───────────────────────────────────
        // Fire recompare whenever the user changes an encoding or ignore toggle.
        optionsBar.setOnOptionsChanged(opts -> vm.recompare(opts));

        // After each compare, reflect the detected encoding in the combo boxes.
        vm.diffModelProperty().addListener((obs, oldModel, model) -> {
            if (model != null) {
                if (model.leftEncoding()  != null) {
                    optionsBar.setLeftEncodingDisplay(model.leftEncoding().name());
                }
                if (model.rightEncoding() != null) {
                    optionsBar.setRightEncodingDisplay(model.rightEncoding().name());
                }
            }
        });
    }

    /**
     * Returns {@code true} if either document has unsaved changes (REQ-005).
     * Always {@code false} when no ViewModel is bound.
     */
    public boolean hasUnsavedChanges() {
        return boundViewModel != null
                && (boundViewModel.isLeftDirty() || boundViewModel.isRightDirty());
    }

    /**
     * Shows a "Save / Discard / Cancel" dialog when there are unsaved changes
     * and performs the chosen action (REQ-005).
     *
     * @return {@code true} if the caller may proceed (saved or discarded);
     *         {@code false} if the user cancelled
     */
    public boolean promptUnsavedChanges() {
        if (!hasUnsavedChanges()) return true;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("You have unsaved changes.");
        alert.setContentText("Save before closing?");
        ButtonType saveBtn    = new ButtonType("Save");
        ButtonType discardBtn = new ButtonType("Discard");
        alert.getButtonTypes().setAll(saveBtn, discardBtn, ButtonType.CANCEL);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) return false;
        if (result.get() == saveBtn) {
            if (boundViewModel.isLeftDirty())  boundViewModel.saveLeft();
            if (boundViewModel.isRightDirty()) boundViewModel.saveRight();
        }
        return true;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void navigateNext() {
        DiffBlock block = navigator.goNext();
        if (block != null) {
            navigateTo(block);
        }
    }

    private void navigatePrevious() {
        DiffBlock block = navigator.goPrevious();
        if (block != null) {
            navigateTo(block);
        }
    }

    /** Dispatches keyboard shortcuts from the scene-level event filter. */
    private void handleKeyEvent(KeyEvent e) {
        if (e.getCode() == KeyCode.F7 && e.isShiftDown()) {
            navigatePrevious();
            e.consume();
        } else if (e.getCode() == KeyCode.F7) {
            navigateNext();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.Z
                   && boundViewModel != null && boundViewModel.canUndo()) {
            boundViewModel.undo();
            e.consume();
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.Y
                   && boundViewModel != null && boundViewModel.canRedo()) {
            boundViewModel.redo();
            e.consume();
        } else if (e.isShortcutDown() && e.isShiftDown() && e.getCode() == KeyCode.RIGHT
                   && boundViewModel != null) {
            // Ctrl+Shift+Right = copy all L→R (REQ-015.1)
            boundViewModel.copyAll(MergeDirection.LEFT_TO_RIGHT);
            e.consume();
        } else if (e.isShortcutDown() && e.isShiftDown() && e.getCode() == KeyCode.LEFT
                   && boundViewModel != null) {
            // Ctrl+Shift+Left = copy all R→L
            boundViewModel.copyAll(MergeDirection.RIGHT_TO_LEFT);
            e.consume();
        } else if (e.isShortcutDown() && !e.isShiftDown() && e.getCode() == KeyCode.RIGHT
                   && boundViewModel != null) {
            // Ctrl+Right = copy current block L→R
            DiffBlock block = navigator.currentBlock();
            if (block != null) {
                boundViewModel.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
                e.consume();
            }
        } else if (e.isShortcutDown() && !e.isShiftDown() && e.getCode() == KeyCode.LEFT
                   && boundViewModel != null) {
            // Ctrl+Left = copy current block R→L
            DiffBlock block = navigator.currentBlock();
            if (block != null) {
                boundViewModel.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);
                e.consume();
            }
        } else if (e.isShortcutDown() && e.getCode() == KeyCode.S
                   && boundViewModel != null) {
            // Ctrl+S = save all dirty documents (REQ-015.1)
            if (boundViewModel.isLeftDirty())  boundViewModel.saveLeft();
            if (boundViewModel.isRightDirty()) boundViewModel.saveRight();
            e.consume();
        }
    }

    /** Enables/disables per-block copy buttons based on whether a block is selected. */
    private void updateBlockCopyButtons() {
        boolean hasBlock = navigator.currentBlock() != null;
        copyBlockLToR.setDisable(!hasBlock);
        copyBlockRToL.setDisable(!hasBlock);
    }

    /** Enables "Save All" only when at least one side is dirty. */
    private void updateSaveAllButton() {
        saveAllBtn.setDisable(boundViewModel == null
                || (!boundViewModel.isLeftDirty() && !boundViewModel.isRightDirty()));
    }

    /**
     * Scrolls and selects both {@link DiffPane} ListViews to the first row of the
     * given block so both sides stay aligned (REQ-003).
     */
    private void navigateTo(DiffBlock block) {
        int row = block.firstRowIndex();
        leftPane.getListView().scrollTo(row);
        rightPane.getListView().scrollTo(row);
        leftPane.getListView().getSelectionModel().select(row);
        rightPane.getListView().getSelectionModel().select(row);
    }

    /**
     * Refreshes the counter label text and the enabled/disabled state of the
     * prev/next buttons from the current {@link DiffNavigator} state.
     * Called whenever the navigator's index or block-count property changes.
     */
    private void updateNavState() {
        int total = navigator.getBlockCount();
        int idx   = navigator.getCurrentIndex();

        if (total == 0) {
            diffCountLabel.setText("0 / 0");
        } else if (idx == DiffNavigator.NONE) {
            diffCountLabel.setText("\u2013 / " + total);  // – / N
        } else {
            diffCountLabel.setText((idx + 1) + " / " + total);
        }

        boolean atFirst = idx == 0;
        boolean atLast  = idx != DiffNavigator.NONE && idx == total - 1;
        prevButton.setDisable(total == 0 || atFirst);
        nextButton.setDisable(total == 0 || atLast);
    }

    private void showIdenticalIndicator(boolean show) {
        identicalLabel.setVisible(show);
        identicalLabel.setManaged(show);
    }
}
