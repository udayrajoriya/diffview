package com.diffview.core.folder;

import com.diffview.infra.concurrent.CancellationToken;
import com.diffview.infra.concurrent.ProgressReporter;
import com.diffview.model.DiffTreeNode;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderComparisonResult;
import com.diffview.model.ItemError;

import java.nio.file.Path;
import java.util.List;

/**
 * Recursively walks two directory trees, pairs their entries by relative path,
 * and returns a {@link FolderComparisonResult} containing the paired tree and
 * per-status summary counts.
 *
 * <p>Implementations must <em>not</em> follow symbolic links by default; symlinks
 * appear as opaque leaf entries and their targets are never traversed.
 */
public interface FolderDiffEngine {

    /**
     * Compares two directory trees and returns the paired result with summary counts.
     *
     * @param left     root of the left-side directory tree
     * @param right    root of the right-side directory tree
     * @param options  comparison options (match mode, masks, ignores, etc.)
     * @param progress receiver for progress notifications; use
     *                 {@link ProgressReporter#noOp()} to discard notifications
     * @param token    cancellation token; use {@link CancellationToken#neverCancelled()}
     *                 if cancellation is not needed
     * @return a {@link FolderComparisonResult} whose root relative path is {@code Path.of("")}
     * @throws IllegalArgumentException if either {@code left} or {@code right} is not
     *                                  an existing directory
     * @throws java.io.UncheckedIOException if either tree cannot be read
     * @throws com.diffview.infra.concurrent.CancellationException if cancelled
     */
    FolderComparisonResult compare(Path left, Path right,
                                   FolderComparisonOptions options,
                                   ProgressReporter progress,
                                   CancellationToken token);

    // ── Static utility ────────────────────────────────────────────────────────

    /**
     * Walks the paired tree rooted at {@code root} and returns a
     * {@link FolderComparisonResult} with per-status counts for every
     * non-root node in the tree (files and directories).
     *
     * <p>The root node itself is excluded from all counts.  IGNORED nodes are
     * counted but not recursed into (they are always leaves in the tree).
     *
     * <p>The returned result has an empty {@code errors} list.
     */
    static FolderComparisonResult summarize(DiffTreeNode root) {
        return summarize(root, List.of());
    }

    /**
     * Walks the paired tree rooted at {@code root} and returns a
     * {@link FolderComparisonResult} with per-status counts and the supplied
     * per-item errors (REQ-016.1).
     *
     * @param root   root node of the diff tree
     * @param errors non-fatal per-item errors collected during scanning; may be empty
     */
    static FolderComparisonResult summarize(DiffTreeNode root, List<ItemError> errors) {
        int[] counts = new int[5]; // [IDENTICAL, DIFFERENT, LEFT_ONLY, RIGHT_ONLY, IGNORED]
        for (DiffTreeNode child : root.children()) {
            accumulateCounts(child, counts);
        }
        return new FolderComparisonResult(root,
                counts[0], counts[1], counts[2], counts[3], counts[4], errors);
    }

    private static void accumulateCounts(DiffTreeNode node, int[] counts) {
        switch (node.status()) {
            case IDENTICAL  -> counts[0]++;
            case DIFFERENT  -> counts[1]++;
            case LEFT_ONLY  -> counts[2]++;
            case RIGHT_ONLY -> counts[3]++;
            case IGNORED    -> { counts[4]++; return; }
        }
        for (DiffTreeNode child : node.children()) {
            accumulateCounts(child, counts);
        }
    }
}