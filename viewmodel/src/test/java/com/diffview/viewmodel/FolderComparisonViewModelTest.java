package com.diffview.viewmodel;

import com.diffview.core.service.ComparisonService;
import com.diffview.infra.concurrent.CancellationToken;
import com.diffview.infra.concurrent.DirectTaskExecutor;
import com.diffview.infra.concurrent.ProgressReporter;
import com.diffview.model.ComparisonOptions;
import com.diffview.model.DiffTreeNode;
import com.diffview.model.FileMeta;
import com.diffview.model.FileMatchMode;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderComparisonResult;
import com.diffview.model.FolderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FolderComparisonViewModel} (task 10.2).
 *
 * <ul>
 *   <li>No JavaFX {@code Node} subclasses.</li>
 *   <li>A {@link DirectTaskExecutor} is injected so {@code compareFolders()} runs synchronously.</li>
 *   <li>{@link ComparisonService} is mocked via Mockito.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FolderComparisonViewModelTest {

    @Mock ComparisonService comparisonService;

    private final DirectTaskExecutor executor = new DirectTaskExecutor();
    private FolderComparisonViewModel vm;

    private static final FolderComparisonOptions DEFAULT_OPTS =
            FolderComparisonOptions.defaults();

    @BeforeEach
    void setUp() {
        vm = new FolderComparisonViewModel(comparisonService, executor);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static FileMeta meta(Path absolute, Path relative) {
        return new FileMeta(absolute, relative, false, 10L, Instant.EPOCH);
    }

    private static FileMeta dirMeta(Path absolute, Path relative) {
        return new FileMeta(absolute, relative, true, 0L, Instant.EPOCH);
    }

    /**
     * Builds a minimal flat tree with a virtual root and given file children.
     * Root uses a dummy left/right of Path.of("") and left/right of the first child paths.
     */
    private static FolderComparisonResult buildResult(Path leftRoot, Path rightRoot,
                                                       List<DiffTreeNode> children) {
        // Root node: both sides present, status derived from children
        FolderItemStatus rootStatus = children.stream().anyMatch(c -> c.status().isDifferent())
                ? FolderItemStatus.DIFFERENT : FolderItemStatus.IDENTICAL;
        DiffTreeNode root = new DiffTreeNode(
                Path.of(""),
                true,
                dirMeta(leftRoot,  Path.of("")),
                dirMeta(rightRoot, Path.of("")),
                rootStatus,
                children);
        return FolderComparisonResult.fromRoot(root, leftRoot, rightRoot);
    }

    private static DiffTreeNode fileNode(Path leftRoot, Path rightRoot,
                                          String relPath, FolderItemStatus status) {
        Path rel = Path.of(relPath);
        // IGNORED items typically exist on both sides; LEFT/RIGHT_ONLY have one side.
        boolean includeLeft  = status != FolderItemStatus.RIGHT_ONLY;
        boolean includeRight = status != FolderItemStatus.LEFT_ONLY;
        FileMeta left  = includeLeft  ? meta(leftRoot.resolve(relPath),  rel) : null;
        FileMeta right = includeRight ? meta(rightRoot.resolve(relPath), rel) : null;
        return new DiffTreeNode(rel, false, left, right, status, List.of());
    }

    private static DiffTreeNode dirNode(Path leftRoot, Path rightRoot,
                                         String relPath, List<DiffTreeNode> children) {
        Path rel = Path.of(relPath);
        FolderItemStatus status = children.stream().anyMatch(c -> c.status().isDifferent())
                ? FolderItemStatus.DIFFERENT : FolderItemStatus.IDENTICAL;
        return new DiffTreeNode(rel, true,
                dirMeta(leftRoot.resolve(relPath),  rel),
                dirMeta(rightRoot.resolve(relPath), rel),
                status, children);
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Nested
    class InitialState {

        @Test
        void resultIsNullBeforeCompare() {
            assertThat(vm.getResult()).isNull();
        }

        @Test
        void visibleNodesIsEmptyBeforeCompare() {
            assertThat(vm.getVisibleNodes()).isEmpty();
        }

        @Test
        void countsAreZeroBeforeCompare() {
            assertThat(vm.getTotalCount()).isZero();
            assertThat(vm.getDifferentCount()).isZero();
            assertThat(vm.getIdenticalCount()).isZero();
        }

        @Test
        void notLoadingBeforeCompare() {
            assertThat(vm.isLoading()).isFalse();
        }

        @Test
        void pendingFileDiffIsNullBeforeAnyAction() {
            assertThat(vm.getPendingFileDiff()).isNull();
        }
    }

    // ── After compareFolders ──────────────────────────────────────────────────

    @Nested
    class AfterCompare {

        @TempDir Path leftRoot;
        @TempDir Path rightRoot;

        @Test
        void resultIsSetAfterCompare() {
            FolderComparisonResult expected = buildResult(leftRoot, rightRoot, List.of());
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(expected));

            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            assertThat(vm.getResult()).isSameAs(expected);
        }

        @Test
        void countsArePopulatedFromResult() {
            DiffTreeNode identical = fileNode(leftRoot, rightRoot, "same.txt", FolderItemStatus.IDENTICAL);
            DiffTreeNode different = fileNode(leftRoot, rightRoot, "diff.txt", FolderItemStatus.DIFFERENT);
            DiffTreeNode leftOnly  = fileNode(leftRoot, rightRoot, "left.txt", FolderItemStatus.LEFT_ONLY);
            FolderComparisonResult r =
                    buildResult(leftRoot, rightRoot, List.of(identical, different, leftOnly));
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(r));

            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            assertThat(vm.getIdenticalCount()).isEqualTo(1);
            assertThat(vm.getDifferentCount()).isEqualTo(1);
            assertThat(vm.getLeftOnlyCount()).isEqualTo(1);
            assertThat(vm.getTotalCount()).isEqualTo(3);
        }

        @Test
        void notLoadingAfterCompareCompletes() {
            FolderComparisonResult r = buildResult(leftRoot, rightRoot, List.of());
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(r));

            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            assertThat(vm.isLoading()).isFalse();
        }
    }

    // ── Filter toggle: showOnlyDifferences ───────────────────────────────────

    @Nested
    class FilterToggle {

        @TempDir Path leftRoot;
        @TempDir Path rightRoot;

        private void setupTreeAndCompare(List<DiffTreeNode> nodes) {
            FolderComparisonResult r = buildResult(leftRoot, rightRoot, nodes);
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(r));
            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);
        }

        @Test
        void allNodesVisibleByDefault() {
            List<DiffTreeNode> nodes = List.of(
                    fileNode(leftRoot, rightRoot, "a.txt", FolderItemStatus.IDENTICAL),
                    fileNode(leftRoot, rightRoot, "b.txt", FolderItemStatus.DIFFERENT));
            setupTreeAndCompare(nodes);

            assertThat(vm.getVisibleNodes()).hasSize(2);
        }

        @Test
        void filterHidesIdenticalNodes() {
            List<DiffTreeNode> nodes = List.of(
                    fileNode(leftRoot, rightRoot, "same.txt",  FolderItemStatus.IDENTICAL),
                    fileNode(leftRoot, rightRoot, "diff.txt",  FolderItemStatus.DIFFERENT),
                    fileNode(leftRoot, rightRoot, "left.txt",  FolderItemStatus.LEFT_ONLY),
                    fileNode(leftRoot, rightRoot, "right.txt", FolderItemStatus.RIGHT_ONLY));
            setupTreeAndCompare(nodes);

            vm.setShowOnlyDifferences(true);

            List<DiffTreeNode> visible = vm.getVisibleNodes();
            assertThat(visible).hasSize(3);
            assertThat(visible).noneMatch(n -> n.status() == FolderItemStatus.IDENTICAL);
        }

        @Test
        void filterHidesIgnoredNodes() {
            List<DiffTreeNode> nodes = List.of(
                    fileNode(leftRoot, rightRoot, "diff.txt",    FolderItemStatus.DIFFERENT),
                    fileNode(leftRoot, rightRoot, "ignored.txt", FolderItemStatus.IGNORED));
            setupTreeAndCompare(nodes);

            vm.setShowOnlyDifferences(true);

            List<DiffTreeNode> visible = vm.getVisibleNodes();
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void togglingFilterOffRestoresAllNodes() {
            List<DiffTreeNode> nodes = List.of(
                    fileNode(leftRoot, rightRoot, "same.txt", FolderItemStatus.IDENTICAL),
                    fileNode(leftRoot, rightRoot, "diff.txt", FolderItemStatus.DIFFERENT));
            setupTreeAndCompare(nodes);

            vm.setShowOnlyDifferences(true);
            assertThat(vm.getVisibleNodes()).hasSize(1);

            vm.setShowOnlyDifferences(false);
            assertThat(vm.getVisibleNodes()).hasSize(2);
        }

        @Test
        void dirWithNoDiffsIsPrunedWhenFilterOn() {
            DiffTreeNode subDir = dirNode(leftRoot, rightRoot, "sub",
                    List.of(fileNode(leftRoot, rightRoot, "sub/a.txt", FolderItemStatus.IDENTICAL)));
            List<DiffTreeNode> nodes = List.of(
                    fileNode(leftRoot, rightRoot, "diff.txt", FolderItemStatus.DIFFERENT),
                    subDir);
            setupTreeAndCompare(nodes);

            vm.setShowOnlyDifferences(true);

            List<DiffTreeNode> visible = vm.getVisibleNodes();
            // Only the different file should be visible; the identical-only dir is pruned
            assertThat(visible).hasSize(1);
            assertThat(visible.get(0).relativePath()).isEqualTo(Path.of("diff.txt"));
        }

        @Test
        void dirWithDiffsIsShownWhenFilterOn() {
            DiffTreeNode subDir = dirNode(leftRoot, rightRoot, "sub",
                    List.of(fileNode(leftRoot, rightRoot, "sub/diff.txt", FolderItemStatus.DIFFERENT)));
            setupTreeAndCompare(List.of(subDir));

            vm.setShowOnlyDifferences(true);

            List<DiffTreeNode> visible = vm.getVisibleNodes();
            // dir + its child
            assertThat(visible).hasSize(2);
        }
    }

    // ── openFileDiff ──────────────────────────────────────────────────────────

    @Nested
    class OpenFileDiff {

        @TempDir Path leftRoot;
        @TempDir Path rightRoot;

        private void runCompare(List<DiffTreeNode> nodes) {
            FolderComparisonResult r = buildResult(leftRoot, rightRoot, nodes);
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(r));
            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);
        }

        @Test
        void pairedNodeYieldsActualRequest() {
            DiffTreeNode paired = fileNode(leftRoot, rightRoot, "file.txt", FolderItemStatus.DIFFERENT);
            runCompare(List.of(paired));

            vm.openFileDiff(paired);

            FileDiffRequest req = vm.getPendingFileDiff();
            assertThat(req).isInstanceOf(FileDiffRequest.Actual.class);
            FileDiffRequest.Actual actual = (FileDiffRequest.Actual) req;
            assertThat(actual.left()).isEqualTo(leftRoot.resolve("file.txt"));
            assertThat(actual.right()).isEqualTo(rightRoot.resolve("file.txt"));
        }

        @Test
        void leftOnlyNodeYieldsPlaceholderWithLeftSideTrue() {
            DiffTreeNode leftOnly = fileNode(leftRoot, rightRoot, "only.txt", FolderItemStatus.LEFT_ONLY);
            runCompare(List.of(leftOnly));

            vm.openFileDiff(leftOnly);

            FileDiffRequest req = vm.getPendingFileDiff();
            assertThat(req).isInstanceOf(FileDiffRequest.Placeholder.class);
            FileDiffRequest.Placeholder ph = (FileDiffRequest.Placeholder) req;
            assertThat(ph.leftSide()).isTrue();
            assertThat(ph.side()).isEqualTo(leftRoot.resolve("only.txt"));
        }

        @Test
        void rightOnlyNodeYieldsPlaceholderWithLeftSideFalse() {
            DiffTreeNode rightOnly = fileNode(leftRoot, rightRoot, "only.txt", FolderItemStatus.RIGHT_ONLY);
            runCompare(List.of(rightOnly));

            vm.openFileDiff(rightOnly);

            FileDiffRequest req = vm.getPendingFileDiff();
            assertThat(req).isInstanceOf(FileDiffRequest.Placeholder.class);
            FileDiffRequest.Placeholder ph = (FileDiffRequest.Placeholder) req;
            assertThat(ph.leftSide()).isFalse();
            assertThat(ph.side()).isEqualTo(rightRoot.resolve("only.txt"));
        }

        @Test
        void directoryNodeIsIgnored() {
            DiffTreeNode dir = dirNode(leftRoot, rightRoot, "sub", List.of());
            // dirNode has no file children, give it an identical file child to avoid empty
            runCompare(List.of(dir));

            vm.openFileDiff(dir);

            assertThat(vm.getPendingFileDiff()).isNull();
        }

        @Test
        void pendingFileDiffPropertyFiresOnChange() {
            DiffTreeNode leftOnly = fileNode(leftRoot, rightRoot, "f.txt", FolderItemStatus.LEFT_ONLY);
            runCompare(List.of(leftOnly));

            FileDiffRequest[] captured = {null};
            vm.pendingFileDiffProperty().addListener((obs, o, n) -> captured[0] = n);

            vm.openFileDiff(leftOnly);

            assertThat(captured[0]).isInstanceOf(FileDiffRequest.Placeholder.class);
        }
    }

    // ── Status refresh after copy ─────────────────────────────────────────────

    @Nested
    class StatusRefreshAfterCopy {

        @TempDir Path leftRoot;
        @TempDir Path rightRoot;

        @Test
        void copyToRightRefreshesNodeStatusToIdentical() throws IOException {
            // Create a real file on the left side
            Path leftFile = leftRoot.resolve("hello.txt");
            Files.writeString(leftFile, "hello");

            DiffTreeNode leftOnly = fileNode(leftRoot, rightRoot, "hello.txt", FolderItemStatus.LEFT_ONLY);
            FolderComparisonResult initial = buildResult(leftRoot, rightRoot, List.of(leftOnly));
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(initial));

            // After copy, evaluatePair should return IDENTICAL
            when(comparisonService.evaluatePair(any(), any(), any()))
                    .thenReturn(FolderItemStatus.IDENTICAL);

            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            // Act
            vm.copyToRight(leftOnly);

            // The right file should now exist (copied)
            assertThat(rightRoot.resolve("hello.txt")).exists();

            // The viewmodel should have re-evaluated the node
            verify(comparisonService).evaluatePair(any(), any(), any());

            // Result counts should reflect the updated status
            FolderComparisonResult updated = vm.getResult();
            assertThat(updated).isNotNull();
            // The root tree should have the node updated (identicalCount includes it)
            assertThat(updated.identicalCount()).isEqualTo(1);
            assertThat(updated.leftOnlyCount()).isEqualTo(0);
        }

        @Test
        void copyToLeftRefreshesNodeStatusToIdentical() throws IOException {
            // Create a real file on the right side
            Path rightFile = rightRoot.resolve("world.txt");
            Files.writeString(rightFile, "world");

            DiffTreeNode rightOnly = fileNode(leftRoot, rightRoot, "world.txt", FolderItemStatus.RIGHT_ONLY);
            FolderComparisonResult initial = buildResult(leftRoot, rightRoot, List.of(rightOnly));
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(initial));

            when(comparisonService.evaluatePair(any(), any(), any()))
                    .thenReturn(FolderItemStatus.IDENTICAL);

            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            vm.copyToLeft(rightOnly);

            assertThat(leftRoot.resolve("world.txt")).exists();
            verify(comparisonService).evaluatePair(any(), any(), any());

            FolderComparisonResult updated = vm.getResult();
            assertThat(updated.identicalCount()).isEqualTo(1);
            assertThat(updated.rightOnlyCount()).isEqualTo(0);
        }

        @Test
        void visibleListIsRefreshedAfterCopyWhenFilterIsOn() throws IOException {
            Path leftFile = leftRoot.resolve("onlyleft.txt");
            Files.writeString(leftFile, "content");

            DiffTreeNode leftOnly = fileNode(leftRoot, rightRoot, "onlyleft.txt", FolderItemStatus.LEFT_ONLY);
            FolderComparisonResult initial = buildResult(leftRoot, rightRoot, List.of(leftOnly));
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(initial));
            when(comparisonService.evaluatePair(any(), any(), any()))
                    .thenReturn(FolderItemStatus.IDENTICAL);

            vm.setShowOnlyDifferences(true);
            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            // Before copy: leftOnly is visible (it's a difference)
            assertThat(vm.getVisibleNodes()).hasSize(1);

            vm.copyToRight(leftOnly);

            // After copy: now IDENTICAL, hidden by filter
            assertThat(vm.getVisibleNodes()).isEmpty();
        }

        @Test
        void copyToRightThrowsWhenNodeHasNoLeftSide() {
            DiffTreeNode rightOnly = fileNode(leftRoot, rightRoot, "f.txt", FolderItemStatus.RIGHT_ONLY);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> vm.copyToRight(rightOnly));
        }

        @Test
        void copyToLeftThrowsWhenNodeHasNoRightSide() {
            DiffTreeNode leftOnly = fileNode(leftRoot, rightRoot, "f.txt", FolderItemStatus.LEFT_ONLY);
            // Need a result so currentLeft/Right are set
            FolderComparisonResult r = buildResult(leftRoot, rightRoot, List.of(leftOnly));
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(r));
            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> vm.copyToLeft(leftOnly));
        }
    }

    // ── Expand / Collapse ─────────────────────────────────────────────────────

    @Nested
    class ExpandCollapse {

        @TempDir Path leftRoot;
        @TempDir Path rightRoot;

        private void setupNestedTree() {
            DiffTreeNode child = fileNode(leftRoot, rightRoot, "sub/file.txt", FolderItemStatus.DIFFERENT);
            DiffTreeNode sub   = dirNode(leftRoot, rightRoot, "sub", List.of(child));
            FolderComparisonResult r = buildResult(leftRoot, rightRoot, List.of(sub));
            when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                    .thenReturn(CompletableFuture.completedFuture(r));
            vm.compareFolders(leftRoot, rightRoot, DEFAULT_OPTS, null, null);
        }

        @Test
        void initiallyAllNodesVisible() {
            setupNestedTree();
            // dir + its child = 2
            assertThat(vm.getVisibleNodes()).hasSize(2);
        }

        @Test
        void collapseAllHidesDirChildren() {
            setupNestedTree();

            vm.collapseAll();

            // Only the 'sub' dir node; its child is hidden
            assertThat(vm.getVisibleNodes()).hasSize(1);
            assertThat(vm.getVisibleNodes().get(0).directory()).isTrue();
        }

        @Test
        void expandAllAfterCollapseRestoresChildren() {
            setupNestedTree();
            vm.collapseAll();

            vm.expandAll();

            assertThat(vm.getVisibleNodes()).hasSize(2);
        }

        @Test
        void toggleExpandCollapsesSingleDir() {
            setupNestedTree();
            DiffTreeNode sub = vm.getVisibleNodes().get(0); // first node is the dir

            vm.toggleExpand(sub);
            assertThat(vm.getVisibleNodes()).hasSize(1); // child hidden

            vm.toggleExpand(sub);
            assertThat(vm.getVisibleNodes()).hasSize(2); // child restored
        }
    }

    // ── Guard rails ───────────────────────────────────────────────────────────

    @Nested
    class GuardRails {

        @Test
        void nullComparisonServiceThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new FolderComparisonViewModel(null, executor));
        }

        @Test
        void nullExecutorThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new FolderComparisonViewModel(comparisonService, null));
        }

        @Test
        void openFileDiffNullThrowsNPE() {
            assertThatNullPointerException()
                    .isThrownBy(() -> vm.openFileDiff(null));
        }

        @Test
        void compareFoldersNullLeftThrowsNPE(@TempDir Path rightRoot) {
            assertThatNullPointerException()
                    .isThrownBy(() -> vm.compareFolders(null, rightRoot, DEFAULT_OPTS, null, null));
        }
    }
}
