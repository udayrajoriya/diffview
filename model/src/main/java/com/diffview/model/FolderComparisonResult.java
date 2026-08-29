package com.comparetool.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The top-level result of a folder comparison, pairing the paired {@link DiffTreeNode} tree
 * with pre-aggregated summary counts and any non-fatal per-item errors encountered during
 * the scan (REQ-016.1 — fail-soft).
 *
 * <p>Counts are over <em>leaf file nodes</em> only; directories themselves are not counted.
 *
 * @param root           root node of the paired diff tree (represents the compared directories)
 * @param identicalCount number of files present on both sides and considered identical
 * @param differentCount number of files present on both sides but different
 * @param leftOnlyCount  number of files present only on the left side
 * @param rightOnlyCount number of files present only on the right side
 * @param ignoredCount   number of items excluded by ignore rules
 * @param errors         non-fatal errors for items that could not be read; empty when clean
 */
public record FolderComparisonResult(
        DiffTreeNode root,
        int identicalCount,
        int differentCount,
        int leftOnlyCount,
        int rightOnlyCount,
        int ignoredCount,
        List<ItemError> errors) {

    /** Canonical constructor — validates all components. */
    public FolderComparisonResult {
        Objects.requireNonNull(root,   "root must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        if (identicalCount < 0) throw new IllegalArgumentException("identicalCount < 0");
        if (differentCount < 0) throw new IllegalArgumentException("differentCount < 0");
        if (leftOnlyCount  < 0) throw new IllegalArgumentException("leftOnlyCount < 0");
        if (rightOnlyCount < 0) throw new IllegalArgumentException("rightOnlyCount < 0");
        if (ignoredCount   < 0) throw new IllegalArgumentException("ignoredCount < 0");
        errors = List.copyOf(errors);
    }

    /**
     * Backwards-compatible 6-param constructor — equivalent to passing an empty errors list.
     * Used by {@link com.comparetool.core.folder.FolderDiffEngine#summarize(DiffTreeNode)} and
     * any existing callers that predate the {@code errors} field.
     */
    public FolderComparisonResult(DiffTreeNode root,
                                  int identicalCount, int differentCount,
                                  int leftOnlyCount,  int rightOnlyCount,
                                  int ignoredCount) {
        this(root, identicalCount, differentCount, leftOnlyCount, rightOnlyCount,
                ignoredCount, List.of());
    }

    // ── Derived query methods ─────────────────────────────────────────────────

    /** Total number of items scanned (identical + different + left-only + right-only + ignored). */
    public int totalCount() {
        return identicalCount + differentCount + leftOnlyCount + rightOnlyCount + ignoredCount;
    }

    /** Total number of items that differ in any way (different + left-only + right-only). */
    public int totalDifferenceCount() {
        return differentCount + leftOnlyCount + rightOnlyCount;
    }

    /** Returns {@code true} if every paired file is identical and there are no one-sided files. */
    public boolean isFullyIdentical() {
        return differentCount == 0 && leftOnlyCount == 0 && rightOnlyCount == 0;
    }

    /** Returns {@code true} if any per-item errors were recorded during the comparison scan. */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Builds a {@code FolderComparisonResult} by walking the provided tree, computing all
     * counts automatically, and recording no errors.
     *
     * @param root      the root node of the already-built diff tree
     * @param leftRoot  absolute path of the left comparison root (informational only)
     * @param rightRoot absolute path of the right comparison root (informational only)
     * @return a fully populated result with an empty errors list
     */
    public static FolderComparisonResult fromRoot(DiffTreeNode root,
                                                  @SuppressWarnings("unused") Path leftRoot,
                                                  @SuppressWarnings("unused") Path rightRoot) {
        return fromRoot(root, leftRoot, rightRoot, List.of());
    }

    /**
     * Builds a {@code FolderComparisonResult} by walking the provided tree, computing all
     * counts automatically, and attaching the supplied per-item errors.
     *
     * @param root      the root node of the already-built diff tree
     * @param leftRoot  absolute path of the left comparison root (informational only)
     * @param rightRoot absolute path of the right comparison root (informational only)
     * @param errors    non-fatal per-item errors recorded during scanning; may be empty
     * @return a fully populated result
     */
    public static FolderComparisonResult fromRoot(DiffTreeNode root,
                                                  @SuppressWarnings("unused") Path leftRoot,
                                                  @SuppressWarnings("unused") Path rightRoot,
                                                  List<ItemError> errors) {
        Objects.requireNonNull(root,   "root must not be null");
        Objects.requireNonNull(errors, "errors must not be null");
        int[] counts = new int[5]; // identical, different, leftOnly, rightOnly, ignored
        accumulateCounts(root, counts);
        return new FolderComparisonResult(root,
                counts[0], counts[1], counts[2], counts[3], counts[4], errors);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void accumulateCounts(DiffTreeNode node, int[] counts) {
        if (!node.directory()) {
            switch (node.status()) {
                case IDENTICAL   -> counts[0]++;
                case DIFFERENT   -> counts[1]++;
                case LEFT_ONLY   -> counts[2]++;
                case RIGHT_ONLY  -> counts[3]++;
                case IGNORED     -> counts[4]++;
            }
            return;
        }
        for (DiffTreeNode child : node.children()) {
            accumulateCounts(child, counts);
        }
    }
}
