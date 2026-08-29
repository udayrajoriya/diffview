package com.comparetool.app;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.core.folder.DefaultFolderDiffEngine;
import com.comparetool.core.service.DefaultComparisonService;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.infra.encoding.JUniversalChardetDetector;
import com.comparetool.infra.hash.Sha256HashService;
import com.comparetool.infra.io.NioFileIOService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DecodedText;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.MergeDirection;
import com.comparetool.viewmodel.FileComparisonViewModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests for the file comparison flow (task 17.1).
 *
 * <p>Requirements: 2.x, 5.x, 7.x, 8.x
 *
 * <p>Uses real I/O ({@link NioFileIOService}) and a {@link DirectTaskExecutor} so all
 * async operations complete synchronously on the calling thread.
 */
class FileComparisonFlowTest {

    private DirectTaskExecutor      executor;
    private NioFileIOService        fileIOService;
    private DefaultComparisonService service;
    private FileComparisonViewModel  vm;

    @BeforeEach
    void setUp() {
        executor      = new DirectTaskExecutor();
        fileIOService = new NioFileIOService(new JUniversalChardetDetector());
        service       = new DefaultComparisonService(
                new LineDiffEngine(),
                new DefaultFolderDiffEngine(),
                fileIOService,
                new Sha256HashService(),
                executor);
        vm = new FileComparisonViewModel(service, new LineDiffEngine(), executor, fileIOService);
    }

    // ── REQ-2.x / REQ-5.x: compare → copy all L→R → save → verify on-disk content ──

    @Test
    void compareAndSaveMergePreservesContent(@TempDir Path tmp) throws Exception {
        Path left  = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");

        Files.writeString(left,  "alpha\nbeta\ngamma\n", StandardCharsets.UTF_8);
        Files.writeString(right, "alpha\nDELTA\ngamma\n", StandardCharsets.UTF_8);

        Future<?> f = vm.compare(left, right, ComparisonOptions.defaults());
        f.get(); // DirectTaskExecutor — completes synchronously

        assertThat(vm.getDiffModel()).isNotNull();
        assertThat(vm.getDifferenceCount()).isGreaterThan(0);

        // Copy all diffs from left to right
        vm.copyAll(MergeDirection.LEFT_TO_RIGHT);
        assertThat(vm.isRightDirty()).isTrue();

        vm.saveRight();
        assertThat(vm.isRightDirty()).isFalse();

        // On-disk right file must now equal the original left content
        String saved = Files.readString(right, StandardCharsets.UTF_8);
        assertThat(saved).isEqualTo("alpha\nbeta\ngamma\n");
    }

    // ── REQ-7.x: encoding preserved after save ────────────────────────────────

    @Test
    void compareAndSavePreservesUTF8Encoding(@TempDir Path tmp) throws Exception {
        Path left  = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");

        Files.writeString(left,  "café\nnaïve\n", StandardCharsets.UTF_8);
        Files.writeString(right, "café\nchange\n", StandardCharsets.UTF_8);

        vm.compare(left, right, ComparisonOptions.defaults()).get();

        vm.copyAll(MergeDirection.LEFT_TO_RIGHT);
        vm.saveRight();

        // Re-read via service to verify encoding detection
        DecodedText decoded = fileIOService.read(right, null);
        assertThat(decoded.encoding()).isEqualTo(StandardCharsets.UTF_8);
        // trailing newline in source file → empty string at end of decoded lines
        assertThat(decoded.lines()).containsExactly("café", "naïve", "");
    }

    // ── REQ-7.x: CRLF line endings preserved after merge + save ──────────────

    @Test
    void compareAndSaveCrlfLineEndingsPreserved(@TempDir Path tmp) throws Exception {
        Path left  = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");

        byte[] leftBytes  = "line1\r\nline2\r\nline3\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = "line1\r\nCHANGED\r\nline3\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(left,  leftBytes);
        Files.write(right, rightBytes);

        vm.compare(left, right, ComparisonOptions.defaults()).get();

        vm.copyAll(MergeDirection.LEFT_TO_RIGHT);
        vm.saveRight();

        // Raw bytes of saved file must contain \r\n
        byte[] savedBytes = Files.readAllBytes(right);
        String savedContent = new String(savedBytes, StandardCharsets.UTF_8);
        assertThat(savedContent).contains("\r\n");
        // And line2 must be "line2", not "CHANGED"
        assertThat(savedContent).contains("line2\r\n");
        assertThat(savedContent).doesNotContain("CHANGED");
    }

    // ── REQ-8.x: single-block copy preserves the rest of the file ────────────

    @Test
    void copyBlockMergesOnlyTargetedDiff(@TempDir Path tmp) throws Exception {
        Path left  = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");

        // Two independent differences: line 2 and line 4
        Files.writeString(left,  "A\nB\nC\nD\n", StandardCharsets.UTF_8);
        Files.writeString(right, "A\nX\nC\nY\n", StandardCharsets.UTF_8);

        vm.compare(left, right, ComparisonOptions.defaults()).get();
        assertThat(vm.getDifferenceCount()).isEqualTo(2);

        // Copy only the first block (A-line diff) L→R
        List<DiffBlock> blocks = vm.getDiffModel().blocks();
        DiffBlock firstBlock = blocks.get(0);
        vm.copyBlock(firstBlock, MergeDirection.LEFT_TO_RIGHT);
        vm.saveRight();

        DecodedText decoded = fileIOService.read(right, null);
        // After copying first block only: line 2 becomes B (from left), line 4 remains Y
        // trailing newline in source file → empty string at end of decoded lines
        assertThat(decoded.lines()).containsExactly("A", "B", "C", "Y", "");
    }
}
