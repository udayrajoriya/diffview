package com.diffview.viewmodel;

import com.diffview.core.service.ComparisonService;
import com.diffview.infra.concurrent.CancellationToken;
import com.diffview.infra.concurrent.ProgressReporter;
import com.diffview.infra.concurrent.TaskExecutor;
import com.diffview.model.ComparisonOptions;
import com.diffview.model.DiffTreeNode;
import com.diffview.model.FileMeta;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderComparisonResult;
import com.diffview.model.FolderItemStatus;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ViewModel for the folder comparison view (MVVM, task 10.2).
 *
 * <h3>Observable state</h3>
 * <ul>
 *   <li>{@link #resultProperty()} — full comparison result (null before first comparison)</li>
 *   <li>{@link #visibleNodesProperty()} — flat, ordered list of nodes after filtering;
 *       updated automatically when {@link #showOnlyDifferencesProperty()} toggles</li>
 *   <li>Summary counts: identical, different, leftOnly, rightOnly, ignored, total</li>
 *   <li>{@link #loadingProperty()} — true while a comparison is running</li>
 *   <li>{@link #pendingFileDiffProperty()} — set by {@link #openFileDiff(DiffTreeNode)} to
 *       instruct the UI which pane to open</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * <p>The {@code compareFolders} command submits work to the injected {@link TaskExecutor}.
 * In production that is a {@code PooledTaskExecutor}; tests inject a
 * {@code DirectTaskExecutor} for synchronous, deterministic execution.
 *
 * <h3>No JavaFX nodes</h3>
 * <p>Only {@code javafx.beans.property} types from {@code javafx.base} are used — no
 * {@code Node} subclasses — so this class is instantiable without a running JavaFX Application.
 */
public final class FolderComparisonViewModel {

    // ── observable state ──────────────────────────────────────────────────────
    private final SimpleObjectProperty<FolderComparisonResult> result
            = new SimpleObjectProperty<>(null);
    private final SimpleObjectProperty<List<DiffTreeNode>> visibleNodes
            = new SimpleObjectProperty<>(List.of());
    private final SimpleIntegerProperty  totalCount      = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty  identicalCount  = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty  differentCount  = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty  leftOnlyCount   = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty  rightOnlyCount  = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty  ignoredCount    = new SimpleIntegerProperty(0);
    private final SimpleBooleanProperty  loading         = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty  showOnlyDifferences = new SimpleBooleanProperty(false);
    private final SimpleObjectProperty<FileDiffRequest>  pendingFileDiff
            = new SimpleObjectProperty<>(null);
    private final SimpleObjectProperty<DiffTreeNode> selectedNode
            = new SimpleObjectProperty<>(null);

    // ── expand/collapse state (relative paths of collapsed directories) ────────
    private final Set<Path> collapsedPaths = new HashSet<>();

    // ── current comparison context ────────────────────────────────────────────
    private Path                    currentLeft;
    private Path                    currentRight;
    private FolderComparisonOptions currentOptions;

    // ── injected services ─────────────────────────────────────────────────────
    private final ComparisonService comparisonService;
    private final TaskExecutor      executor;

    // ── constructor ───────────────────────────────────────────────────────────

    /**
     * @param comparisonService drives folder diffing and single-pair re-evaluation
     * @param executor          runs folder comparisons on a background thread;
     *                          tests inject {@code DirectTaskExecutor}
     */
    public FolderComparisonViewModel(ComparisonService comparisonService,
                                     TaskExecutor executor) {
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService");
        this.executor          = Objects.requireNonNull(executor,          "executor");
        // Rebuild visible list whenever the filter toggle changes.
        showOnlyDifferences.addListener((obs, oldVal, newVal) -> refreshVisibleNodes());
    }

    // ── commands ──────────────────────────────────────────────────────────────

    /**
     * Launches a folder comparison.  Results are pushed to observable properties when
     * the comparison finishes.
     *
     * @param left     left-side directory root
     * @param right    right-side directory root
     * @param options  comparison options (match mode, masks, ignores)
     * @param progress progress reporter; may be {@code null} (treated as no-op)
     * @param token    cancellation token; may be {@code null} (treated as never-cancelled)
     */
    public void compareFolders(Path left, Path right,
                               FolderComparisonOptions options,
                               ProgressReporter progress,
                               CancellationToken token) {
        Objects.requireNonNull(left,    "left");
        Objects.requireNonNull(right,   "right");
        Objects.requireNonNull(options, "options");
        currentLeft    = left;
        currentRight   = right;
        currentOptions = options;
        collapsedPaths.clear();
        loading.set(true);

        ProgressReporter  p = progress != null ? progress : ProgressReporter.noOp();
        CancellationToken t = token    != null ? token    : CancellationToken.neverCancelled();

        executor.submit(() -> {
            try {
                FolderComparisonResult r =
                        comparisonService.compareFolders(left, right, options, p, t).get();
                applyResult(r);
            } catch (Exception e) {
                loading.set(false);
                throw new RuntimeException("Folder comparison failed", e);
            }
        });
    }

    /**
     * Posts a {@link FileDiffRequest} for the given node, which the UI observes via
     * {@link #pendingFileDiffProperty()}.
     *
     * <ul>
     *   <li>For a <em>paired</em> file node → {@link FileDiffRequest.Actual}</li>
     *   <li>For a <em>one-sided</em> file node → {@link FileDiffRequest.Placeholder}</li>
     * </ul>
     * Directory nodes are silently ignored.
     *
     * @param node the tree node to open; must not be {@code null}
     */
    public void openFileDiff(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        if (node.directory()) return;

        if (node.isOneSided()) {
            boolean leftSide = (node.left() != null);
            Path side = leftSide ? node.left().absolutePath() : node.right().absolutePath();
            pendingFileDiff.set(new FileDiffRequest.Placeholder(side, leftSide));
        } else {
            ComparisonOptions opts = currentOptions != null
                    ? currentOptions.content()
                    : ComparisonOptions.defaults();
            pendingFileDiff.set(new FileDiffRequest.Actual(
                    node.left().absolutePath(),
                    node.right().absolutePath(),
                    opts));
        }
    }

    /**
     * Copies the left-side item to the right side.
     * After the copy, the node's status is re-evaluated via
     * {@link ComparisonService#evaluatePair} and the tree is updated.
     *
     * @param node the node to copy; must have a left-side item
     */
    public void copyToRight(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        if (node.left() == null) {
            throw new IllegalArgumentException("Node has no left side to copy: " + node.relativePath());
        }
        Path source = node.left().absolutePath();
        Path target = currentRight.resolve(node.relativePath());
        try {
            if (node.directory()) {
                copyDirectoryTree(source, target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("copyToRight failed for " + node.relativePath(), e);
        }
        refreshSingleNode(node);
    }

    /**
     * Copies the right-side item to the left side.
     * After the copy, the node's status is re-evaluated.
     *
     * @param node the node to copy; must have a right-side item
     */
    public void copyToLeft(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        if (node.right() == null) {
            throw new IllegalArgumentException("Node has no right side to copy: " + node.relativePath());
        }
        Path source = node.right().absolutePath();
        Path target = currentLeft.resolve(node.relativePath());
        try {
            if (node.directory()) {
                copyDirectoryTree(source, target);
            } else {
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("copyToLeft failed for " + node.relativePath(), e);
        }
        refreshSingleNode(node);
    }

    /**
     * Deletes the item from whichever side(s) it exists on, then re-runs the comparison
     * so that the tree reflects the deletion.
     *
     * @param node the node to delete; must not be {@code null}
     */
    public void deleteItem(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        try {
            if (node.left() != null) {
                deleteRecursive(node.left().absolutePath());
            }
            if (node.right() != null) {
                deleteRecursive(node.right().absolutePath());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("deleteItem failed for " + node.relativePath(), e);
        }
        if (currentLeft != null) {
            compareFolders(currentLeft, currentRight, currentOptions,
                    ProgressReporter.noOp(), CancellationToken.neverCancelled());
        }
    }

    /**
     * Adds {@code node.relativePath()} to the manual-ignores set and re-runs the comparison.
     *
     * @param node the node to ignore
     */
    public void ignoreItem(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        if (currentOptions == null) return;
        Set<Path> updated = new HashSet<>(currentOptions.manualIgnores());
        updated.add(node.relativePath());
        currentOptions = currentOptions.withManualIgnores(Collections.unmodifiableSet(updated));
        if (currentLeft != null) {
            compareFolders(currentLeft, currentRight, currentOptions,
                    ProgressReporter.noOp(), CancellationToken.neverCancelled());
        }
    }

    /**
     * Removes {@code node.relativePath()} from the manual-ignores set and re-runs the comparison.
     *
     * @param node the node to un-ignore
     */
    public void unignoreItem(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        if (currentOptions == null) return;
        Set<Path> updated = new HashSet<>(currentOptions.manualIgnores());
        updated.remove(node.relativePath());
        currentOptions = currentOptions.withManualIgnores(Collections.unmodifiableSet(updated));
        if (currentLeft != null) {
            compareFolders(currentLeft, currentRight, currentOptions,
                    ProgressReporter.noOp(), CancellationToken.neverCancelled());
        }
    }

    /**
     * Expands all directory nodes (makes every item visible in the flat list, subject
     * to the show-only-differences filter).
     */
    public void expandAll() {
        collapsedPaths.clear();
        refreshVisibleNodes();
    }

    /**
     * Collapses all directory nodes (only top-level children of the root are visible).
     */
    public void collapseAll() {
        FolderComparisonResult r = result.get();
        if (r == null) return;
        collectDirectoryRelativePaths(r.root(), collapsedPaths);
        refreshVisibleNodes();
    }

    /**
     * Toggles the expand/collapse state of the given directory node and refreshes the
     * visible list.  A no-op for file nodes.
     *
     * @param node the directory node to toggle
     */
    public void toggleExpand(DiffTreeNode node) {
        if (!node.directory()) return;
        if (collapsedPaths.contains(node.relativePath())) {
            collapsedPaths.remove(node.relativePath());
        } else {
            collapsedPaths.add(node.relativePath());
        }
        refreshVisibleNodes();
    }

    // ── read-only property accessors ──────────────────────────────────────────

    public ReadOnlyObjectProperty<FolderComparisonResult> resultProperty() {
        return result;
    }

    public FolderComparisonResult getResult() {
        return result.get();
    }

    public ReadOnlyObjectProperty<List<DiffTreeNode>> visibleNodesProperty() {
        return visibleNodes;
    }

    public List<DiffTreeNode> getVisibleNodes() {
        return visibleNodes.get();
    }

    public ReadOnlyIntegerProperty totalCountProperty()     { return totalCount;     }
    public ReadOnlyIntegerProperty identicalCountProperty() { return identicalCount; }
    public ReadOnlyIntegerProperty differentCountProperty() { return differentCount; }
    public ReadOnlyIntegerProperty leftOnlyCountProperty()  { return leftOnlyCount;  }
    public ReadOnlyIntegerProperty rightOnlyCountProperty() { return rightOnlyCount; }
    public ReadOnlyIntegerProperty ignoredCountProperty()   { return ignoredCount;   }

    public int getTotalCount()     { return totalCount.get();     }
    public int getIdenticalCount() { return identicalCount.get(); }
    public int getDifferentCount() { return differentCount.get(); }
    public int getLeftOnlyCount()  { return leftOnlyCount.get();  }
    public int getRightOnlyCount() { return rightOnlyCount.get(); }
    public int getIgnoredCount()   { return ignoredCount.get();   }

    public ReadOnlyBooleanProperty loadingProperty()              { return loading; }
    public boolean isLoading()                                    { return loading.get(); }

    /** Mutable property; set to {@code true} to hide identical/ignored nodes. */
    public SimpleBooleanProperty showOnlyDifferencesProperty()    { return showOnlyDifferences; }
    public boolean isShowOnlyDifferences()                        { return showOnlyDifferences.get(); }
    public void setShowOnlyDifferences(boolean value)             { showOnlyDifferences.set(value); }

    public ReadOnlyObjectProperty<FileDiffRequest> pendingFileDiffProperty() {
        return pendingFileDiff;
    }

    public FileDiffRequest getPendingFileDiff() {
        return pendingFileDiff.get();
    }

    /**
     * Clears the pending file-diff request (e.g., after the shell has acted on it or the
     * user returns from the file comparison view).  Preserves all folder tree state.
     */
    public void clearPendingFileDiff() {
        pendingFileDiff.set(null);
    }

    /**
     * Re-evaluates the status of {@code node} after the user has saved changes in the
     * drill-down file comparison view (REQ-9.4).
     *
     * <p>Delegates to the internal {@code refreshSingleNode} logic which reads current
     * metadata from disk, calls {@link com.diffview.core.service.ComparisonService#evaluatePair}
     * and rebuilds the result tree.
     *
     * @param node the node whose status should be refreshed; must not be {@code null}
     */
    public void refreshNodeAfterSave(DiffTreeNode node) {
        Objects.requireNonNull(node, "node");
        refreshSingleNode(node);
    }

    public SimpleObjectProperty<DiffTreeNode> selectedNodeProperty() {
        return selectedNode;
    }

    public DiffTreeNode getSelectedNode() {
        return selectedNode.get();
    }

    public void setSelectedNode(DiffTreeNode node) {
        selectedNode.set(node);
    }

    /**
     * Returns {@code true} if the directory at {@code relativePath} is currently
     * collapsed (its children are hidden from the flat visible list).
     *
     * <p>Used by {@link com.diffview.ui.FolderTreeCell} to render the correct
     * expand/collapse arrow without needing direct access to the internal set.
     *
     * @param relativePath relative path of the directory to check
     * @return {@code true} if the directory is collapsed
     */
    public boolean isCollapsed(Path relativePath) {
        return collapsedPaths.contains(relativePath);
    }

    // ── internal helpers ──────────────────────────────────────────────────────

    private void applyResult(FolderComparisonResult r) {
        result.set(r);
        identicalCount.set(r.identicalCount());
        differentCount.set(r.differentCount());
        leftOnlyCount.set(r.leftOnlyCount());
        rightOnlyCount.set(r.rightOnlyCount());
        ignoredCount.set(r.ignoredCount());
        totalCount.set(r.totalCount());
        loading.set(false);
        refreshVisibleNodes();
    }

    private void refreshVisibleNodes() {
        FolderComparisonResult r = result.get();
        if (r == null) {
            visibleNodes.set(List.of());
            return;
        }
        boolean onlyDiffs = showOnlyDifferences.get();
        List<DiffTreeNode> flat = new ArrayList<>();
        // Root node itself is the compared-directories placeholder; add its children.
        for (DiffTreeNode child : r.root().children()) {
            flattenNode(child, onlyDiffs, flat);
        }
        visibleNodes.set(Collections.unmodifiableList(flat));
    }

    /**
     * Recursively adds {@code node} to {@code out}, honouring the filter and collapse state.
     *
     * <p>Filter logic:
     * <ul>
     *   <li>If {@code onlyDiffs} is {@code true} and {@code node} is a file with a
     *       non-differing status (IDENTICAL or IGNORED) → skip.</li>
     *   <li>If {@code onlyDiffs} is {@code true} and {@code node} is a directory with
     *       {@code differenceCount() == 0} → skip the whole subtree.</li>
     * </ul>
     */
    private void flattenNode(DiffTreeNode node, boolean onlyDiffs, List<DiffTreeNode> out) {
        if (onlyDiffs) {
            if (!node.directory()) {
                if (!node.status().isDifferent()) return; // skip identical / ignored files
            } else {
                if (node.differenceCount() == 0) return; // prune dirs with no diffs
            }
        }
        out.add(node);
        if (node.directory() && !collapsedPaths.contains(node.relativePath())) {
            for (DiffTreeNode child : node.children()) {
                flattenNode(child, onlyDiffs, out);
            }
        }
    }

    /**
     * Re-evaluates the status of {@code original} after a copy and pushes updated
     * counts + visible list.
     */
    private void refreshSingleNode(DiffTreeNode original) {
        FolderComparisonResult current = result.get();
        if (current == null || currentOptions == null) return;

        Path leftAbsolute  = currentLeft.resolve(original.relativePath());
        Path rightAbsolute = currentRight.resolve(original.relativePath());

        FileMeta newLeft  = readMeta(leftAbsolute,  original.relativePath());
        FileMeta newRight = readMeta(rightAbsolute, original.relativePath());

        FolderItemStatus newStatus =
                comparisonService.evaluatePair(newLeft, newRight, currentOptions);

        DiffTreeNode updated = new DiffTreeNode(
                original.relativePath(),
                original.directory(),
                newLeft, newRight,
                newStatus,
                original.children());

        DiffTreeNode newRoot =
                replaceNode(current.root(), original.relativePath(), updated);
        FolderComparisonResult newResult =
                FolderComparisonResult.fromRoot(newRoot, currentLeft, currentRight);
        applyResult(newResult);
    }

    // ── static helpers ────────────────────────────────────────────────────────

    private static FileMeta readMeta(Path absolute, Path relative) {
        if (absolute == null || !Files.exists(absolute)) return null;
        try {
            boolean isDir = Files.isDirectory(absolute);
            long size = isDir ? 0L : Files.size(absolute);
            FileTime ft = Files.getLastModifiedTime(absolute);
            return new FileMeta(absolute, relative, isDir, size, ft.toInstant());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns a copy of {@code root} with the node at {@code targetRelPath} replaced by
     * {@code replacement}.  If {@code targetRelPath} is not found, returns {@code root}
     * unchanged.
     */
    private static DiffTreeNode replaceNode(DiffTreeNode root,
                                            Path targetRelPath,
                                            DiffTreeNode replacement) {
        if (root.relativePath().equals(targetRelPath)) return replacement;
        List<DiffTreeNode> newChildren = root.children().stream()
                .map(child -> replaceNode(child, targetRelPath, replacement))
                .collect(Collectors.toList());
        return new DiffTreeNode(root.relativePath(), root.directory(),
                root.left(), root.right(), root.status(), newChildren);
    }

    private static void collectDirectoryRelativePaths(DiffTreeNode node, Set<Path> out) {
        if (node.directory()) {
            out.add(node.relativePath());
            node.children().forEach(c -> collectDirectoryRelativePaths(c, out));
        }
    }

    private static void copyDirectoryTree(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.sorted(Comparator.reverseOrder())
                      .forEach(p -> {
                          try { Files.delete(p); }
                          catch (IOException e) { throw new UncheckedIOException(e); }
                      });
            }
        } else {
            Files.deleteIfExists(path);
        }
    }
}
