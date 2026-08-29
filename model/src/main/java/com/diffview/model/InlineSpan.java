package com.diffview.model;

/**
 * A contiguous run of characters within a single diff line that is highlighted
 * for intra-line (word or character level) difference display.
 *
 * <p>Offsets are zero-based character indices into the line's text string.
 * {@code startOffset == endOffset} represents a zero-length (insertion-point)
 * span, which is valid.
 *
 * @param startOffset first character index of the changed run (inclusive, >= 0)
 * @param endOffset   one-past-the-last character index of the changed run (>= startOffset)
 */
public record InlineSpan(int startOffset, int endOffset) {

    public InlineSpan {
        if (startOffset < 0) {
            throw new IllegalArgumentException(
                    "startOffset must be >= 0, got: " + startOffset);
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException(
                    "endOffset must be >= startOffset, got endOffset=" + endOffset
                    + " startOffset=" + startOffset);
        }
    }

    /** Number of characters covered by this span. */
    public int length() {
        return endOffset - startOffset;
    }

    /** Returns {@code true} if this span covers zero characters. */
    public boolean isEmpty() {
        return startOffset == endOffset;
    }
}
