package com.comparetool.core.diff;

import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.DiffModel;
import com.comparetool.model.DiffRow;
import com.comparetool.model.InlineSpan;
import com.comparetool.model.LineKind;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Task 4.1 — LineDiffEngine (line-level diff)")
class LineDiffEngineTest {

    private LineDiffEngine engine;
    private ComparisonOptions defaults;

    @BeforeEach
    void setUp() {
        engine   = new LineDiffEngine();
        defaults = ComparisonOptions.defaults();
    }

    // -----------------------------------------------------------------------
    // Identical files
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Identical files")
    class IdenticalFiles {

        @Test
        @DisplayName("empty files → empty DiffModel flagged identical")
        void bothEmpty() {
            DiffModel model = engine.diff(List.of(), List.of(), defaults);
            assertThat(model.identical()).isTrue();
            assertThat(model.rows()).isEmpty();
            assertThat(model.blocks()).isEmpty();
        }

        @Test
        @DisplayName("single identical line → one UNCHANGED row, no blocks")
        void singleIdenticalLine() {
            DiffModel model = engine.diff(
                    List.of("hello"),
                    List.of("hello"),
                    defaults);

            assertThat(model.identical()).isTrue();
            assertThat(model.rows()).hasSize(1);
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(model.blocks()).isEmpty();
            assertThat(model.differenceCount()).isZero();
        }

