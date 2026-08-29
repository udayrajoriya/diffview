package com.diffview.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DiffModelTest {

    // -----------------------------------------------------------------------
    // DiffModel construction and helpers
    // -----------------------------------------------------------------------

    @Test
    void constructsWithRowsAndBlocks() {
        List<DiffRow> rows   = List.of(DiffRow.unchanged(1, 1, "same"));
        List<DiffBlock> blocks = List.of(new DiffBlock(0, 0, LineKind.CHANGED));

        DiffModel model = new DiffModel(rows, blocks,
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);

        assertThat(model.rows()).hasSize(1);
        assertThat(model.blocks()).hasSize(1);
        assertThat(model.identical()).isFalse();
        assertThat(model.differenceCount()).isEqualTo(1);
    }

    @Test
    void identicalFactoryProducesEmptyBlockList() {
        List<DiffRow> rows = List.of(DiffRow.unchanged(1, 1, "text"));
        DiffModel model = DiffModel.identical(rows, StandardCharsets.UTF_8, StandardCharsets.UTF_8);

        assertThat(model.identical()).isTrue();
        assertThat(model.blocks()).isEmpty();
        assertThat(model.differenceCount()).isZero();
    }

    @Test
    void emptyFactoryProducesEmptyModel() {
        DiffModel model = DiffModel.empty(null, null);
        assertThat(model.rows()).isEmpty();
        assertThat(model.blocks()).isEmpty();
        assertThat(model.identical()).isTrue();
    }

    @Test
    void nullEncodingsArePermitted() {
        assertThatCode(() -> DiffModel.empty(null, null)).doesNotThrowAnyException();
    }

    @Test
    void rowsListIsImmutable() {
        java.util.List<DiffRow> mutable = new java.util.ArrayList<>();
        mutable.add(DiffRow.unchanged(1, 1, "a"));
        DiffModel model = new DiffModel(mutable, List.of(), null, null, true);

        mutable.add(DiffRow.unchanged(2, 2, "b"));      // mutate source
        assertThat(model.rows()).hasSize(1);             // model unaffected
    }

    @Test
    void nullRowsThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DiffModel(null, List.of(), null, null, true));
    }

    @Test
    void nullBlocksThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DiffModel(List.of(), null, null, null, true));
    }

    // -----------------------------------------------------------------------
    // FileComparisonResult construction
    // -----------------------------------------------------------------------

    @Test
    void fileComparisonResultStoresModelAndPaths() {
        DiffModel model = DiffModel.empty(null, null);
        Path left  = Path.of("/tmp/left.txt");
        Path right = Path.of("/tmp/right.txt");

        FileComparisonResult result = new FileComparisonResult(model, left, right);

        assertThat(result.model()).isSameAs(model);
        assertThat(result.left()).isEqualTo(left);
        assertThat(result.right()).isEqualTo(right);
    }

    @Test
    void fileComparisonResultNullModelThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FileComparisonResult(null,
                        Path.of("/a"), Path.of("/b")));
    }

    @Test
    void fileComparisonResultNullPathThrows() {
        DiffModel model = DiffModel.empty(null, null);
        assertThatNullPointerException()
                .isThrownBy(() -> new FileComparisonResult(model, null, Path.of("/b")));
        assertThatNullPointerException()
                .isThrownBy(() -> new FileComparisonResult(model, Path.of("/a"), null));
    }
}
