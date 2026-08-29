package com.comparetool.core.merge;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.LineEnding;
import com.comparetool.model.LineKind;
import com.comparetool.model.MergeDirection;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DefaultMergeManager}.
 *
 * <p>Every test uses {@link LineDiffEngine} (the real diff engine) so that we
 * verify the full round-trip: diff → copy → re-diff. {@link ComparisonOptions}
 * is always the default (no normalization flags) unless otherwise stated.
 */
class DefaultMergeManagerTest {

    private static final ComparisonOptions OPTIONS = new ComparisonOptions(
            false, false, false, null, null, 10_000_000L);

    /** Convenience factory — both sides writable by default. */
    private static DefaultMergeManager manager(List<String> leftLines, List<String> rightLines) {
        return manager(leftLines, rightLines, false, false);
    }

    private static DefaultMergeManager manager(
            List<String> leftLines, List<String> rightLines,
            boolean leftReadOnly, boolean rightReadOnly) {
        EditableDocument left  = new EditableDocument(leftLines,  StandardCharsets.UTF_8, LineEnding.LF);
        EditableDocument right = new EditableDocument(rightLines, StandardCharsets.UTF_8, LineEnding.LF);
        return new DefaultMergeManager(left, right, leftReadOnly, rightReadOnly,
                new LineDiffEngine(), OPTIONS);
    }

