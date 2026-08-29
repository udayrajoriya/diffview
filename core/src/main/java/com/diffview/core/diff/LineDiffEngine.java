package com.diffview.core.diff;

import com.diffview.model.ComparisonOptions;
import com.diffview.model.DiffBlock;
import com.diffview.model.DiffModel;
import com.diffview.model.DiffRow;
import com.diffview.model.InlineSpan;
import com.diffview.model.LineKind;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Myers-algorithm line-level diff engine backed by {@code java-diff-utils}.
 *
 * <h3>Alignment strategy</h3>
 * <p>Each INSERT delta produces ADDED rows (left side is a placeholder).
 * Each DELETE delta produces REMOVED rows (right side is a placeholder).
 * Each CHANGE delta pairs lines 1-to-1 as CHANGED; if one side has more lines
 * than the other, the unpaired lines become REMOVED (extra left) or ADDED
 * (extra right).
 *
 * <h3>Block grouping</h3>
 * <p>Contiguous runs of rows with the same non-UNCHANGED kind form a single
 * {@link DiffBlock}. UNCHANGED rows are not included in blocks; this keeps
 * {@link DiffModel#differenceCount()} equal to the number of mergeable hunks.
 *
 * <h3>Intra-line diff</h3>
 * <p>Both sides of a CHANGED row are tokenized into alternating word/non-word
 * tokens. Myers diff runs on the token list to find changed tokens, which are
 * then mapped back to character-offset {@link InlineSpan}s. Adjacent spans are
 * merged into a single span for a cleaner highlight.
 *
 * <p>Lines longer than {@link #MAX_INLINE_CHARS} characters are skipped
 * (empty spans returned) to keep worst-case O(ND) memory/time bounded.
 */
public class LineDiffEngine implements TextDiffEngine {

    /**
     * Lines longer than this many characters skip intra-line highlighting to
     * bound worst-case computation.
     */
    static final int MAX_INLINE_CHARS = 10_000;

    /**
     * Creates a new {@code LineDiffEngine}.
     * This engine has no mutable state and is safe for concurrent use.
     */
    public LineDiffEngine() { }

    // -----------------------------------------------------------------------
    // TextDiffEngine implementation
    // -----------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public DiffModel diff(List<String> leftLines,
                          List<String> rightLines,
                          ComparisonOptions options) {
        Objects.requireNonNull(leftLines,  "leftLines must not be null");
        Objects.requireNonNull(rightLines, "rightLines must not be null");
        Objects.requireNonNull(options,    "options must not be null");

        // Resolve encoding from options, fall back to UTF-8 when not specified.
        Charset leftCharset  = resolveCharset(options.leftEncodingOverride());
        Charset rightCharset = resolveCharset(options.rightEncodingOverride());

        // Fast path: both documents empty.
        if (leftLines.isEmpty() && rightLines.isEmpty()) {
            return DiffModel.empty(leftCharset, rightCharset);
        }

        // Compute patch (common prefix/suffix skipped automatically by Myers).
        // Normalize lines according to options so that ignored differences do
        // not produce diff deltas; original lines are preserved in the DiffRows.
        List<String> leftComp  = normalizeLines(leftLines,  options);
        List<String> rightComp = normalizeLines(rightLines, options);
        Patch<String> patch = DiffUtils.diff(leftComp, rightComp);

        // Fast path: no deltas → files are identical under the active options.
        if (patch.getDeltas().isEmpty()) {
            List<DiffRow> rows = buildUnchangedRows(leftLines, rightLines);
            return DiffModel.identical(rows, leftCharset, rightCharset);
        }

        // Walk deltas and build aligned rows.
        List<DiffRow> rows = buildRows(leftLines, rightLines, patch);

        // Group non-UNCHANGED runs into DiffBlocks.
        List<DiffBlock> blocks = buildBlocks(rows);

        // The diff has differences only when at least one block exists.
        boolean identical = blocks.isEmpty();

        return new DiffModel(rows, blocks, leftCharset, rightCharset, identical);
    }

    /**
     * Computes intra-line changed spans by tokenizing each line into word and
     * non-word tokens, running Myers diff on the token lists, and mapping
     * changed tokens back to character-offset {@link InlineSpan}s.
     *
     * <p>Returns two empty lists when either line exceeds {@link #MAX_INLINE_CHARS}
     * or when the lines are identical, avoiding unnecessary work.
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<InlineSpan>[] inlineDiff(String leftLine,
                                         String rightLine,
                                         ComparisonOptions options) {
        Objects.requireNonNull(leftLine,  "leftLine must not be null");
        Objects.requireNonNull(rightLine, "rightLine must not be null");
        Objects.requireNonNull(options,   "options must not be null");

        // Cap: skip highlighting for very long lines to bound O(ND) cost.
        if (leftLine.length() > MAX_INLINE_CHARS || rightLine.length() > MAX_INLINE_CHARS) {
            return new List[]{List.of(), List.of()};
        }

        // Fast path: identical lines need no highlighting.
        if (leftLine.equals(rightLine)) {
            return new List[]{List.of(), List.of()};
        }

        // Tokenize each line into word / non-word token runs.
        List<Token> leftTokens  = tokenize(leftLine);
        List<Token> rightTokens = tokenize(rightLine);

        // Run Myers diff on the token-text lists.
        List<String> leftTexts  = leftTokens.stream().map(Token::text).toList();
        List<String> rightTexts = rightTokens.stream().map(Token::text).toList();
        Patch<String> patch = DiffUtils.diff(leftTexts, rightTexts);

        // Collect InlineSpans from each delta.
        List<InlineSpan> leftSpans  = new ArrayList<>();
        List<InlineSpan> rightSpans = new ArrayList<>();

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            switch (delta.getType()) {
                case DELETE -> {
                    int pos = delta.getSource().getPosition();
                    for (int i = 0; i < delta.getSource().getLines().size(); i++) {
                        Token t = leftTokens.get(pos + i);
                        leftSpans.add(new InlineSpan(t.start(), t.end()));
                    }
                }
                case INSERT -> {
                    int pos = delta.getTarget().getPosition();
                    for (int i = 0; i < delta.getTarget().getLines().size(); i++) {
                        Token t = rightTokens.get(pos + i);
                        rightSpans.add(new InlineSpan(t.start(), t.end()));
                    }
                }
                case CHANGE -> {
                    int srcPos = delta.getSource().getPosition();
                    for (int i = 0; i < delta.getSource().getLines().size(); i++) {
                        Token t = leftTokens.get(srcPos + i);
                        leftSpans.add(new InlineSpan(t.start(), t.end()));
                    }
                    int tgtPos = delta.getTarget().getPosition();
                    for (int i = 0; i < delta.getTarget().getLines().size(); i++) {
                        Token t = rightTokens.get(tgtPos + i);
                        rightSpans.add(new InlineSpan(t.start(), t.end()));
                    }
                }
                case EQUAL -> { /* no spans needed for equal tokens */ }
            }
        }

        return new List[]{mergeSpans(leftSpans), mergeSpans(rightSpans)};
    }

    // -----------------------------------------------------------------------
    // Row-building helpers
    // -----------------------------------------------------------------------

    private static List<DiffRow> buildUnchangedRows(List<String> leftLines, List<String> rightLines) {
        List<DiffRow> rows = new ArrayList<>(leftLines.size());
        for (int i = 0; i < leftLines.size(); i++) {
            rows.add(unchangedRow(i + 1, i + 1, leftLines.get(i), rightLines.get(i)));
        }
        return rows;
    }

    private static List<DiffRow> buildRows(List<String> leftLines,
                                           List<String> rightLines,
                                           Patch<String> patch) {
        List<DiffRow> rows = new ArrayList<>();
        // 0-based cursors tracking how far we have consumed each side.
        int leftPos  = 0;
        int rightPos = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            // Emit UNCHANGED rows that precede this delta.
            int deltaLeftPos = delta.getSource().getPosition();
            while (leftPos < deltaLeftPos) {
                rows.add(unchangedRow(leftPos + 1, rightPos + 1,
                        leftLines.get(leftPos), rightLines.get(rightPos)));
                leftPos++;
                rightPos++;
            }

            switch (delta.getType()) {
                case INSERT -> {
                    // Lines added on the right; left side has no corresponding lines.
                    for (String line : delta.getTarget().getLines()) {
                        rows.add(DiffRow.added(rightPos + 1, line));
                        rightPos++;
                    }
                }
                case DELETE -> {
                    // Lines removed from the left; right side has no corresponding lines.
                    for (String line : delta.getSource().getLines()) {
                        rows.add(DiffRow.removed(leftPos + 1, line));
                        leftPos++;
                    }
                }
                case CHANGE -> {
                    List<String> srcLines = delta.getSource().getLines();
                    List<String> tgtLines = delta.getTarget().getLines();
                    int common = Math.min(srcLines.size(), tgtLines.size());

                    // 1-to-1 CHANGED pairs for the common prefix.
                    for (int i = 0; i < common; i++) {
                        rows.add(DiffRow.changed(leftPos + 1, rightPos + 1,
                                srcLines.get(i), tgtLines.get(i)));
                        leftPos++;
                        rightPos++;
                    }
                    // Extra left-side lines become REMOVED.
                    for (int i = common; i < srcLines.size(); i++) {
                        rows.add(DiffRow.removed(leftPos + 1, srcLines.get(i)));
                        leftPos++;
                    }
                    // Extra right-side lines become ADDED.
                    for (int i = common; i < tgtLines.size(); i++) {
                        rows.add(DiffRow.added(rightPos + 1, tgtLines.get(i)));
                        rightPos++;
                    }
                }
                case EQUAL -> {
                    // Not normally emitted by DiffUtils.diff(), but handled defensively.
                    for (String line : delta.getSource().getLines()) {
                        rows.add(DiffRow.unchanged(leftPos + 1, rightPos + 1, line));
                        leftPos++;
                        rightPos++;
                    }
                }
            }
        }

        // Emit any UNCHANGED rows after the last delta.
        while (leftPos < leftLines.size()) {
            rows.add(unchangedRow(leftPos + 1, rightPos + 1,
                    leftLines.get(leftPos), rightLines.get(rightPos)));
            leftPos++;
            rightPos++;
        }

        return rows;
    }

    // -----------------------------------------------------------------------
    // Block-building helper
    // -----------------------------------------------------------------------

    /**
     * Creates an UNCHANGED row whose {@code leftText} and {@code rightText} may
     * differ (e.g. normalised-equal but different original indentation).  When
     * they are the same string the standard factory is used; otherwise the raw
     * constructor preserves both originals.
     */
    private static DiffRow unchangedRow(int leftNum, int rightNum,
                                        String leftText, String rightText) {
        if (leftText.equals(rightText)) {
            return DiffRow.unchanged(leftNum, rightNum, leftText);
        }
        return new DiffRow(leftNum, rightNum, leftText, rightText,
                LineKind.UNCHANGED, List.of(), List.of());
    }

    // -----------------------------------------------------------------------
    // Block-building helper
    // -----------------------------------------------------------------------
    private static List<DiffBlock> buildBlocks(List<DiffRow> rows) {
        List<DiffBlock> blocks = new ArrayList<>();
        int i = 0;
        int size = rows.size();
        while (i < size) {
            LineKind kind = rows.get(i).kind();
            if (kind != LineKind.UNCHANGED) {
                int start = i;
                // Extend the run while the kind stays the same.
                while (i < size && rows.get(i).kind() == kind) {
                    i++;
                }
                blocks.add(new DiffBlock(start, i - 1, kind));
            } else {
                i++;
            }
        }
        return blocks;
    }

    // -----------------------------------------------------------------------
    // Option normalization
    // -----------------------------------------------------------------------

    /**
     * Returns a list where each line has been normalized according to the
     * active ignore flags.  The returned list has the same size as {@code lines};
     * only the string content may differ.  Returns the original list unchanged
     * when no flags are active to avoid unnecessary allocation.
     */
    private static List<String> normalizeLines(List<String> lines, ComparisonOptions options) {
        if (!options.hasAnyIgnoreFlag()) {
            return lines; // fast path
        }
        return lines.stream().map(l -> normalizeLine(l, options)).toList();
    }

    /**
     * Applies the active ignore transformations to a single line in order:
     * <ol>
     *   <li>{@code ignoreLineEndings} — strip embedded {@code \r} characters.</li>
     *   <li>{@code ignoreWhitespace}  — trim leading/trailing whitespace and
     *       collapse all internal whitespace runs to a single space.</li>
     *   <li>{@code ignoreCase}        — fold to lower-case (root locale).</li>
     * </ol>
     */
    static String normalizeLine(String line, ComparisonOptions options) {
        String result = line;
        if (options.ignoreLineEndings()) {
            result = result.replace("\r", "");
        }
        if (options.ignoreWhitespace()) {
            result = result.strip();
            result = result.replaceAll("\\s+", " ");
        }
        if (options.ignoreCase()) {
            result = result.toLowerCase();
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Tokenizer
    // -----------------------------------------------------------------------

    /**
     * A token carrying its text and its zero-based start offset in the source line.
     * {@link #end()} is the exclusive end offset (= {@code start + text.length()}).
     */
    private record Token(String text, int start) {
        int end() { return start + text.length(); }
    }

    /**
     * Splits {@code line} into alternating word (alphanumeric + underscore) and
     * non-word runs. Each run becomes a single token carrying its start offset.
     * This granularity gives word-level highlighting for prose and symbol changes,
     * while still producing fine-grained spans for punctuation and whitespace.
     */
    static List<Token> tokenize(String line) {
        if (line.isEmpty()) {
            return List.of();
        }
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = line.length();
        while (i < len) {
            int start = i;
            boolean wordChar = isWordChar(line.charAt(i));
            // Consume a run of the same character class.
            while (i < len && isWordChar(line.charAt(i)) == wordChar) {
                i++;
            }
            tokens.add(new Token(line.substring(start, i), start));
        }
        return tokens;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // -----------------------------------------------------------------------
    // Span merging
    // -----------------------------------------------------------------------

    /**
     * Sorts {@code spans} by start offset and merges any adjacent or overlapping
     * entries into a single span.  Returns an unmodifiable list.
     */
    static List<InlineSpan> mergeSpans(List<InlineSpan> spans) {
        if (spans.isEmpty()) {
            return List.of();
        }
        List<InlineSpan> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingInt(InlineSpan::startOffset));

        List<InlineSpan> merged = new ArrayList<>();
        InlineSpan current = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            InlineSpan next = sorted.get(i);
            if (next.startOffset() <= current.endOffset()) {
                // Overlapping or adjacent — extend the current span.
                current = new InlineSpan(current.startOffset(),
                        Math.max(current.endOffset(), next.endOffset()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return List.copyOf(merged);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static Charset resolveCharset(Charset override) {
        return override != null ? override : StandardCharsets.UTF_8;
    }
}
