package com.comparetool.model;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;

/**
 * The complete result of a line-by-line text file comparison, ready for
 * rendering in the side-by-side diff view.
 *
 * <p>The {@link #rows()} list drives the virtualized renderer: each element
 * maps to one visible row (including placeholder rows for alignment).
 * The {@link #blocks()} list contains the mergeable difference groups
 * in order of appearance.
 *
 * @param rows          all display rows, ordered by position; immutable.
 * @param blocks        mergeable diff blocks in document order; immutable.
 * @param leftEncoding  detected or overridden encoding for the left file
 *                      (may be {@code null} if not yet detected).
 * @param rightEncoding detected or overridden encoding for the right file
 *                      (may be {@code null} if not yet detected).
 * @param identical     {@code true} when both files are byte-for-byte equal
 *                      under the active comparison options.
 */
public record DiffModel(
        List<DiffRow> rows,
        List<DiffBlock> blocks,
        Charset leftEncoding,
        Charset rightEncoding,
        boolean identical) {

    public DiffModel {
        Objects.requireNonNull(rows,   "rows must not be null");
        Objects.requireNonNull(blocks, "blocks must not be null");
        rows   = List.copyOf(rows);
        blocks = List.copyOf(blocks);
    }

    /** Number of difference blocks (convenience alias for {@code blocks().size()}). */
    public int differenceCount() {
        return blocks.size();
    }

    /**
     * Creates a {@code DiffModel} representing two identical files.
     * All rows are UNCHANGED, and the block list is empty.
     */
    public static DiffModel identical(List<DiffRow> rows,
                                      Charset leftEncoding,
                                      Charset rightEncoding) {
        return new DiffModel(rows, List.of(), leftEncoding, rightEncoding, true);
    }

    /**
     * Creates an empty {@code DiffModel} (e.g. when both files are empty).
     */
    public static DiffModel empty(Charset leftEncoding, Charset rightEncoding) {
        return new DiffModel(List.of(), List.of(), leftEncoding, rightEncoding, true);
    }
}
