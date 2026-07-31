package com.comparetool.core.merge;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.LineEnding;
import com.comparetool.model.LineKind;
import com.comparetool.model.MergeDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the undo/redo history in {@link DefaultMergeManager} (task 5.3).
 *
 * <p>Each test builds a manager with {@link LineDiffEngine} (real diff) so the
 * full copy → undo → redo round-trip is exercised end-to-end.
 */
class UndoRedoTest {

    private static final ComparisonOptions OPTIONS = new ComparisonOptions(
            false, false, false, null, null, 10_000_000L);

    /** Left: [A, B, C]  Right: [A, X, C]  — one CHANGED block. */
    private DefaultMergeManager changedMgr;

    @BeforeEach
    void setUp() {
        changedMgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
    }

    // ── initial state ─────────────────────────────────────────────────────────

    @Test
    void canUndoIsFalseBeforeAnyOperation() {
        assertThat(changedMgr.canUndo()).isFalse();
    }

    @Test
    void canRedoIsFalseBeforeAnyOperation() {
        assertThat(changedMgr.canRedo()).isFalse();
    }

    @Test
    void undoThrowsIllegalStateWhenStackIsEmpty() {
        assertThatIllegalStateException().isThrownBy(changedMgr::undo);
    }

    @Test
    void redoThrowsIllegalStateWhenStackIsEmpty() {
        assertThatIllegalStateException().isThrownBy(changedMgr::redo);
    }

    // ── canUndo after copyBlock ───────────────────────────────────────────────

    @Test
    void canUndoIsTrueAfterCopyBlock() {
        DiffBlock block = firstChanged(changedMgr);
        changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

        assertThat(changedMgr.canUndo()).isTrue();
    }

    @Test
    void canUndoIsTrueAfterCopyAll() {
        changedMgr.copyAll(MergeDirection.LEFT_TO_RIGHT);

        assertThat(changedMgr.canUndo()).isTrue();
    }

    // ── undo restores prior state ─────────────────────────────────────────────

    @Nested
    class UndoRestoresPriorState {

        @Test
        void undoCopyBlockRestoresRightDocument() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            // right is now ["A", "B", "C"]

            changedMgr.undo();

            assertThat(changedMgr.rightDocument().lines()).containsExactly("A", "X", "C");
        }

        @Test
        void undoCopyBlockRestoresLeftDocument() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            DiffBlock block = firstChanged(mgr);
            mgr.copyBlock(block, MergeDirection.RIGHT_TO_LEFT);
            // left is now ["A", "X", "C"]

            mgr.undo();

            assertThat(mgr.leftDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void undoCopyAllRestoresTarget() {
            var mgr = manager(List.of("P", "Q"), List.of("X", "Y", "Z"));
            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);

            mgr.undo();

            assertThat(mgr.rightDocument().lines()).containsExactly("X", "Y", "Z");
        }

        @Test
        void undoReDiffsAfterRestore() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            assertThat(changedMgr.currentDiff().differenceCount()).isEqualTo(0);

            changedMgr.undo();

