package com.comparetool.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A node in the paired folder-diff tree that aligns a left-side item and/or a right-side item
 * at the same {@code relativePath}.
 *
 * <p>Either {@code left} or {@code right} may be {@code null}:
 * <ul>
 *   <li>{@code left == null}  → the item exists only on the right  ({@link FolderItemStatus#RIGHT_ONLY}).</li>
 *   <li>{@code right == null} → the item exists only on the left   ({@link FolderItemStatus#LEFT_ONLY}).</li>
 *   <li>both non-null        → paired; status is {@link FolderItemStatus#IDENTICAL},
 *                              {@link FolderItemStatus#DIFFERENT}, or {@link FolderItemStatus#IGNORED}.</li>
 * </ul>
 *
 * <p>For directory nodes the {@code status} is rolled up from children: a directory containing
 * any differing descendant is marked {@link FolderItemStatus#DIFFERENT}.
 *
 * @param relativePath the path relative to the comparison root (used as the alignment key)
 * @param directory    {@code true} if this node represents a directory
 * @param left         left-side metadata, or {@code null} if absent
 * @param right        right-side metadata, or {@code null} if absent
 * @param status       comparison outcome for this node
 * @param children     ordered child nodes; empty for file nodes
 */
public record DiffTreeNode(
        Path relativePath,
        boolean directory,
        FileMeta left,
        FileMeta right,
        FolderItemStatus status,
        List<DiffTreeNode> children) {

    public DiffTreeNode {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(children, "children must not be null");
        if (left == null && right == null) {
            throw new IllegalArgumentException(
                    "DiffTreeNode must have at least one side (left and right are both null)");
        }
        children = List.copyOf(children);
    }

    // ── Convenience factories ──────────────────────────────────────────────

    /** Creates a paired (both-sides present) node. */
    public static DiffTreeNode paired(Path relativePath, boolean directory,
                                      FileMeta left, FileMeta right,
                                      FolderItemStatus status, List<DiffTreeNode> children) {
        return new DiffTreeNode(relativePath, directory, left, right, status, children);
    }

    /** Creates a left-only node ({@code right == null}). */
    public static DiffTreeNode leftOnly(Path relativePath, boolean directory,
                                        FileMeta left, List<DiffTreeNode> children) {
        return new DiffTreeNode(relativePath, directory, left, null,
                FolderItemStatus.LEFT_ONLY, children);
    }

    /** Creates a right-only node ({@code left == null}). */
    public static DiffTreeNode rightOnly(Path relativePath, boolean directory,
                                         FileMeta right, List<DiffTreeNode> children) {
        return new DiffTreeNode(relativePath, directory, null, right,
                FolderItemStatus.RIGHT_ONLY, children);
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    /** Returns {@code true} if this node has no left-side entry (right-only). */
    public boolean isLeftPlaceholder() {
        return left == null;
    }

    /** Returns {@code true} if this node has no right-side entry (left-only). */
    public boolean isRightPlaceholder() {
        return right == null;
    }

    /** Returns {@code true} if only one side is present. */
    public boolean isOneSided() {
        return left == null || right == null;
    }

    /** Returns {@code true} if this is a leaf file node (not a directory). */
    public boolean isFile() {
        return !directory;
    }

    /**
     * Returns the total number of leaf (file) descendants with a status that indicates
     * a difference: {@link FolderItemStatus#DIFFERENT}, {@link FolderItemStatus#LEFT_ONLY},
     * or {@link FolderItemStatus#RIGHT_ONLY}.
     */
    public int differenceCount() {
        if (!directory) {
            return status.isDifferent() ? 1 : 0;
        }
        int count = 0;
        for (DiffTreeNode child : children) {
            count += child.differenceCount();
        }
        return count;
    }
}