    /** Extracts the first (and typically only) diff block that has the given kind. */
    private static DiffBlock firstBlock(DefaultMergeManager mgr, LineKind kind) {
        return mgr.currentDiff().blocks().stream()
                .filter(b -> b.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No block of kind " + kind + " in diff"));
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    @Test
    void leftDocumentAndRightDocumentReturnDocuments() {
        var mgr = manager(List.of("A"), List.of("B"));
        assertThat(mgr.leftDocument().getLine(0)).isEqualTo("A");
        assertThat(mgr.rightDocument().getLine(0)).isEqualTo("B");
    }

    @Test
    void initialDiffIsComputedOnConstruction() {
        var mgr = manager(List.of("A"), List.of("B"));
        assertThat(mgr.currentDiff().differenceCount()).isGreaterThan(0);
    }

    @Test
    void identicalDocumentsHaveZeroDifferenceBlocks() {
        var mgr = manager(List.of("A", "B"), List.of("A", "B"));
        assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        assertThat(mgr.currentDiff().identical()).isTrue();
    }

    // ── copyBlock: CHANGED LEFT_TO_RIGHT ─────────────────────────────────────

    @Nested
    class CopyBlockChangedLeftToRight {

        @Test
        void replacesRightLineWithLeftContent() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void reDiffShowsNoDifferencesAfterCopy() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }

        @Test
        void setsRightDocumentDirty() {
            var mgr = manager(List.of("A", "B"), List.of("A", "X"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().isDirty()).isTrue();
        }

        @Test
        void leftDocumentIsUntouched() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);
            mgr.rightDocument().markClean(); // reset right's dirty flag for clarity
            mgr.leftDocument().markClean();

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.leftDocument().isDirty()).isFalse();
            assertThat(mgr.leftDocument().lines()).containsExactly("A", "B", "C");
        }
    }

    // ── copyBlock: CHANGED RIGHT_TO_LEFT ─────────────────────────────────────

    @Nested
    class CopyBlockChangedRightToLeft {

        @Test
        void replacesLeftLineWithRightContent() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.leftDocument().lines()).containsExactly("A", "X", "C");
        }

        @Test
        void reDiffShowsNoDifferencesAfterCopy() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }

        @Test
        void rightDocumentIsUntouched() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "X", "C");
        }
    }

    // ── copyBlock: REMOVED LEFT_TO_RIGHT (insert left-only lines into right) ──

    @Nested
    class CopyBlockRemovedLeftToRight {

        @Test
        void insertsLeftLineIntoRightAtCorrectPosition() {
            // Left: [A, B, C]  Right: [A, C]  → "B" is REMOVED (left-only)
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.REMOVED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void insertsAtBeginningWhenRemovedBlockIsFirst() {
            // Left: [A, B, C]  Right: [C]  → A and B are REMOVED (left-only)
            var mgr = manager(List.of("A", "B", "C"), List.of("C"));
            DiffBlock block = firstBlock(mgr, LineKind.REMOVED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void reDiffShowsNoDifferencesAfterCopy() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.REMOVED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }
    }

    // ── copyBlock: REMOVED RIGHT_TO_LEFT (delete left-only lines from left) ───

    @Nested
    class CopyBlockRemovedRightToLeft {

        @Test
        void deletesLeftOnlyLine() {
            // Left: [A, B, C]  Right: [A, C]  → "B" is REMOVED (left-only); delete from left
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.REMOVED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.leftDocument().lines()).containsExactly("A", "C");
        }

        @Test
        void reDiffShowsNoDifferencesAfterCopy() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.REMOVED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }
    }

    // ── copyBlock: ADDED LEFT_TO_RIGHT (delete right-only lines from right) ───

    @Nested
    class CopyBlockAddedLeftToRight {

        @Test
        void deletesRightOnlyLine() {
            // Left: [A, C]  Right: [A, B, C]  → "B" is ADDED (right-only); delete from right
            var mgr = manager(List.of("A", "C"), List.of("A", "B", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.ADDED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "C");
        }

        @Test
        void deletesRightOnlyLinesAtStart() {
            // Left: [C]  Right: [A, B, C]  → A and B are ADDED; delete from right
            var mgr = manager(List.of("C"), List.of("A", "B", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.ADDED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("C");
        }

        @Test
        void reDiffShowsNoDifferencesAfterCopy() {
            var mgr = manager(List.of("A", "C"), List.of("A", "B", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.ADDED);

            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }
    }

    // ── copyBlock: ADDED RIGHT_TO_LEFT (insert right-only lines into left) ────

    @Nested
    class CopyBlockAddedRightToLeft {

        @Test
        void insertsRightLineIntoLeft() {
            // Left: [A, C]  Right: [A, B, C]  → "B" is ADDED; insert into left
            var mgr = manager(List.of("A", "C"), List.of("A", "B", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.ADDED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.leftDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void insertsAtBeginningWhenAddedBlockIsFirst() {
            // Left: [C]  Right: [A, B, C]  → A and B are ADDED; insert at start of left
            var mgr = manager(List.of("C"), List.of("A", "B", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.ADDED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.leftDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void reDiffShowsNoDifferencesAfterCopy() {
            var mgr = manager(List.of("A", "C"), List.of("A", "B", "C"));
            DiffBlock block = firstBlock(mgr, LineKind.ADDED);

            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }
    }

    // ── copyAll ───────────────────────────────────────────────────────────────

    @Nested
    class CopyAll {

        @Test
        void leftToRightMakesRightIdenticalToLeft() {
            var mgr = manager(List.of("A", "B", "C"), List.of("X", "Y", "Z"));

            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void rightToLeftMakesLeftIdenticalToRight() {
            var mgr = manager(List.of("X", "Y", "Z"), List.of("A", "B", "C"));

            mgr.copyAll(MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.leftDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void leftToRightReducesDifferenceCountToZero() {
            var mgr = manager(List.of("A", "B"), List.of("C", "D", "E"));
            assertThat(mgr.currentDiff().differenceCount()).isGreaterThan(0);

            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }

        @Test
        void rightToLeftReducesDifferenceCountToZero() {
            var mgr = manager(List.of("A", "B"), List.of("C", "D", "E"));

            mgr.copyAll(MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }

        @Test
        void copyAllOnAlreadyIdenticalDocumentsIsNoOp() {
            var mgr = manager(List.of("A", "B"), List.of("A", "B"));

            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.rightDocument().lines()).containsExactly("A", "B");
            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
        }

        @Test
        void leftToRightWithMultipleBlocksReducesToZero() {
            // multiple different blocks across the document
            var mgr = manager(
                    List.of("A", "B", "C", "D", "E"),
                    List.of("A", "X", "C", "Y", "E"));
            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(2);

            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.currentDiff().differenceCount()).isEqualTo(0);
            assertThat(mgr.rightDocument().lines())
                    .containsExactly("A", "B", "C", "D", "E");
        }
    }

    // ── re-diff after copyBlock ───────────────────────────────────────────────

    @Test
    void currentDiffReflectsStateAfterCopyBlock() {
        // Start with two CHANGED blocks; copy one → one block should remain
        var mgr = manager(
                List.of("A", "B", "C", "D", "E"),
                List.of("A", "X", "C", "Y", "E"));
        assertThat(mgr.currentDiff().differenceCount()).isEqualTo(2);

        // Copy only the first CHANGED block
        DiffBlock firstChanged = mgr.currentDiff().blocks().get(0);
        mgr.copyBlock(firstChanged, MergeDirection.LEFT_TO_RIGHT);

        assertThat(mgr.currentDiff().differenceCount()).isEqualTo(1);
    }

    // ── read-only rejection ───────────────────────────────────────────────────

    @Nested
    class ReadOnlyRejection {

        @Test
        void copyBlockLeftToRightThrowsWhenRightIsReadOnly() {
            var mgr = manager(List.of("A", "B"), List.of("A", "X"),
                    false, true);
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            assertThatIllegalStateException()
                    .isThrownBy(() -> mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT))
                    .withMessageContaining("read-only");
        }

        @Test
        void copyBlockRightToLeftThrowsWhenLeftIsReadOnly() {
            var mgr = manager(List.of("A", "B"), List.of("A", "X"),
                    true, false);
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            assertThatIllegalStateException()
                    .isThrownBy(() -> mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT))
                    .withMessageContaining("read-only");
        }

        @Test
        void copyAllLeftToRightThrowsWhenRightIsReadOnly() {
            var mgr = manager(List.of("A"), List.of("B"), false, true);

            assertThatIllegalStateException()
                    .isThrownBy(() -> mgr.copyAll(MergeDirection.LEFT_TO_RIGHT))
                    .withMessageContaining("read-only");
        }

        @Test
        void copyAllRightToLeftThrowsWhenLeftIsReadOnly() {
            var mgr = manager(List.of("A"), List.of("B"), true, false);

            assertThatIllegalStateException()
                    .isThrownBy(() -> mgr.copyAll(MergeDirection.RIGHT_TO_LEFT))
                    .withMessageContaining("read-only");
        }

        @Test
        void readOnlyCheckDoesNotPreventCopyToWritableSide() {
            // leftReadOnly = true but direction is LEFT_TO_RIGHT (writes to right) → OK
            var mgr = manager(List.of("A", "B"), List.of("A", "X"),
                    true, false);
            DiffBlock block = firstBlock(mgr, LineKind.CHANGED);

            assertThatCode(() -> mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT))
                    .doesNotThrowAnyException();
        }
    }

    // ── canUndo / canRedo initial state ──────────────────────────────────────

    @Test
    void canUndoReturnsFalseBeforeAnyOperation() {
        var mgr = manager(List.of("A"), List.of("B"));
        assertThat(mgr.canUndo()).isFalse();
    }

    @Test
    void canRedoReturnsFalseBeforeAnyOperation() {
        var mgr = manager(List.of("A"), List.of("B"));
        assertThat(mgr.canRedo()).isFalse();
    }

    @Test
    void undoThrowsIllegalStateWhenStackIsEmpty() {
        var mgr = manager(List.of("A"), List.of("B"));
        assertThatIllegalStateException().isThrownBy(mgr::undo);
    }

    @Test
    void redoThrowsIllegalStateWhenStackIsEmpty() {
        var mgr = manager(List.of("A"), List.of("B"));
        assertThatIllegalStateException().isThrownBy(mgr::redo);
    }
}
