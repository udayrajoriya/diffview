package com.diffview.app;

import com.diffview.core.diff.LineDiffEngine;
import com.diffview.core.folder.DefaultFolderDiffEngine;
import com.diffview.core.service.DefaultComparisonService;
import com.diffview.infra.concurrent.CancellationToken;
import com.diffview.infra.concurrent.DirectTaskExecutor;
import com.diffview.infra.concurrent.ProgressReporter;
import com.diffview.infra.encoding.JUniversalChardetDetector;
import com.diffview.infra.hash.Sha256HashService;
import com.diffview.infra.io.NioFileIOService;
import com.diffview.model.DiffTreeNode;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderComparisonResult;
import com.diffview.model.FolderItemStatus;
import com.diffview.viewmodel.FileDiffRequest;
import com.diffview.viewmodel.FolderComparisonViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests for the folder comparison flow (task 17.1).
 *
 * <p>Requirements: 2.x, 5.x, 9.x, 13.x, 14.x
 *
 * <p>Uses real I/O and {@link DirectTaskExecutor} for synchronous, deterministic execution.
 */
class FolderComparisonFlowTest {

    private DirectTaskExecutor       executor;
    private DefaultComparisonService service;
    private FolderComparisonViewModel vm;

    @BeforeEach
    void setUp() {
        executor = new DirectTaskExecutor();
        service  = new DefaultComparisonService(
                new LineDiffEngine(),
                new DefaultFolderDiffEngine(),
                new NioFileIOService(new JUniversalChardetDetector()),
                new Sha256HashService(),
                executor);
        vm = new FolderComparisonViewModel(service, executor);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a file (and parent dirs) with the given UTF-8 content. */
    private static void write(Path base, String relativePath, String content) throws IOException {
        Path file = base.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    // ── REQ-9.x: correct counts after folder compare ──────────────────────────

    @Test
    void folderCompareProducesCorrectCounts(@TempDir Path left, @TempDir Path right) throws IOException {
        // 2 identical files
        write(left,  "same1.txt", "identical content");
        write(right, "same1.txt", "identical content");
        write(left,  "same2.txt", "also identical");
        write(right, "same2.txt", "also identical");

        // 1 different file
        write(left,  "diff.txt", "left version");
        write(right, "diff.txt", "right version");

        // 1 left-only
        write(left, "only-left.txt", "only on left");

        // 1 right-only
        write(right, "only-right.txt", "only on right");

        vm.compareFolders(left, right, FolderComparisonOptions.defaults(), null, null);

        assertThat(vm.getIdenticalCount()).isEqualTo(2);
        assertThat(vm.getDifferentCount()).isEqualTo(1);
        assertThat(vm.getLeftOnlyCount()).isEqualTo(1);
        assertThat(vm.getRightOnlyCount()).isEqualTo(1);
    }

    // ── REQ-9.x: drill-down opens correct file pair ───────────────────────────

    @Test
    void openFileDiffSetsCorrectPaths(@TempDir Path left, @TempDir Path right) throws IOException {
        write(left,  "a.txt", "left text");
        write(right, "a.txt", "right text");

        vm.compareFolders(left, right, FolderComparisonOptions.defaults(), null, null);

        FolderComparisonResult result = vm.getResult();
        assertThat(result).isNotNull();

        // Find the DIFFERENT node
        DiffTreeNode differentNode = result.root().children().stream()
                .filter(n -> n.status() == FolderItemStatus.DIFFERENT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a DIFFERENT node"));

        vm.openFileDiff(differentNode);

        FileDiffRequest pending = vm.getPendingFileDiff();
        assertThat(pending).isInstanceOf(FileDiffRequest.Actual.class);
        FileDiffRequest.Actual actual = (FileDiffRequest.Actual) pending;
        assertThat(actual.left()).isEqualTo(left.resolve("a.txt"));
        assertThat(actual.right()).isEqualTo(right.resolve("a.txt"));
    }

    // ── REQ-9.x: status refreshes to IDENTICAL after copyToRight ─────────────

    @Test
    void statusRefreshAfterCopyToRight(@TempDir Path left, @TempDir Path right) throws IOException {
        write(left,  "b.txt", "left content");
        write(right, "b.txt", "different content");

        vm.compareFolders(left, right, FolderComparisonOptions.defaults(), null, null);

        // Find the DIFFERENT node before copy
        DiffTreeNode differentNode = vm.getResult().root().children().stream()
                .filter(n -> n.status() == FolderItemStatus.DIFFERENT)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a DIFFERENT node"));

        // Copy left → right (triggers refreshSingleNode internally)
        vm.copyToRight(differentNode);

        // After refresh, node should be IDENTICAL
        DiffTreeNode refreshedNode = vm.getResult().root().children().stream()
                .filter(n -> n.relativePath().equals(differentNode.relativePath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Node missing after refresh"));
        assertThat(refreshedNode.status()).isEqualTo(FolderItemStatus.IDENTICAL);
    }

    // ── REQ-13.x / REQ-14.x: progress reporter receives increments ───────────

    @Test
    void progressReporterReceivesIncrements(@TempDir Path left, @TempDir Path right) throws IOException {
        write(left,  "p1.txt", "a");
        write(right, "p1.txt", "a");
        write(left,  "p2.txt", "b");
        write(right, "p2.txt", "b");

        AtomicInteger callCount = new AtomicInteger(0);
        ProgressReporter reporter = (current, total, msg) -> callCount.incrementAndGet();

        vm.compareFolders(left, right, FolderComparisonOptions.defaults(), reporter, null);

        assertThat(callCount.get()).isGreaterThan(0);
    }

    // ── REQ-13.x / REQ-14.x: cancellation aborts compare ────────────────────

    @Test
    void cancellationTokenAbortsCompare(@TempDir Path left, @TempDir Path right) throws IOException {
        // Create enough files so the scan encounters the token check
        for (int i = 0; i < 20; i++) {
            write(left,  "file" + i + ".txt", "left " + i);
            write(right, "file" + i + ".txt", "right " + i);
        }

        CancellationToken token = new CancellationToken();
        token.cancel(); // pre-cancel so the very first checkCancelled() throws

        DefaultFolderDiffEngine engine = new DefaultFolderDiffEngine();

        assertThatThrownBy(() ->
                engine.compare(left, right, FolderComparisonOptions.defaults(),
                        ProgressReporter.noOp(), token))
                .isInstanceOf(CancellationException.class);
    }

    // ── REQ-5.x: left-only node triggers Placeholder diff request ─────────────

    @Test
    void openFileDiffForLeftOnlyNodeProducesPlaceholderRequest(
            @TempDir Path left, @TempDir Path right) throws IOException {
        write(left, "onlyleft.txt", "left only content");
        // no corresponding file on right side

        vm.compareFolders(left, right, FolderComparisonOptions.defaults(), null, null);

        DiffTreeNode leftOnlyNode = vm.getResult().root().children().stream()
                .filter(n -> n.status() == FolderItemStatus.LEFT_ONLY)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a LEFT_ONLY node"));

        vm.openFileDiff(leftOnlyNode);

        FileDiffRequest pending = vm.getPendingFileDiff();
        assertThat(pending).isInstanceOf(FileDiffRequest.Placeholder.class);
        FileDiffRequest.Placeholder placeholder = (FileDiffRequest.Placeholder) pending;
        assertThat(placeholder.leftSide()).isTrue();
        assertThat(placeholder.side()).isEqualTo(left.resolve("onlyleft.txt"));
    }
}
