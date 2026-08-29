package com.comparetool.core.service;

import com.comparetool.infra.concurrent.CancellationToken;
import com.comparetool.infra.concurrent.ProgressReporter;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FileComparisonResult;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderComparisonResult;
import com.comparetool.model.FolderItemStatus;

import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * High-level facade that orchestrates the comparison engines.
 *
 * <p>Both {@link #compareFiles} and {@link #compareFolders} submit their work to an
 * injected {@link com.comparetool.infra.concurrent.TaskExecutor}, allowing callers
 * (e.g. ViewModels) to remain responsive while a comparison runs in the background.
 * Tests inject a {@link com.comparetool.infra.concurrent.DirectTaskExecutor} for
 * synchronous, deterministic execution.
 *
 * <h3>File comparison specifics</h3>
 * <ul>
 *   <li><b>Large-file warning</b> — if either file's size exceeds
 *       {@link ComparisonOptions#largeFileWarnBytes()} (and the threshold is > 0),
 *       the {@code largeFileWarning} callback is invoked with both paths
 *       <em>before</em> the diff runs.  The diff is never aborted by the warning.</li>
 *   <li><b>Binary fallback</b> — if either file is detected as binary, a content-equal
 *       check replaces the line diff and an empty {@link com.comparetool.model.DiffModel}
 *       is returned ({@link com.comparetool.model.DiffModel#identical()} reflects
 *       whether the files are byte-equal).</li>
 * </ul>
 */
public interface ComparisonService {

    /**
     * Compares two files asynchronously and returns a {@link Future} that resolves to
     * a {@link FileComparisonResult}.
     *
     * @param left              path to the left (original) file
     * @param right             path to the right (revised) file
     * @param options           comparison options (encoding, ignore flags, large-file threshold)
     * @param largeFileWarning  called with {@code (left, right)} when the large-file threshold
     *                          is exceeded; may be {@code null} to suppress warnings
     * @return a Future resolving to the comparison result
     */
    Future<FileComparisonResult> compareFiles(
            Path left, Path right,
            ComparisonOptions options,
            BiConsumer<Path, Path> largeFileWarning);

    /**
     * Compares two directory trees asynchronously, applying ignore masks and manual
     * ignores from {@code options}.
     *
     * @param left     root of the left-side directory
     * @param right    root of the right-side directory
     * @param options  folder comparison options (match mode, masks, ignores, etc.)
     * @param progress progress reporter; use {@link ProgressReporter#noOp()} to discard
     * @param token    cancellation token; use {@link CancellationToken#neverCancelled()} if not needed
     * @return a Future resolving to the folder comparison result
     */
    Future<FolderComparisonResult> compareFolders(
            Path left, Path right,
            FolderComparisonOptions options,
            ProgressReporter progress,
            CancellationToken token);

    /**
     * Synchronously evaluates the status of a single paired file entry using the
     * match mode and content options from {@code options}.
     *
     * <p>Used by ViewModels for on-demand status refresh (e.g. after a copy/merge
     * operation) without re-scanning the entire tree.
     *
     * @param left    left-side {@link FileMeta}; may be {@code null} for right-only entries
     * @param right   right-side {@link FileMeta}; may be {@code null} for left-only entries
     * @param options folder comparison options governing how the pair is evaluated
     * @return the computed {@link FolderItemStatus}
     * @throws IllegalArgumentException if both {@code left} and {@code right} are null
     */
    FolderItemStatus evaluatePair(
            FileMeta left, FileMeta right,
            FolderComparisonOptions options);
}
