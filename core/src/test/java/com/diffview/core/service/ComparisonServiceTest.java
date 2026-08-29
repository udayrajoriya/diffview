package com.comparetool.core.service;

import com.comparetool.infra.concurrent.CancellationToken;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.infra.concurrent.ProgressReporter;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FileComparisonResult;
import com.comparetool.model.FileMatchMode;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderComparisonResult;
import com.comparetool.model.FolderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DefaultComparisonService} (task 8.1).
 *
 * <p>All tests inject a {@link DirectTaskExecutor} so that {@code future.get()}
 * returns synchronously with no thread scheduling involved.
 */
class ComparisonServiceTest {

    private DefaultComparisonService service;

    @BeforeEach
    void setUp() {
        // Wire production engines but use a synchronous executor for determinism
        service = new DefaultComparisonService(
                new com.comparetool.core.diff.LineDiffEngine(),
                new com.comparetool.core.folder.DefaultFolderDiffEngine(),
                new com.comparetool.infra.io.NioFileIOService(
                        new com.comparetool.infra.encoding.JUniversalChardetDetector()),
                new com.comparetool.infra.hash.Sha256HashService(),
                new DirectTaskExecutor());
    }

    // ── compareFiles ──────────────────────────────────────────────────────────

    @Nested
    class CompareFiles {

        @Test
        void identicalTextFilesProduceIdenticalModel(@TempDir Path tempDir)
                throws Exception {
            Path left  = writeText(tempDir, "left.txt",  "line1\nline2\n");
            Path right = writeText(tempDir, "right.txt", "line1\nline2\n");

            FileComparisonResult result =
                    service.compareFiles(left, right, ComparisonOptions.defaults(), null).get();

            assertThat(result.model().identical()).isTrue();
            assertThat(result.model().differenceCount()).isZero();
            assertThat(result.left()).isEqualTo(left);
            assertThat(result.right()).isEqualTo(right);
        }

        @Test
        void differentTextFilesProduceDifferences(@TempDir Path tempDir)
                throws Exception {
            Path left  = writeText(tempDir, "left.txt",  "hello\n");
            Path right = writeText(tempDir, "right.txt", "world\n");

            FileComparisonResult result =
                    service.compareFiles(left, right, ComparisonOptions.defaults(), null).get();

            assertThat(result.model().identical()).isFalse();
            assertThat(result.model().differenceCount()).isGreaterThan(0);
        }

