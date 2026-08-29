package com.diffview.core.folder;

import com.diffview.infra.concurrent.CancellationToken;
import com.diffview.infra.concurrent.ProgressReporter;
import com.diffview.model.ErrorCode;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderComparisonResult;
import com.diffview.model.ItemError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies REQ-016.1: if a file becomes inaccessible during a folder comparison scan,
 * the engine records an {@link ItemError} for that path and continues processing the
 * remaining items rather than aborting the whole operation.
 *
 * <p>Because making a file unreadable at the OS level is not reliable on Windows without
 * elevated permissions, these tests use a protected-hook subclass to inject I/O failures
 * for specific paths.
 */
class FailSoftFolderEngineTest {

    private static final FolderComparisonOptions OPTIONS = FolderComparisonOptions.defaults();

    // ── Test helper subclass ──────────────────────────────────────────────────

    /**
     * Overrides {@link DefaultFolderDiffEngine#readFileAttributes(Path)} to throw an
     * {@link IOException} when asked about a pre-configured absolute path, and delegates
     * to the real implementation for all other paths.
     */
    private static class ErrorInjectingEngine extends DefaultFolderDiffEngine {

        private final Path errorTarget;

        ErrorInjectingEngine(Path errorTarget) {
            this.errorTarget = errorTarget.toAbsolutePath().normalize();
        }

        @Override
        protected BasicFileAttributes readFileAttributes(Path p) throws IOException {
            if (p.toAbsolutePath().normalize().equals(errorTarget)) {
                throw new IOException("Simulated access denied for: " + p);
            }
            return super.readFileAttributes(p);
        }
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void compareSkipsInaccessibleFileAndContinuesWithOthers(@TempDir Path tmpDir)
            throws IOException {
        Path left  = Files.createDirectory(tmpDir.resolve("left"));
        Path right = Files.createDirectory(tmpDir.resolve("right"));

        // Two files on both sides — one of them will be made inaccessible on the left
        Files.writeString(left.resolve("accessible.txt"),   "hello");
        Files.writeString(left.resolve("inaccessible.txt"), "secret");
        Files.writeString(right.resolve("accessible.txt"),  "hello");
        Files.writeString(right.resolve("inaccessible.txt"), "secret");

        Path errorTarget = left.resolve("inaccessible.txt");
        DefaultFolderDiffEngine engine = new ErrorInjectingEngine(errorTarget);

        FolderComparisonResult result = engine.compare(
                left, right, OPTIONS, ProgressReporter.noOp(), CancellationToken.neverCancelled());

        // The accessible file must appear in the diff tree
        assertThat(result.root().children())
                .as("accessible.txt should be in the result tree")
                .anyMatch(n -> n.relativePath().getFileName().toString().equals("accessible.txt"));

        // Exactly one error recorded
        assertThat(result.errors()).as("exactly one ItemError expected").hasSize(1);

        ItemError err = result.errors().get(0);
        assertThat(err.code()).isEqualTo(ErrorCode.IO_ERROR);
        assertThat(err.message()).contains("inaccessible.txt");
    }

    @Test
    void compareWithNoErrorsProducesEmptyErrorsList(@TempDir Path tmpDir)
            throws IOException {
        Path left  = Files.createDirectory(tmpDir.resolve("left"));
        Path right = Files.createDirectory(tmpDir.resolve("right"));
        Files.writeString(left.resolve("a.txt"),  "foo");
        Files.writeString(right.resolve("a.txt"), "foo");

        FolderComparisonResult result = new DefaultFolderDiffEngine().compare(
                left, right, OPTIONS, ProgressReporter.noOp(), CancellationToken.neverCancelled());

        assertThat(result.errors()).as("no errors when all files are accessible").isEmpty();
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void itemErrorHasCorrectRelativePath(@TempDir Path tmpDir)
            throws IOException {
        Path left  = Files.createDirectory(tmpDir.resolve("left"));
        Path right = Files.createDirectory(tmpDir.resolve("right"));

        Files.writeString(left.resolve("bad.txt"),  "data");
        Files.writeString(right.resolve("bad.txt"), "data");

        Path errorTarget = left.resolve("bad.txt");
        DefaultFolderDiffEngine engine = new ErrorInjectingEngine(errorTarget);

        FolderComparisonResult result = engine.compare(
                left, right, OPTIONS, ProgressReporter.noOp(), CancellationToken.neverCancelled());

        assertThat(result.errors()).hasSize(1);
        // The relative path in the error should be just "bad.txt", not the absolute path
        assertThat(result.errors().get(0).relativePath().toString())
                .endsWith("bad.txt");
        assertThat(result.errors().get(0).relativePath().isAbsolute())
                .as("error relative path should not be absolute")
                .isFalse();
    }

    @Test
    void errorsListIsImmutable(@TempDir Path tmpDir) throws IOException {
        Path left  = Files.createDirectory(tmpDir.resolve("left"));
        Path right = Files.createDirectory(tmpDir.resolve("right"));
        Files.writeString(left.resolve("x.txt"),  "a");
        Files.writeString(right.resolve("x.txt"), "a");

        FolderComparisonResult result = new DefaultFolderDiffEngine().compare(
                left, right, OPTIONS, ProgressReporter.noOp(), CancellationToken.neverCancelled());

        List<ItemError> errors = result.errors();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> errors.add(new ItemError(
                        Path.of("fake.txt"), ErrorCode.UNKNOWN, "manual add")));
    }
}
