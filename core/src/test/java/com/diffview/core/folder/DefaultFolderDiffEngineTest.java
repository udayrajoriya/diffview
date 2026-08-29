package com.diffview.core.folder;

import com.diffview.infra.concurrent.CancellationToken;
import com.diffview.infra.concurrent.ProgressReporter;
import com.diffview.model.DiffTreeNode;
import com.diffview.model.FileMatchMode;
import com.diffview.model.FolderComparisonOptions;
import com.diffview.model.FolderComparisonResult;
import com.diffview.model.FolderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link DefaultFolderDiffEngine} (task 6.1).
 *
 * <p>Each test uses a real file system via {@code @TempDir} to exercise the full
 * walk + pairing logic.  The symlink-containment test is conditional: if symbolic
 * link creation fails (e.g., Windows without Developer Mode), the test is skipped
 * rather than failed.
 */
class DefaultFolderDiffEngineTest {

    private DefaultFolderDiffEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultFolderDiffEngine();
    }

    private static final FolderComparisonOptions OPTIONS = FolderComparisonOptions.defaults();

    private DiffTreeNode compare(Path left, Path right) {
        return engine.compare(left, right, OPTIONS,
                ProgressReporter.noOp(), CancellationToken.neverCancelled()).root();
    }

    private FolderComparisonResult compareResult(Path left, Path right) {
        return engine.compare(left, right, OPTIONS,
                ProgressReporter.noOp(), CancellationToken.neverCancelled());
    }

    private FolderComparisonResult compareResult(Path left, Path right, FolderComparisonOptions opts) {
        return engine.compare(left, right, opts,
                ProgressReporter.noOp(), CancellationToken.neverCancelled());
    }

    /** Finds the first child whose relative path ends with {@code name}. */
    private static Optional<DiffTreeNode> childNamed(DiffTreeNode parent, String name) {
        return parent.children().stream()
                .filter(n -> n.relativePath().getFileName().toString().equals(name))
                .findFirst();
    }

    // ── root node ─────────────────────────────────────────────────────────────

    @Test
    void rootNodeIsDirectoryAndPaired(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));

        DiffTreeNode root = compare(left, right);

        assertThat(root.directory()).isTrue();
        assertThat(root.left()).isNotNull();
        assertThat(root.right()).isNotNull();
        assertThat(root.relativePath().toString()).isEmpty();
    }

    @Test
    void emptyDirectoriesProduceIdenticalRoot(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));

        DiffTreeNode root = compare(left, right);

        assertThat(root.children()).isEmpty();
        assertThat(root.status()).isEqualTo(FolderItemStatus.IDENTICAL);
    }

    @Test
    void invalidLeftPathThrowsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path nonExistent = tempDir.resolve("missing");
        Path right = Files.createDirectory(tempDir.resolve("right"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> compare(nonExistent, right));
    }

    @Test
    void invalidRightPathThrowsIllegalArgument(@TempDir Path tempDir) throws IOException {
        Path left = Files.createDirectory(tempDir.resolve("left"));
        Path nonExistent = tempDir.resolve("missing");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> compare(left, nonExistent));
    }

    // ── LEFT_ONLY / RIGHT_ONLY pairing ────────────────────────────────────────

    @Nested
    class PairingStatus {

        @Test
        void leftOnlyFileIsMarkedLeftOnly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("only-left.txt"), "content");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode node = childNamed(root, "only-left.txt").orElseThrow();
            assertThat(node.status()).isEqualTo(FolderItemStatus.LEFT_ONLY);
            assertThat(node.left()).isNotNull();
            assertThat(node.right()).isNull();
        }

        @Test
        void rightOnlyFileIsMarkedRightOnly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(right.resolve("only-right.txt"), "content");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode node = childNamed(root, "only-right.txt").orElseThrow();
            assertThat(node.status()).isEqualTo(FolderItemStatus.RIGHT_ONLY);
            assertThat(node.left()).isNull();
            assertThat(node.right()).isNotNull();
        }

        @Test
        void matchedFileIsMarkedIdentical(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("shared.txt"),  "same");
            Files.writeString(right.resolve("shared.txt"), "same");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode node = childNamed(root, "shared.txt").orElseThrow();
            assertThat(node.status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(node.left()).isNotNull();
            assertThat(node.right()).isNotNull();
        }

        @Test
        void mixedFiles_correctCounts(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("shared.txt"),    "content");
            Files.writeString(right.resolve("shared.txt"),   "content");
            Files.writeString(left.resolve("left-only.txt"), "left");
            Files.writeString(right.resolve("right-only.txt"), "right");

            DiffTreeNode root = compare(left, right);

            assertThat(root.children()).hasSize(3);
            assertThat(childNamed(root, "shared.txt")   .orElseThrow().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(childNamed(root, "left-only.txt") .orElseThrow().status()).isEqualTo(FolderItemStatus.LEFT_ONLY);
            assertThat(childNamed(root, "right-only.txt").orElseThrow().status()).isEqualTo(FolderItemStatus.RIGHT_ONLY);
        }

        @Test
        void rootWithLeftOnlyFileIsDifferent(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("extra.txt"), "extra");

            DiffTreeNode root = compare(left, right);

            assertThat(root.status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }
    }

    // ── nested directories ────────────────────────────────────────────────────

    @Nested
    class NestedDirectories {

        @Test
        void matchedSubdirAppearsAsPairedDirectoryNode(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.createDirectory(left.resolve("sub"));
            Files.createDirectory(right.resolve("sub"));

            DiffTreeNode root = compare(left, right);

            DiffTreeNode sub = childNamed(root, "sub").orElseThrow();
            assertThat(sub.directory()).isTrue();
            assertThat(sub.status()).isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void leftOnlySubdirIsMarkedLeftOnly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Path subLeft = Files.createDirectory(left.resolve("extra-dir"));
            Files.writeString(subLeft.resolve("file.txt"), "x");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode sub = childNamed(root, "extra-dir").orElseThrow();
            assertThat(sub.status()).isEqualTo(FolderItemStatus.LEFT_ONLY);
            assertThat(sub.directory()).isTrue();
        }

        @Test
        void fileInsideMatchedSubdirIsNested(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Path subLeft  = Files.createDirectory(left.resolve("sub"));
            Path subRight = Files.createDirectory(right.resolve("sub"));
            Files.writeString(subLeft.resolve("file.txt"),         "v1");
            Files.writeString(subRight.resolve("file.txt"),        "v1");
            Files.writeString(subLeft.resolve("left-only.txt"),    "left");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode sub = childNamed(root, "sub").orElseThrow();
            assertThat(sub.children()).hasSize(2);

            DiffTreeNode fileTxt = childNamed(sub, "file.txt").orElseThrow();
            assertThat(fileTxt.status()).isEqualTo(FolderItemStatus.IDENTICAL);

            DiffTreeNode leftOnly = childNamed(sub, "left-only.txt").orElseThrow();
            assertThat(leftOnly.status()).isEqualTo(FolderItemStatus.LEFT_ONLY);
        }

        @Test
        void subdirWithDifferencesIsMarkedDifferent(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Path subLeft  = Files.createDirectory(left.resolve("sub"));
            Files.createDirectory(right.resolve("sub"));
            Files.writeString(subLeft.resolve("extra.txt"), "extra");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode sub = childNamed(root, "sub").orElseThrow();
            assertThat(sub.status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void deeplyNestedStructureBuiltCorrectly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            // left/a/b/c.txt  vs  right/a/b/c.txt + right/a/b/extra.txt
            Files.createDirectories(left.resolve("a").resolve("b"));
            Files.createDirectories(right.resolve("a").resolve("b"));
            Files.writeString(left.resolve("a").resolve("b").resolve("c.txt"),    "c");
            Files.writeString(right.resolve("a").resolve("b").resolve("c.txt"),   "c");
            Files.writeString(right.resolve("a").resolve("b").resolve("extra.txt"), "extra");

            DiffTreeNode root = compare(left, right);

            DiffTreeNode a = childNamed(root, "a").orElseThrow();
            assertThat(a.status()).isEqualTo(FolderItemStatus.DIFFERENT);

            DiffTreeNode b = childNamed(a, "b").orElseThrow();
            assertThat(b.status()).isEqualTo(FolderItemStatus.DIFFERENT);

            assertThat(childNamed(b, "c.txt")    .orElseThrow().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(childNamed(b, "extra.txt").orElseThrow().status()).isEqualTo(FolderItemStatus.RIGHT_ONLY);
        }
    }

    // ── manual ignores ────────────────────────────────────────────────────────

    @Test
    void manuallyIgnoredEntryIsMarkedIgnored(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));
        Files.writeString(left.resolve("normal.txt"),  "n");
        Files.writeString(right.resolve("normal.txt"), "n");
        Files.writeString(left.resolve("ignored.txt"), "i");

        FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                .withManualIgnores(Set.of(Path.of("ignored.txt")));

        DiffTreeNode root = engine.compare(left, right, opts,
                ProgressReporter.noOp(), CancellationToken.neverCancelled()).root();

        DiffTreeNode ignored = childNamed(root, "ignored.txt").orElseThrow();
        assertThat(ignored.status()).isEqualTo(FolderItemStatus.IGNORED);
    }

    @Test
    void manuallyIgnoredEntryIsNotCountedAsDifference(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));
        Files.writeString(left.resolve("ignored.txt"), "left-only-but-ignored");

        FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                .withManualIgnores(Set.of(Path.of("ignored.txt")));

        DiffTreeNode root = engine.compare(left, right, opts,
                ProgressReporter.noOp(), CancellationToken.neverCancelled()).root();

        // IGNORED is not "different" — root should remain IDENTICAL
        assertThat(root.status()).isEqualTo(FolderItemStatus.IDENTICAL);
    }

    // ── symlink containment ───────────────────────────────────────────────────

    @Test
    void symlinkToDirectoryOutsideRootIsNotTraversed(@TempDir Path tempDir) throws IOException {
        Path leftRoot = Files.createDirectory(tempDir.resolve("left"));
        Path rightRoot = Files.createDirectory(tempDir.resolve("right"));
        Path outside  = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret-content");

        // Try to create a symlink; skip the test if not supported (e.g., Windows without
        // Developer Mode / admin rights).
        Path symlink = leftRoot.resolve("link_to_outside");
        try {
            Files.createSymbolicLink(symlink, outside);
        } catch (Exception e) {
            assumeTrue(false, "Symlink creation not supported on this platform: " + e.getMessage());
        }

        DiffTreeNode root = compare(leftRoot, rightRoot);

        // The symlink should appear as a leaf entry on the left side
        DiffTreeNode linkNode = childNamed(root, "link_to_outside").orElseThrow();
        assertThat(linkNode.status()).isEqualTo(FolderItemStatus.LEFT_ONLY);

        // The symlink must NOT be treated as a directory node (no traversal)
        assertThat(linkNode.directory()).isFalse();

        // "secret.txt" inside the external directory must NOT appear anywhere in the tree
        boolean secretFound = root.children().stream()
                .flatMap(c -> c.children().stream())
                .anyMatch(n -> n.relativePath().getFileName().toString().equals("secret.txt"));
        assertThat(secretFound).isFalse();
    }

    @Test
    void symlinkToFileAppearsAsLeafEntry(@TempDir Path tempDir) throws IOException {
        Path leftRoot  = Files.createDirectory(tempDir.resolve("left"));
        Path rightRoot = Files.createDirectory(tempDir.resolve("right"));
        Path target    = Files.writeString(tempDir.resolve("real.txt"), "hello");

        Path symlink = leftRoot.resolve("link.txt");
        try {
            Files.createSymbolicLink(symlink, target);
        } catch (Exception e) {
            assumeTrue(false, "Symlink creation not supported: " + e.getMessage());
        }

        DiffTreeNode root = compare(leftRoot, rightRoot);

        DiffTreeNode linkNode = childNamed(root, "link.txt").orElseThrow();
        assertThat(linkNode.status()).isEqualTo(FolderItemStatus.LEFT_ONLY);
        assertThat(linkNode.directory()).isFalse();
        assertThat(linkNode.children()).isEmpty();
    }

    // ── FileMeta correctness ──────────────────────────────────────────────────

    @Test
    void fileMetaRelativePathMatchesPosition(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));
        Path subLeft = Files.createDirectory(left.resolve("subdir"));
        Files.writeString(subLeft.resolve("nested.txt"), "n");

        DiffTreeNode root = compare(left, right);

        DiffTreeNode sub    = childNamed(root, "subdir").orElseThrow();
        DiffTreeNode nested = childNamed(sub, "nested.txt").orElseThrow();

        assertThat(nested.relativePath()).isEqualTo(Path.of("subdir", "nested.txt"));
        assertThat(nested.left().relativePath()).isEqualTo(Path.of("subdir", "nested.txt"));
    }

    @Test
    void fileMetaSizeIsNonZeroForNonEmptyFile(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));
        Files.writeString(left.resolve("data.txt"), "hello world");

        DiffTreeNode root = compare(left, right);

        DiffTreeNode node = childNamed(root, "data.txt").orElseThrow();
        assertThat(node.left().size()).isGreaterThan(0);
    }

    // ── cancellation ─────────────────────────────────────────────────────────

    @Test
    void cancelledTokenThrowsBeforeComparison(@TempDir Path tempDir) throws IOException {
        Path left  = Files.createDirectory(tempDir.resolve("left"));
        Path right = Files.createDirectory(tempDir.resolve("right"));

        // Use the real cancellable token
        CancellationToken token = new CancellationToken();
        token.cancel();

        assertThatThrownBy(() ->
                engine.compare(left, right, OPTIONS, ProgressReporter.noOp(), token))
                .isInstanceOf(Exception.class); // CancellationException or similar
    }

    // ── Task 6.3: FileMatchCriteria integration ───────────────────────────────

    @Nested
    class FileMatchCriteriaIntegration {

        @Test
        void pairedFilesWithDifferentSizeAreDifferent_sizeOnlyMode(@TempDir Path tempDir)
                throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("file.txt"),  "short");
            Files.writeString(right.resolve("file.txt"), "much longer content here");

            FolderComparisonOptions opts = OPTIONS.withMatchMode(FileMatchMode.SIZE_ONLY);
            DiffTreeNode root = compareResult(left, right, opts).root();

            assertThat(childNamed(root, "file.txt").orElseThrow().status())
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void pairedFilesWithSameSizeAndRecentTimestampAreIdentical_sizeAndTimestampMode(
                @TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            // Same content => same size; created milliseconds apart => within 2-second tolerance
            Files.writeString(left.resolve("file.txt"),  "same content");
            Files.writeString(right.resolve("file.txt"), "same content");

            DiffTreeNode root = compare(left, right);

            assertThat(childNamed(root, "file.txt").orElseThrow().status())
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void pairedFilesWithSameContentDifferentTimestampAreIdentical_contentMode(
                @TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Path lf = left.resolve("file.txt");
            Path rf = right.resolve("file.txt");
            Files.writeString(lf, "identical bytes");
            Files.writeString(rf, "identical bytes");
            // Force timestamps far apart to ensure SIZE_AND_TIMESTAMP would say DIFFERENT
            lf.toFile().setLastModified(1_000_000L);    // 1970
            rf.toFile().setLastModified(System.currentTimeMillis()); // now

            FolderComparisonOptions opts = OPTIONS.withMatchMode(FileMatchMode.CONTENT);
            DiffTreeNode root = compareResult(left, right, opts).root();

            assertThat(childNamed(root, "file.txt").orElseThrow().status())
                    .isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void pairedFilesWithDifferentContentAreDifferent_contentMode(@TempDir Path tempDir)
                throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("file.txt"),  "version A");
            Files.writeString(right.resolve("file.txt"), "version B");

            FolderComparisonOptions opts = OPTIONS.withMatchMode(FileMatchMode.CONTENT);
            DiffTreeNode root = compareResult(left, right, opts).root();

            assertThat(childNamed(root, "file.txt").orElseThrow().status())
                    .isEqualTo(FolderItemStatus.DIFFERENT);
        }
    }

    // ── Task 6.3: ancestor roll-up ────────────────────────────────────────────

    @Nested
    class AncestorRollUp {

        @Test
        void differentFileRollsUpThroughDeepDirectoryHierarchy(@TempDir Path tempDir)
                throws IOException {
            // left:  a/b/c/file.txt  = "version-L"
            // right: a/b/c/file.txt  = "version-R"  (different size)
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.createDirectories(left .resolve("a/b/c"));
            Files.createDirectories(right.resolve("a/b/c"));
            Files.writeString(left .resolve("a/b/c/file.txt"), "short");
            Files.writeString(right.resolve("a/b/c/file.txt"), "much longer content here");

            FolderComparisonOptions opts = OPTIONS.withMatchMode(FileMatchMode.SIZE_ONLY);
            DiffTreeNode root = compareResult(left, right, opts).root();

            // Root should be DIFFERENT
            assertThat(root.status()).isEqualTo(FolderItemStatus.DIFFERENT);

            // Each intermediate dir should also be DIFFERENT
            DiffTreeNode a = childNamed(root, "a").orElseThrow();
            assertThat(a.status()).isEqualTo(FolderItemStatus.DIFFERENT);

            DiffTreeNode b = childNamed(a, "b").orElseThrow();
            assertThat(b.status()).isEqualTo(FolderItemStatus.DIFFERENT);

            DiffTreeNode c = childNamed(b, "c").orElseThrow();
            assertThat(c.status()).isEqualTo(FolderItemStatus.DIFFERENT);

            // The leaf file itself should be DIFFERENT
            DiffTreeNode file = childNamed(c, "file.txt").orElseThrow();
            assertThat(file.status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void allIdenticalFilesKeepAllAncestorsIdentical(@TempDir Path tempDir)
                throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.createDirectories(left .resolve("x/y"));
            Files.createDirectories(right.resolve("x/y"));
            Files.writeString(left .resolve("x/y/same.txt"), "same");
            Files.writeString(right.resolve("x/y/same.txt"), "same");

            FolderComparisonOptions opts = OPTIONS.withMatchMode(FileMatchMode.SIZE_ONLY);
            DiffTreeNode root = compareResult(left, right, opts).root();

            assertThat(root.status()).isEqualTo(FolderItemStatus.IDENTICAL);
            DiffTreeNode x = childNamed(root, "x").orElseThrow();
            assertThat(x.status()).isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void leftOnlyFileRollsParentToDifferent(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.createDirectories(left.resolve("sub"));
            Files.createDirectories(right.resolve("sub"));
            Files.writeString(left.resolve("sub/extra.txt"), "extra");

            DiffTreeNode root = compare(left, right);

            assertThat(root.status()).isEqualTo(FolderItemStatus.DIFFERENT);
            DiffTreeNode sub = childNamed(root, "sub").orElseThrow();
            assertThat(sub.status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }
    }

    // ── Task 6.3: summary counts ──────────────────────────────────────────────

    @Nested
    class SummaryCounts {

        @Test
        void emptyDirectoriesProduceZeroCounts(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));

            FolderComparisonResult result = compareResult(left, right);

            assertThat(result.identicalCount()).isZero();
            assertThat(result.differentCount()).isZero();
            assertThat(result.leftOnlyCount()).isZero();
            assertThat(result.rightOnlyCount()).isZero();
            assertThat(result.ignoredCount()).isZero();
        }

        @Test
        void leftOnlyFilesAreCountedCorrectly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("a.txt"), "a");
            Files.writeString(left.resolve("b.txt"), "b");

            FolderComparisonResult result = compareResult(left, right);

            assertThat(result.leftOnlyCount()).isEqualTo(2);
            assertThat(result.rightOnlyCount()).isZero();
            assertThat(result.identicalCount()).isZero();
        }

        @Test
        void rightOnlyFilesAreCountedCorrectly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(right.resolve("x.txt"), "x");

            FolderComparisonResult result = compareResult(left, right);

            assertThat(result.rightOnlyCount()).isEqualTo(1);
            assertThat(result.leftOnlyCount()).isZero();
        }

        @Test
        void mixedStatusCountsAreCorrect(@TempDir Path tempDir) throws IOException {
            // identical: same.txt (same size, created milliseconds apart — within 2s tolerance)
            // different: diff.txt (different sizes)
            // left-only: lo.txt
            // right-only: ro.txt
            // ignored: ign.txt (manual ignore)
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("same.txt"),  "same");
            Files.writeString(right.resolve("same.txt"), "same");
            Files.writeString(left.resolve("diff.txt"),  "short");
            Files.writeString(right.resolve("diff.txt"), "longer content");
            Files.writeString(left.resolve("lo.txt"),    "left only");
            Files.writeString(right.resolve("ro.txt"),   "right only");
            Files.writeString(left.resolve("ign.txt"),   "ignored");
            Files.writeString(right.resolve("ign.txt"),  "ignored");

            FolderComparisonOptions opts = OPTIONS
                    .withMatchMode(FileMatchMode.SIZE_ONLY)
                    .withManualIgnores(Set.of(Path.of("ign.txt")));

            FolderComparisonResult result = compareResult(left, right, opts);

            assertThat(result.identicalCount()).isEqualTo(1);  // same.txt
            assertThat(result.differentCount()).isEqualTo(1);  // diff.txt
            assertThat(result.leftOnlyCount()).isEqualTo(1);   // lo.txt
            assertThat(result.rightOnlyCount()).isEqualTo(1);  // ro.txt
            assertThat(result.ignoredCount()).isEqualTo(1);    // ign.txt
        }

        @Test
        void nestedDifferentFileIncrementsOnlyDiffAndParentDirDiff(@TempDir Path tempDir)
                throws IOException {
            // Structure: sub/file.txt (DIFFERENT) + sub/ dir (DIFFERENT from roll-up)
            // Counts: 1 DIFFERENT file + 1 DIFFERENT dir = 2 DIFFERENT total
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.createDirectories(left .resolve("sub"));
            Files.createDirectories(right.resolve("sub"));
            Files.writeString(left .resolve("sub/file.txt"), "v1");
            Files.writeString(right.resolve("sub/file.txt"), "v2 longer");

            FolderComparisonOptions opts = OPTIONS.withMatchMode(FileMatchMode.SIZE_ONLY);
            FolderComparisonResult result = compareResult(left, right, opts);

            // sub/ dir is DIFFERENT (roll-up), sub/file.txt is DIFFERENT
            assertThat(result.differentCount()).isEqualTo(2);
            assertThat(result.identicalCount()).isZero();
        }
    }

    // ── Task 6.4: progress and cancellation ───────────────────────────────────

    @Nested
    class ProgressAndCancellation {

        @Test
        void progressCurrentValuesAreMonotonicallyIncreasing(@TempDir Path tempDir)
                throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("a.txt"),  "a");
            Files.writeString(left.resolve("b.txt"),  "b");
            Files.writeString(right.resolve("a.txt"), "a");
            Files.writeString(right.resolve("c.txt"), "c");
            // unique paths: a.txt, b.txt, c.txt → 3 progress events expected

            List<Long> progressValues = new ArrayList<>();
            ProgressReporter reporter = (current, total, msg) -> {
                if (current > 0) progressValues.add(current); // skip initial report(0, ...)
            };
            engine.compare(left, right, OPTIONS, reporter, CancellationToken.neverCancelled());

            assertThat(progressValues).isNotEmpty();
            for (int i = 1; i < progressValues.size(); i++) {
                assertThat(progressValues.get(i))
                        .as("progress values must increase: index %d", i)
                        .isGreaterThan(progressValues.get(i - 1));
            }
        }

        @Test
        void progressTotalMatchesNumberOfUniqueItems(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("a.txt"),  "a");   // left-only
            Files.writeString(right.resolve("b.txt"), "b");   // right-only
            Files.writeString(left.resolve("c.txt"),  "c");   // shared
            Files.writeString(right.resolve("c.txt"), "c");   // shared
            // unique paths: a.txt, b.txt, c.txt → total = 3

            long[] lastProgress = {-1L, -1L};
            ProgressReporter reporter = (current, total, msg) -> {
                lastProgress[0] = current;
                lastProgress[1] = total;
            };
            engine.compare(left, right, OPTIONS, reporter, CancellationToken.neverCancelled());

            assertThat(lastProgress[1]).as("total").isEqualTo(3L);
            assertThat(lastProgress[0]).as("final current").isEqualTo(3L);
        }

        @Test
        void cancellationMidWalkThrowsCancellationException(@TempDir Path tempDir)
                throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            // Create 5 paired files — cancellation fires after the 3rd progress event
            for (int i = 0; i < 5; i++) {
                Files.writeString(left.resolve("file" + i + ".txt"),  "content " + i);
                Files.writeString(right.resolve("file" + i + ".txt"), "content " + i);
            }

            CancellationToken token = new CancellationToken();
            List<Long> received = new ArrayList<>();
            ProgressReporter reporter = (current, total, msg) -> {
                if (current > 0) {
                    received.add(current);
                    if (received.size() == 3) {
                        token.cancel();
                    }
                }
            };

            assertThatThrownBy(() ->
                    engine.compare(left, right, OPTIONS, reporter, token))
                    .isInstanceOf(CancellationException.class);

            // Some progress events were received before cancellation
            assertThat(received).hasSizeLessThanOrEqualTo(5);
        }

        @Test
        void completedComparisonReturnsFullResultNotException(@TempDir Path tempDir)
                throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("file.txt"),  "content");
            Files.writeString(right.resolve("file.txt"), "content");

            FolderComparisonResult result = engine.compare(
                    left, right, OPTIONS,
                    ProgressReporter.noOp(), CancellationToken.neverCancelled());

            // Full, non-null result with expected structure
            assertThat(result).isNotNull();
            assertThat(result.root()).isNotNull();
            assertThat(result.root().children()).hasSize(1);
        }

        @Test
        void emptyDirectoriesProduceNoProgressEvents(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));

            List<long[]> events = new ArrayList<>();
            ProgressReporter reporter = (current, total, msg) -> events.add(new long[]{current, total});

            engine.compare(left, right, OPTIONS, reporter, CancellationToken.neverCancelled());

            // Only the initial report(0, 0, ...) — no per-item events
            assertThat(events).hasSize(1);
            assertThat(events.get(0)[0]).isZero();  // current = 0
            assertThat(events.get(0)[1]).isZero();  // total   = 0
        }
    }

    // ── task 7.2: mask-based exclude + manual-ignore integration ─────────────

    @Nested
    class IgnoreIntegration {

        @Test
        void excludeMaskMarksFileAsIgnored(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("keep.txt"),   "data");
            Files.writeString(right.resolve("keep.txt"),  "data");
            Files.writeString(left.resolve("cache.tmp"),  "temp");
            Files.writeString(right.resolve("cache.tmp"), "temp");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withExcludeMasks(List.of("*.tmp"));

            FolderComparisonResult result = compareResult(left, right, opts);

            Optional<DiffTreeNode> kept = childNamed(result.root(), "keep.txt");
            Optional<DiffTreeNode> excl = childNamed(result.root(), "cache.tmp");

            assertThat(kept).isPresent();
            assertThat(kept.get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(excl).isPresent();
            assertThat(excl.get().status()).isEqualTo(FolderItemStatus.IGNORED);
        }

        @Test
        void excludeMaskCountedInIgnoredCount(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("a.log"),  "log");
            Files.writeString(right.resolve("a.log"), "log");
            Files.writeString(left.resolve("b.log"),  "log");
            Files.writeString(right.resolve("b.log"), "log");
            Files.writeString(left.resolve("keep.txt"),  "x");
            Files.writeString(right.resolve("keep.txt"), "x");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withExcludeMasks(List.of("*.log"));

            FolderComparisonResult result = compareResult(left, right, opts);

            assertThat(result.ignoredCount()).isEqualTo(2);
            assertThat(result.identicalCount()).isEqualTo(1);
            assertThat(result.differentCount()).isZero();
        }

        @Test
        void includeMaskExcludesNonMatchingFiles(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("Main.java"),  "class Main{}");
            Files.writeString(right.resolve("Main.java"), "class Main{}");
            Files.writeString(left.resolve("Main.class"),  "bytecode");
            Files.writeString(right.resolve("Main.class"), "bytecode");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withIncludeMasks(List.of("*.java"));

            FolderComparisonResult result = compareResult(left, right, opts);

            Optional<DiffTreeNode> javaNode  = childNamed(result.root(), "Main.java");
            Optional<DiffTreeNode> classNode = childNamed(result.root(), "Main.class");

            assertThat(javaNode).isPresent();
            assertThat(javaNode.get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(classNode).isPresent();
            assertThat(classNode.get().status()).isEqualTo(FolderItemStatus.IGNORED);
        }

        @Test
        void manualIgnoreViaOptionsMarksNodeAsIgnored(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("important.txt"),  "data");
            Files.writeString(right.resolve("important.txt"), "data");
            Files.writeString(left.resolve("ignored.txt"),  "content");
            Files.writeString(right.resolve("ignored.txt"), "content");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withManualIgnores(Set.of(Path.of("ignored.txt")));

            FolderComparisonResult result = compareResult(left, right, opts);

            Optional<DiffTreeNode> imp = childNamed(result.root(), "important.txt");
            Optional<DiffTreeNode> ign = childNamed(result.root(), "ignored.txt");

            assertThat(imp).isPresent().get().extracting(DiffTreeNode::status)
                    .isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(ign).isPresent().get().extracting(DiffTreeNode::status)
                    .isEqualTo(FolderItemStatus.IGNORED);

            assertThat(result.ignoredCount()).isEqualTo(1);
            assertThat(result.identicalCount()).isEqualTo(1);
        }

        @Test
        void ignoredItemExcludedFromNonIgnoredCounts(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("a.txt"),  "same");
            Files.writeString(right.resolve("a.txt"), "same");
            Files.writeString(left.resolve("b.txt"),  "left-only-file");
            // b.txt is left-only but excluded

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withExcludeMasks(List.of("b.txt"));

            FolderComparisonResult result = compareResult(left, right, opts);

            assertThat(result.leftOnlyCount()).isZero();    // b.txt excluded → not in left-only
            assertThat(result.ignoredCount()).isEqualTo(1);
        }

        @Test
        void directoryExcludeMaskIgnoresEntireSubtree(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Path leftBuild  = Files.createDirectory(left.resolve("build"));
            Path rightBuild = Files.createDirectory(right.resolve("build"));
            Files.writeString(leftBuild.resolve("artifact.jar"),  "jar");
            Files.writeString(rightBuild.resolve("artifact.jar"), "jar");
            Files.writeString(left.resolve("src.java"),  "class Src{}");
            Files.writeString(right.resolve("src.java"), "class Src{}");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withExcludeMasks(List.of("build/"));

            FolderComparisonResult result = compareResult(left, right, opts);

            Optional<DiffTreeNode> buildNode = childNamed(result.root(), "build");
            assertThat(buildNode).isPresent();
            assertThat(buildNode.get().status()).isEqualTo(FolderItemStatus.IGNORED);
            // Children of ignored dir are NOT traversed
            assertThat(buildNode.get().children()).isEmpty();

            assertThat(result.ignoredCount()).isEqualTo(1);
            assertThat(result.identicalCount()).isEqualTo(1); // src.java
        }
    }

    // ── task 7.2: content ignore flags ────────────────────────────────────────

    @Nested
    class ContentIgnoreFlags {

        private static FolderComparisonOptions contentOpts(
                boolean ignoreWs, boolean ignoreCase, boolean ignoreLE) {
            return FolderComparisonOptions.defaults()
                    .withMatchMode(FileMatchMode.CONTENT)
                    .withContent(FolderComparisonOptions.defaults().content()
                            .withIgnoreWhitespace(ignoreWs)
                            .withIgnoreCase(ignoreCase)
                            .withIgnoreLineEndings(ignoreLE));
        }

        @Test
        void sameContentBytesDiffStaysIdentical(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("f.txt"),  "Hello World");
            Files.writeString(right.resolve("f.txt"), "Hello World");

            FolderComparisonResult r = compareResult(left, right, contentOpts(false, false, false));
            assertThat(childNamed(r.root(), "f.txt").get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void ignoreWhitespaceMakesFilesIdentical(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("f.txt"),  "Hello   World");
            Files.writeString(right.resolve("f.txt"), "Hello World");

            FolderComparisonResult wsOn  = compareResult(left, right, contentOpts(true,  false, false));
            FolderComparisonResult wsOff = compareResult(left, right, contentOpts(false, false, false));

            assertThat(childNamed(wsOn.root(),  "f.txt").get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(childNamed(wsOff.root(), "f.txt").get().status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void ignoreCaseMakesFilesIdentical(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.writeString(left.resolve("f.txt"),  "Hello World");
            Files.writeString(right.resolve("f.txt"), "HELLO WORLD");

            FolderComparisonResult caseOn  = compareResult(left, right, contentOpts(false, true,  false));
            FolderComparisonResult caseOff = compareResult(left, right, contentOpts(false, false, false));

            assertThat(childNamed(caseOn.root(),  "f.txt").get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(childNamed(caseOff.root(), "f.txt").get().status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void ignoreLineEndingsMakesFilesIdentical(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            // Write LF vs CRLF content directly as bytes
            Files.write(left.resolve("f.txt"),  "line1\nline2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(right.resolve("f.txt"), "line1\r\nline2\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            FolderComparisonResult leOn  = compareResult(left, right, contentOpts(false, false, true));
            FolderComparisonResult leOff = compareResult(left, right, contentOpts(false, false, false));

            assertThat(childNamed(leOn.root(),  "f.txt").get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
            assertThat(childNamed(leOff.root(), "f.txt").get().status()).isEqualTo(FolderItemStatus.DIFFERENT);
        }

        @Test
        void allFlagsTogetherNormalizesCorrectly(@TempDir Path tempDir) throws IOException {
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            Files.write(left.resolve("f.txt"),  "  HELLO   WORLD  \n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(right.resolve("f.txt"), "hello world\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

            FolderComparisonResult r = compareResult(left, right, contentOpts(true, true, true));
            assertThat(childNamed(r.root(), "f.txt").get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
        }

        @Test
        void contentFlagsDoNotAffectSizeOnlyMode(@TempDir Path tempDir) throws IOException {
            // Content flags are irrelevant for SIZE_ONLY — result depends only on file size
            Path left  = Files.createDirectory(tempDir.resolve("left"));
            Path right = Files.createDirectory(tempDir.resolve("right"));
            // Same number of bytes, different content
            Files.writeString(left.resolve("f.txt"),  "AAAA");
            Files.writeString(right.resolve("f.txt"), "BBBB");

            FolderComparisonOptions opts = FolderComparisonOptions.defaults()
                    .withMatchMode(FileMatchMode.SIZE_ONLY)
                    .withContent(FolderComparisonOptions.defaults().content()
                            .withIgnoreCase(true));

            FolderComparisonResult r = compareResult(left, right, opts);
            // SIZE_ONLY says identical (same size), ignoreCase flag is irrelevant here
            assertThat(childNamed(r.root(), "f.txt").get().status()).isEqualTo(FolderItemStatus.IDENTICAL);
        }
    }
}
