package com.comparetool.ui;

import com.comparetool.infra.concurrent.CancellationToken;
import com.comparetool.infra.concurrent.ProgressReporter;
import com.comparetool.model.DiffTreeNode;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderItemStatus;
import com.comparetool.viewmodel.FileDiffRequest;
import com.comparetool.viewmodel.FolderComparisonViewModel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Side-by-side folder comparison view (task 13.1 — REQ-007).
 *
 * <pre>
 * ┌─────────────────────────────────────────────────────────────┐
 * │  [Expand All]  [Collapse All]    toolbar (top)              │
 * ├──────────────────────────┬──────────────────────────────────┤
 * │  FolderTreePane (left)   │  FolderTreePane (right)          │
 * │  ListView&lt;DiffTreeNode&gt;  │  ListView&lt;DiffTreeNode&gt;         │
 * └──────────────────────────┴──────────────────────────────────┘
 * </pre>
 *
 * <h3>Row alignment</h3>
 * <p>Both panes are driven by the same flat {@code visibleNodes} list from
 * {@link FolderComparisonViewModel}, so rows are always aligned (REQ-007.6).
 * There are no separate {@code TreeTableView} expand-state books to synchronize —
 * the ViewModel owns the single source of truth.
 *
 * <h3>Expand / collapse</h3>
 * <p>Each directory cell has a small arrow button.  Clicking it calls
 * {@link FolderComparisonViewModel#toggleExpand(DiffTreeNode)} on the ViewModel,
 * which updates {@code visibleNodes} and propagates the change to both panes.
 * The toolbar buttons call {@link FolderComparisonViewModel#expandAll()} and
 * {@link FolderComparisonViewModel#collapseAll()}.
 *
 * <h3>Scroll synchronization</h3>
 * <p>Vertical scroll bars are linked via {@link ScrollSyncManager}.
 */
public class FolderComparisonView extends BorderPane {

    // ── Panes ─────────────────────────────────────────────────────────────────
    private final FolderTreePane leftPane  = new FolderTreePane(true);
    private final FolderTreePane rightPane = new FolderTreePane(false);

    // ── Toolbar controls ──────────────────────────────────────────────────────
    private final Button   expandAllBtn    = new Button("Expand All");
    private final Button   collapseAllBtn  = new Button("Collapse All");
    private final CheckBox showDiffsCheck  = new CheckBox("Differences only");

    // ── Options bar (match-mode, masks) ────────────────────────────────────
    private final FolderOptionsBar optionsBar = new FolderOptionsBar();

    // ── Item action buttons (copy, delete, ignore) ──────────────────────────
    private final Button copyLToRBtn  = new Button("→ Copy");
    private final Button copyRToLBtn  = new Button("← Copy");
    private final Button deleteBtn    = new Button("Delete");
    private final Button ignoreBtn    = new Button("Ignore");
    private final Button unignoreBtn  = new Button("Un-ignore");

    // ── Summary bar ───────────────────────────────────────────────────────────
    private final Label summaryLabel = new Label();


    // ── Progress / cancel row (visible only during comparison) ──────────────
    private final ProgressBar progressBar   = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
    private final Button       cancelButton  = new Button("Cancel");
    // ── Confirmation injection (tests replace this to avoid Alert dialogs) ────
    private BiFunction<String, String, Boolean> confirmHandler = this::showAlert;
    // ── Scroll sync ───────────────────────────────────────────────────────────
    private final ScrollSyncManager scrollSync;
    // Reference kept so bindViewModel() can wire visibility to loadingProperty()
    private HBox progressRow;
    // ── Cancellation ─────────────────────────────────────────────────
    private CancellationToken currentToken;
    // Last comparison paths/options — re-used when Apply re-triggers compare
    private Path currentLeft;
    private Path currentRight;
    private FolderComparisonOptions currentOptions = FolderComparisonOptions.defaults();
    // ── Drill-down callback (set by shell before binding) ─────────────────────
    private Consumer<FileDiffRequest> onFileDiffRequested;
    // ── Bound ViewModel ───────────────────────────────────────────────────────
    private FolderComparisonViewModel boundViewModel;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FolderComparisonView() {
        expandAllBtn.setId("expandAllButton");
        expandAllBtn.setAccessibleText("Expand all folders");
        collapseAllBtn.setId("collapseAllButton");
        collapseAllBtn.setAccessibleText("Collapse all folders");
        expandAllBtn.setDisable(true);
        collapseAllBtn.setDisable(true);

        showDiffsCheck.setId("showDiffsOnlyCheckBox");
        showDiffsCheck.setAccessibleText("Show differences only");
        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);

        HBox toolbar = new HBox(
                expandAllBtn, new Separator(Orientation.VERTICAL), collapseAllBtn,
                toolbarSpacer, showDiffsCheck);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(4, 8, 4, 8));
        toolbar.setSpacing(8);

        // Item action buttons (REQ-011, REQ-012)
        copyLToRBtn .setId("copyLeftToRightButton");
        copyRToLBtn .setId("copyRightToLeftButton");
        deleteBtn   .setId("deleteItemButton");
        ignoreBtn   .setId("ignoreItemButton");
        unignoreBtn .setId("unignoreItemButton");
        copyLToRBtn .setDisable(true);
        copyRToLBtn .setDisable(true);
        deleteBtn   .setDisable(true);
        ignoreBtn   .setDisable(true);
        unignoreBtn .setDisable(true);

        // Accessible names (REQ-015.3)
        copyLToRBtn .setAccessibleText("Copy selected item from left to right");
        copyRToLBtn .setAccessibleText("Copy selected item from right to left");
        deleteBtn   .setAccessibleText("Delete selected item");
        ignoreBtn   .setAccessibleText("Ignore selected item");
        unignoreBtn .setAccessibleText("Un-ignore selected item");

        HBox actionsRow = new HBox(6,
                copyLToRBtn, copyRToLBtn,
                new Separator(Orientation.VERTICAL),
                ignoreBtn, unignoreBtn,
                new Separator(Orientation.VERTICAL),
                deleteBtn);
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        actionsRow.setPadding(new Insets(2, 8, 2, 8));

        // Progress row — shown while comparison is running
        progressBar.setId("compareProgressBar");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setVisible(false);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        cancelButton.setId("cancelButton");
        cancelButton.setAccessibleText("Cancel comparison");
        cancelButton.setVisible(false);
        HBox progressRow = new HBox(8, progressBar, cancelButton);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setPadding(new Insets(2, 8, 2, 8));
        progressRow.setVisible(false);
        progressRow.setManaged(false);

        // Summary bar — shown at bottom after comparison
        summaryLabel.setId("summaryLabel");
        summaryLabel.setStyle("-fx-padding: 3 8 3 8;");
        HBox summaryBar = new HBox(summaryLabel);
        summaryBar.setAlignment(Pos.CENTER_LEFT);

        SplitPane splitPane = new SplitPane(leftPane, rightPane);
        splitPane.setId("folderSplitPane");
        splitPane.setDividerPositions(0.5);
        HBox.setHgrow(leftPane,  Priority.ALWAYS);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        scrollSync = new ScrollSyncManager(
                leftPane.getListView(), rightPane.getListView(), true);

        setTop(new VBox(toolbar, optionsBar, actionsRow, progressRow));
        setCenter(splitPane);
        setBottom(summaryBar);

        this.progressRow = progressRow;
    }

    // ── ViewModel binding ─────────────────────────────────────────────────────

    /**
     * Binds this view to the given {@link FolderComparisonViewModel}.
     * May only be called once per view instance.
     *
     * <p>After binding:
     * <ul>
     *   <li>Both panes are populated from {@code vm.visibleNodesProperty()}.</li>
     *   <li>Expand/collapse arrow clicks delegate to {@code vm.toggleExpand()}.</li>
     *   <li>Toolbar buttons delegate to {@code vm.expandAll()} / {@code vm.collapseAll()}.</li>
     *   <li>Buttons are enabled when the first non-empty node list arrives.</li>
     * </ul>
     *
     * @param vm the ViewModel to bind; must not be {@code null}
     * @throws IllegalStateException if a ViewModel is already bound
     */
    public void bindViewModel(FolderComparisonViewModel vm) {
        if (this.boundViewModel != null) {
            throw new IllegalStateException("ViewModel already bound");
        }
        this.boundViewModel = vm;

        // Tell each pane how to check collapse state and how to handle toggle clicks
        leftPane .setIsCollapsedFn(vm::isCollapsed);
        rightPane.setIsCollapsedFn(vm::isCollapsed);
        leftPane .setOnToggleExpand(vm::toggleExpand);
        rightPane.setOnToggleExpand(vm::toggleExpand);
        // Wire drill-down: double-click on a file cell opens it (REQ-9.1, REQ-9.2)
        leftPane .setOnOpenNode(vm::openFileDiff);
        rightPane.setOnOpenNode(vm::openFileDiff);

        // Keep both panes in sync with the flat visible-node list
        vm.visibleNodesProperty().addListener((obs, oldNodes, nodes) -> {
            leftPane .setNodes(nodes);
            rightPane.setNodes(nodes);
            boolean hasNodes = !nodes.isEmpty();
            expandAllBtn  .setDisable(!hasNodes);
            collapseAllBtn.setDisable(!hasNodes);
        });

        // Toolbar actions
        expandAllBtn  .setOnAction(e -> vm.expandAll());
        collapseAllBtn.setOnAction(e -> vm.collapseAll());

        // ── Progress row visibility (REQ-8.5) ───────────────────────────────
        vm.loadingProperty().addListener((obs, oldLoading, loading) -> {
            progressRow  .setVisible(loading);
            progressRow  .setManaged(loading);
            progressBar  .setVisible(loading);
            cancelButton .setVisible(loading);
        });

        // ── Show-only-differences filter (REQ-8.7) ──────────────────────────
        showDiffsCheck.selectedProperty()
                .bindBidirectional(vm.showOnlyDifferencesProperty());

        // ── Summary counts (REQ-8.6) ─────────────────────────────────────
        Runnable updateSummary = () -> summaryLabel.setText(String.format(
                "= %d   ≠ %d   ◄ %d   ► %d",
                vm.getIdenticalCount(),
                vm.getDifferentCount(),
                vm.getLeftOnlyCount(),
                vm.getRightOnlyCount()));
        vm.identicalCountProperty() .addListener((obs, o, n) -> updateSummary.run());
        vm.differentCountProperty() .addListener((obs, o, n) -> updateSummary.run());
        vm.leftOnlyCountProperty()  .addListener((obs, o, n) -> updateSummary.run());
        vm.rightOnlyCountProperty() .addListener((obs, o, n) -> updateSummary.run());

        // ── Pending file-diff (REQ-9.1) ──────────────────────────────────────
        vm.pendingFileDiffProperty().addListener((obs, old, req) -> {
            if (req != null && onFileDiffRequested != null) {
                onFileDiffRequested.accept(req);
            }
        });
        // ── Options bar: apply re-triggers comparison with new options (REQ-010/011)
        optionsBar.setBaseOptions(currentOptions);
        optionsBar.setOnApply(opts -> {
            if (currentLeft != null) startCompare(currentLeft, currentRight, opts);
        });

        // ── Selection sync: click either pane → vm.selectedNodeProperty() updated
        final boolean[] syncingSelection = {false};
        leftPane.getListView().getSelectionModel().selectedItemProperty()
                .addListener((obs, o, node) -> {
                    if (syncingSelection[0]) return;
                    vm.setSelectedNode(node);
                    if (node != null) {
                        syncingSelection[0] = true;
                        rightPane.getListView().getSelectionModel().select(node);
                        syncingSelection[0] = false;
                    }
                });
        rightPane.getListView().getSelectionModel().selectedItemProperty()
                .addListener((obs, o, node) -> {
                    if (syncingSelection[0]) return;
                    vm.setSelectedNode(node);
                    if (node != null) {
                        syncingSelection[0] = true;
                        leftPane.getListView().getSelectionModel().select(node);
                        syncingSelection[0] = false;
                    }
                });

        // ── Action buttons: enable/disable based on selected node (REQ-011, REQ-012)
        vm.selectedNodeProperty().addListener((obs, o, node) ->
                updateActionButtons(node));

        // ── Action button handlers ────────────────────────────────────────────
        copyLToRBtn.setOnAction(e -> {
            DiffTreeNode node = vm.getSelectedNode();
            if (node == null) return;
            if (node.status() == FolderItemStatus.DIFFERENT
                    && !confirmHandler.apply("Overwrite?",
                        "Overwrite the right-side file '" + nodeName(node) + "'?")) return;
            try { vm.copyToRight(node); } catch (Exception ex) { showError("Copy failed", ex); }
        });

        copyRToLBtn.setOnAction(e -> {
            DiffTreeNode node = vm.getSelectedNode();
            if (node == null) return;
            if (node.status() == FolderItemStatus.DIFFERENT
                    && !confirmHandler.apply("Overwrite?",
                        "Overwrite the left-side file '" + nodeName(node) + "'?")) return;
            try { vm.copyToLeft(node); } catch (Exception ex) { showError("Copy failed", ex); }
        });

        deleteBtn.setOnAction(e -> {
            DiffTreeNode node = vm.getSelectedNode();
            if (node == null) return;
            if (!confirmHandler.apply("Delete?",
                    "Permanently delete '" + nodeName(node) + "'?")) return;
            try { vm.deleteItem(node); } catch (Exception ex) { showError("Delete failed", ex); }
        });

        ignoreBtn  .setOnAction(e -> { DiffTreeNode n = vm.getSelectedNode(); if (n != null) vm.ignoreItem(n); });
        unignoreBtn.setOnAction(e -> { DiffTreeNode n = vm.getSelectedNode(); if (n != null) vm.unignoreItem(n); });    }

    // ── Compare lifecycle ─────────────────────────────────────────────────

    /**
     * Starts a folder comparison with progress reporting and cancellation support (REQ-8.5).
     * <p>The view creates a fresh {@link CancellationToken} per call; cancelling the in-progress
     * comparison is done via the "Cancel" button wired here.
     *
     * @param left    left root directory
     * @param right   right root directory
     * @param options comparison options
     * @throws IllegalStateException if {@link #bindViewModel(FolderComparisonViewModel)} has not
     *                               been called
     */
    public void startCompare(Path left, Path right, FolderComparisonOptions options) {
        Objects.requireNonNull(boundViewModel, "bindViewModel must be called before startCompare");
        this.currentLeft    = left;
        this.currentRight   = right;
        this.currentOptions = options;
        optionsBar.setBaseOptions(options);
        currentToken = new CancellationToken();
        cancelButton.setOnAction(e -> currentToken.cancel());
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        // Throttle progress updates: only touch the FX thread when the whole-number
        // percentage changes, so a scan of thousands of files enqueues at most ~100
        // Platform.runLater calls instead of one per file (avoids UI-thread flooding).
        final int[] lastPct = { -1 };
        ProgressReporter reporter = (current, total, msg) -> {
            if (total > 0) {
                int pct = (int) ((current * 100L) / total);
                if (pct != lastPct[0]) {
                    lastPct[0] = pct;
                    Platform.runLater(() -> progressBar.setProgress(pct / 100.0));
                }
            }
        };
        boundViewModel.compareFolders(left, right, options, reporter, currentToken);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void updateActionButtons(DiffTreeNode node) {
        boolean hasNode    = node != null;
        boolean isDir      = hasNode && node.directory();
        boolean hasLeft    = hasNode && node.left()  != null;
        boolean hasRight   = hasNode && node.right() != null;
        boolean isIgnored  = hasNode && node.status() == FolderItemStatus.IGNORED;
        copyLToRBtn .setDisable(!hasLeft);
        copyRToLBtn .setDisable(!hasRight);
        deleteBtn   .setDisable(!hasNode);
        ignoreBtn   .setDisable(!hasNode || isDir || isIgnored);
        unignoreBtn .setDisable(!hasNode || !isIgnored);
    }

    private boolean showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    private void showError(String title, Throwable ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage(), ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    private static String nodeName(DiffTreeNode node) {
        Path fn = node.relativePath().getFileName();
        return fn != null ? fn.toString() : node.relativePath().toString();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the left tree pane. */
    public FolderTreePane getLeftPane()           { return leftPane; }

    /** Returns the right tree pane. */
    public FolderTreePane getRightPane()          { return rightPane; }

    /** Returns the "Expand All" toolbar button. */
    public Button getExpandAllButton()            { return expandAllBtn; }

    /** Returns the "Collapse All" toolbar button. */
    public Button getCollapseAllButton()          { return collapseAllBtn; }

    /** Returns the scroll-synchronization manager. */
    public ScrollSyncManager getScrollSync()      { return scrollSync; }

    /** Returns the progress bar (id: {@code compareProgressBar}). */
    public ProgressBar getProgressBar()           { return progressBar; }

    /** Returns the cancel button (id: {@code cancelButton}). */
    public Button getCancelButton()               { return cancelButton; }

    /** Returns the "Differences only" check-box (id: {@code showDiffsOnlyCheckBox}). */
    public CheckBox getShowDiffsCheckBox()        { return showDiffsCheck; }

    /** Returns the summary label (id: {@code summaryLabel}). */
    public Label getSummaryLabel()                { return summaryLabel; }

    /**
     * Registers a callback that is invoked whenever the user opens a file pair for
     * drill-down (REQ-9.1, REQ-9.2).  The shell uses this to push the
     * {@link FileComparisonView} onto the display stack.
     *
     * @param callback receives the {@link FileDiffRequest} describing what to show;
     *                 may be {@code null} to unregister
     */
    public void setOnFileDiffRequested(Consumer<FileDiffRequest> callback) {
        this.onFileDiffRequested = callback;
    }

    /**
     * Replaces the confirmation handler used for destructive actions (REQ-012, REQ-016).
     * The handler receives (title, message) and returns {@code true} to proceed.
     *
     * <p>Default implementation shows a JavaFX {@link Alert}.  Tests inject
     * {@code (t, m) -> true} (auto-confirm) or {@code (t, m) -> false} (auto-cancel).
     *
     * @param handler the new handler; must not be {@code null}
     */
    public void setConfirmHandler(BiFunction<String, String, Boolean> handler) {
        this.confirmHandler = Objects.requireNonNull(handler);
    }

    /** Returns the options bar (match-mode, masks, apply). */
    public FolderOptionsBar getOptionsBar()       { return optionsBar; }

    /** Returns the "Copy L\u2192R" action button. */
    public Button getCopyLToRButton()             { return copyLToRBtn; }

    /** Returns the "Copy R\u2192L" action button. */
    public Button getCopyRToLButton()             { return copyRToLBtn; }

    /** Returns the "Delete" action button. */
    public Button getDeleteButton()               { return deleteBtn; }

    /** Returns the "Ignore" action button. */
    public Button getIgnoreButton()               { return ignoreBtn; }

    /** Returns the "Un-ignore" action button. */
    public Button getUnignoreButton()             { return unignoreBtn; }

    /** Returns the bound ViewModel, or {@code null} if not yet bound. */
    public FolderComparisonViewModel getBoundViewModel() { return boundViewModel; }
}
