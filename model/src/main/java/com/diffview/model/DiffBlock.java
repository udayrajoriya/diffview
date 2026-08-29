package com.diffview.model;

import java.util.Objects;

/**
 * A contiguous, mergeable group of changed rows within a {@link DiffModel}.
 *
 * <p>Indices are zero-based positions into {@link DiffModel#rows()}.
 * A block always spans at least one row ({@code firstRowIndex <= lastRowIndex}).
 *
 * @param firstRowIndex zero-based index of the first row in this block (>= 0).
 * @param lastRowIndex  zero-based index of the last row in this block (>= firstRowIndex).
 * @param kind          the kind of all rows in this block; typically
 *                      {@link LineKind#CHANGED}, {@link LineKind#ADDED},
 *                      or {@link LineKind#REMOVED}.
 */
public record DiffBlock(int firstRowIndex, int lastRowIndex, LineKind kind) {

    public DiffBlock {
        if (firstRowIndex < 0) {
            throw new IllegalArgumentException(
                    "firstRowIndex must be >= 0, got: " + firstRowIndex);
        }
        if (lastRowIndex < firstRowIndex) {
            throw new IllegalArgumentException(
                    "lastRowIndex must be >= firstRowIndex, got lastRowIndex="
                    + lastRowIndex + " firstRowIndex=" + firstRowIndex);
        }
        Objects.requireNonNull(kind, "kind must not be null");
    }

    /** Number of rows spanned by this block (always >= 1). */
    public int rowCount() {
        return lastRowIndex - firstRowIndex + 1;
    }

    /** Returns {@code true} if this block covers exactly one row. */
    public boolean isSingleRow() {
        return firstRowIndex == lastRowIndex;
    }
}
