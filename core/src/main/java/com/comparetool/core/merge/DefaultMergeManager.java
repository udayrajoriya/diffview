package com.comparetool.core.merge;

import com.comparetool.core.diff.TextDiffEngine;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.DiffModel;
import com.comparetool.model.DiffRow;
import com.comparetool.model.MergeDirection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link MergeManager}.
 *
 * <p>After every {@link #copyBlock} or {@link #copyAll} call the pair of
 * documents is re-diffed so that {@link #currentDiff()} always reflects the
 * latest state.
 *
 * <p>Undo/redo history (command-pattern stack) is added in task 5.3; the
 * corresponding methods throw {@link UnsupportedOperationException} until then.
 */
public final class DefaultMergeManager implements MergeManager {

    private final EditableDocument left;
    private final EditableDocument right;
    private final boolean leftReadOnly;
    private final boolean rightReadOnly;
    private final TextDiffEngine engine;
    private final ComparisonOptions options;
    private DiffModel currentDiff;

    // ── undo/redo stacks (command-pattern, task 5.3) ──────────────────────────
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();

    /**
     * Creates a manager for the given pair of documents.
     *
     * @param left          the left-side editable document
     * @param right         the right-side editable document
     * @param leftReadOnly  {@code true} to prevent merging into the left document
     * @param rightReadOnly {@code true} to prevent merging into the right document
     * @param engine        diff engine used to re-diff after each operation
     * @param options       comparison options forwarded to the diff engine
     */
    public DefaultMergeManager(
            EditableDocument left,
            EditableDocument right,
            boolean leftReadOnly,
            boolean rightReadOnly,
            TextDiffEngine engine,
            ComparisonOptions options) {
        this.left = Objects.requireNonNull(left, "left must not be null");
        this.right = Objects.requireNonNull(right, "right must not be null");
        this.leftReadOnly = leftReadOnly;
        this.rightReadOnly = rightReadOnly;
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.currentDiff = engine.diff(left.lines(), right.lines(), options);
    }

    // ── MergeManager ─────────────────────────────────────────────────────────

    @Override
    public void copyBlock(DiffBlock block, MergeDirection direction) {
        Objects.requireNonNull(block, "block must not be null");
        Objects.requireNonNull(direction, "direction must not be null");

        checkWritable(direction);

        List<DiffRow> allRows = currentDiff.rows();
        List<DiffRow> blockRows = allRows.subList(block.firstRowIndex(), block.lastRowIndex() + 1);
        int firstRowIndex = block.firstRowIndex();
        com.comparetool.model.LineKind kind = block.kind();

        performWithHistory(() -> {
            if (direction == MergeDirection.LEFT_TO_RIGHT) {
                applyLeftToRight(kind, blockRows, allRows, firstRowIndex);
            } else {
                applyRightToLeft(kind, blockRows, allRows, firstRowIndex);
            }
        });
    }

    @Override
    public void copyAll(MergeDirection direction) {
        Objects.requireNonNull(direction, "direction must not be null");

        checkWritable(direction);

        // Copying all differences in one direction makes the target identical to
        // the source, so we can replace the target's entire content in one shot.
        performWithHistory(() -> {
            if (direction == MergeDirection.LEFT_TO_RIGHT) {
                right.replaceLines(0, right.lineCount(), left.lines());
            } else {
                left.replaceLines(0, left.lineCount(), right.lines());
            }
        });
    }

    @Override
    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    @Override
    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    @Override
    public void undo() {
        if (!canUndo()) {
            throw new IllegalStateException("Nothing to undo");
        }
        HistoryEntry entry = undoStack.pop();
        left.restore(entry.leftBefore());
        right.restore(entry.rightBefore());
        reDiff();
        redoStack.push(entry);
    }

    @Override
    public void redo() {
        if (!canRedo()) {
            throw new IllegalStateException("Nothing to redo");
        }
        HistoryEntry entry = redoStack.pop();
        left.restore(entry.leftAfter());
        right.restore(entry.rightAfter());
        reDiff();
        undoStack.push(entry);
    }

    @Override
    public EditableDocument leftDocument() {
        return left;
    }

    @Override
    public EditableDocument rightDocument() {
        return right;
    }

    // ── Additional accessor ───────────────────────────────────────────────────

    /**
     * Returns the diff model computed after the most recent operation (or the
     * initial diff if no operations have been performed yet).
     */
    public DiffModel currentDiff() {
        return currentDiff;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Takes pre-snapshots, runs {@code operation}, re-diffs, takes post-snapshots,
     * pushes a {@link HistoryEntry} onto the undo stack, and clears the redo stack.
     */
    private void performWithHistory(Runnable operation) {
        EditableDocument.Snapshot leftBefore  = left.takeSnapshot();
        EditableDocument.Snapshot rightBefore = right.takeSnapshot();

        operation.run();
        reDiff();

        EditableDocument.Snapshot leftAfter  = left.takeSnapshot();
        EditableDocument.Snapshot rightAfter = right.takeSnapshot();

        undoStack.push(new HistoryEntry(leftBefore, rightBefore, leftAfter, rightAfter));
        redoStack.clear();
    }

    private void checkWritable(MergeDirection direction) {
        if (direction == MergeDirection.LEFT_TO_RIGHT && rightReadOnly) {
            throw new IllegalStateException(
                    "Right document is read-only; cannot merge LEFT_TO_RIGHT");
        }
        if (direction == MergeDirection.RIGHT_TO_LEFT && leftReadOnly) {
            throw new IllegalStateException(
                    "Left document is read-only; cannot merge RIGHT_TO_LEFT");
        }
    }

    /**
     * Applies a single block copy from left → right.
     *
     * <ul>
     *   <li>ADDED  — the block exists only on the right; copying left (nothing) removes it.</li>
     *   <li>REMOVED — the block exists only on the left; copying inserts it into the right.</li>
     *   <li>CHANGED — both sides differ; left content replaces right content.</li>
     * </ul>
     */
    private void applyLeftToRight(
            com.comparetool.model.LineKind kind,
            List<DiffRow> blockRows,
            List<DiffRow> allRows,
            int blockFirstRowIndex) {
        switch (kind) {
            case ADDED -> {
                // Right-only lines → delete them from right
                int firstRight = blockRows.stream()
                        .mapToInt(r -> r.rightLineNumber())
                        .min().getAsInt();
                int lastRight = blockRows.stream()
                        .mapToInt(r -> r.rightLineNumber())
                        .max().getAsInt();
                right.replaceLines(firstRight - 1, lastRight, List.of());
            }
            case REMOVED -> {
                // Left-only lines → insert them into right at the correct position
                List<String> leftLines = blockRows.stream()
                        .map(DiffRow::leftText)
                        .collect(Collectors.toUnmodifiableList());
                int insertAt = findRightInsertionIndex(blockFirstRowIndex, allRows);
                right.replaceLines(insertAt, insertAt, leftLines);
            }
            case CHANGED -> {
                // Both sides differ → replace right slice with left content
                List<String> leftLines = blockRows.stream()
                        .map(DiffRow::leftText)
                        .collect(Collectors.toUnmodifiableList());
                int firstRight = blockRows.stream()
                        .mapToInt(r -> r.rightLineNumber())
                        .min().getAsInt();
                int lastRight = blockRows.stream()
                        .mapToInt(r -> r.rightLineNumber())
                        .max().getAsInt();
                right.replaceLines(firstRight - 1, lastRight, leftLines);
            }
            default -> throw new IllegalArgumentException(
                    "Cannot copy block of kind " + kind
                            + "; only ADDED, REMOVED, and CHANGED blocks are mergeable");
        }
    }

    /**
     * Applies a single block copy from right → left (symmetric to
     * {@link #applyLeftToRight}).
     */
    private void applyRightToLeft(
            com.comparetool.model.LineKind kind,
            List<DiffRow> blockRows,
            List<DiffRow> allRows,
            int blockFirstRowIndex) {
        switch (kind) {
            case REMOVED -> {
                // Left-only lines → delete them from left
                int firstLeft = blockRows.stream()
                        .mapToInt(r -> r.leftLineNumber())
                        .min().getAsInt();
                int lastLeft = blockRows.stream()
                        .mapToInt(r -> r.leftLineNumber())
                        .max().getAsInt();
                left.replaceLines(firstLeft - 1, lastLeft, List.of());
            }
            case ADDED -> {
                // Right-only lines → insert them into left at the correct position
                List<String> rightLines = blockRows.stream()
                        .map(DiffRow::rightText)
                        .collect(Collectors.toUnmodifiableList());
                int insertAt = findLeftInsertionIndex(blockFirstRowIndex, allRows);
                left.replaceLines(insertAt, insertAt, rightLines);
            }
            case CHANGED -> {
                // Both sides differ → replace left slice with right content
                List<String> rightLines = blockRows.stream()
                        .map(DiffRow::rightText)
                        .collect(Collectors.toUnmodifiableList());
                int firstLeft = blockRows.stream()
                        .mapToInt(r -> r.leftLineNumber())
                        .min().getAsInt();
                int lastLeft = blockRows.stream()
                        .mapToInt(r -> r.leftLineNumber())
                        .max().getAsInt();
                left.replaceLines(firstLeft - 1, lastLeft, rightLines);
            }
            default -> throw new IllegalArgumentException(
                    "Cannot copy block of kind " + kind
                            + "; only ADDED, REMOVED, and CHANGED blocks are mergeable");
        }
    }

    /**
     * Returns the 0-based insertion index in the RIGHT document for a REMOVED block.
     *
     * <p>Scans backwards through rows preceding the block to find the last row
     * that has a non-null {@code rightLineNumber}. The insertion point is
     * immediately after that line (1-based line number equals the 0-based
     * insertion index).
     *
     * @return 0 if the block is at the very start of the document and no
     *         right-side line precedes it
     */
    private int findRightInsertionIndex(int blockFirstRowIndex, List<DiffRow> rows) {
        for (int i = blockFirstRowIndex - 1; i >= 0; i--) {
            Integer rln = rows.get(i).rightLineNumber();
            if (rln != null) {
                return rln; // 1-based line number == 0-based "insert after" index
            }
        }
        return 0;
    }

    /**
     * Returns the 0-based insertion index in the LEFT document for an ADDED block
     * (symmetric to {@link #findRightInsertionIndex}).
     */
    private int findLeftInsertionIndex(int blockFirstRowIndex, List<DiffRow> rows) {
        for (int i = blockFirstRowIndex - 1; i >= 0; i--) {
            Integer lln = rows.get(i).leftLineNumber();
            if (lln != null) {
                return lln;
            }
        }
        return 0;
    }

    private void reDiff() {
        currentDiff = engine.diff(left.lines(), right.lines(), options);
    }

    /** Immutable record capturing document state before and after a single merge operation. */
    private record HistoryEntry(
            EditableDocument.Snapshot leftBefore,
            EditableDocument.Snapshot rightBefore,
            EditableDocument.Snapshot leftAfter,
            EditableDocument.Snapshot rightAfter) {}
}