            assertThat(changedMgr.currentDiff().differenceCount()).isGreaterThan(0);
        }

        @Test
        void undoSetsCanUndoFalseAfterSingleOperation() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);

            changedMgr.undo();

            assertThat(changedMgr.canUndo()).isFalse();
        }
    }

    // ── redo re-applies the operation ─────────────────────────────────────────

    @Nested
    class RedoReappliesOperation {

        @Test
        void canRedoIsTrueAfterUndo() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            changedMgr.undo();

            assertThat(changedMgr.canRedo()).isTrue();
        }

        @Test
        void redoRestoresRightToPostOperationState() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            changedMgr.undo();

            changedMgr.redo();

            assertThat(changedMgr.rightDocument().lines()).containsExactly("A", "B", "C");
        }

        @Test
        void redoReDiffsAfterReapply() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            changedMgr.undo();
            assertThat(changedMgr.currentDiff().differenceCount()).isGreaterThan(0);

            changedMgr.redo();

            assertThat(changedMgr.currentDiff().differenceCount()).isEqualTo(0);
        }

        @Test
        void redoSetsCanRedoFalseAfterSingleUndo() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            changedMgr.undo();

            changedMgr.redo();

            assertThat(changedMgr.canRedo()).isFalse();
        }

        @Test
        void redoRestoredDocumentMatchesPreUndoState() {
            var mgr = manager(List.of("P", "Q"), List.of("X", "Y", "Z"));
            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);
            List<String> afterCopy = mgr.rightDocument().lines();
            mgr.undo();

            mgr.redo();

            assertThat(mgr.rightDocument().lines()).isEqualTo(afterCopy);
        }
    }

    // ── multi-step undo ───────────────────────────────────────────────────────

    @Nested
    class MultiStepUndo {

        @Test
        void multipleOperationsCanEachBeUndoneInOrder() {
            // Start: left=[A,B,C]  right=[A,X,C]
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            // Op1: copy block L→R → right=[A,B,C]
            mgr.copyBlock(firstChanged(mgr), MergeDirection.LEFT_TO_RIGHT);
            // Op2: copy block back R→L would require the block, but now they're identical.
            // Instead, perform a new copyAll R→L on a second manager pass.
            // Reset right to something different for a second operation:
            mgr.rightDocument().setLine(1, "Z"); // direct edit outside history
            // (re-diff not called, so we need a helper op via copyAll)
            mgr.copyAll(MergeDirection.RIGHT_TO_LEFT); // left becomes [A,Z,C]

            // Undo Op2 → left goes back to [A,B,C]
            mgr.undo();
            assertThat(mgr.leftDocument().lines()).containsExactly("A", "B", "C");

            // Undo Op1 → right goes back to [A,X,C]
            mgr.undo();
            assertThat(mgr.rightDocument().lines()).containsExactly("A", "X", "C");

            assertThat(mgr.canUndo()).isFalse();
        }

        @Test
        void undoStackDepthMatchesOperationCount() {
            var mgr = manager(List.of("A", "B", "C"), List.of("A", "X", "C"));
            mgr.copyBlock(firstChanged(mgr), MergeDirection.LEFT_TO_RIGHT);
            // no more diff blocks exist; manually edit and copyAll for a second op
            mgr.rightDocument().setLine(0, "Q");
            mgr.copyAll(MergeDirection.RIGHT_TO_LEFT);

            assertThat(mgr.canUndo()).isTrue();
            mgr.undo();
            assertThat(mgr.canUndo()).isTrue();
            mgr.undo();
            assertThat(mgr.canUndo()).isFalse();
        }
    }

    // ── redo clears on new operation ──────────────────────────────────────────

    @Nested
    class RedoClearedByNewOperation {

        @Test
        void newCopyBlockAfterUndoClearsRedoStack() {
            var mgr = manager(List.of("A", "B"), List.of("A", "X"));
            DiffBlock block = firstChanged(mgr);
            mgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            mgr.undo(); // canRedo = true

            // Perform a new operation — redo stack must be cleared
            // Right is back to [A, X]; do a fresh LEFT_TO_RIGHT copy
            mgr.copyBlock(firstChanged(mgr), MergeDirection.LEFT_TO_RIGHT);

            assertThat(mgr.canRedo()).isFalse();
        }

        @Test
        void newCopyAllAfterUndoClearsRedoStack() {
            var mgr = manager(List.of("1", "2"), List.of("3", "4"));
            mgr.copyAll(MergeDirection.LEFT_TO_RIGHT);
            mgr.undo(); // canRedo = true

            mgr.copyAll(MergeDirection.RIGHT_TO_LEFT); // new operation

            assertThat(mgr.canRedo()).isFalse();
        }
    }

    // ── mixed undo/redo sequences ─────────────────────────────────────────────

    @Nested
    class MixedUndoRedoSequences {

        @Test
        void undoRedoUndoSequenceIsConsistent() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            // right = [A,B,C]

            changedMgr.undo();
            // right = [A,X,C]
            assertThat(changedMgr.rightDocument().lines()).containsExactly("A", "X", "C");

            changedMgr.redo();
            // right = [A,B,C] again
            assertThat(changedMgr.rightDocument().lines()).containsExactly("A", "B", "C");

            changedMgr.undo();
            // right = [A,X,C] again
            assertThat(changedMgr.rightDocument().lines()).containsExactly("A", "X", "C");
        }

        @Test
        void redoAfterRedoThrowsWhenStackExhausted() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            changedMgr.undo();
            changedMgr.redo(); // exhausts redo stack

            assertThatIllegalStateException().isThrownBy(changedMgr::redo);
        }

        @Test
        void undoAfterUndoThrowsWhenStackExhausted() {
            DiffBlock block = firstChanged(changedMgr);
            changedMgr.copyBlock(block, MergeDirection.LEFT_TO_RIGHT);
            changedMgr.undo(); // exhausts undo stack

            assertThatIllegalStateException().isThrownBy(changedMgr::undo);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static DefaultMergeManager manager(List<String> leftLines, List<String> rightLines) {
        EditableDocument left  = new EditableDocument(leftLines,  StandardCharsets.UTF_8, LineEnding.LF);
        EditableDocument right = new EditableDocument(rightLines, StandardCharsets.UTF_8, LineEnding.LF);
        return new DefaultMergeManager(left, right, false, false, new LineDiffEngine(), OPTIONS);
    }

    private static DiffBlock firstChanged(DefaultMergeManager mgr) {
        return mgr.currentDiff().blocks().stream()
                .filter(b -> b.kind() == LineKind.CHANGED)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CHANGED block in diff"));
    }
}
