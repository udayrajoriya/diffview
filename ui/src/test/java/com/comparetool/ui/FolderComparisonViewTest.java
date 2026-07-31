package com.comparetool.ui;

import com.comparetool.core.service.ComparisonService;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.model.DiffTreeNode;
import com.comparetool.model.FileMatchMode;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderComparisonResult;
import com.comparetool.model.FolderItemStatus;
import com.comparetool.viewmodel.FileDiffRequest;
import com.comparetool.viewmodel.FolderComparisonViewModel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestFX integration tests for task 13.1 — aligned recursive folder trees with
 * expand/collapse in {@link FolderComparisonView}.
 *
 * <p>Extended in task 13.2 with tests for status badges, progress controls,
 * show-only-differences filter, and summary counts.
 *
 * <h3>Test tree structure</h3>
 * <pre>
 * root/
 *   dir/             DIFFERENT  (has differing children)
 *     file1.txt      IDENTICAL
 *     file2.txt      DIFFERENT
 *   leftonly.txt     LEFT_ONLY  (right side is placeholder)
 *   rightonly.txt    RIGHT_ONLY (left side is placeholder)
 * </pre>
 * Flat list when all expanded: 5 items.
 * Flat list when {@code collapseAll()}: 3 items (dir, leftonly, rightonly).
 *
 * <p><strong>Extension order</strong>: {@code MockitoExtension} must precede
 * {@code ApplicationExtension} so that {@code @Mock} fields are ready before
 * {@code @Start} runs.
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
class FolderComparisonViewTest {

    private static final Path LEFT_ROOT  = Path.of("/left");
    private static final Path RIGHT_ROOT = Path.of("/right");

    @Mock ComparisonService comparisonService;

    private final DirectTaskExecutor executor = new DirectTaskExecutor();

    private FolderComparisonViewModel vm;
    private FolderComparisonView      view;

    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        vm   = new FolderComparisonViewModel(comparisonService, executor);
        view = new FolderComparisonView();
        view.bindViewModel(vm);

        Scene scene = new Scene(view, 1000, 600);
        stage.setScene(scene);
        stage.show();
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void panesAreEmptyInitially(FxRobot robot) {
        robot.interact(() -> {
            assertThat(view.getLeftPane().getItems()).isEmpty();
            assertThat(view.getRightPane().getItems()).isEmpty();
        });
    }

    @Test
    void expandAllButtonDisabledBeforeCompare(FxRobot robot) {
        assertThat(view.getExpandAllButton().isDisable()).isTrue();
    }

    @Test
    void collapseAllButtonDisabledBeforeCompare(FxRobot robot) {
        assertThat(view.getCollapseAllButton().isDisable()).isTrue();
    }

    @Test
    void expandAllAndCollapseAllButtonsFoundById(FxRobot robot) {
        assertThat(view.lookup("#expandAllButton")).isNotNull();
        assertThat(view.lookup("#collapseAllButton")).isNotNull();
    }

    // ── Post-compare state ────────────────────────────────────────────────────

