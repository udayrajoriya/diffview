package com.comparetool.model;

/**
 * Classification of a single item (file or directory) in a folder comparison.
 *
 * <ul>
 *   <li>{@link #IDENTICAL}   - present on both sides and considered equal by the chosen match mode.</li>
 *   <li>{@link #DIFFERENT}   - present on both sides but content/metadata differs.</li>
 *   <li>{@link #LEFT_ONLY}   - present only on the left side.</li>
 *   <li>{@link #RIGHT_ONLY}  - present only on the right side.</li>
 *   <li>{@link #IGNORED}     - excluded by an ignore rule and not examined further.</li>
 * </ul>
 *
 * For directory nodes, the status is rolled up: a directory whose subtree contains at least
 * one {@link #DIFFERENT}, {@link #LEFT_ONLY}, or {@link #RIGHT_ONLY} descendant is itself
 * marked {@link #DIFFERENT}.
 */
public enum FolderItemStatus {
    IDENTICAL,
    DIFFERENT,
    LEFT_ONLY,
    RIGHT_ONLY,
    IGNORED;

    /** Returns {@code true} if this status represents a difference (any non-identical, non-ignored state). */
    public boolean isDifferent() {
        return this == DIFFERENT || this == LEFT_ONLY || this == RIGHT_ONLY;
    }

    /** Returns {@code true} if this item exists on the left side. */
    public boolean hasLeft() {
        return this != RIGHT_ONLY && this != IGNORED;
    }

    /** Returns {@code true} if this item exists on the right side. */
    public boolean hasRight() {
        return this != LEFT_ONLY && this != IGNORED;
    }
}
