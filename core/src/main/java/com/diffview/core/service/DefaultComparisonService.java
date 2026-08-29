package com.comparetool.core.service;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.core.diff.TextDiffEngine;
import com.comparetool.core.folder.FileMatchCriteria;
import com.comparetool.core.folder.FolderDiffEngine;
import com.comparetool.core.folder.DefaultFolderDiffEngine;
import com.comparetool.infra.concurrent.CancellationToken;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.infra.concurrent.ProgressReporter;
import com.comparetool.infra.concurrent.TaskExecutor;
import com.comparetool.infra.concurrent.PooledTaskExecutor;
import com.comparetool.infra.encoding.JUniversalChardetDetector;
import com.comparetool.infra.hash.HashService;
import com.comparetool.infra.hash.Sha256HashService;
import com.comparetool.infra.io.FileIOService;
import com.comparetool.infra.io.NioFileIOService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.DecodedText;
import com.comparetool.model.DiffModel;
import com.comparetool.model.FileComparisonResult;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderComparisonResult;
import com.comparetool.model.FolderItemStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * Default {@link ComparisonService} implementation.
 *
 * <h3>Dependency injection</h3>
 * <p>All collaborators are constructor-injected so tests can supply fakes
 * (e.g. {@link DirectTaskExecutor} for synchronous execution).  The
 * no-arg constructor wires production defaults.
 */
public final class DefaultComparisonService implements ComparisonService {

    private final TextDiffEngine   textDiffEngine;
    private final FolderDiffEngine folderDiffEngine;
    private final FileIOService    fileIOService;
    private final HashService      hashService;
    private final TaskExecutor     executor;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Production constructor: wires default implementations.
     * Uses a {@link PooledTaskExecutor} for background work.
     */
    public DefaultComparisonService() {
        this(new LineDiffEngine(),
             new DefaultFolderDiffEngine(),
             new NioFileIOService(new JUniversalChardetDetector()),
             new Sha256HashService(),
             new PooledTaskExecutor());
    }

    /**
     * Full constructor for dependency injection (production and tests).
     */
    public DefaultComparisonService(
            TextDiffEngine   textDiffEngine,
            FolderDiffEngine folderDiffEngine,
            FileIOService    fileIOService,
            HashService      hashService,
            TaskExecutor     executor) {
        this.textDiffEngine   = Objects.requireNonNull(textDiffEngine,   "textDiffEngine");
        this.folderDiffEngine = Objects.requireNonNull(folderDiffEngine, "folderDiffEngine");
        this.fileIOService    = Objects.requireNonNull(fileIOService,    "fileIOService");
        this.hashService      = Objects.requireNonNull(hashService,      "hashService");
        this.executor         = Objects.requireNonNull(executor,         "executor");
    }

    // ── ComparisonService ─────────────────────────────────────────────────────

    @Override
    public Future<FileComparisonResult> compareFiles(
            Path left, Path right,
            ComparisonOptions options,
            BiConsumer<Path, Path> largeFileWarning) {
        Objects.requireNonNull(left,    "left");
        Objects.requireNonNull(right,   "right");
        Objects.requireNonNull(options, "options");

        return executor.submit(() -> doCompareFiles(left, right, options, largeFileWarning));
    }

    @Override
    public Future<FolderComparisonResult> compareFolders(
            Path left, Path right,
            FolderComparisonOptions options,
            ProgressReporter progress,
            CancellationToken token) {
        Objects.requireNonNull(left,     "left");
        Objects.requireNonNull(right,    "right");
        Objects.requireNonNull(options,  "options");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(token,    "token");

        return executor.submit(() ->
                folderDiffEngine.compare(left, right, options, progress, token));
    }

    @Override
    public FolderItemStatus evaluatePair(
            FileMeta left, FileMeta right,
            FolderComparisonOptions options) {
        Objects.requireNonNull(options, "options");
        if (left == null && right == null) {
            throw new IllegalArgumentException("At least one of left/right must be non-null");
        }
        if (left == null)  return FolderItemStatus.RIGHT_ONLY;
        if (right == null) return FolderItemStatus.LEFT_ONLY;
        if (left.directory() || right.directory()) {
            // Directory equality is computed from children; a lone call yields IDENTICAL
            return FolderItemStatus.IDENTICAL;
        }
        return FileMatchCriteria.forMode(options.matchMode(), hashService)
                                .compare(left, right, options);
    }

    // ── File comparison implementation ────────────────────────────────────────

    private FileComparisonResult doCompareFiles(
            Path left, Path right,
            ComparisonOptions options,
            BiConsumer<Path, Path> largeFileWarning) {

        // 1. Large-file threshold warning
        if (largeFileWarning != null) {
            long threshold = options.largeFileWarnBytes();
            if (threshold > 0 && exceedsThreshold(left, right, threshold)) {
                largeFileWarning.accept(left, right);
            }
        }

        // 2. Binary fallback: if either file is binary, skip the line diff
        if (fileIOService.isBinary(left) || fileIOService.isBinary(right)) {
            boolean equal = hashService.contentEquals(left, right);
            DiffModel binaryModel = new DiffModel(List.of(), List.of(), null, null, equal);
            return new FileComparisonResult(binaryModel, left, right);
        }

        // 3. Text path: decode files, run line diff
        DecodedText leftText  = fileIOService.read(left,  options.leftEncodingOverride());
        DecodedText rightText = fileIOService.read(right, options.rightEncodingOverride());

        DiffModel model = textDiffEngine.diff(leftText.lines(), rightText.lines(), options);
        return new FileComparisonResult(model, left, right);
    }

    /**
     * Returns {@code true} if the size of {@code left} or {@code right} exceeds
     * {@code threshold}.  Silently returns {@code false} if the size cannot be read.
     */
    private static boolean exceedsThreshold(Path left, Path right, long threshold) {
        try {
            return Files.size(left) > threshold || Files.size(right) > threshold;
        } catch (IOException e) {
            return false; // Non-fatal: skip warning rather than crashing
        }
    }
}
