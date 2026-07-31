package com.comparetool.model;

import java.util.List;
import java.util.Objects;

/**
 * One horizontally-aligned row in the side-by-side file comparison view.
 *
 * <h3>Placeholder semantics</h3>
 * <p>When a line exists on only one side, the opposite side renders an empty
 * "placeholder" row so that matching content stays on the same visual row:
 * <ul>
 *   <li>{@code leftLineNumber == null}  → left side is a placeholder (line is ADDED on right).</li>
 *   <li>{@code rightLineNumber == null} → right side is a placeholder (line is REMOVED from left).</li>
 * </ul>
 * At least one of the two line numbers must be non-null; a row where both sides
 * are absent is invalid and rejected by the compact constructor.
 *
 * @param leftLineNumber  1-based line number on the left side, or {@code null} for a placeholder.
 * @param rightLineNumber 1-based line number on the right side, or {@code null} for a placeholder.
 * @param leftText        text content of the left side (empty string {@code ""} when placeholder).
 * @param rightText       text content of the right side (empty string {@code ""} when placeholder).
 * @param kind            classification of this row.
 * @param leftSpans       intra-line highlight spans for the left side (immutable, never null).
 * @param rightSpans      intra-line highlight spans for the right side (immutable, never null).
 */
public record DiffRow(
        Integer leftLineNumber,
        Integer rightLineNumber,
        String leftText,
        String rightText,
        LineKind kind,
        List<InlineSpan> leftSpans,
        List<InlineSpan> rightSpans) {

    /** Compact constructor — validates invariants and makes span lists immutable. */
    public DiffRow {
        if (leftLineNumber == null && rightLineNumber == null) {
            throw new IllegalArgumentException(
                    "A DiffRow must have at least one non-null line number.");
        }
        Objects.requireNonNull(leftText,  "leftText must not be null");
        Objects.requireNonNull(rightText, "rightText must not be null");
        Objects.requireNonNull(kind,      "kind must not be null");
        leftSpans  = leftSpans  != null ? List.copyOf(leftSpans)  : List.of();
        rightSpans = rightSpans != null ? List.copyOf(rightSpans) : List.of();
    }

    // -----------------------------------------------------------------------
    // Placeholder queries
    // -----------------------------------------------------------------------

    /** Returns {@code true} when the left side of this row is an empty placeholder. */
    public boolean isLeftPlaceholder() {
        return leftLineNumber == null;
    }

    /** Returns {@code true} when the right side of this row is an empty placeholder. */
    public boolean isRightPlaceholder() {
        return rightLineNumber == null;
    }

    /** Returns {@code true} when either side of this row is a placeholder. */
    public boolean isPlaceholder() {
        return isLeftPlaceholder() || isRightPlaceholder();
    }

    // -----------------------------------------------------------------------
    // Factory helpers
    // -----------------------------------------------------------------------

    /** Creates an UNCHANGED row (identical content on both sides). */
    public static DiffRow unchanged(int leftLineNumber, int rightLineNumber, String text) {
        return new DiffRow(leftLineNumber, rightLineNumber, text, text,
                LineKind.UNCHANGED, List.of(), List.of());
    }

    /** Creates a CHANGED row (both sides present, content differs). */
    public static DiffRow changed(int leftLineNumber, int rightLineNumber,
                                  String leftText, String rightText) {
        return new DiffRow(leftLineNumber, rightLineNumber, leftText, rightText,
                LineKind.CHANGED, List.of(), List.of());
    }

    /**
     * Creates an ADDED row: the line exists only on the right side.
     * The left side is rendered as a placeholder (null left line number).
     */
    public static DiffRow added(int rightLineNumber, String rightText) {
        return new DiffRow(null, rightLineNumber, "", rightText,
                LineKind.ADDED, List.of(), List.of());
    }

    /**
     * Creates a REMOVED row: the line exists only on the left side.
     * The right side is rendered as a placeholder (null right line number).
     */
    public static DiffRow removed(int leftLineNumber, String leftText) {
        return new DiffRow(leftLineNumber, null, leftText, "",
                LineKind.REMOVED, List.of(), List.of());
    }
}
