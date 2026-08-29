package com.diffview.core.folder;

import com.diffview.infra.hash.Sha256HashService;
import com.diffview.model.FileMatchMode;
import com.diffview.model.FileMeta;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderItemStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class FileMatchCriteriaTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final Instant BASE_TIME = Instant.parse("2024-01-01T00:00:00Z");

    /** Creates a FileMeta with a dummy absolute path (no disk access needed). */
    private static FileMeta meta(long size, Instant lastModified) {
        return new FileMeta(
                Path.of("dummy-" + size + "-" + lastModified.toEpochMilli()),
                Path.of("file.txt"),
                false,
                size,
                lastModified);
    }

    private static FileMeta metaAt(long size, long secondsOffset) {
        return meta(size, BASE_TIME.plusSeconds(secondsOffset));
    }

    private static FolderComparisonOptions optionsWithTolerance(Duration tolerance) {
        return FolderComparisonOptions.defaults().withTimestampTolerance(tolerance);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    @Nested
    class ForMode {
        @Test
        void nullModeThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> FileMatchCriteria.forMode(null, null));
        }

        @Test
        void sizeOnlyReturnsSizeOnlyInstance() {
            var criteria = FileMatchCriteria.forMode(FileMatchMode.SIZE_ONLY, null);
            assertThat(criteria).isInstanceOf(SizeOnlyMatchCriteria.class);
        }

        @Test
        void sizeAndTimestampReturnsSizeAndTimestampInstance() {
            var criteria = FileMatchCriteria.forMode(FileMatchMode.SIZE_AND_TIMESTAMP, null);
            assertThat(criteria).isInstanceOf(SizeAndTimestampMatchCriteria.class);
        }

        @Test
        void contentNullHashServiceThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> FileMatchCriteria.forMode(FileMatchMode.CONTENT, null));
        }
    }

    // ── SIZE_ONLY ─────────────────────────────────────────────────────────────

    @Nested
    class SizeOnly {

        private final FileMatchCriteria criteria =
                FileMatchCriteria.forMode(FileMatchMode.SIZE_ONLY, null);
        private final FolderComparisonOptions options = FolderComparisonOptions.defaults();

        @Test
        void sameSizeIsIdentical() {
            assertThat(criteria.compare(metaAt(1024, 0), metaAt(1024, 100), options))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void differentSizeIsDifferent() {
            assertThat(criteria.compare(metaAt(1024, 0), metaAt(2048, 0), options))
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void zeroSizeFilesAreIdentical() {
            assertThat(criteria.compare(metaAt(0, 0), metaAt(0, 9999), options))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void sameTimestampDifferentSizeIsDifferent() {
            assertThat(criteria.compare(metaAt(100, 0), metaAt(200, 0), options))
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }
    }

    // ── SIZE_AND_TIMESTAMP ────────────────────────────────────────────────────

    @Nested
    class SizeAndTimestamp {

        private final FileMatchCriteria criteria =
                FileMatchCriteria.forMode(FileMatchMode.SIZE_AND_TIMESTAMP, null);

        @Test
        void sameSizeSameTimestampIsIdentical() {
            var opts = optionsWithTolerance(Duration.ZERO);
            assertThat(criteria.compare(metaAt(512, 0), metaAt(512, 0), opts))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void differentSizeIsDifferentRegardlessOfTimestamp() {
            var opts = optionsWithTolerance(Duration.ofSeconds(60));
            assertThat(criteria.compare(metaAt(100, 0), metaAt(200, 0), opts))
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void sameSizeTimestampWithinToleranceIsIdentical() {
            var opts = optionsWithTolerance(Duration.ofSeconds(2));
            // delta = 1 second — within tolerance
            assertThat(criteria.compare(metaAt(512, 0), metaAt(512, 1), opts))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void sameSizeTimestampExactlyAtToleranceBoundaryIsIdentical() {
            var opts = optionsWithTolerance(Duration.ofSeconds(2));
            // delta == tolerance (inclusive boundary)
            assertThat(criteria.compare(metaAt(512, 0), metaAt(512, 2), opts))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void sameSizeTimestampJustOutsideToleranceIsDifferent() {
            var opts = optionsWithTolerance(Duration.ofSeconds(2));
            // delta = 3 seconds — outside tolerance
            assertThat(criteria.compare(metaAt(512, 0), metaAt(512, 3), opts))
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void zeroToleranceRequiresExactTimestampMatch() {
            var opts = optionsWithTolerance(Duration.ZERO);
            assertThat(criteria.compare(metaAt(512, 0), metaAt(512, 1), opts))
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void toleranceChecksAbsoluteDeltaSymmetrically() {
            var opts = optionsWithTolerance(Duration.ofSeconds(2));
            // left is newer than right — delta still within tolerance
            assertThat(criteria.compare(metaAt(512, 2), metaAt(512, 0), opts))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }
    }

    // ── CONTENT ───────────────────────────────────────────────────────────────

    @Nested
    class Content {

        @TempDir
        Path tempDir;

        private FileMatchCriteria criteria() {
            return FileMatchCriteria.forMode(FileMatchMode.CONTENT, new Sha256HashService());
        }

        private final FolderComparisonOptions options = FolderComparisonOptions.defaults();

        /** Builds a real FileMeta pointing at an actual temp file. */
        private FileMeta realMeta(Path file, long offsetSeconds) {
            return new FileMeta(
                    file.toAbsolutePath(),
                    Path.of(file.getFileName().toString()),
                    false,
                    getSize(file),
                    BASE_TIME.plusSeconds(offsetSeconds));
        }

        private static long getSize(Path p) {
            try { return Files.size(p); } catch (IOException e) { throw new RuntimeException(e); }
        }

        @Test
        void sameContentIsIdentical() throws IOException {
            Path a = tempDir.resolve("a.txt");
            Path b = tempDir.resolve("b.txt");
            Files.writeString(a, "hello world");
            Files.writeString(b, "hello world");

            assertThat(criteria().compare(realMeta(a, 0), realMeta(b, 99), options))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void differentContentIsDifferent() throws IOException {
            Path a = tempDir.resolve("a.txt");
            Path b = tempDir.resolve("b.txt");
            Files.writeString(a, "hello world");
            Files.writeString(b, "hello java");

            assertThat(criteria().compare(realMeta(a, 0), realMeta(b, 0), options))
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void sameContentDifferentTimestampIsIdentical() throws IOException {
            // Content wins over timestamp — content-identical files are IDENTICAL even if
            // timestamps differ by a large amount
            Path a = tempDir.resolve("a.txt");
            Path b = tempDir.resolve("b.txt");
            Files.writeString(a, "same bytes");
            Files.writeString(b, "same bytes");

            // 1-year timestamp gap — irrelevant for CONTENT mode
            assertThat(criteria().compare(realMeta(a, 0), realMeta(b, 365L * 24 * 3600), options))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void emptyFilesAreIdentical() throws IOException {
            Path a = tempDir.resolve("a.txt");
            Path b = tempDir.resolve("b.txt");
            Files.writeString(a, "");
            Files.writeString(b, "");

            assertThat(criteria().compare(realMeta(a, 0), realMeta(b, 0), options))
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }
    }
}
