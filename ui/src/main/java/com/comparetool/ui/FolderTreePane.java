package com.comparetool.ui;

import com.comparetool.model.DiffTreeNode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A pane that wraps a {@link ListView} of {@link DiffTreeNode} items for one side
 * (left or right) of the folder comparison tree (task 13.1 — REQ-007).
 *
 * <p>Both panes in {@link FolderComparisonView} are driven by the same flat
 * {@code visibleNodes} list from {@link com.comparetool.viewmodel.FolderComparisonViewModel},
 * guaranteeing row-by-row alignment.  The left pane renders left-side file metadata;
 * the right pane renders right-side metadata.  Placeholder rows are rendered when an item
 * exists only on the other side.
 *
 * <h3>Cell updates</h3>
 * <p>The cell factory captures lambda references that delegate to this pane's current
 * {@link #setIsCollapsedFn(Predicate)} and {@link #setOnToggleExpand(Consumer)} fields,
 * so updating those fields takes effect on the next cell refresh without recreating cells.
 */
public class FolderTreePane extends VBox {

    private final ListView<DiffTreeNode>     listView;
    private final ObservableList<DiffTreeNode> items = FXCollections.observableArrayList();
    private final boolean leftSide;

    // Mutable — re-read by cell lambdas on every updateItem call
    private Predicate<Path>        isCollapsedFn  = path -> false;
    private Consumer<DiffTreeNode> onToggleExpand = node -> {};
    private Consumer<DiffTreeNode> onOpen         = node -> {};

    // ── Constructor ───────────────────────────────────────────────────────────

    public FolderTreePane(boolean leftSide) {
        this.leftSide = leftSide;
        listView = new ListView<>(items);
        listView.setId(leftSide ? "leftFolderTree" : "rightFolderTree");

        // The lambdas here capture `this` (the pane) and always read the current
        // isCollapsedFn / onToggleExpand fields, so updating those fields is reflected
        // on the next cell render without needing to recreate the cell factory.
        listView.setCellFactory(lv -> new FolderTreeCell(
                leftSide,
                path -> isCollapsedFn.test(path),
                node -> onToggleExpand.accept(node),
                node -> onOpen.accept(node)));

        VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().add(listView);
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    /**
     * Replaces the displayed items and forces a visual refresh so that arrow states
     * (collapsed / expanded) are re-evaluated for the new data.
     *
     * @param nodes the new flat node list from {@code FolderComparisonViewModel}
     */
    public void setNodes(List<DiffTreeNode> nodes) {
        items.setAll(nodes);
        listView.refresh(); // re-evaluate arrow state for unchanged directory nodes
    }

    /**
     * Sets the predicate used to determine whether a directory node is currently
     * collapsed.  Typically wired to {@code vm::isCollapsed}.
     *
     * <p>Triggers a {@link ListView#refresh()} so existing cells pick up the change.
     *
     * @param fn predicate; {@code true} → directory is collapsed
     */
    public void setIsCollapsedFn(Predicate<Path> fn) {
        this.isCollapsedFn = fn;
        listView.refresh();
    }

    /**
     * Sets the callback invoked when the user clicks a directory's expand/collapse arrow.
     * Typically wired to {@code vm::toggleExpand}.
     *
     * @param callback receives the {@link DiffTreeNode} whose arrow was clicked
     */
    public void setOnToggleExpand(Consumer<DiffTreeNode> callback) {
        this.onToggleExpand = callback;
    }

    /**
     * Sets the callback invoked when the user double-clicks a file cell (drill-down, REQ-9.1).
     * Typically wired to {@code vm::openFileDiff}.
     *
     * @param callback receives the {@link DiffTreeNode} that was double-clicked
     */
    public void setOnOpenNode(Consumer<DiffTreeNode> callback) {
        this.onOpen = (callback != null) ? callback : node -> {};
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the underlying {@link ListView}. */
    public ListView<DiffTreeNode> getListView()          { return listView; }

    /** Returns the backing observable item list. */
    public ObservableList<DiffTreeNode> getItems()       { return items; }

    /** {@code true} if this pane renders the left side. */
    public boolean isLeftSide()                          { return leftSide; }
}
