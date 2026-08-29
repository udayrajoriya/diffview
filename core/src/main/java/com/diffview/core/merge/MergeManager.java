package com.diffview.core.merge;

import com.diffview.model.DiffBlock;
import com.diffview.model.MergeDirection;

/**
 * Applies directional merge operations on a pair of {@link EditableDocument}s and
 * maintains a re-diffed model after every change.
 *
 * <p>Undo/redo history is implemented in {@code DefaultMergeManager} and backed by a
 * command-pattern stack (see task 5.3).
 */
public interface MergeManager {

    /**
     * Copies the content of {@code block} from the source side to the target side.
     *
     * <ul>
     *   <li>{@link MergeDirection#LEFT_TO_RIGHT}: write left content into the right document.</li>
     *   <li>{@link MergeDirection#RIGHT_TO_LEFT}: write right content into the left document.</li>
     * </ul>
     *
     * @throws IllegalStateException    if the target document is read-only.
     * @throws IllegalArgumentException if {@code block} has kind {@code UNCHANGED}.
     */
    void copyBlock(DiffBlock block, MergeDirection direction);

    /**
     * Copies all difference blocks in the given direction, making the target
     * document identical to the source document.
     *
     * @throws IllegalStateException if the target document is read-only.
     */
    void copyAll(MergeDirection direction);

    /** Returns {@code true} when there is at least one operation that can be undone. */
    boolean canUndo();

    /** Returns {@code true} when there is at least one undone operation that can be redone. */
    boolean canRedo();

    /**
     * Reverts the most recent merge operation.
     *
     * @throws UnsupportedOperationException if undo is not yet available.
     * @throws IllegalStateException         if there is nothing to undo.
     */
    void undo();

    /**
     * Re-applies the most recently undone operation.
     *
     * @throws UnsupportedOperationException if redo is not yet available.
     * @throws IllegalStateException         if there is nothing to redo.
     */
    void redo();

    /** Returns the editable document for the left side. */
    EditableDocument leftDocument();

    /** Returns the editable document for the right side. */
    EditableDocument rightDocument();

    /** Returns the current {@link com.diffview.model.DiffModel} reflecting the latest merge state. */
    com.diffview.model.DiffModel currentDiff();
}
