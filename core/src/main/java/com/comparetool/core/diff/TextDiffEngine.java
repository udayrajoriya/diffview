package com.comparetool.core.diff;

import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DiffModel;
import com.comparetool.model.InlineSpan;

import java.util.List;

/**
 * Computes line-level and intra-line differences between two text documents.
 *
 * <h3>Line-level diff ({@link #diff})</h3>
 * <p>Returns a fully aligned {@link DiffModel}: every row has a left and/or right
 * line number so that matching content sits on the same visual row in the
 * side-by-side view (REQ-002). Rows where one side is absent are "placeholder"
 * rows; their line number is {@code null} on the absent side.
 *
 * <h3>Intra-line diff ({@link #inlineDiff})</h3>
 * <p>Returns the changed character spans within a single pair of CHANGED lines
 * (REQ-006). This is intentionally separated so callers can evaluate it lazily
 * (e.g. only for the rows currently visible on screen).
 */
public interface TextDiffEngine {

    /**
     * Computes a full line-level diff between {@code leftLines} and {@code rightLines}.
     *
     * <p>The returned {@link DiffModel} contains:
     * <ul>
     *   <li>One {@link com.comparetool.model.DiffRow} per visible row (including
     *       placeholder rows for alignment).</li>
     *   <li>One {@link com.comparetool.model.DiffBlock} per contiguous run of
     *       CHANGED / ADDED / REMOVED rows (UNCHANGED rows are not grouped into blocks).</li>
     * </ul>
     *
     * @param leftLines  lines of the left (original) document; must not be null
     * @param rightLines lines of the right (revised) document; must not be null
     * @param options    comparison options (ignore-whitespace, ignore-case, etc.);
     *                   must not be null
     * @return a fully populated {@link DiffModel}, never null
     */
    DiffModel diff(List<String> leftLines, List<String> rightLines, ComparisonOptions options);

    /**
     * Computes intra-line changed spans for a single pair of CHANGED lines.
     *
     * <p>The returned lists contain zero-based character offsets marking the
     * changed tokens within {@code leftLine} and {@code rightLine} respectively.
     * An empty list means the whole line is considered changed (or the engine
     * does not support span-level highlighting for this pair).
     *
     * @param leftLine  the original line text; must not be null
     * @param rightLine the revised line text; must not be null
     * @param options   comparison options; must not be null
     * @return a two-element array: {@code [0]} = left spans, {@code [1]} = right spans
     */
    List<InlineSpan>[] inlineDiff(String leftLine, String rightLine, ComparisonOptions options);
}