        @Test
        void largeFileWarningCalledWhenThresholdExceeded(@TempDir Path tempDir)
                throws Exception {
            // Write files with 20 bytes each; threshold = 10
            Path left  = writeText(tempDir, "left.txt",  "01234567890123456789");
            Path right = writeText(tempDir, "right.txt", "01234567890123456789");

            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withLargeFileWarnBytes(10L);

            List<Path[]> warnings = new ArrayList<>();
            service.compareFiles(left, right, opts,
                    (l, r) -> warnings.add(new Path[]{l, r})).get();

            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0)[0]).isEqualTo(left);
            assertThat(warnings.get(0)[1]).isEqualTo(right);
        }

        @Test
        void largeFileWarningNotCalledWhenBelowThreshold(@TempDir Path tempDir)
                throws Exception {
            Path left  = writeText(tempDir, "left.txt",  "small");
            Path right = writeText(tempDir, "right.txt", "small");

            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withLargeFileWarnBytes(1000L);

            List<Path[]> warnings = new ArrayList<>();
            service.compareFiles(left, right, opts,
                    (l, r) -> warnings.add(new Path[]{l, r})).get();

            assertThat(warnings).isEmpty();
        }

        @Test
        void largeFileWarningDisabledWhenThresholdIsZero(@TempDir Path tempDir)
                throws Exception {
            Path left  = writeText(tempDir, "left.txt",  "01234567890123456789");
            Path right = writeText(tempDir, "right.txt", "01234567890123456789");

            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withLargeFileWarnBytes(0L); // 0 = disabled

            List<Path[]> warnings = new ArrayList<>();
            service.compareFiles(left, right, opts,
                    (l, r) -> warnings.add(new Path[]{l, r})).get();

            assertThat(warnings).isEmpty();
        }

        @Test
        void nullWarningConsumerDoesNotThrow(@TempDir Path tempDir) throws Exception {
            Path left  = writeText(tempDir, "left.txt",  "text");
            Path right = writeText(tempDir, "right.txt", "text");

            // Should not throw even if largeFileWarning is null
            assertThatCode(() ->
                    service.compareFiles(left, right, ComparisonOptions.defaults(), null).get()
            ).doesNotThrowAnyException();
        }

        @Test
        void binaryFilesUseFallbackEqualityPath(@TempDir Path tempDir) throws Exception {
            // Binary files contain a NUL byte (0x00)
            Path left  = writeBinary(tempDir, "left.bin",  new byte[]{0x42, 0x00, 0x42});
            Path right = writeBinary(tempDir, "right.bin", new byte[]{0x42, 0x00, 0x42});

            FileComparisonResult result =
                    service.compareFiles(left, right, ComparisonOptions.defaults(), null).get();

            // Binary fallback: empty rows, but identical = true (same content)
            assertThat(result.model().rows()).isEmpty();
            assertThat(result.model().identical()).isTrue();
        }

        @Test
        void binaryFilesWithDifferentContentAreNotIdentical(@TempDir Path tempDir) throws Exception {
            Path left  = writeBinary(tempDir, "left.bin",  new byte[]{0x00, 0x01});
            Path right = writeBinary(tempDir, "right.bin", new byte[]{0x00, 0x02});

            FileComparisonResult result =
                    service.compareFiles(left, right, ComparisonOptions.defaults(), null).get();

            assertThat(result.model().rows()).isEmpty();
            assertThat(result.model().identical()).isFalse();
        }

        @Test
        void contentIgnoreCaseAppliedDuringTextDiff(@TempDir Path tempDir) throws Exception {
            Path left  = writeText(tempDir, "left.txt",  "Hello\n");
            Path right = writeText(tempDir, "right.txt", "HELLO\n");

            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreCase(true);

            FileComparisonResult result =
                    service.compareFiles(left, right, opts, null).get();

            assertThat(result.model().identical()).isTrue();
        }
    }

    // ── compareFolders ────────────────────────────────────────────────────────

    @Nested
    class CompareFolders {

        @Test
        void identicalFoldersBothSidesSameFiles(@TempDir Path tempDir) throws Exception {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            writeText(left.resolve("a.txt"),  "content");
            writeText(right.resolve("a.txt"), "content");

            FolderComparisonResult result = service.compareFolders(
                    left, right,
                    FolderComparisonOptions.defaults(),
                    ProgressReporter.noOp(),
                    CancellationToken.neverCancelled()).get();

            assertThat(result.identicalCount()).isEqualTo(1);
            assertThat(result.differentCount()).isZero();
            assertThat(result.leftOnlyCount()).isZero();
            assertThat(result.rightOnlyCount()).isZero();
        }

        @Test
        void leftOnlyFilesDetected(@TempDir Path tempDir) throws Exception {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            writeText(left.resolve("extra.txt"), "only on left");

            FolderComparisonResult result = service.compareFolders(
                    left, right,
                    FolderComparisonOptions.defaults(),
                    ProgressReporter.noOp(),
                    CancellationToken.neverCancelled()).get();

            assertThat(result.leftOnlyCount()).isEqualTo(1);
        }

        @Test
        void excludeMaskAppliedThroughFacade(@TempDir Path tempDir) throws Exception {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            writeText(left.resolve("keep.txt"),   "x");
            writeText(right.resolve("keep.txt"),  "x");
            writeText(left.resolve("temp.tmp"),   "tmp");
            writeText(right.resolve("temp.tmp"),  "tmp");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withExcludeMasks(List.of("*.tmp"));

            FolderComparisonResult result = service.compareFolders(
                    left, right, opts,
                    ProgressReporter.noOp(),
                    CancellationToken.neverCancelled()).get();

            assertThat(result.ignoredCount()).isEqualTo(1);
            assertThat(result.identicalCount()).isEqualTo(1);
        }
    }

    // ── evaluatePair ──────────────────────────────────────────────────────────

    @Nested
    class EvaluatePair {

        @Test
        void bothNullThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> service.evaluatePair(null, null,
                            FolderComparisonOptions.defaults()));
        }

        @Test
        void leftNullReturnsRightOnly(@TempDir Path tempDir) {
            FileMeta right = fileMetaFor(tempDir.resolve("r.txt"));
            FolderItemStatus status = service.evaluatePair(null, right,
                    FolderComparisonOptions.defaults());
            assertThat(status).isEqualTo(FolderItemStatus.RIGHT_ONLY);
        }

        @Test
        void rightNullReturnsLeftOnly(@TempDir Path tempDir) {
            FileMeta left = fileMetaFor(tempDir.resolve("l.txt"));
            FolderItemStatus status = service.evaluatePair(left, null,
                    FolderComparisonOptions.defaults());
            assertThat(status).isEqualTo(FolderItemStatus.LEFT_ONLY);
        }

        @Test
        void sameFileSizeReturnedIdenticalUnderSizeOnly(@TempDir Path tempDir)
                throws IOException {
            Path lPath = writeText(tempDir, "l.txt", "same");
            Path rPath = writeText(tempDir, "r.txt", "same");

            FileMeta left  = FileMeta.file(lPath, Path.of("l.txt"), Files.size(lPath), Instant.now());
            FileMeta right = FileMeta.file(rPath, Path.of("r.txt"), Files.size(rPath), Instant.now());

            FolderItemStatus status = service.evaluatePair(left, right,
                    FolderComparisonOptions.defaults().withMatchMode(FileMatchMode.SIZE_ONLY));

            assertThat(status).isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void differentContentReturnsDifferentUnderContentMode(@TempDir Path tempDir)
                throws IOException {
            Path lPath = writeText(tempDir, "l.txt", "hello");
            Path rPath = writeText(tempDir, "r.txt", "world");

            FileMeta left  = FileMeta.file(lPath, Path.of("l.txt"), Files.size(lPath), Instant.now());
            FileMeta right = FileMeta.file(rPath, Path.of("r.txt"), Files.size(rPath), Instant.now());

            FolderItemStatus status = service.evaluatePair(left, right,
                    FolderComparisonOptions.defaults().withMatchMode(FileMatchMode.CONTENT));

            assertThat(status).isEqualTo(FolderItemStatus.DIFFERENT);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static Path writeText(Path tempDir, String name, String content) throws IOException {
        return Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private static Path writeText(Path file, String content) throws IOException {
        return Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static Path writeBinary(Path dir, String name, byte[] bytes) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, bytes);
        return p;
    }

    private static FileMeta fileMetaFor(Path path) {
        return FileMeta.file(path.toAbsolutePath(), path.getFileName(), 0L, Instant.now());
    }
}
