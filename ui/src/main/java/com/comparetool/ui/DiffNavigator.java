package com.comparetool.ui;

import com.comparetool.model.DiffBlock;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.List;
import java.util.Objects;

/**
 * Manages navigation through diff blocks for the file comparison view (task 12.3).
 *
 * <p>Tracks the index of the currently selected diff block and provides
 * {@link #goNext()} / {@link #goPrevious()} actions. Both the current index
 * and the total block count are exposed as observable properties so the UI
 * can bind counter labels and button states to them.
 *
 * <h3>Index contract</h3>
 * <ul>
 *   <li>{@link #NONE} ({@code -1}) → no block selected (initial state or empty model)</li>
 *   <li>{@code 0..n-1} → the block at that index is selected</li>
 * </ul>
 *
 * <h3>Boundary behavior (REQ-003)</h3>
 * <p>When {@link #goNext()} is called at the last block (or {@link #goPrevious()} at
 * the first block), the method returns {@code null} and the selection does not change.
 * The UI layer uses this to suppress navigation and indicate the boundary to the user
 * (e.g. by disabling the corresponding button).
 */
public class DiffNavigator {

    /** Sentinel value meaning "no block selected". */
    public static final int NONE = -1;

    private List<DiffBlock> blocks = List.of();

    /** Zero-based index of the currently selected block, or {@link #NONE}. */
    private final SimpleIntegerProperty currentIndex = new SimpleIntegerProperty(NONE);

    /** Total number of blocks (mirrors {@code blocks.size()}). */
    private final SimpleIntegerProperty blockCount = new SimpleIntegerProperty(0);

    // ── Mutators ───────────────────────────────────────────────────────────────

    /**
     * Replaces the block list and resets the selection to {@link #NONE}.
     *
     * @param blocks new list of diff blocks (must not be {@code null})
     */
    public void setBlocks(List<DiffBlock> blocks) {
        this.blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        blockCount.set(this.blocks.size());
        currentIndex.set(NONE);
    }

    /**
     * Advances to the next block.  If no block is currently selected, selects
     * the first one.  If already at the last block, returns {@code null} and
     * leaves the selection unchanged (boundary condition per REQ-003).
     *
     * @return the newly selected block, or {@code null} if already at the last
     */
    public DiffBlock goNext() {
        if (blocks.isEmpty()) return null;
        int idx = currentIndex.get();
        if (idx < blocks.size() - 1) {
            idx++;
            currentIndex.set(idx);
            return blocks.get(idx);
        }
        return null; // boundary – caller should indicate this to the user
    }

    /**
     * Moves to the previous block.  If no block is currently selected, selects
     * the last one.  If already at the first block, returns {@code null} and
     * leaves the selection unchanged (boundary condition per REQ-003).
     *
     * @return the newly selected block, or {@code null} if already at the first
     */
    public DiffBlock goPrevious() {
        if (blocks.isEmpty()) return null;
        int idx = currentIndex.get();
        if (idx == NONE) {
            // Nothing selected → jump to the last block
            idx = blocks.size() - 1;
            currentIndex.set(idx);
            return blocks.get(idx);
        }
        if (idx > 0) {
            idx--;
            currentIndex.set(idx);
            return blocks.get(idx);
        }
        return null; // boundary
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    /** Returns {@code true} when no blocks exist or the first block is selected. */
    public boolean isAtFirst() {
        return blocks.isEmpty() || currentIndex.get() == 0;
    }

    /** Returns {@code true} when no blocks exist or the last block is selected. */
    public boolean isAtLast() {
        return blocks.isEmpty() || currentIndex.get() == blocks.size() - 1;
    }

    /** Returns {@code true} if the block list is non-empty. */
    public boolean hasBlocks() {
        return !blocks.isEmpty();
    }

    /**
     * Returns the currently selected block, or {@code null} when the selection
     * is {@link #NONE} or the block list is empty.
     */
    public DiffBlock currentBlock() {
        int idx = currentIndex.get();
        return (idx == NONE || blocks.isEmpty()) ? null : blocks.get(idx);
    }

    // ── Observable properties ─────────────────────────────────────────────────

    /** Observable current index; value is {@link #NONE} when nothing is selected. */
    public ReadOnlyIntegerProperty currentIndexProperty() { return currentIndex; }

    /** Observable total number of diff blocks. */
    public ReadOnlyIntegerProperty blockCountProperty()   { return blockCount; }

    /** Zero-based index of the selected block, or {@link #NONE}. */
    public int getCurrentIndex() { return currentIndex.get(); }

    /** Total number of diff blocks. */
    public int getBlockCount()   { return blockCount.get(); }
}
