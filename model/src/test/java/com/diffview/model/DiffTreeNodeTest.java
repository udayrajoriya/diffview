package com.comparetool.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DiffTreeNodeTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");
    private static final Path REL = Path.of("a/b.txt");

    private FileMeta fileMeta(String rel) {
        return FileMeta.file(Path.of("/root/" + rel), Path.of(rel), 100L, NOW);
    }

    @Nested
    class Construction {

        @Test
        void pairedFileNode() {
            FileMeta left = fileMeta("a.txt");
            FileMeta right = fileMeta("a.txt");
            DiffTreeNode node = DiffTreeNode.paired(REL, false, left, right,
                    FolderItemStatus.IDENTICAL, List.of());

            assertThat(node.relativePath()).isEqualTo(REL);
            assertThat(node.directory()).isFalse();
            assertThat(node.left()).isEqualTo(left);
            assertThat(node.right()).isEqualTo(right);
            assertThat(node.status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(node.children()).isEmpty();
        }

        @Test
        void leftOnlyFactory_setsNullRight() {
            FileMeta left = fileMeta("a.txt");
            DiffTreeNode node = DiffTreeNode.leftOnly(REL, false, left, List.of());

            assertThat(node.left()).isNotNull();
            assertThat(node.right()).isNull();
            assertThat(node.status()).isEqualTo(FolderItemStatus.LEFT_ONLY);
            assertThat(node.isLeftPlaceholder()).isFalse();
            assertThat(node.isRightPlaceholder()).isTrue();
            assertThat(node.isOneSided()).isTrue();
        }

        @Test
        void rightOnlyFactory_setsNullLeft() {
            FileMeta right = fileMeta("a.txt");
            DiffTreeNode node = DiffTreeNode.rightOnly(REL, false, right, List.of());

            assertThat(node.left()).isNull();
            assertThat(node.right()).isNotNull();
            assertThat(node.status()).isEqualTo(FolderItemStatus.RIGHT_ONLY);
            assertThat(node.isLeftPlaceholder()).isTrue();
            assertThat(node.isRightPlaceholder()).isFalse();
            assertThat(node.isOneSided()).isTrue();
        }

        @Test
        void bothNullSidesThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DiffTreeNode(REL, false, null, null,
                            FolderItemStatus.DIFFERENT, List.of()))
                    .withMessageContaining("both null");
        }

        @Test
        void nullRelativePathThrows() {
            FileMeta meta = fileMeta("a.txt");
            assertThatNullPointerException()
                    .isThrownBy(() -> new DiffTreeNode(null, false, meta, meta,
                            FolderItemStatus.IDENTICAL, List.of()));
        }

        @Test
        void nullStatusThrows() {
            FileMeta meta = fileMeta("a.txt");
            assertThatNullPointerException()
                    .isThrownBy(() -> new DiffTreeNode(REL, false, meta, meta,
                            null, List.of()));
        }

        @Test
        void childrenListIsImmutable() {
            FileMeta left = fileMeta("a.txt");
            DiffTreeNode node = DiffTreeNode.leftOnly(REL, false, left, List.of());
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> node.children().add(node));
        }
    }

    @Nested
    class StatusEnumCoverage {

        @ParameterizedTest
        @EnumSource(FolderItemStatus.class)
        void allStatusValuesAreUsable(FolderItemStatus status) {
            assertThat(status).isNotNull();
        }

        @Test
        void differentStatusIsDifferent() {
            assertThat(FolderItemStatus.DIFFERENT.isDifferent()).isTrue();
        }

        @Test
        void leftOnlyIsDifferent() {
            assertThat(FolderItemStatus.LEFT_ONLY.isDifferent()).isTrue();
        }

        @Test
        void rightOnlyIsDifferent() {
            assertThat(FolderItemStatus.RIGHT_ONLY.isDifferent()).isTrue();
        }

        @Test
        void identicalIsNotDifferent() {
            assertThat(FolderItemStatus.IDENTICAL.isDifferent()).isFalse();
        }

        @Test
        void ignoredIsNotDifferent() {
            assertThat(FolderItemStatus.IGNORED.isDifferent()).isFalse();
        }

        @Test
        void hasLeftAndRight() {
            assertThat(FolderItemStatus.IDENTICAL.hasLeft()).isTrue();
            assertThat(FolderItemStatus.IDENTICAL.hasRight()).isTrue();
            assertThat(FolderItemStatus.LEFT_ONLY.hasLeft()).isTrue();
            assertThat(FolderItemStatus.LEFT_ONLY.hasRight()).isFalse();
            assertThat(FolderItemStatus.RIGHT_ONLY.hasLeft()).isFalse();
            assertThat(FolderItemStatus.RIGHT_ONLY.hasRight()).isTrue();
        }
    }

    @Nested
    class DifferenceCount {

        private DiffTreeNode fileNode(FolderItemStatus status) {
            FileMeta meta = fileMeta("x.txt");
            return DiffTreeNode.paired(Path.of("x.txt"), false, meta, meta, status, List.of());
        }

        @Test
        void identicalFileCountsZero() {
            assertThat(fileNode(FolderItemStatus.IDENTICAL).differenceCount()).isZero();
        }

        @Test
        void differentFileCountsOne() {
            assertThat(fileNode(FolderItemStatus.DIFFERENT).differenceCount()).isOne();
        }

        @Test
        void leftOnlyFileCountsOne() {
            FileMeta meta = fileMeta("x.txt");
            DiffTreeNode node = DiffTreeNode.leftOnly(Path.of("x.txt"), false, meta, List.of());
            assertThat(node.differenceCount()).isOne();
        }

        @Test
        void rightOnlyFileCountsOne() {
            FileMeta meta = fileMeta("x.txt");
            DiffTreeNode node = DiffTreeNode.rightOnly(Path.of("x.txt"), false, meta, List.of());
            assertThat(node.differenceCount()).isOne();
        }

        @Test
        void ignoredFileCountsZero() {
            assertThat(fileNode(FolderItemStatus.IGNORED).differenceCount()).isZero();
        }

        @Test
        void directoryAggregatesChildCounts() {
            FileMeta meta = fileMeta("dir");
            DiffTreeNode child1 = fileNode(FolderItemStatus.DIFFERENT);
            DiffTreeNode child2 = fileNode(FolderItemStatus.IDENTICAL);
            DiffTreeNode child3 = DiffTreeNode.leftOnly(Path.of("c.txt"), false, meta, List.of());

            DiffTreeNode dir = DiffTreeNode.paired(
                    Path.of("dir"), true, meta, meta, FolderItemStatus.DIFFERENT,
                    List.of(child1, child2, child3));

            assertThat(dir.differenceCount()).isEqualTo(2);
        }
    }
}