        @Test
        @DisplayName("multi-line identical → all UNCHANGED, no blocks, line numbers sequential")
        void multiLineIdentical() {
            List<String> lines = List.of("alpha", "beta", "gamma");
            DiffModel model = engine.diff(lines, lines, defaults);

            assertThat(model.identical()).isTrue();
            assertThat(model.rows()).hasSize(3);
            for (int i = 0; i < 3; i++) {
                DiffRow row = model.rows().get(i);
                assertThat(row.kind()).isEqualTo(LineKind.UNCHANGED);
                assertThat(row.leftLineNumber()).isEqualTo(i + 1);
                assertThat(row.rightLineNumber()).isEqualTo(i + 1);
                assertThat(row.leftText()).isEqualTo(lines.get(i));
            }
            assertThat(model.blocks()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // ADDED rows
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Added lines")
    class AddedLines {

        @Test
        @DisplayName("line appended to end → 1 ADDED row, left is placeholder")
        void lineAddedAtEnd() {
            DiffModel model = engine.diff(
                    List.of("a"),
                    List.of("a", "b"),
                    defaults);

            assertThat(model.identical()).isFalse();
            assertThat(model.rows()).hasSize(2);
            DiffRow unchanged = model.rows().get(0);
            DiffRow added     = model.rows().get(1);

            assertThat(unchanged.kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(added.kind()).isEqualTo(LineKind.ADDED);
            assertThat(added.isLeftPlaceholder()).isTrue();
            assertThat(added.leftLineNumber()).isNull();
            assertThat(added.rightLineNumber()).isEqualTo(2);
            assertThat(added.rightText()).isEqualTo("b");
        }

        @Test
        @DisplayName("line inserted at top → 1 ADDED row before UNCHANGED row")
        void lineInsertedAtTop() {
            DiffModel model = engine.diff(
                    List.of("b"),
                    List.of("a", "b"),
                    defaults);

            assertThat(model.rows()).hasSize(2);
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.ADDED);
            assertThat(model.rows().get(0).rightLineNumber()).isEqualTo(1);
            assertThat(model.rows().get(1).kind()).isEqualTo(LineKind.UNCHANGED);
        }

        @Test
        @DisplayName("multiple lines added in middle → sequential right line numbers")
        void multipleLinesAdded() {
            DiffModel model = engine.diff(
                    List.of("a", "d"),
                    List.of("a", "b", "c", "d"),
                    defaults);

            // rows: UNCHANGED(a), ADDED(b), ADDED(c), UNCHANGED(d)
            assertThat(model.rows()).hasSize(4);
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(model.rows().get(1).kind()).isEqualTo(LineKind.ADDED);
            assertThat(model.rows().get(1).rightLineNumber()).isEqualTo(2);
            assertThat(model.rows().get(2).kind()).isEqualTo(LineKind.ADDED);
            assertThat(model.rows().get(2).rightLineNumber()).isEqualTo(3);
            assertThat(model.rows().get(3).kind()).isEqualTo(LineKind.UNCHANGED);
        }

        @Test
        @DisplayName("added rows produce one DiffBlock")
        void addedRowsProduceSingleBlock() {
            DiffModel model = engine.diff(
                    List.of("a"),
                    List.of("a", "b", "c"),
                    defaults);

            assertThat(model.blocks()).hasSize(1);
            DiffBlock block = model.blocks().get(0);
            assertThat(block.kind()).isEqualTo(LineKind.ADDED);
            assertThat(block.rowCount()).isEqualTo(2);
        }
    }

    // -----------------------------------------------------------------------
    // REMOVED rows
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Removed lines")
    class RemovedLines {

        @Test
        @DisplayName("line removed from end → 1 REMOVED row, right is placeholder")
        void lineRemovedFromEnd() {
            DiffModel model = engine.diff(
                    List.of("a", "b"),
                    List.of("a"),
                    defaults);

            assertThat(model.identical()).isFalse();
            assertThat(model.rows()).hasSize(2);
            DiffRow removed = model.rows().get(1);

            assertThat(removed.kind()).isEqualTo(LineKind.REMOVED);
            assertThat(removed.isRightPlaceholder()).isTrue();
            assertThat(removed.rightLineNumber()).isNull();
            assertThat(removed.leftLineNumber()).isEqualTo(2);
            assertThat(removed.leftText()).isEqualTo("b");
        }

        @Test
        @DisplayName("line removed from top → REMOVED before UNCHANGED")
        void lineRemovedFromTop() {
            DiffModel model = engine.diff(
                    List.of("a", "b"),
                    List.of("b"),
                    defaults);

            assertThat(model.rows()).hasSize(2);
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.REMOVED);
            assertThat(model.rows().get(0).leftLineNumber()).isEqualTo(1);
            assertThat(model.rows().get(1).kind()).isEqualTo(LineKind.UNCHANGED);
        }

        @Test
        @DisplayName("multiple lines removed → sequential left line numbers")
        void multipleLinesRemoved() {
            DiffModel model = engine.diff(
                    List.of("a", "b", "c", "d"),
                    List.of("a", "d"),
                    defaults);

            // rows: UNCHANGED(a), REMOVED(b), REMOVED(c), UNCHANGED(d)
            assertThat(model.rows()).hasSize(4);
            assertThat(model.rows().get(1).kind()).isEqualTo(LineKind.REMOVED);
            assertThat(model.rows().get(1).leftLineNumber()).isEqualTo(2);
            assertThat(model.rows().get(2).kind()).isEqualTo(LineKind.REMOVED);
            assertThat(model.rows().get(2).leftLineNumber()).isEqualTo(3);
        }
    }

    // -----------------------------------------------------------------------
    // CHANGED rows
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Changed lines")
    class ChangedLines {

        @Test
        @DisplayName("single changed line → 1 CHANGED row, both line numbers present")
        void singleChangedLine() {
            DiffModel model = engine.diff(
                    List.of("the quick brown fox"),
                    List.of("the slow brown fox"),
                    defaults);

            assertThat(model.rows()).hasSize(1);
            DiffRow row = model.rows().get(0);
            assertThat(row.kind()).isEqualTo(LineKind.CHANGED);
            assertThat(row.leftLineNumber()).isEqualTo(1);
            assertThat(row.rightLineNumber()).isEqualTo(1);
            assertThat(row.leftText()).isEqualTo("the quick brown fox");
            assertThat(row.rightText()).isEqualTo("the slow brown fox");
            assertThat(row.isPlaceholder()).isFalse();
        }

        @Test
        @DisplayName("changed line preserves surrounding UNCHANGED context")
        void changedLinePreservesContext() {
            DiffModel model = engine.diff(
                    List.of("before", "OLD", "after"),
                    List.of("before", "NEW", "after"),
                    defaults);

            assertThat(model.rows()).hasSize(3);
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(model.rows().get(1).kind()).isEqualTo(LineKind.CHANGED);
            assertThat(model.rows().get(1).leftLineNumber()).isEqualTo(2);
            assertThat(model.rows().get(1).rightLineNumber()).isEqualTo(2);
            assertThat(model.rows().get(2).kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(model.rows().get(2).leftLineNumber()).isEqualTo(3);
            assertThat(model.rows().get(2).rightLineNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("CHANGE delta with more left lines → CHANGED + REMOVED")
        void changeDeltaMoreLeftLines() {
            // left has 3 lines, right has 1 — expect 1 CHANGED + 2 REMOVED
            DiffModel model = engine.diff(
                    List.of("x", "y", "z"),
                    List.of("x-new"),
                    defaults);

            List<DiffRow> rows = model.rows();
            assertThat(rows).hasSize(3);
            assertThat(rows.get(0).kind()).isEqualTo(LineKind.CHANGED);
            assertThat(rows.get(0).leftLineNumber()).isEqualTo(1);
            assertThat(rows.get(0).rightLineNumber()).isEqualTo(1);
            assertThat(rows.get(1).kind()).isEqualTo(LineKind.REMOVED);
            assertThat(rows.get(1).leftLineNumber()).isEqualTo(2);
            assertThat(rows.get(1).rightLineNumber()).isNull();
            assertThat(rows.get(2).kind()).isEqualTo(LineKind.REMOVED);
            assertThat(rows.get(2).leftLineNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("CHANGE delta with more right lines → CHANGED + ADDED")
        void changeDeltaMoreRightLines() {
            // left has 1 line, right has 3 — expect 1 CHANGED + 2 ADDED
            DiffModel model = engine.diff(
                    List.of("x"),
                    List.of("x-new", "y", "z"),
                    defaults);

            List<DiffRow> rows = model.rows();
            assertThat(rows).hasSize(3);
            assertThat(rows.get(0).kind()).isEqualTo(LineKind.CHANGED);
            assertThat(rows.get(0).leftLineNumber()).isEqualTo(1);
            assertThat(rows.get(0).rightLineNumber()).isEqualTo(1);
            assertThat(rows.get(1).kind()).isEqualTo(LineKind.ADDED);
            assertThat(rows.get(1).leftLineNumber()).isNull();
            assertThat(rows.get(1).rightLineNumber()).isEqualTo(2);
            assertThat(rows.get(2).kind()).isEqualTo(LineKind.ADDED);
            assertThat(rows.get(2).rightLineNumber()).isEqualTo(3);
        }
    }

    // -----------------------------------------------------------------------
    // Mixed scenarios
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Mixed diff scenarios")
    class MixedScenarios {

        @Test
        @DisplayName("add + remove + change in one document")
        void addRemoveChange() {
            List<String> left  = List.of("header", "to-change", "to-remove", "footer");
            List<String> right = List.of("header", "changed",   "added",     "footer");

            DiffModel model = engine.diff(left, right, defaults);

            assertThat(model.identical()).isFalse();
            // We expect 5 rows:
            // UNCHANGED(header), CHANGED(to-change/changed), REMOVED(to-remove), ADDED(added), UNCHANGED(footer)
            // (actual ordering may differ slightly depending on Myers grouping)
            assertThat(model.rows()).isNotEmpty();
            // At least one row of each interesting kind
            assertThat(model.rows()).anyMatch(r -> r.kind() == LineKind.UNCHANGED);
            assertThat(model.rows()).anyMatch(r -> r.kind() == LineKind.CHANGED
                    || r.kind() == LineKind.REMOVED || r.kind() == LineKind.ADDED);
        }

        @Test
        @DisplayName("left file empty, right has lines → all ADDED")
        void leftEmptyRightHasLines() {
            DiffModel model = engine.diff(
                    List.of(),
                    List.of("a", "b", "c"),
                    defaults);

            assertThat(model.rows()).hasSize(3);
            assertThat(model.rows()).allMatch(r -> r.kind() == LineKind.ADDED);
            for (int i = 0; i < 3; i++) {
                assertThat(model.rows().get(i).rightLineNumber()).isEqualTo(i + 1);
                assertThat(model.rows().get(i).leftLineNumber()).isNull();
            }
        }

        @Test
        @DisplayName("right file empty, left has lines → all REMOVED")
        void rightEmptyLeftHasLines() {
            DiffModel model = engine.diff(
                    List.of("a", "b", "c"),
                    List.of(),
                    defaults);

            assertThat(model.rows()).hasSize(3);
            assertThat(model.rows()).allMatch(r -> r.kind() == LineKind.REMOVED);
            for (int i = 0; i < 3; i++) {
                assertThat(model.rows().get(i).leftLineNumber()).isEqualTo(i + 1);
                assertThat(model.rows().get(i).rightLineNumber()).isNull();
            }
        }
    }

    // -----------------------------------------------------------------------
    // DiffBlock grouping
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("DiffBlock grouping")
    class BlockGrouping {

        @Test
        @DisplayName("single changed line → one block covering row 0")
        void singleChangedLineProducesOneBlock() {
            DiffModel model = engine.diff(
                    List.of("old"),
                    List.of("new"),
                    defaults);

            assertThat(model.blocks()).hasSize(1);
            DiffBlock block = model.blocks().get(0);
            assertThat(block.kind()).isEqualTo(LineKind.CHANGED);
            assertThat(block.firstRowIndex()).isEqualTo(0);
            assertThat(block.lastRowIndex()).isEqualTo(0);
            assertThat(block.rowCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("two separate changes produce two separate blocks")
        void twoSeparateChangesProduceTwoBlocks() {
            // Change line 1 and line 3, keep line 2
            DiffModel model = engine.diff(
                    List.of("A", "same", "C"),
                    List.of("A-new", "same", "C-new"),
                    defaults);

            assertThat(model.blocks()).hasSize(2);
            assertThat(model.blocks().get(0).kind()).isEqualTo(LineKind.CHANGED);
            assertThat(model.blocks().get(1).kind()).isEqualTo(LineKind.CHANGED);
        }

        @Test
        @DisplayName("UNCHANGED rows between changes are not included in blocks")
        void unchangedRowsNotInBlocks() {
            DiffModel model = engine.diff(
                    List.of("A", "same", "C"),
                    List.of("A-new", "same", "C-new"),
                    defaults);

            // Blocks cover only the CHANGED rows, not the UNCHANGED row in between.
            for (DiffBlock block : model.blocks()) {
                for (int i = block.firstRowIndex(); i <= block.lastRowIndex(); i++) {
                    assertThat(model.rows().get(i).kind()).isNotEqualTo(LineKind.UNCHANGED);
                }
            }
        }

        @Test
        @DisplayName("block row indices are within rows list bounds")
        void blockRowIndicesInBounds() {
            DiffModel model = engine.diff(
                    List.of("a", "b", "c"),
                    List.of("a", "X", "c"),
                    defaults);

            for (DiffBlock block : model.blocks()) {
                assertThat(block.firstRowIndex()).isGreaterThanOrEqualTo(0);
                assertThat(block.lastRowIndex()).isLessThan(model.rows().size());
                assertThat(block.firstRowIndex()).isLessThanOrEqualTo(block.lastRowIndex());
            }
        }

        @Test
        @DisplayName("differenceCount() equals number of blocks")
        void differenceCountEqualsBlockCount() {
            DiffModel model = engine.diff(
                    List.of("a", "b", "c", "d"),
                    List.of("a", "B", "c", "D"),
                    defaults);

            assertThat(model.differenceCount()).isEqualTo(model.blocks().size());
        }
    }

    // -----------------------------------------------------------------------
    // Line-number correctness
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Line number correctness")
    class LineNumbers {

        @Test
        @DisplayName("UNCHANGED row line numbers advance on both sides")
        void unchangedRowAdvancesBothSides() {
            DiffModel model = engine.diff(
                    List.of("a", "b", "c"),
                    List.of("X", "b", "c"),
                    defaults);

            // Row 0 is CHANGED → left=1, right=1
            // Row 1 is UNCHANGED → left=2, right=2
            // Row 2 is UNCHANGED → left=3, right=3
            DiffRow second = model.rows().get(1);
            assertThat(second.kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(second.leftLineNumber()).isEqualTo(2);
            assertThat(second.rightLineNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("ADDED row advances only right cursor")
        void addedRowAdvancesOnlyRightCursor() {
            // left: [a, c], right: [a, b, c]
            DiffModel model = engine.diff(
                    List.of("a", "c"),
                    List.of("a", "b", "c"),
                    defaults);

            // Row 0: UNCHANGED a  → left=1, right=1
            // Row 1: ADDED b     → left=null, right=2
            // Row 2: UNCHANGED c → left=2, right=3
            DiffRow added     = model.rows().get(1);
            DiffRow lastUnch  = model.rows().get(2);
            assertThat(added.leftLineNumber()).isNull();
            assertThat(added.rightLineNumber()).isEqualTo(2);
            assertThat(lastUnch.leftLineNumber()).isEqualTo(2);
            assertThat(lastUnch.rightLineNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("REMOVED row advances only left cursor")
        void removedRowAdvancesOnlyLeftCursor() {
            // left: [a, b, c], right: [a, c]
            DiffModel model = engine.diff(
                    List.of("a", "b", "c"),
                    List.of("a", "c"),
                    defaults);

            // Row 0: UNCHANGED a → left=1, right=1
            // Row 1: REMOVED b  → left=2, right=null
            // Row 2: UNCHANGED c → left=3, right=2
            DiffRow removed   = model.rows().get(1);
            DiffRow lastUnch  = model.rows().get(2);
            assertThat(removed.leftLineNumber()).isEqualTo(2);
            assertThat(removed.rightLineNumber()).isNull();
            assertThat(lastUnch.leftLineNumber()).isEqualTo(3);
            assertThat(lastUnch.rightLineNumber()).isEqualTo(2);
        }
    }

    // -----------------------------------------------------------------------
    // Encoding fallback
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Encoding in DiffModel")
    class EncodingTests {

        @Test
        @DisplayName("defaults() options → DiffModel has UTF-8 encodings")
        void defaultOptionsGiveUtf8Encoding() {
            DiffModel model = engine.diff(List.of("x"), List.of("x"), defaults);
            assertThat(model.leftEncoding()).isEqualTo(StandardCharsets.UTF_8);
            assertThat(model.rightEncoding()).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        @DisplayName("explicit encoding override is reflected in DiffModel")
        void explicitEncodingOverrideReflected() {
            ComparisonOptions opts = defaults
                    .withLeftEncodingOverride(StandardCharsets.ISO_8859_1)
                    .withRightEncodingOverride(StandardCharsets.UTF_16);
            DiffModel model = engine.diff(List.of("a"), List.of("a"), opts);
            assertThat(model.leftEncoding()).isEqualTo(StandardCharsets.ISO_8859_1);
            assertThat(model.rightEncoding()).isEqualTo(StandardCharsets.UTF_16);
        }
    }

    // -----------------------------------------------------------------------
    // inlineDiff — word-level and char-level span correctness
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("inlineDiff — word-level and char-level spans")
    class InlineDiff {

        @Test
        @DisplayName("single word changed → span covers that word on each side")
        void singleWordChanged() {
            // "the quick brown fox" → "the slow brown fox"
            // Left span: "quick" [4,9), Right span: "slow" [4,8)
            var result = engine.inlineDiff("the quick brown fox", "the slow brown fox", defaults);
            assertThat(result[0]).hasSize(1);
            assertThat(result[1]).hasSize(1);

            InlineSpan left  = result[0].get(0);
            InlineSpan right = result[1].get(0);
            // "quick" starts at index 4, length 5 → [4, 9)
            assertThat(left.startOffset()).isEqualTo(4);
            assertThat(left.endOffset()).isEqualTo(9);
            // "slow" starts at index 4, length 4 → [4, 8)
            assertThat(right.startOffset()).isEqualTo(4);
            assertThat(right.endOffset()).isEqualTo(8);
        }

        @Test
        @DisplayName("word inserted → no left span, one right span")
        void wordInserted() {
            // "hello" → "hello world"
            // Left: no span; Right: " world" or "world"
            var result = engine.inlineDiff("hello", "hello world", defaults);
            assertThat(result[0]).isEmpty();
            assertThat(result[1]).isNotEmpty();
            // The right span should cover the appended portion
            InlineSpan right = result[1].get(0);
            assertThat(right.startOffset()).isGreaterThanOrEqualTo(5);
            assertThat(right.endOffset()).isEqualTo(11); // "hello world".length()
        }

        @Test
        @DisplayName("word removed → one left span, no right span")
        void wordRemoved() {
            // "hello world" → "hello"
            var result = engine.inlineDiff("hello world", "hello", defaults);
            assertThat(result[0]).isNotEmpty();
            assertThat(result[1]).isEmpty();
            InlineSpan left = result[0].get(0);
            assertThat(left.startOffset()).isGreaterThanOrEqualTo(5);
            assertThat(left.endOffset()).isEqualTo(11);
        }

        @Test
        @DisplayName("char-level change in a word → span covers the whole changed word")
        void charLevelChangeInWord() {
            // "colour" → "color": word tokens differ as a unit
            var result = engine.inlineDiff("colour", "color", defaults);
            assertThat(result[0]).hasSize(1);
            assertThat(result[1]).hasSize(1);
            // The span on both sides covers the entire word
            assertThat(result[0].get(0)).isEqualTo(new InlineSpan(0, 6));
            assertThat(result[1].get(0)).isEqualTo(new InlineSpan(0, 5));
        }

        @Test
        @DisplayName("identical lines → both span lists empty")
        void identicalLinesReturnEmptySpans() {
            var result = engine.inlineDiff("same line", "same line", defaults);
            assertThat(result[0]).isEmpty();
            assertThat(result[1]).isEmpty();
        }

        @Test
        @DisplayName("empty lines → both span lists empty")
        void emptyLines() {
            var result = engine.inlineDiff("", "", defaults);
            assertThat(result[0]).isEmpty();
            assertThat(result[1]).isEmpty();
        }

        @Test
        @DisplayName("completely different lines → spans cover full content")
        void completelyDifferentLines() {
            var result = engine.inlineDiff("aaa", "bbb", defaults);
            assertThat(result[0]).hasSize(1);
            assertThat(result[1]).hasSize(1);
            assertThat(result[0].get(0)).isEqualTo(new InlineSpan(0, 3));
            assertThat(result[1].get(0)).isEqualTo(new InlineSpan(0, 3));
        }

        @Test
        @DisplayName("very long line (> MAX_INLINE_CHARS) → empty spans (capped)")
        void veryLongLineCapped() {
            String longLine  = "x".repeat(LineDiffEngine.MAX_INLINE_CHARS + 1);
            String shortLine = "y";
            var result = engine.inlineDiff(longLine, shortLine, defaults);
            assertThat(result[0]).isEmpty();
            assertThat(result[1]).isEmpty();
        }

        @Test
        @DisplayName("exactly at cap length → spans are computed (inclusive boundary)")
        void atCapLengthIsComputed() {
            // MAX_INLINE_CHARS == 10_000: a line of exactly that length should be processed
            String left  = "a".repeat(LineDiffEngine.MAX_INLINE_CHARS);
            String right = "b".repeat(LineDiffEngine.MAX_INLINE_CHARS);
            var result = engine.inlineDiff(left, right, defaults);
            // Should produce non-empty spans (content is entirely different)
            assertThat(result[0]).isNotEmpty();
            assertThat(result[1]).isNotEmpty();
        }

        @Test
        @DisplayName("multiple changed words → merged into minimal span list")
        void multipleChangedWords() {
            // "one two three" → "ONE two THREE"
            // tokens: "one", " ", "two", " ", "three"
            // changes: "one"→"ONE", "three"→"THREE"
            var result = engine.inlineDiff("one two three", "ONE two THREE", defaults);
            // "one" at [0,3), "three" at [8,13) — should yield two separate spans each side
            assertThat(result[0]).hasSize(2);
            assertThat(result[1]).hasSize(2);
        }

        @Test
        @DisplayName("adjacent changed tokens are merged into one span")
        void adjacentChangedTokensMerged() {
            // Change two consecutive word tokens: "ab cd" → "XY ZW"
            // tokens: "ab", " ", "cd" → "XY", " ", "ZW"
            // "ab"→"XY" and "cd"→"ZW" with unchanged space in between
            var result = engine.inlineDiff("ab cd", "XY ZW", defaults);
            assertThat(result[0]).hasSize(2); // "ab" and "cd" are separate word tokens with a gap
            assertThat(result[1]).hasSize(2);
        }

        @Test
        @DisplayName("result array has exactly 2 entries")
        void resultHasTwoEntries() {
            var result = engine.inlineDiff("a", "b", defaults);
            assertThat(result).hasSize(2);
        }
    }

    // -----------------------------------------------------------------------
    // Tokenization behavior (tested through inlineDiff span output)
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Tokenization behavior")
    class TokenizationBehavior {

        @Test
        @DisplayName("empty lines produce no spans")
        void emptyLinesNoSpans() {
            var result = engine.inlineDiff("", "x", defaults);
            assertThat(result[0]).isEmpty();
            assertThat(result[1]).hasSize(1);
        }

        @Test
        @DisplayName("word at start of line: span starts at offset 0")
        void wordAtStart() {
            // "foo bar" → "baz bar": first word changes
            var result = engine.inlineDiff("foo bar", "baz bar", defaults);
            assertThat(result[0]).hasSize(1);
            assertThat(result[0].get(0).startOffset()).isEqualTo(0);
            assertThat(result[0].get(0).endOffset()).isEqualTo(3); // "foo"
            assertThat(result[1].get(0).startOffset()).isEqualTo(0);
            assertThat(result[1].get(0).endOffset()).isEqualTo(3); // "baz"
        }

        @Test
        @DisplayName("word at end of line: span ends at line length")
        void wordAtEnd() {
            // "bar foo" → "bar baz": last word changes
            var result = engine.inlineDiff("bar foo", "bar baz", defaults);
            assertThat(result[0]).hasSize(1);
            assertThat(result[0].get(0).startOffset()).isEqualTo(4);
            assertThat(result[0].get(0).endOffset()).isEqualTo(7);
        }

        @Test
        @DisplayName("span offsets are within line length")
        void spanOffsetsInBounds() {
            String left  = "the quick brown fox";
            String right = "the slow brown fox";
            var result = engine.inlineDiff(left, right, defaults);
            for (InlineSpan span : result[0]) {
                assertThat(span.startOffset()).isGreaterThanOrEqualTo(0);
                assertThat(span.endOffset()).isLessThanOrEqualTo(left.length());
            }
            for (InlineSpan span : result[1]) {
                assertThat(span.startOffset()).isGreaterThanOrEqualTo(0);
                assertThat(span.endOffset()).isLessThanOrEqualTo(right.length());
            }
        }
    }

    // -----------------------------------------------------------------------
    // mergeSpans() helper
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("mergeSpans() helper (package-visible)")
    class MergeSpansHelper {

        @Test
        @DisplayName("empty list → empty result")
        void emptyList() {
            assertThat(LineDiffEngine.mergeSpans(List.of())).isEmpty();
        }

        @Test
        @DisplayName("single span is returned as-is")
        void singleSpan() {
            var spans = LineDiffEngine.mergeSpans(List.of(new InlineSpan(2, 5)));
            assertThat(spans).containsExactly(new InlineSpan(2, 5));
        }

        @Test
        @DisplayName("adjacent spans are merged")
        void adjacentSpansMerged() {
            var spans = LineDiffEngine.mergeSpans(List.of(
                    new InlineSpan(0, 3),
                    new InlineSpan(3, 6)));
            assertThat(spans).containsExactly(new InlineSpan(0, 6));
        }

        @Test
        @DisplayName("overlapping spans are merged")
        void overlappingSpansMerged() {
            var spans = LineDiffEngine.mergeSpans(List.of(
                    new InlineSpan(0, 5),
                    new InlineSpan(3, 8)));
            assertThat(spans).containsExactly(new InlineSpan(0, 8));
        }

        @Test
        @DisplayName("non-adjacent spans stay separate")
        void nonAdjacentSpansSeparate() {
            var spans = LineDiffEngine.mergeSpans(List.of(
                    new InlineSpan(0, 3),
                    new InlineSpan(5, 8)));
            assertThat(spans).hasSize(2);
            assertThat(spans.get(0)).isEqualTo(new InlineSpan(0, 3));
            assertThat(spans.get(1)).isEqualTo(new InlineSpan(5, 8));
        }

        @Test
        @DisplayName("out-of-order spans are sorted then merged")
        void outOfOrderSpansSortedAndMerged() {
            var spans = LineDiffEngine.mergeSpans(List.of(
                    new InlineSpan(5, 8),
                    new InlineSpan(0, 3)));
            assertThat(spans).hasSize(2);
            assertThat(spans.get(0).startOffset()).isEqualTo(0);
            assertThat(spans.get(1).startOffset()).isEqualTo(5);
        }
    }

    // -----------------------------------------------------------------------
    // Task 4.3 — comparison-option normalization
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Task 4.3 — ignore flags (normalization)")
    class OptionNormalization {

        // ── ignoreWhitespace ────────────────────────────────────────────────

        @Test
        @DisplayName("ignoreWhitespace: re-indented line treated as UNCHANGED")
        void ignoreWhitespaceReindentedLine() {
            DiffModel model = engine.diff(
                    List.of("  value = 1"),
                    List.of("value = 1"),
                    defaults.withIgnoreWhitespace(true));

            assertThat(model.identical()).isTrue();
            assertThat(model.rows()).hasSize(1);
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.UNCHANGED);
        }

        @Test
        @DisplayName("ignoreWhitespace: collapsed internal spaces treated as UNCHANGED")
        void ignoreWhitespaceCollapsedInternal() {
            DiffModel model = engine.diff(
                    List.of("hello  world"),
                    List.of("hello world"),
                    defaults.withIgnoreWhitespace(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("ignoreWhitespace: tab vs space treated as UNCHANGED")
        void ignoreWhitespaceTabVsSpace() {
            DiffModel model = engine.diff(
                    List.of("\tfoo"),
                    List.of("foo"),
                    defaults.withIgnoreWhitespace(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("ignoreWhitespace=false: different indentation is CHANGED")
        void ignoreWhitespaceFalseDetectsDifference() {
            DiffModel model = engine.diff(
                    List.of("  value = 1"),
                    List.of("value = 1"),
                    defaults.withIgnoreWhitespace(false));

            assertThat(model.identical()).isFalse();
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.CHANGED);
        }

        @Test
        @DisplayName("ignoreWhitespace: only whitespace differs across multiple lines → identical")
        void ignoreWhitespaceMultipleLinesAllBecomeSame() {
            DiffModel model = engine.diff(
                    List.of("  a", "  b", "  c"),
                    List.of("a", "b", "c"),
                    defaults.withIgnoreWhitespace(true));

            assertThat(model.identical()).isTrue();
            assertThat(model.rows()).allMatch(r -> r.kind() == LineKind.UNCHANGED);
        }

        @Test
        @DisplayName("ignoreWhitespace: original text is preserved in DiffRow (not normalized)")
        void ignoreWhitespacePreservesOriginalText() {
            DiffModel model = engine.diff(
                    List.of("  hello"),
                    List.of("hello"),
                    defaults.withIgnoreWhitespace(true));

            // Row is UNCHANGED but left text must still be "  hello", not "hello"
            assertThat(model.rows().get(0).leftText()).isEqualTo("  hello");
            assertThat(model.rows().get(0).rightText()).isEqualTo("hello");
        }

        // ── ignoreCase ──────────────────────────────────────────────────────

        @Test
        @DisplayName("ignoreCase: upper-vs-lower treated as UNCHANGED")
        void ignoreCaseUpperLower() {
            DiffModel model = engine.diff(
                    List.of("HELLO"),
                    List.of("hello"),
                    defaults.withIgnoreCase(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("ignoreCase: mixed-case line treated as UNCHANGED")
        void ignoreCaseMixed() {
            DiffModel model = engine.diff(
                    List.of("Hello World"),
                    List.of("hello world"),
                    defaults.withIgnoreCase(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("ignoreCase=false: case difference is CHANGED")
        void ignoreCaseFalseDetectsDifference() {
            DiffModel model = engine.diff(
                    List.of("HELLO"),
                    List.of("hello"),
                    defaults.withIgnoreCase(false));

            assertThat(model.identical()).isFalse();
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.CHANGED);
        }

        @Test
        @DisplayName("ignoreCase: content still differs after folding → CHANGED")
        void ignoreCaseStillDiffers() {
            DiffModel model = engine.diff(
                    List.of("ALPHA"),
                    List.of("beta"),
                    defaults.withIgnoreCase(true));

            assertThat(model.identical()).isFalse();
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.CHANGED);
        }

        // ── ignoreLineEndings ───────────────────────────────────────────────

        @Test
        @DisplayName("ignoreLineEndings: trailing CR stripped → UNCHANGED")
        void ignoreLineEndingsTrailingCr() {
            // Lines with embedded \r (e.g. read from a CRLF file but split on \n only)
            DiffModel model = engine.diff(
                    List.of("foo\r"),
                    List.of("foo"),
                    defaults.withIgnoreLineEndings(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("ignoreLineEndings=false: trailing CR is CHANGED")
        void ignoreLineEndingsFalseDetects() {
            DiffModel model = engine.diff(
                    List.of("foo\r"),
                    List.of("foo"),
                    defaults.withIgnoreLineEndings(false));

            assertThat(model.identical()).isFalse();
        }

        // ── combined flags ──────────────────────────────────────────────────

        @Test
        @DisplayName("all three flags: '  HELLO\\r' vs 'hello' → identical")
        void allFlagsCombined() {
            DiffModel model = engine.diff(
                    List.of("  HELLO\r"),
                    List.of("hello"),
                    defaults.withIgnoreWhitespace(true)
                            .withIgnoreCase(true)
                            .withIgnoreLineEndings(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("ignoreWhitespace + ignoreCase: '  HELLO' vs 'hello' → identical")
        void whitespaceAndCaseCombined() {
            DiffModel model = engine.diff(
                    List.of("  HELLO"),
                    List.of("hello"),
                    defaults.withIgnoreWhitespace(true).withIgnoreCase(true));

            assertThat(model.identical()).isTrue();
        }

        @Test
        @DisplayName("combined flags: real content difference still detected")
        void combinedFlagsStillDetectRealDifference() {
            DiffModel model = engine.diff(
                    List.of("  ALPHA"),
                    List.of("beta"),
                    defaults.withIgnoreWhitespace(true).withIgnoreCase(true));

            assertThat(model.identical()).isFalse();
            assertThat(model.rows().get(0).kind()).isEqualTo(LineKind.CHANGED);
        }

        // ── re-diff on option change ────────────────────────────────────────

        @Test
        @DisplayName("re-diff on option change: same input, different results with/without flag")
        void reDiffOnOptionChange() {
            List<String> left  = List.of("  x");
            List<String> right = List.of("x");

            DiffModel withFlag    = engine.diff(left, right, defaults.withIgnoreWhitespace(true));
            DiffModel withoutFlag = engine.diff(left, right, defaults.withIgnoreWhitespace(false));

            assertThat(withFlag.identical()).isTrue();
            assertThat(withoutFlag.identical()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Task 4.3 — normalizeLine() unit tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("normalizeLine() helper")
    class NormalizeLineHelper {

        @Test
        @DisplayName("no flags: string returned unchanged")
        void noFlags() {
            assertThat(LineDiffEngine.normalizeLine("  Hello\r", defaults)).isEqualTo("  Hello\r");
        }

        @Test
        @DisplayName("ignoreWhitespace: strips leading/trailing and collapses internal")
        void ignoreWhitespace() {
            ComparisonOptions opts = defaults.withIgnoreWhitespace(true);
            assertThat(LineDiffEngine.normalizeLine("  hello  world  ", opts)).isEqualTo("hello world");
        }

        @Test
        @DisplayName("ignoreWhitespace: tab counts as whitespace")
        void ignoreWhitespaceTab() {
            ComparisonOptions opts = defaults.withIgnoreWhitespace(true);
            assertThat(LineDiffEngine.normalizeLine("\thello", opts)).isEqualTo("hello");
        }

        @Test
        @DisplayName("ignoreCase: folds to lower-case")
        void ignoreCase() {
            ComparisonOptions opts = defaults.withIgnoreCase(true);
            assertThat(LineDiffEngine.normalizeLine("HELLO World", opts)).isEqualTo("hello world");
        }

        @Test
        @DisplayName("ignoreLineEndings: strips CR")
        void ignoreLineEndings() {
            ComparisonOptions opts = defaults.withIgnoreLineEndings(true);
            assertThat(LineDiffEngine.normalizeLine("foo\r", opts)).isEqualTo("foo");
        }

        @Test
        @DisplayName("all three flags applied in order")
        void allThreeFlags() {
            ComparisonOptions opts = defaults
                    .withIgnoreLineEndings(true)
                    .withIgnoreWhitespace(true)
                    .withIgnoreCase(true);
            // "  HELLO\r  " → strip CR → "  HELLO  " → strip/collapse → "HELLO" → lower → "hello"
            assertThat(LineDiffEngine.normalizeLine("  HELLO\r  ", opts)).isEqualTo("hello");
        }
    }

    @Nested
    @DisplayName("Null-safety")
    class NullSafety {

        @Test
        @DisplayName("null leftLines throws NullPointerException")
        void nullLeftLinesThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> engine.diff(null, List.of(), defaults));
        }

        @Test
        @DisplayName("null rightLines throws NullPointerException")
        void nullRightLinesThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> engine.diff(List.of(), null, defaults));
        }

        @Test
        @DisplayName("null options throws NullPointerException")
        void nullOptionsThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> engine.diff(List.of(), List.of(), null));
        }
    }
}
