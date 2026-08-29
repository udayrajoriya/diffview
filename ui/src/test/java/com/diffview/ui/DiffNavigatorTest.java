package com.diffview.ui;

import com.diffview.model.DiffBlock;
import com.diffview.model.LineKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link DiffNavigator} (task 12.3).
 *
 * <p>No JavaFX platform or TestFX needed — {@link DiffNavigator} is a plain
 * Java class that uses JavaFX properties but does not require a running
 * Application thread for its logic.
 */
class DiffNavigatorTest {

    private DiffNavigator     nav;
    private List<DiffBlock>   threeBlocks;

    @BeforeEach
    void setup() {
        nav = new DiffNavigator();
        threeBlocks = List.of(
                new DiffBlock(2,  3,  LineKind.CHANGED),
                new DiffBlock(7,  8,  LineKind.ADDED),
                new DiffBlock(12, 12, LineKind.REMOVED));
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void initialStateHasNoSelection() {
        assertThat(nav.getCurrentIndex()).isEqualTo(DiffNavigator.NONE);
    }

    @Test
    void initialBlockCountIsZero() {
        assertThat(nav.getBlockCount()).isZero();
    }

    @Test
    void initialHasNoBlocks() {
        assertThat(nav.hasBlocks()).isFalse();
    }

    @Test
    void currentBlockIsNullInitially() {
        assertThat(nav.currentBlock()).isNull();
    }

    // ── setBlocks ─────────────────────────────────────────────────────────────

    @Test
    void setBlocksUpdatesBlockCount() {
        nav.setBlocks(threeBlocks);
        assertThat(nav.getBlockCount()).isEqualTo(3);
    }

    @Test
    void setBlocksResetsSelectionToNone() {
        nav.setBlocks(threeBlocks);
        nav.goNext();                    // → 0
        nav.setBlocks(threeBlocks);      // reset
        assertThat(nav.getCurrentIndex()).isEqualTo(DiffNavigator.NONE);
    }

    @Test
    void setEmptyBlocksDisablesNavigation() {
        nav.setBlocks(threeBlocks);
        nav.goNext();
        nav.setBlocks(List.of());
        assertThat(nav.hasBlocks()).isFalse();
        assertThat(nav.goNext()).isNull();
    }

    // ── goNext ────────────────────────────────────────────────────────────────

    @Test
    void goNextFromNoneSelectsFirstBlock() {
        nav.setBlocks(threeBlocks);
        DiffBlock result = nav.goNext();
        assertThat(result).isEqualTo(threeBlocks.get(0));
        assertThat(nav.getCurrentIndex()).isZero();
    }

    @Test
    void goNextAdvancesForward() {
        nav.setBlocks(threeBlocks);
        nav.goNext();                    // → 0
        DiffBlock result = nav.goNext(); // → 1
        assertThat(result).isEqualTo(threeBlocks.get(1));
        assertThat(nav.getCurrentIndex()).isEqualTo(1);
    }

    @Test
    void goNextAtLastBlockReturnsNull() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); nav.goNext(); nav.goNext(); // 0 → 1 → 2
        DiffBlock result = nav.goNext();
        assertThat(result).isNull();
    }

    @Test
    void goNextAtLastBlockDoesNotChangeIndex() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); nav.goNext(); nav.goNext(); // → 2
        nav.goNext();                              // boundary
        assertThat(nav.getCurrentIndex()).isEqualTo(2);
    }

    @Test
    void goNextOnEmptyBlocksReturnsNull() {
        assertThat(nav.goNext()).isNull();
    }

    // ── goPrevious ────────────────────────────────────────────────────────────

    @Test
    void goPreviousFromNoneSelectsLastBlock() {
        nav.setBlocks(threeBlocks);
        DiffBlock result = nav.goPrevious();
        assertThat(result).isEqualTo(threeBlocks.get(2));
        assertThat(nav.getCurrentIndex()).isEqualTo(2);
    }

    @Test
    void goPreviousMovesBackward() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); nav.goNext(); nav.goNext(); // → 2
        DiffBlock result = nav.goPrevious();      // → 1
        assertThat(result).isEqualTo(threeBlocks.get(1));
        assertThat(nav.getCurrentIndex()).isEqualTo(1);
    }

    @Test
    void goPreviousAtFirstBlockReturnsNull() {
        nav.setBlocks(threeBlocks);
        nav.goNext();                        // → 0
        DiffBlock result = nav.goPrevious(); // boundary
        assertThat(result).isNull();
    }

    @Test
    void goPreviousAtFirstBlockDoesNotChangeIndex() {
        nav.setBlocks(threeBlocks);
        nav.goNext();        // → 0
        nav.goPrevious();    // boundary
        assertThat(nav.getCurrentIndex()).isZero();
    }

    @Test
    void goPreviousOnEmptyBlocksReturnsNull() {
        assertThat(nav.goPrevious()).isNull();
    }

    // ── isAtFirst / isAtLast ──────────────────────────────────────────────────

    @Test
    void isAtFirstTrueWhenAtFirstBlock() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); // → 0
        assertThat(nav.isAtFirst()).isTrue();
    }

    @Test
    void isAtFirstFalseWhenAtMiddleBlock() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); nav.goNext(); // → 1
        assertThat(nav.isAtFirst()).isFalse();
    }

    @Test
    void isAtLastTrueWhenAtLastBlock() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); nav.goNext(); nav.goNext(); // → 2
        assertThat(nav.isAtLast()).isTrue();
    }

    @Test
    void isAtLastFalseWhenAtFirstBlock() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); // → 0
        assertThat(nav.isAtLast()).isFalse();
    }

    // ── currentBlock ──────────────────────────────────────────────────────────

    @Test
    void currentBlockReturnsNullWhenNoneSelected() {
        nav.setBlocks(threeBlocks);
        assertThat(nav.currentBlock()).isNull();
    }

    @Test
    void currentBlockReturnsCorrectBlockWhenSelected() {
        nav.setBlocks(threeBlocks);
        nav.goNext(); nav.goNext(); // → 1
        assertThat(nav.currentBlock()).isEqualTo(threeBlocks.get(1));
    }
}
