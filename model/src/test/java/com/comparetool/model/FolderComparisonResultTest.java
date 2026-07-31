package com.comparetool.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class FolderComparisonResultTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    private FileMeta meta(String rel) {
        return FileMeta.file(Path.of("/root/" + rel), Path.of(rel), 100L, NOW);
    }

    private DiffTreeNode fileNode(String name, FolderItemStatus status) {
        FileMeta m = meta(name);
        return DiffTreeNode.paired(Path.of(name), false, m, m, status, List.of());
    }

    /** Minimal root directory node with the given children. */
    private DiffTreeNode rootDir(List<DiffTreeNode> children) {
        FileMeta m = meta(".");
        return DiffTreeNode.paired(Path.of("."), true, m, m, FolderItemStatus.DIFFERENT, children);
    }

    @Nested
    class DirectConstruction {

        @Test
        void validResultStoresAllFields() {
            DiffTreeNode root = rootDir(List.of());
            FolderComparisonResult r = new FolderComparisonResult(root, 3, 1, 2, 0, 1);

            assertThat(r.root()).isEqualTo(root);
            assertThat(r.identicalCount()).isEqualTo(3);
            assertThat(r.differentCount()).isEqualTo(1);
            assertThat(r.leftOnlyCount()).isEqualTo(2);
            assertThat(r.rightOnlyCount()).isEqualTo(0);
            assertThat(r.ignoredCount()).isEqualTo(1);
        }

        @Test
        void nullRootThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new FolderComparisonResult(null, 0, 0, 0, 0, 0));
        }

        @Test
        void negativeIdenticalCountThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FolderComparisonResult(rootDir(List.of()), -1, 0, 0, 0, 0));
        }

        @Test
        void negativeDifferentCountThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FolderComparisonResult(rootDir(List.of()), 0, -1, 0, 0, 0));
        }

        @Test
        void negativeLeftOnlyCountThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FolderComparisonResult(rootDir(List.of()), 0, 0, -1, 0, 0));
        }

        @Test
        void negativeRightOnlyCountThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FolderComparisonResult(rootDir(List.of()), 0, 0, 0, -1, 0));
        }

        @Test
        void negativeIgnoredCountThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new FolderComparisonResult(rootDir(List.of()), 0, 0, 0, 0, -1));
        }
    }

    @Nested
    class AggregationHelpers {

        @Test
        void totalCount_sumsAllCategories() {
            DiffTreeNode root = rootDir(List.of());
            FolderComparisonResult r = new FolderComparisonResult(root, 2, 1, 3, 1, 2);
            assertThat(r.totalCount()).isEqualTo(9);
        }

        @Test
        void totalDifferenceCount_excludesIdenticalAndIgnored() {
            DiffTreeNode root = rootDir(List.of());
            FolderComparisonResult r = new FolderComparisonResult(root, 10, 3, 2, 1, 5);
            assertThat(r.totalDifferenceCount()).isEqualTo(6);
        }

        @Test
        void isFullyIdentical_trueWhenNoChanges() {
            DiffTreeNode root = rootDir(List.of());
            FolderComparisonResult r = new FolderComparisonResult(root, 5, 0, 0, 0, 0);
            assertThat(r.isFullyIdentical()).isTrue();
        }

        @Test
        void isFullyIdentical_falseWhenDifferencesExist() {
            DiffTreeNode root = rootDir(List.of());
            FolderComparisonResult r = new FolderComparisonResult(root, 5, 1, 0, 0, 0);
            assertThat(r.isFullyIdentical()).isFalse();
        }
    }

    @Nested
    class FromRootFactory {

        @Test
        void countsIdenticalFiles() {
            DiffTreeNode child = fileNode("a.txt", FolderItemStatus.IDENTICAL);
            DiffTreeNode root = rootDir(List.of(child));

            FolderComparisonResult r = FolderComparisonResult.fromRoot(
                    root, Path.of("/left"), Path.of("/right"));

            assertThat(r.identicalCount()).isEqualTo(1);
            assertThat(r.differentCount()).isZero();
            assertThat(r.leftOnlyCount()).isZero();
            assertThat(r.rightOnlyCount()).isZero();
            assertThat(r.ignoredCount()).isZero();
        }

        @Test
        void countsDifferentFiles() {
            DiffTreeNode child = fileNode("b.txt", FolderItemStatus.DIFFERENT);
            DiffTreeNode root = rootDir(List.of(child));

            FolderComparisonResult r = FolderComparisonResult.fromRoot(
                    root, Path.of("/left"), Path.of("/right"));

            assertThat(r.differentCount()).isEqualTo(1);
            assertThat(r.identicalCount()).isZero();
        }

        @Test
        void countsOneSidedFiles() {
            FileMeta m = meta("c.txt");
            DiffTreeNode leftOnly = DiffTreeNode.leftOnly(Path.of("c.txt"), false, m, List.of());
            FileMeta m2 = meta("d.txt");
            DiffTreeNode rightOnly = DiffTreeNode.rightOnly(Path.of("d.txt"), false, m2, List.of());
            DiffTreeNode root = rootDir(List.of(leftOnly, rightOnly));

            FolderComparisonResult r = FolderComparisonResult.fromRoot(
                    root, Path.of("/left"), Path.of("/right"));

            assertThat(r.leftOnlyCount()).isEqualTo(1);
            assertThat(r.rightOnlyCount()).isEqualTo(1);
        }

        @Test
        void countsIgnoredFiles() {
            DiffTreeNode ignored = fileNode("e.txt", FolderItemStatus.IGNORED);
            DiffTreeNode root = rootDir(List.of(ignored));

            FolderComparisonResult r = FolderComparisonResult.fromRoot(
                    root, Path.of("/left"), Path.of("/right"));

            assertThat(r.ignoredCount()).isEqualTo(1);
        }

        @Test
        void aggregatesMixedTree() {
            DiffTreeNode id1 = fileNode("id1.txt", FolderItemStatus.IDENTICAL);
            DiffTreeNode id2 = fileNode("id2.txt", FolderItemStatus.IDENTICAL);
            DiffTreeNode diff = fileNode("diff.txt", FolderItemStatus.DIFFERENT);
            FileMeta subMeta = meta("sub");
            DiffTreeNode sub = DiffTreeNode.paired(Path.of("sub"), true, subMeta, subMeta,
                    FolderItemStatus.DIFFERENT, List.of(diff));
            FileMeta lm = meta("lo.txt");
            DiffTreeNode lo = DiffTreeNode.leftOnly(Path.of("lo.txt"), false, lm, List.of());
            DiffTreeNode root = rootDir(List.of(id1, id2, sub, lo));

            FolderComparisonResult r = FolderComparisonResult.fromRoot(
                    root, Path.of("/left"), Path.of("/right"));

            assertThat(r.identicalCount()).isEqualTo(2);
            assertThat(r.differentCount()).isEqualTo(1);
            assertThat(r.leftOnlyCount()).isEqualTo(1);
            assertThat(r.rightOnlyCount()).isZero();
            assertThat(r.totalCount()).isEqualTo(4);
        }

        @Test
        void fromRootNullThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> FolderComparisonResult.fromRoot(
                            null, Path.of("/left"), Path.of("/right")));
        }
    }
}
