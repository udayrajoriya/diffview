package com.comparetool.core.folder;

import com.comparetool.core.ignore.DefaultIgnoreRuleEngine;
import com.comparetool.core.ignore.IgnoreRuleEngine;
import com.comparetool.infra.concurrent.CancellationToken;
import com.comparetool.infra.concurrent.ProgressReporter;
import com.comparetool.infra.hash.HashService;
import com.comparetool.infra.hash.Sha256HashService;
import com.comparetool.model.DiffTreeNode;
import com.comparetool.model.ErrorCode;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderComparisonResult;
import com.comparetool.model.FolderItemStatus;
import com.comparetool.model.ItemError;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Default implementation of {@link FolderDiffEngine}.
 *
 * <h3>Walk strategy</h3>
 * <p>Both trees are scanned with {@link Files#walk(Path, java.nio.file.FileVisitOption...)}
 * without {@code FOLLOW_LINKS}, so symbolic links are never followed.  A symlink — whether
 * it targets a file or a directory, inside or outside the comparison root — appears as a
 * single leaf entry in the result tree and its target is never traversed.
 *
 * <h3>Status assignment for this task (6.1)</h3>
 * <ul>
 *   <li>Entries present only on the left  → {@link FolderItemStatus#LEFT_ONLY}.</li>
 *   <li>Entries present only on the right → {@link FolderItemStatus#RIGHT_ONLY}.</li>
 *   <li>Paired file entries               → {@link FolderItemStatus#IDENTICAL}
 *       (content comparison is added in task 6.3).</li>
 *   <li>Paired directory entries          → {@link FolderItemStatus#DIFFERENT} if any
 *       descendant is non-IDENTICAL; {@link FolderItemStatus#IDENTICAL} otherwise.</li>
 *   <li>Manually ignored entries          → {@link FolderItemStatus#IGNORED}
 *       (not examined further).</li>
 * </ul>
 */
public class DefaultFolderDiffEngine implements FolderDiffEngine {

    /** Sentinel relative path for the root node. */
    private static final Path ROOT_PATH = Path.of("");

    private final HashService hashService;

    /**
     * Creates an engine that uses a {@link Sha256HashService} for content-hash comparisons.
     * Suitable for tests and non-DI usage.
     */
    public DefaultFolderDiffEngine() {
        this(new Sha256HashService());
    }

    /**
     * Creates an engine that delegates content-hash comparisons to {@code hashService}.
     *
     * @param hashService the hash service to use when the match mode is
     *                    {@link com.comparetool.model.FileMatchMode#CONTENT}
     */
    public DefaultFolderDiffEngine(HashService hashService) {
        this.hashService = Objects.requireNonNull(hashService, "hashService must not be null");
    }

    @Override
    public FolderComparisonResult compare(
            Path left, Path right,
            FolderComparisonOptions options,
            ProgressReporter progress,
            CancellationToken token) {

        Objects.requireNonNull(left,     "left must not be null");
        Objects.requireNonNull(right,    "right must not be null");
        Objects.requireNonNull(options,  "options must not be null");
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(token,    "token must not be null");

        if (!Files.isDirectory(left,  LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Left path is not a directory: " + left);
        }
        if (!Files.isDirectory(right, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Right path is not a directory: " + right);
        }

        token.checkCancelled();

        List<ItemError> errors = new ArrayList<>();

        FileMeta leftRoot  = readRootMeta(left);
        FileMeta rightRoot = readRootMeta(right);

        Map<Path, FileMeta> leftMap  = scanTree(left,  token, errors);
        Map<Path, FileMeta> rightMap = scanTree(right, token, errors);

        token.checkCancelled();

        // Compute total unique paths across both trees for progress reporting.
        long totalItems;
        {
            Set<Path> allPaths = new HashSet<>(leftMap.keySet());
            allPaths.addAll(rightMap.keySet());
            totalItems = allPaths.size();
        }
        long[] progressCounter = {0L};
        progress.report(0, totalItems, "Starting comparison");

        IgnoreRuleEngine ignoreEngine = DefaultIgnoreRuleEngine.from(options);
        List<DiffTreeNode> children =
                buildChildren(ROOT_PATH, leftMap, rightMap, options, ignoreEngine, token, progress, totalItems, progressCounter);
        FolderItemStatus rootStatus = computeDirectoryStatus(children);

        DiffTreeNode root = DiffTreeNode.paired(ROOT_PATH, true, leftRoot, rightRoot, rootStatus, children);
        return FolderDiffEngine.summarize(root, errors);
    }

    // ── scanning ─────────────────────────────────────────────────────────────

    /**
     * Walks {@code root} without following symlinks and returns a map from each
     * entry's relative path to its {@link FileMeta}.  The root itself is excluded.
     *
     * <p>Entries that cannot be read (permission, race, I/O error) are skipped and
     * an {@link ItemError} is appended to {@code errors} (REQ-016.1 fail-soft).
     */
    private Map<Path, FileMeta> scanTree(Path root, CancellationToken token, List<ItemError> errors) {
        Map<Path, FileMeta> entries = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (var iter = stream.iterator(); iter.hasNext(); ) {
                Path p = iter.next();
                if (p.equals(root)) {
                    continue; // skip the root itself
                }
                token.checkCancelled();

                Path relative = root.relativize(p);
                try {
                    BasicFileAttributes attrs = readFileAttributes(p);
                    boolean isDir  = attrs.isDirectory();
                    long    size   = isDir ? 0L : attrs.size();
                    Instant lastMod = attrs.lastModifiedTime().toInstant();
                    entries.put(relative,
                            new FileMeta(p.toAbsolutePath().normalize(),
                                    relative, isDir, size, lastMod));
                } catch (IOException e) {
                    // Fail-soft: record the error and continue with remaining items
                    errors.add(new ItemError(relative, ErrorCode.IO_ERROR,
                            "Cannot read file attributes for '" + p + "': " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan directory: " + root, e);
        }
        return entries;
    }

    /** Reads the {@link FileMeta} for a root directory entry (relative path = empty). */
    private FileMeta readRootMeta(Path root) {
        try {
            BasicFileAttributes attrs = readFileAttributes(root);
            return new FileMeta(root.toAbsolutePath().normalize(),
                    ROOT_PATH, true, 0L, attrs.lastModifiedTime().toInstant());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read root directory: " + root, e);
        }
    }

    /**
     * Reads file attributes for the given path.
     *
     * <p><strong>Protected for testing only.</strong>  Subclasses in tests may override
     * this method to simulate I/O failures for specific paths, enabling fail-soft
     * verification without OS-level permission manipulation.
     *
     * @param p the absolute path to read
     * @return the file attributes; never {@code null}
     * @throws IOException if the attributes cannot be read
     */
    protected BasicFileAttributes readFileAttributes(Path p) throws IOException {
        return Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    // ── tree building ─────────────────────────────────────────────────────────

    /**
     * Builds the list of direct child {@link DiffTreeNode}s for a directory at
     * {@code parentRelPath}, sorted lexicographically.
     *
     * <p>Reports one progress event per child entry processed and checks for
     * cancellation after each report.
     */
    private List<DiffTreeNode> buildChildren(
            Path parentRelPath,
            Map<Path, FileMeta> leftMap,
            Map<Path, FileMeta> rightMap,
            FolderComparisonOptions options,
            IgnoreRuleEngine ignoreEngine,
            CancellationToken token,
            ProgressReporter progress,
            long totalItems,
            long[] progressCounter) {

        // Collect all unique direct-child relative paths from both sides
        TreeSet<Path> childPaths = new TreeSet<>(Comparator.comparing(Path::toString));
        leftMap .keySet().stream().filter(p -> isDirectChild(p, parentRelPath)).forEach(childPaths::add);
        rightMap.keySet().stream().filter(p -> isDirectChild(p, parentRelPath)).forEach(childPaths::add);

        List<DiffTreeNode> result = new ArrayList<>();
        for (Path childRelPath : childPaths) {
            progressCounter[0]++;
            progress.report(progressCounter[0], totalItems,
                    childRelPath.getFileName().toString());
            token.checkCancelled();

            FileMeta leftMeta  = leftMap .get(childRelPath);
            FileMeta rightMeta = rightMap.get(childRelPath);
            boolean  isDir     = isDirectory(childRelPath, leftMap, rightMap);

            // Ignore rule engine check: covers masks + manual ignores
            if (ignoreEngine.isExcluded(childRelPath, isDir)) {
                result.add(ignoredNode(childRelPath, isDir, leftMeta, rightMeta));
                continue;
            }

            List<DiffTreeNode> subChildren = isDir
                    ? buildChildren(childRelPath, leftMap, rightMap, options, ignoreEngine, token,
                                    progress, totalItems, progressCounter)
                    : List.of();

            DiffTreeNode node;
            if (leftMeta == null) {
                node = DiffTreeNode.rightOnly(childRelPath, isDir, rightMeta, subChildren);
            } else if (rightMeta == null) {
                node = DiffTreeNode.leftOnly(childRelPath, isDir, leftMeta, subChildren);
            } else {
                // Both sides present — call FileMatchCriteria for files; roll up dirs from children.
                FolderItemStatus status = isDir
                        ? computeDirectoryStatus(subChildren)
                        : FileMatchCriteria.forMode(options.matchMode(), hashService)
                                           .compare(leftMeta, rightMeta, options);
                node = DiffTreeNode.paired(childRelPath, isDir, leftMeta, rightMeta, status, subChildren);
            }
            result.add(node);
        }
        return result;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code candidate} is a direct child of {@code parent}.
     *
     * <p>When {@code parent} is the root (empty path), any path with exactly one
     * name element is a direct child.  Otherwise the path must have exactly one more
     * name element and share the parent as a prefix.
     */
    private static boolean isDirectChild(Path candidate, Path parent) {
        if (parent.toString().isEmpty()) {
            return candidate.getNameCount() == 1;
        }
        return candidate.getNameCount() == parent.getNameCount() + 1
                && candidate.startsWith(parent);
    }

    /** Returns {@code true} if the entry at {@code relPath} is a directory on either side. */
    private static boolean isDirectory(
            Path relPath,
            Map<Path, FileMeta> leftMap,
            Map<Path, FileMeta> rightMap) {
        FileMeta meta = leftMap.get(relPath);
        if (meta != null) return meta.directory();
        meta = rightMap.get(relPath);
        return meta != null && meta.directory();
    }

    /**
     * Computes the status of a directory node by rolling up its children's statuses:
     * {@link FolderItemStatus#DIFFERENT} if any child has a differing status;
     * {@link FolderItemStatus#IDENTICAL} otherwise.
     */
    private static FolderItemStatus computeDirectoryStatus(List<DiffTreeNode> children) {
        return children.stream().anyMatch(c -> c.status().isDifferent())
                ? FolderItemStatus.DIFFERENT
                : FolderItemStatus.IDENTICAL;
    }

    /** Creates an IGNORED node for a manually-ignored entry (one or both sides may be null). */
    private static DiffTreeNode ignoredNode(
            Path relPath, boolean isDir, FileMeta leftMeta, FileMeta rightMeta) {
        return new DiffTreeNode(relPath, isDir, leftMeta, rightMeta,
                FolderItemStatus.IGNORED, List.of());
    }
}