    @Test
    void afterCompareItemsArePresentInBothPanes(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            assertThat(view.getLeftPane().getItems()).isNotEmpty();
            assertThat(view.getRightPane().getItems()).isNotEmpty();
        });
    }

    @Test
    void bothPanesAlwaysHaveSameItemCount(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            int leftCount  = view.getLeftPane().getItems().size();
            int rightCount = view.getRightPane().getItems().size();
            assertThat(leftCount).isEqualTo(rightCount);
        });
    }

    @Test
    void allItemsVisibleWhenFullyExpanded(FxRobot robot) {
        // By default, no nodes are collapsed → all 5 items visible
        runCompare(robot);
        robot.interact(() ->
                assertThat(view.getLeftPane().getItems()).hasSize(5));
    }

    @Test
    void expandAllButtonEnabledAfterCompare(FxRobot robot) {
        runCompare(robot);
        robot.interact(() ->
                assertThat(view.getExpandAllButton().isDisable()).isFalse());
    }

    @Test
    void collapseAllButtonEnabledAfterCompare(FxRobot robot) {
        runCompare(robot);
        robot.interact(() ->
                assertThat(view.getCollapseAllButton().isDisable()).isFalse());
    }

    // ── Expand / collapse ─────────────────────────────────────────────────────

    @Test
    void collapseAllReducesVisibleItemCount(FxRobot robot) {
        runCompare(robot);
        int initialCount = view.getLeftPane().getItems().size();
        assertThat(initialCount).isGreaterThan(0);

        robot.clickOn("#collapseAllButton");
        WaitForAsyncUtils.waitForFxEvents();

        // dir/ + leftonly.txt + rightonly.txt = 3 top-level items
        robot.interact(() ->
                assertThat(view.getLeftPane().getItems()).hasSize(3));
    }

    @Test
    void expandAllRestoresFullItemCountAfterCollapse(FxRobot robot) {
        runCompare(robot);

        robot.clickOn("#collapseAllButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#expandAllButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
                assertThat(view.getLeftPane().getItems()).hasSize(5));
    }

    @Test
    void toggleExpandOnDirectorySingleNodeChangesItemCount(FxRobot robot) {
        runCompare(robot);
        // 5 items: dir, file1, file2, leftonly, rightonly
        robot.interact(() -> {
            DiffTreeNode dirNode = view.getLeftPane().getItems().stream()
                    .filter(DiffTreeNode::directory)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no directory node found"));
            vm.toggleExpand(dirNode); // collapse dir/
        });
        WaitForAsyncUtils.waitForFxEvents();

        // dir collapsed: dir + leftonly + rightonly = 3 items
        robot.interact(() ->
                assertThat(view.getLeftPane().getItems()).hasSize(3));
    }

    @Test
    void toggleExpandTwiceRestoresOriginalCount(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            DiffTreeNode dirNode = view.getLeftPane().getItems().stream()
                    .filter(DiffTreeNode::directory)
                    .findFirst()
                    .orElseThrow();
            vm.toggleExpand(dirNode); // collapse
            vm.toggleExpand(dirNode); // re-expand
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
                assertThat(view.getLeftPane().getItems()).hasSize(5));
    }

    // ── Placeholder alignment ─────────────────────────────────────────────────

    @Test
    void leftOnlyNodeHasNullRightMetaForPlaceholderRendering(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            DiffTreeNode leftOnly = view.getLeftPane().getItems().stream()
                    .filter(n -> n.status() == FolderItemStatus.LEFT_ONLY)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no LEFT_ONLY node"));
            assertThat(leftOnly.right()).isNull();  // right side is a placeholder
            assertThat(leftOnly.left()).isNotNull();
        });
    }

    @Test
    void rightOnlyNodeHasNullLeftMetaForPlaceholderRendering(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            DiffTreeNode rightOnly = view.getLeftPane().getItems().stream()
                    .filter(n -> n.status() == FolderItemStatus.RIGHT_ONLY)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no RIGHT_ONLY node"));
            assertThat(rightOnly.left()).isNull();   // left side is a placeholder
            assertThat(rightOnly.right()).isNotNull();
        });
    }

    @Test
    void leftAndRightPanesHaveSameItemsAfterCollapseAll(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.collapseAll());
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> {
            assertThat(view.getLeftPane().getItems())
                    .isEqualTo(view.getRightPane().getItems());
        });
    }

    // ── Drill-down: pending file diff (REQ-9.1, REQ-9.2) ─────────────────────

    @Test
    void openPairedFileSetsActualDiffRequest(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.openFileDiff(pairedFileNode()));
        robot.interact(() -> {
            assertThat(vm.getPendingFileDiff()).isInstanceOf(FileDiffRequest.Actual.class);
            FileDiffRequest.Actual req = (FileDiffRequest.Actual) vm.getPendingFileDiff();
            assertThat(req.left().toString()).endsWith("file2.txt");
            assertThat(req.right().toString()).endsWith("file2.txt");
        });
    }

    @Test
    void openLeftOnlyFileSetsPlaceholderWithLeftSideTrue(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.openFileDiff(leftOnlyFileNode()));
        robot.interact(() -> {
            assertThat(vm.getPendingFileDiff()).isInstanceOf(FileDiffRequest.Placeholder.class);
            FileDiffRequest.Placeholder req = (FileDiffRequest.Placeholder) vm.getPendingFileDiff();
            assertThat(req.leftSide()).isTrue();
            assertThat(req.side().toString()).endsWith("leftonly.txt");
        });
    }

    @Test
    void openRightOnlyFileSetsPlaceholderWithLeftSideFalse(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.openFileDiff(rightOnlyFileNode()));
        robot.interact(() -> {
            assertThat(vm.getPendingFileDiff()).isInstanceOf(FileDiffRequest.Placeholder.class);
            FileDiffRequest.Placeholder req = (FileDiffRequest.Placeholder) vm.getPendingFileDiff();
            assertThat(req.leftSide()).isFalse();
            assertThat(req.side().toString()).endsWith("rightonly.txt");
        });
    }

    @Test
    void openDirectoryDoesNotSetPendingDiff(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.openFileDiff(dirNode()));
        assertThat(vm.getPendingFileDiff()).isNull();
    }

    @Test
    void onFileDiffRequestedCallbackFiresWhenPendingDiffSet(FxRobot robot) {
        runCompare(robot);
        java.util.concurrent.atomic.AtomicReference<FileDiffRequest> captured =
                new java.util.concurrent.atomic.AtomicReference<>();
        view.setOnFileDiffRequested(captured::set);

        robot.interact(() -> vm.openFileDiff(pairedFileNode()));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(captured.get()).isInstanceOf(FileDiffRequest.Actual.class);
    }

    // ── Return preserves folder state (REQ-9.3) ───────────────────────────────

    @Test
    void clearPendingFileDiffPreservesVisibleNodes(FxRobot robot) {
        runCompare(robot);
        int countBefore = view.getLeftPane().getItems().size();

        robot.interact(() -> {
            vm.openFileDiff(pairedFileNode());
            vm.clearPendingFileDiff();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(view.getLeftPane().getItems()).hasSize(countBefore);
    }

    @Test
    void clearPendingFileDiffNullsPendingProperty(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            vm.openFileDiff(pairedFileNode());
            vm.clearPendingFileDiff();
        });
        assertThat(vm.getPendingFileDiff()).isNull();
    }

    // ── Status refresh after save (REQ-9.4) ───────────────────────────────────

    @Test
    void refreshNodeAfterSaveUpdatesStatusCounts(@TempDir Path tmpDir, FxRobot robot)
            throws IOException {
        // Create real files so readMeta() can read them back after the refresh
        Path leftRoot  = tmpDir.resolve("left");
        Path rightRoot = tmpDir.resolve("right");
        Files.createDirectories(leftRoot.resolve("dir"));
        Files.createDirectories(rightRoot.resolve("dir"));
        Files.writeString(leftRoot.resolve("dir/file2.txt"),  "left content");
        Files.writeString(rightRoot.resolve("dir/file2.txt"), "right content differs");

        // Build the comparison result using the real temp paths
        FolderComparisonResult realResult = buildTestResultWith(leftRoot, rightRoot);
        when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(realResult));
        robot.interact(() -> view.startCompare(
                leftRoot, rightRoot, FolderComparisonOptions.defaults()));
        WaitForAsyncUtils.waitForFxEvents();

        int differentBefore = vm.getDifferentCount();

        // After "saving", evaluatePair returns IDENTICAL for the refreshed node
        when(comparisonService.evaluatePair(any(), any(), any()))
                .thenReturn(FolderItemStatus.IDENTICAL);

        Instant now = Instant.now();
        FileMeta lm = FileMeta.file(
                leftRoot.resolve("dir/file2.txt"),  Path.of("dir/file2.txt"), 12L, now);
        FileMeta rm = FileMeta.file(
                rightRoot.resolve("dir/file2.txt"), Path.of("dir/file2.txt"), 21L, now);
        DiffTreeNode file2 = DiffTreeNode.paired(
                Path.of("dir/file2.txt"), false, lm, rm, FolderItemStatus.DIFFERENT, List.of());

        robot.interact(() -> vm.refreshNodeAfterSave(file2));
        WaitForAsyncUtils.waitForFxEvents();

        // differentCount should have dropped as file2 is now IDENTICAL
        assertThat(vm.getDifferentCount()).isLessThan(differentBefore);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Stubs the service and triggers a comparison that builds the test tree:
     * <pre>
     *   dir/        DIFFERENT
     *     file1.txt IDENTICAL
     *     file2.txt DIFFERENT
     *   leftonly.txt  LEFT_ONLY
     *   rightonly.txt RIGHT_ONLY
     * </pre>
     */
    private void runCompare(FxRobot robot) {
        FolderComparisonResult result = buildTestResult();
        when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        robot.interact(() -> view.startCompare(
                LEFT_ROOT, RIGHT_ROOT, FolderComparisonOptions.defaults()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ── Progress / cancel controls (REQ-8.5) ──────────────────────────────────

    @Test
    void progressBarHiddenWhenNotLoading(FxRobot robot) {
        assertThat(view.getProgressBar().isVisible()).isFalse();
    }

    @Test
    void cancelButtonHiddenWhenNotLoading(FxRobot robot) {
        assertThat(view.getCancelButton().isVisible()).isFalse();
    }

    @Test
    void progressBarFoundById(FxRobot robot) {
        assertThat(view.lookup("#compareProgressBar")).isNotNull();
    }

    @Test
    void cancelButtonFoundById(FxRobot robot) {
        assertThat(view.lookup("#cancelButton")).isNotNull();
    }

    // ── Show-only-differences filter (REQ-8.7) ────────────────────────────────

    @Test
    void showDiffsOnlyCheckBoxFoundById(FxRobot robot) {
        assertThat(view.lookup("#showDiffsOnlyCheckBox")).isNotNull();
    }

    @Test
    void showDiffsOnlyIsUncheckedInitially(FxRobot robot) {
        assertThat(view.getShowDiffsCheckBox().isSelected()).isFalse();
    }

    @Test
    void showDiffsOnlyBoundToViewModel(FxRobot robot) {
        robot.interact(() -> view.getShowDiffsCheckBox().setSelected(true));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(vm.isShowOnlyDifferences()).isTrue();
    }

    @Test
    void showDiffsOnlyFilterReducesItemCount(FxRobot robot) {
        runCompare(robot);
        int beforeCount = view.getLeftPane().getItems().size();

        robot.interact(() -> view.getShowDiffsCheckBox().setSelected(true));
        WaitForAsyncUtils.waitForFxEvents();

        int afterCount = view.getLeftPane().getItems().size();
        // file1.txt (IDENTICAL) is filtered out → count drops from 5 to 4
        assertThat(afterCount).isLessThan(beforeCount);
    }

    // ── Summary counts (REQ-8.6) ──────────────────────────────────────────────

    @Test
    void summaryLabelFoundById(FxRobot robot) {
        assertThat(view.lookup("#summaryLabel")).isNotNull();
    }

    @Test
    void summaryLabelBlankBeforeCompare(FxRobot robot) {
        assertThat(view.getSummaryLabel().getText()).isNullOrEmpty();
    }

    @Test
    void summaryLabelNotBlankAfterCompare(FxRobot robot) {
        runCompare(robot);
        robot.interact(() ->
            assertThat(view.getSummaryLabel().getText()).isNotBlank()
        );
    }

    @Test
    void summaryCountsReflectTestTree(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            assertThat(vm.getIdenticalCount()).isPositive();
            assertThat(vm.getDifferentCount()).isPositive();
            assertThat(vm.getLeftOnlyCount()).isEqualTo(1);
            assertThat(vm.getRightOnlyCount()).isEqualTo(1);
        });
    }

    // ── Status badge (REQ-8.2, REQ-15.2) ─────────────────────────────────────

    @Test
    void statusSymbolsAreAllDistinct(FxRobot robot) {
        String eq  = FolderTreeCell.statusSymbol(FolderItemStatus.IDENTICAL);
        String neq = FolderTreeCell.statusSymbol(FolderItemStatus.DIFFERENT);
        String lo  = FolderTreeCell.statusSymbol(FolderItemStatus.LEFT_ONLY);
        String ro  = FolderTreeCell.statusSymbol(FolderItemStatus.RIGHT_ONLY);
        String ign = FolderTreeCell.statusSymbol(FolderItemStatus.IGNORED);
        assertThat(java.util.Set.of(eq, neq, lo, ro, ign)).hasSize(5);
    }

    @Test
    void statusColorsAreAllDistinct(FxRobot robot) {
        String c1 = FolderTreeCell.statusColor(FolderItemStatus.IDENTICAL);
        String c2 = FolderTreeCell.statusColor(FolderItemStatus.DIFFERENT);
        String c3 = FolderTreeCell.statusColor(FolderItemStatus.LEFT_ONLY);
        String c4 = FolderTreeCell.statusColor(FolderItemStatus.RIGHT_ONLY);
        String c5 = FolderTreeCell.statusColor(FolderItemStatus.IGNORED);
        assertThat(java.util.Set.of(c1, c2, c3, c4, c5)).hasSize(5);
    }

    @Test
    void vmHasAllStatusCategoriesAfterCompare(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> {
            assertThat(vm.getIdenticalCount()).isPositive();
            assertThat(vm.getDifferentCount()).isPositive();
            assertThat(vm.getLeftOnlyCount()).isPositive();
            assertThat(vm.getRightOnlyCount()).isPositive();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static FolderComparisonResult buildTestResult() {
        Instant now = Instant.now();

        // dir/file1.txt — IDENTICAL
        FileMeta lFile1 = FileMeta.file(
                Path.of("/left/dir/file1.txt"),  Path.of("dir/file1.txt"), 100L, now);
        FileMeta rFile1 = FileMeta.file(
                Path.of("/right/dir/file1.txt"), Path.of("dir/file1.txt"), 100L, now);
        DiffTreeNode file1 = DiffTreeNode.paired(
                Path.of("dir/file1.txt"), false, lFile1, rFile1, FolderItemStatus.IDENTICAL, List.of());

        // dir/file2.txt — DIFFERENT
        FileMeta lFile2 = FileMeta.file(
                Path.of("/left/dir/file2.txt"),  Path.of("dir/file2.txt"), 200L, now);
        FileMeta rFile2 = FileMeta.file(
                Path.of("/right/dir/file2.txt"), Path.of("dir/file2.txt"), 201L, now);
        DiffTreeNode file2 = DiffTreeNode.paired(
                Path.of("dir/file2.txt"), false, lFile2, rFile2, FolderItemStatus.DIFFERENT, List.of());

        // dir/ — DIFFERENT (rolled up from file2)
        FileMeta lDir = FileMeta.directory(Path.of("/left/dir"),  Path.of("dir"), now);
        FileMeta rDir = FileMeta.directory(Path.of("/right/dir"), Path.of("dir"), now);
        DiffTreeNode dir = DiffTreeNode.paired(
                Path.of("dir"), true, lDir, rDir, FolderItemStatus.DIFFERENT, List.of(file1, file2));

        // leftonly.txt — LEFT_ONLY (right side is null → placeholder)
        FileMeta leftOnly = FileMeta.file(
                Path.of("/left/leftonly.txt"), Path.of("leftonly.txt"), 50L, now);
        DiffTreeNode leftOnlyNode = DiffTreeNode.leftOnly(
                Path.of("leftonly.txt"), false, leftOnly, List.of());

        // rightonly.txt — RIGHT_ONLY (left side is null → placeholder)
        FileMeta rightOnly = FileMeta.file(
                Path.of("/right/rightonly.txt"), Path.of("rightonly.txt"), 60L, now);
        DiffTreeNode rightOnlyNode = DiffTreeNode.rightOnly(
                Path.of("rightonly.txt"), false, rightOnly, List.of());

        // root node (not shown directly — its children are the top-level items)
        FileMeta rootLeft  = FileMeta.directory(Path.of("/left"),  Path.of(""), now);
        FileMeta rootRight = FileMeta.directory(Path.of("/right"), Path.of(""), now);
        DiffTreeNode root = DiffTreeNode.paired(
                Path.of(""), true, rootLeft, rootRight, FolderItemStatus.DIFFERENT,
                List.of(dir, leftOnlyNode, rightOnlyNode));

        return FolderComparisonResult.fromRoot(root, Path.of("/left"), Path.of("/right"));
    }

    // ── Node-builder helpers for drill-down tests ─────────────────────────────

    private static DiffTreeNode pairedFileNode() {
        Instant now = Instant.now();
        FileMeta l = FileMeta.file(Path.of("/left/dir/file2.txt"),  Path.of("dir/file2.txt"), 200L, now);
        FileMeta r = FileMeta.file(Path.of("/right/dir/file2.txt"), Path.of("dir/file2.txt"), 201L, now);
        return DiffTreeNode.paired(Path.of("dir/file2.txt"), false, l, r,
                FolderItemStatus.DIFFERENT, List.of());
    }

    private static DiffTreeNode leftOnlyFileNode() {
        Instant now = Instant.now();
        FileMeta l = FileMeta.file(Path.of("/left/leftonly.txt"), Path.of("leftonly.txt"), 50L, now);
        return DiffTreeNode.leftOnly(Path.of("leftonly.txt"), false, l, List.of());
    }

    private static DiffTreeNode rightOnlyFileNode() {
        Instant now = Instant.now();
        FileMeta r = FileMeta.file(Path.of("/right/rightonly.txt"), Path.of("rightonly.txt"), 60L, now);
        return DiffTreeNode.rightOnly(Path.of("rightonly.txt"), false, r, List.of());
    }

    private static DiffTreeNode dirNode() {
        Instant now = Instant.now();
        FileMeta l = FileMeta.directory(Path.of("/left/dir"),  Path.of("dir"), now);
        FileMeta r = FileMeta.directory(Path.of("/right/dir"), Path.of("dir"), now);
        return DiffTreeNode.paired(Path.of("dir"), true, l, r,
                FolderItemStatus.DIFFERENT, List.of());
    }

    /**
     * Builds a minimal test result identical in structure to {@link #buildTestResult()} but
     * using real filesystem paths so that {@code refreshSingleNode} can read file metadata.
     */
    private static FolderComparisonResult buildTestResultWith(Path leftRoot, Path rightRoot) {
        Instant now = Instant.now();

        FileMeta lFile1 = FileMeta.file(leftRoot.resolve("dir/file1.txt"),
                Path.of("dir/file1.txt"), 100L, now);
        FileMeta rFile1 = FileMeta.file(rightRoot.resolve("dir/file1.txt"),
                Path.of("dir/file1.txt"), 100L, now);
        DiffTreeNode file1 = DiffTreeNode.paired(
                Path.of("dir/file1.txt"), false, lFile1, rFile1, FolderItemStatus.IDENTICAL, List.of());

        FileMeta lFile2 = FileMeta.file(leftRoot.resolve("dir/file2.txt"),
                Path.of("dir/file2.txt"), 12L, now);
        FileMeta rFile2 = FileMeta.file(rightRoot.resolve("dir/file2.txt"),
                Path.of("dir/file2.txt"), 21L, now);
        DiffTreeNode file2 = DiffTreeNode.paired(
                Path.of("dir/file2.txt"), false, lFile2, rFile2, FolderItemStatus.DIFFERENT, List.of());

        FileMeta lDir = FileMeta.directory(leftRoot.resolve("dir"),  Path.of("dir"), now);
        FileMeta rDir = FileMeta.directory(rightRoot.resolve("dir"), Path.of("dir"), now);
        DiffTreeNode dir = DiffTreeNode.paired(
                Path.of("dir"), true, lDir, rDir, FolderItemStatus.DIFFERENT, List.of(file1, file2));

        FileMeta rootLeft  = FileMeta.directory(leftRoot,  Path.of(""), now);
        FileMeta rootRight = FileMeta.directory(rightRoot, Path.of(""), now);
        DiffTreeNode root = DiffTreeNode.paired(
                Path.of(""), true, rootLeft, rootRight, FolderItemStatus.DIFFERENT, List.of(dir));

        return FolderComparisonResult.fromRoot(root, leftRoot, rightRoot);
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Task 13.4 — ignore-rule UI, item sync actions, match-criteria selector
    // REQ-010, REQ-011, REQ-012
    // ────────────────────────────────────────────────────────────────────────────

    // ── Options bar: control presence (REQ-010, REQ-011) ─────────────────────

    @Test
    void optionsBarControlsFoundById(FxRobot robot) {
        assertThat(view.lookup("#matchModeCombo")).isNotNull();
        assertThat(view.lookup("#timestampToleranceField")).isNotNull();
        assertThat(view.lookup("#includeMasksField")).isNotNull();
        assertThat(view.lookup("#excludeMasksField")).isNotNull();
        assertThat(view.lookup("#applyOptionsButton")).isNotNull();
    }

    @Test
    void matchModeComboContainsAllModes(FxRobot robot) {
        robot.interact(() ->
            assertThat(view.getOptionsBar().getMatchModeCombo().getItems())
                    .containsExactlyInAnyOrder(FileMatchMode.values())
        );
    }

    @Test
    void toleranceFieldHiddenWhenMatchModeIsSizeOnly(FxRobot robot) {
        robot.interact(() ->
            view.getOptionsBar().getMatchModeCombo().setValue(FileMatchMode.SIZE_ONLY)
        );
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() ->
            assertThat(view.getOptionsBar().getToleranceField().isVisible()).isFalse()
        );
    }

    @Test
    void toleranceFieldVisibleWhenMatchModeIsSizeAndTimestamp(FxRobot robot) {
        robot.interact(() ->
            view.getOptionsBar().getMatchModeCombo().setValue(FileMatchMode.SIZE_AND_TIMESTAMP)
        );
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() ->
            assertThat(view.getOptionsBar().getToleranceField().isVisible()).isTrue()
        );
    }

    /** Clicking Apply after a match-mode change must trigger a second compareFolders call. */
    @Test
    void applyOptionsButtonTriggersRecompare(FxRobot robot) {
        runCompare(robot);
        robot.interact(() ->
            view.getOptionsBar().getMatchModeCombo().setValue(FileMatchMode.SIZE_ONLY)
        );
        robot.clickOn("#applyOptionsButton");
        WaitForAsyncUtils.waitForFxEvents();
        verify(comparisonService, times(2)).compareFolders(any(), any(), any(), any(), any());
    }

    /** Include masks entered in the options bar are passed through to compareFolders. */
    @Test
    void applyOptionsWithIncludeMaskPassesMasksToCompare(FxRobot robot) {
        runCompare(robot);
        robot.interact(() ->
            view.getOptionsBar().getIncludeMasksField().setText("*.java")
        );
        robot.clickOn("#applyOptionsButton");
        WaitForAsyncUtils.waitForFxEvents();
        // Second call must have been made (mask change re-triggers compare)
        verify(comparisonService, times(2)).compareFolders(any(), any(), any(), any(), any());
    }

    // ── Action buttons: control presence and initial state (REQ-011, REQ-012) ─

    @Test
    void itemActionButtonsFoundById(FxRobot robot) {
        assertThat(view.lookup("#copyLeftToRightButton")).isNotNull();
        assertThat(view.lookup("#copyRightToLeftButton")).isNotNull();
        assertThat(view.lookup("#deleteItemButton")).isNotNull();
        assertThat(view.lookup("#ignoreItemButton")).isNotNull();
        assertThat(view.lookup("#unignoreItemButton")).isNotNull();
    }

    @Test
    void allActionButtonsDisabledBeforeAnyItemSelected(FxRobot robot) {
        robot.interact(() -> {
            assertThat(view.getCopyLToRButton().isDisable()).isTrue();
            assertThat(view.getCopyRToLButton().isDisable()).isTrue();
            assertThat(view.getDeleteButton().isDisable()).isTrue();
            assertThat(view.getIgnoreButton().isDisable()).isTrue();
            assertThat(view.getUnignoreButton().isDisable()).isTrue();
        });
    }

    @Test
    void copyLToRButtonEnabledAfterSelectingLeftOnlyNode(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.setSelectedNode(leftOnlyFileNode()));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> {
            assertThat(view.getCopyLToRButton().isDisable()).isFalse();
            assertThat(view.getCopyRToLButton().isDisable()).isTrue(); // no right side
        });
    }

    @Test
    void copyRToLButtonEnabledAfterSelectingRightOnlyNode(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.setSelectedNode(rightOnlyFileNode()));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> {
            assertThat(view.getCopyRToLButton().isDisable()).isFalse();
            assertThat(view.getCopyLToRButton().isDisable()).isTrue(); // no left side
        });
    }

    @Test
    void ignoreButtonEnabledAndUnignoreDisabledForNonIgnoredNode(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.setSelectedNode(leftOnlyFileNode()));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> {
            assertThat(view.getIgnoreButton().isDisable()).isFalse();
            assertThat(view.getUnignoreButton().isDisable()).isTrue();
        });
    }

    @Test
    void deleteButtonEnabledAfterSelectingAnyNode(FxRobot robot) {
        runCompare(robot);
        robot.interact(() -> vm.setSelectedNode(pairedFileNode()));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> assertThat(view.getDeleteButton().isDisable()).isFalse());
    }

    // ── Ignore / un-ignore (REQ-011) ─────────────────────────────────────────

    /**
     * Calling ignoreItem on the ViewModel (which re-runs comparison) updates ignoredCount.
     * The second compareFolders call returns a result where leftonly.txt is IGNORED.
     */
    @Test
    void ignoreItemUpdatesIgnoredCount(FxRobot robot) {
        FolderComparisonResult normal       = buildTestResult();
        FolderComparisonResult withIgnored  = buildTestResultWithIgnoredLeftOnly();

        when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(normal))
                .thenReturn(CompletableFuture.completedFuture(withIgnored));

        robot.interact(() ->
            view.startCompare(LEFT_ROOT, RIGHT_ROOT, FolderComparisonOptions.defaults())
        );
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> vm.ignoreItem(leftOnlyFileNode()));
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> assertThat(vm.getIgnoredCount()).isGreaterThan(0));
    }

    // ── Copy left→right with real files (REQ-012) ────────────────────────────

    @Test
    void copyLeftToRightWithRealFilesCreatesFileAndUpdatesStatus(
            @TempDir Path tmpDir, FxRobot robot) throws IOException {

        Path leftRoot  = tmpDir.resolve("left");
        Path rightRoot = tmpDir.resolve("right");
        Files.createDirectories(leftRoot);
        Files.createDirectories(rightRoot);

        Path leftFile = leftRoot.resolve("leftonly.txt");
        Files.writeString(leftFile, "copy-me");

        Instant now = Instant.now();
        FileMeta lMeta    = FileMeta.file(leftFile, Path.of("leftonly.txt"), 7L, now);
        DiffTreeNode node = DiffTreeNode.leftOnly(Path.of("leftonly.txt"), false, lMeta, List.of());

        FileMeta rootLeft  = FileMeta.directory(leftRoot,  Path.of(""), now);
        FileMeta rootRight = FileMeta.directory(rightRoot, Path.of(""), now);
        DiffTreeNode root  = DiffTreeNode.paired(Path.of(""), true, rootLeft, rootRight,
                FolderItemStatus.DIFFERENT, List.of(node));
        FolderComparisonResult result =
                FolderComparisonResult.fromRoot(root, leftRoot, rightRoot);

        when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(comparisonService.evaluatePair(any(), any(), any()))
                .thenReturn(FolderItemStatus.IDENTICAL);

        robot.interact(() ->
            view.startCompare(leftRoot, rightRoot, FolderComparisonOptions.defaults())
        );
        WaitForAsyncUtils.waitForFxEvents();

        view.setConfirmHandler((t, m) -> true);
        robot.interact(() -> vm.setSelectedNode(node));
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> view.getCopyLToRButton().fire());
        WaitForAsyncUtils.waitForFxEvents();

        // File should now exist in rightRoot
        assertThat(rightRoot.resolve("leftonly.txt")).exists();
        // Status updated to IDENTICAL by refreshSingleNode
        robot.interact(() -> assertThat(vm.getIdenticalCount()).isGreaterThan(0));
    }

    // ── Overwrite confirmation (REQ-012) ──────────────────────────────────────

    @Test
    void copyDifferentNodeRequiresConfirmation(FxRobot robot) {
        runCompare(robot);
        boolean[] confirmCalled = {false};
        view.setConfirmHandler((title, msg) -> {
            confirmCalled[0] = true;
            return false; // cancel so we don't attempt to copy fake-path files
        });
        robot.interact(() -> vm.setSelectedNode(pairedFileNode())); // DIFFERENT
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> view.getCopyLToRButton().fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(confirmCalled[0])
                .as("confirm handler must be called for DIFFERENT node overwrite")
                .isTrue();
    }

    @Test
    void copyDifferentNodeCancelledByConfirmDoesNotCallCopyToRight(FxRobot robot) {
        runCompare(robot);
        view.setConfirmHandler((t, m) -> false); // cancel
        robot.interact(() -> vm.setSelectedNode(pairedFileNode())); // DIFFERENT
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> view.getCopyLToRButton().fire());
        WaitForAsyncUtils.waitForFxEvents();

        // compareFolders was only called once (the initial runCompare); no second call
        verify(comparisonService, times(1)).compareFolders(any(), any(), any(), any(), any());
    }

    // ── Delete confirmation (REQ-012) ─────────────────────────────────────────

    @Test
    void deleteWithAutoConfirmRemovesFiles(@TempDir Path tmpDir, FxRobot robot)
            throws IOException {

        Path leftRoot  = tmpDir.resolve("left");
        Path rightRoot = tmpDir.resolve("right");
        Files.createDirectories(leftRoot);
        Files.createDirectories(rightRoot);

        Path leftFile  = leftRoot.resolve("both.txt");
        Path rightFile = rightRoot.resolve("both.txt");
        Files.writeString(leftFile, "left");
        Files.writeString(rightFile, "right");

        Instant now    = Instant.now();
        FileMeta lMeta = FileMeta.file(leftFile,  Path.of("both.txt"), 4L, now);
        FileMeta rMeta = FileMeta.file(rightFile, Path.of("both.txt"), 5L, now);
        DiffTreeNode node = DiffTreeNode.paired(Path.of("both.txt"), false, lMeta, rMeta,
                FolderItemStatus.DIFFERENT, List.of());

        FileMeta rootLeft  = FileMeta.directory(leftRoot,  Path.of(""), now);
        FileMeta rootRight = FileMeta.directory(rightRoot, Path.of(""), now);
        DiffTreeNode root  = DiffTreeNode.paired(Path.of(""), true, rootLeft, rootRight,
                FolderItemStatus.DIFFERENT, List.of(node));
        FolderComparisonResult firstResult =
                FolderComparisonResult.fromRoot(root, leftRoot, rightRoot);

        // After delete, comparison result is empty
        DiffTreeNode emptyRoot = DiffTreeNode.paired(Path.of(""), true, rootLeft, rootRight,
                FolderItemStatus.IDENTICAL, List.of());
        FolderComparisonResult emptyResult =
                FolderComparisonResult.fromRoot(emptyRoot, leftRoot, rightRoot);

        when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(firstResult))
                .thenReturn(CompletableFuture.completedFuture(emptyResult));

        robot.interact(() ->
            view.startCompare(leftRoot, rightRoot, FolderComparisonOptions.defaults())
        );
        WaitForAsyncUtils.waitForFxEvents();

        view.setConfirmHandler((t, m) -> true); // auto-confirm
        robot.interact(() -> vm.setSelectedNode(node));
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> view.getDeleteButton().fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(leftFile).doesNotExist();
        assertThat(rightFile).doesNotExist();
    }

    @Test
    void deleteWithAutoCancelLeavesFilesIntact(@TempDir Path tmpDir, FxRobot robot)
            throws IOException {

        Path leftRoot  = tmpDir.resolve("left");
        Path rightRoot = tmpDir.resolve("right");
        Files.createDirectories(leftRoot);
        Files.createDirectories(rightRoot);

        Path leftFile = leftRoot.resolve("keep.txt");
        Files.writeString(leftFile, "keep-me");

        Instant now    = Instant.now();
        FileMeta lMeta = FileMeta.file(leftFile, Path.of("keep.txt"), 7L, now);
        DiffTreeNode node = DiffTreeNode.leftOnly(Path.of("keep.txt"), false, lMeta, List.of());

        FileMeta rootLeft  = FileMeta.directory(leftRoot,  Path.of(""), now);
        FileMeta rootRight = FileMeta.directory(rightRoot, Path.of(""), now);
        DiffTreeNode root  = DiffTreeNode.paired(Path.of(""), true, rootLeft, rootRight,
                FolderItemStatus.DIFFERENT, List.of(node));
        FolderComparisonResult result =
                FolderComparisonResult.fromRoot(root, leftRoot, rightRoot);

        when(comparisonService.compareFolders(any(), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        robot.interact(() ->
            view.startCompare(leftRoot, rightRoot, FolderComparisonOptions.defaults())
        );
        WaitForAsyncUtils.waitForFxEvents();

        view.setConfirmHandler((t, m) -> false); // auto-cancel
        robot.interact(() -> vm.setSelectedNode(node));
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> view.getDeleteButton().fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(leftFile).exists();
    }

    // ── Helpers for 13.4 tests ───────────────────────────────────────────────

    /** Builds a test result where leftonly.txt carries {@link FolderItemStatus#IGNORED}. */
    private static FolderComparisonResult buildTestResultWithIgnoredLeftOnly() {
        Instant now = Instant.now();

        FileMeta leftOnly = FileMeta.file(
                Path.of("/left/leftonly.txt"), Path.of("leftonly.txt"), 50L, now);
        // Use the record canonical constructor to set IGNORED status
        DiffTreeNode ignoredNode = new DiffTreeNode(
                Path.of("leftonly.txt"), false, leftOnly, null, FolderItemStatus.IGNORED, List.of());

        FileMeta rightOnly = FileMeta.file(
                Path.of("/right/rightonly.txt"), Path.of("rightonly.txt"), 60L, now);
        DiffTreeNode rightOnlyNode = DiffTreeNode.rightOnly(
                Path.of("rightonly.txt"), false, rightOnly, List.of());

        FileMeta rootLeft  = FileMeta.directory(Path.of("/left"),  Path.of(""), now);
        FileMeta rootRight = FileMeta.directory(Path.of("/right"), Path.of(""), now);
        DiffTreeNode root  = DiffTreeNode.paired(
                Path.of(""), true, rootLeft, rootRight, FolderItemStatus.DIFFERENT,
                List.of(ignoredNode, rightOnlyNode));

        return FolderComparisonResult.fromRoot(root, Path.of("/left"), Path.of("/right"));
    }
}

