package com.comparetool.app;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.core.folder.DefaultFolderDiffEngine;
import com.comparetool.core.service.DefaultComparisonService;
import com.comparetool.infra.concurrent.CancellationToken;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.infra.concurrent.ProgressReporter;
import com.comparetool.infra.encoding.JUniversalChardetDetector;
import com.comparetool.infra.hash.Sha256HashService;
import com.comparetool.infra.io.NioFileIOService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FileComparisonResult;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderComparisonResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Headless smoke test invoked by the packaged binary when {@code --smoke-test}
 * is passed as a command-line argument (REQ-17.2).
 *
 * <p>Validates that the self-contained runtime can perform basic file and
 * folder comparisons without starting the JavaFX GUI.  Exits with code 0 on
 * success or 1 on failure so CI can assert the packaged image is functional.
 *
 * <p>No JavaFX classes are referenced here — the test returns before
 * {@link javafx.application.Application#launch} is called in
 * {@link MainApp#main}.
 */
final class PackagingSmokeTest {

    static void run() {
        int exitCode = 0;
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("comparetool-smoke-");
            runFileComparisonSmoke(tmp);
            runFolderComparisonSmoke(tmp);
            System.out.println("SMOKE TEST PASSED");
        } catch (Exception e) {
            System.err.println("SMOKE TEST FAILED: " + e.getMessage());
            e.printStackTrace(System.err);
            exitCode = 1;
        } finally {
            if (tmp != null) deleteQuietly(tmp);
        }
        System.exit(exitCode);
    }

    // ── File comparison smoke ─────────────────────────────────────────────────

    private static void runFileComparisonSmoke(Path tmp) throws Exception {
        Path left  = tmp.resolve("left.txt");
        Path right = tmp.resolve("right.txt");
        Files.writeString(left,  "alpha\nbeta\n",  StandardCharsets.UTF_8);
        Files.writeString(right, "alpha\nDELTA\n", StandardCharsets.UTF_8);

        DefaultComparisonService service = buildService();
        FileComparisonResult result =
                service.compareFiles(left, right, ComparisonOptions.defaults(), null).get();

        if (result.model().blocks().isEmpty()) {
            throw new AssertionError(
                    "File comparison: expected at least one diff block but got none");
        }
        System.out.println("  [OK] file comparison: "
                + result.model().blocks().size() + " diff block(s)");
    }

    // ── Folder comparison smoke ───────────────────────────────────────────────

    private static void runFolderComparisonSmoke(Path tmp) throws Exception {
        Path leftDir  = Files.createDirectory(tmp.resolve("left-dir"));
        Path rightDir = Files.createDirectory(tmp.resolve("right-dir"));

        Files.writeString(leftDir.resolve("same.txt"),  "identical\n", StandardCharsets.UTF_8);
        Files.writeString(rightDir.resolve("same.txt"), "identical\n", StandardCharsets.UTF_8);
        Files.writeString(leftDir.resolve("diff.txt"),  "left-side\n",  StandardCharsets.UTF_8);
        Files.writeString(rightDir.resolve("diff.txt"), "right-side\n", StandardCharsets.UTF_8);

        FolderComparisonResult result = new DefaultFolderDiffEngine()
                .compare(leftDir, rightDir, FolderComparisonOptions.defaults(),
                         ProgressReporter.noOp(), CancellationToken.neverCancelled());

        if (result.identicalCount() != 1) {
            throw new AssertionError("Folder comparison: expected identicalCount=1, got "
                    + result.identicalCount());
        }
        if (result.differentCount() != 1) {
            throw new AssertionError("Folder comparison: expected differentCount=1, got "
                    + result.differentCount());
        }
        System.out.println("  [OK] folder comparison: "
                + result.identicalCount() + " identical, "
                + result.differentCount() + " different");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static DefaultComparisonService buildService() {
        return new DefaultComparisonService(
                new LineDiffEngine(),
                new DefaultFolderDiffEngine(),
                new NioFileIOService(new JUniversalChardetDetector()),
                new Sha256HashService(),
                new DirectTaskExecutor());
    }

    private static void deleteQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException ignored) { /* best-effort cleanup */ }
    }

    private PackagingSmokeTest() {}
}
