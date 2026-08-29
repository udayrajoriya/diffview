package com.diffview.viewmodel;

import com.diffview.core.diff.TextDiffEngine;
import com.diffview.core.merge.DefaultMergeManager;
import com.diffview.core.merge.EditableDocument;
import com.diffview.core.merge.MergeManager;
import com.diffview.core.service.ComparisonService;
import com.diffview.infra.concurrent.TaskExecutor;
import com.diffview.infra.io.FileIOService;
import com.diffview.model.ComparisonOptions;
import com.diffview.model.DecodedText;
import com.diffview.model.DiffBlock;
import com.diffview.model.DiffModel;
import com.diffview.model.FileComparisonResult;
import com.diffview.model.MergeDirection;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

/**
 * ViewModel for the side-by-side file comparison view (MVVM, task 10.1).
 *
 * <p>Owns all observable state — diff model, navigation index, dirty flags, undo/redo
 * availability — and exposes commands that delegate to the injected services.
 *
 * <h3>Threading</h3>
 * <p>The {@code compare} command submits work to the injected {@link TaskExecutor}.
 * In production, that is a {@code PooledTaskExecutor}; in tests a
 * {@code DirectTaskExecutor} is injected so everything runs synchronously.
 *
 * <h3>JavaFX properties</h3>
 * <p>All state is exposed as read-only JavaFX observable properties from
 * {@code javafx.base}.  <em>No</em> {@code Node} subclasses are referenced here,
 * so the class can be instantiated and tested without a running JavaFX Application.
 */
public final class FileComparisonViewModel {

    // ── observable state ─────────────────────────────────────────────────────
    private final SimpleObjectProperty<DiffModel> diffModel          = new SimpleObjectProperty<>(null);
    private final SimpleIntegerProperty  differenceCount             = new SimpleIntegerProperty(0);
    private final SimpleIntegerProperty  currentDifferenceIndex      = new SimpleIntegerProperty(-1);
    private final SimpleBooleanProperty  loading                     = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty  leftDirty                   = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty  rightDirty                  = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty  canUndoProp                 = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty  canRedoProp                 = new SimpleBooleanProperty(false);
    private final SimpleBooleanProperty  atFirstDifference           = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty  atLastDifference            = new SimpleBooleanProperty(true);

    // ── injected services ────────────────────────────────────────────────────
    private final ComparisonService comparisonService;
    private final TextDiffEngine    textDiffEngine;
    private final TaskExecutor      executor;
    private final FileIOService     fileIOService;

    // ── per-comparison mutable state ─────────────────────────────────────────
    private Path             leftPath;
    private Path             rightPath;
    private ComparisonOptions currentOptions;
    private MergeManager     mergeManager;
    private EditableDocument leftDocument;
    private EditableDocument rightDocument;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param comparisonService drives file diffing
     * @param textDiffEngine    re-diffs after each merge operation (forwarded to
     *                          {@link DefaultMergeManager})
     * @param executor          runs the comparison on a background thread (tests
     *                          inject {@code DirectTaskExecutor} for synchronous execution)
     * @param fileIOService     reads files to populate editable documents; saves them back
     */
    public FileComparisonViewModel(
            ComparisonService comparisonService,
            TextDiffEngine    textDiffEngine,
            TaskExecutor      executor,
            FileIOService     fileIOService) {
        this.comparisonService = Objects.requireNonNull(comparisonService, "comparisonService");
        this.textDiffEngine    = Objects.requireNonNull(textDiffEngine,    "textDiffEngine");
        this.executor          = Objects.requireNonNull(executor,          "executor");
        this.fileIOService     = Objects.requireNonNull(fileIOService,     "fileIOService");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Read-only property accessors
    // ─────────────────────────────────────────────────────────────────────────

    /** The current {@link DiffModel}; {@code null} when no comparison has been run. */
    public ReadOnlyObjectProperty<DiffModel> diffModelProperty()        { return diffModel; }

    /** Total number of difference blocks in the current comparison. */
    public ReadOnlyIntegerProperty differenceCountProperty()            { return differenceCount; }

    /**
     * 0-based index of the currently highlighted difference block.
     * {@code -1} means no block is selected (initial state or no differences).
     */
    public ReadOnlyIntegerProperty currentDifferenceIndexProperty()     { return currentDifferenceIndex; }

    /** {@code true} while a comparison is being computed. */
    public ReadOnlyBooleanProperty loadingProperty()                    { return loading; }

    /** {@code true} when the left document has unsaved merge changes. */
    public ReadOnlyBooleanProperty leftDirtyProperty()                  { return leftDirty; }

    /** {@code true} when the right document has unsaved merge changes. */
    public ReadOnlyBooleanProperty rightDirtyProperty()                 { return rightDirty; }

    /** {@code true} when at least one merge operation can be undone. */
    public ReadOnlyBooleanProperty canUndoProperty()                    { return canUndoProp; }

    /** {@code true} when at least one undone operation can be re-applied. */
    public ReadOnlyBooleanProperty canRedoProperty()                    { return canRedoProp; }

    /**
     * {@code true} when the current difference index is at (or before) the first block,
     * or there are no differences.
     */
    public ReadOnlyBooleanProperty atFirstDifferenceProperty()          { return atFirstDifference; }

    /**
     * {@code true} when the current difference index is at (or after) the last block,
     * or there are no differences.
     */
    public ReadOnlyBooleanProperty atLastDifferenceProperty()           { return atLastDifference; }

    // ── convenience primitive getters ────────────────────────────────────────

    public DiffModel getDiffModel()            { return diffModel.get(); }
    public int  getDifferenceCount()           { return differenceCount.get(); }
    public int  getCurrentDifferenceIndex()    { return currentDifferenceIndex.get(); }
    public boolean isLoading()                 { return loading.get(); }
    public boolean isLeftDirty()               { return leftDirty.get(); }
    public boolean isRightDirty()              { return rightDirty.get(); }
    public boolean canUndo()                   { return canUndoProp.get(); }
    public boolean canRedo()                   { return canRedoProp.get(); }
    public boolean isAtFirstDifference()       { return atFirstDifference.get(); }
    public boolean isAtLastDifference()        { return atLastDifference.get(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Commands
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Asynchronously compares {@code left} and {@code right}.
     *
     * <p>Steps:
     * <ol>
     *   <li>Set {@code loading = true} and clear all prior state.</li>
     *   <li>Submit a task to {@link #executor} that calls
     *       {@link ComparisonService#compareFiles compareFiles}, reads both files via
     *       {@link FileIOService#read read}, creates {@link EditableDocument}s and a
     *       {@link DefaultMergeManager}, then updates observable properties.</li>
     *   <li>Set {@code loading = false} when done (success or failure).</li>
     * </ol>
     *
     * <p>With {@code DirectTaskExecutor} (tests) the returned {@link Future} is already
     * resolved when this method returns, so callers can assert state immediately.
     *
     * @param left             left-side file path
     * @param right            right-side file path
     * @param options          comparison options
     * @param largeFileWarning callback invoked before diffing if files exceed the
     *                         large-file threshold; {@code null} suppresses the warning
     * @return a Future that completes when the comparison (and state update) is done
     */
    public Future<?> compare(Path left, Path right, ComparisonOptions options,
                             BiConsumer<Path, Path> largeFileWarning) {
        Objects.requireNonNull(left,    "left");
        Objects.requireNonNull(right,   "right");
        Objects.requireNonNull(options, "options");

        loading.set(true);
        resetMergeState();

        return executor.submit(() -> {
            try {
                // 1. Diff the files via the service
                Future<FileComparisonResult> future =
                        comparisonService.compareFiles(left, right, options, largeFileWarning);
                FileComparisonResult result = future.get();

                // 2. Read both files to build editable documents for merging
                DecodedText leftText  = fileIOService.read(left,  options.leftEncodingOverride());
                DecodedText rightText = fileIOService.read(right, options.rightEncodingOverride());

                EditableDocument leftDoc  = EditableDocument.from(leftText);
                EditableDocument rightDoc = EditableDocument.from(rightText);

                // 3. Wire up the merge manager (re-diffs from the editable documents)
                MergeManager mm = new DefaultMergeManager(
                        leftDoc, rightDoc, false, false, textDiffEngine, options);

                // 4. Commit state
                leftPath       = left;
                rightPath      = right;
                currentOptions = options;
                leftDocument   = leftDoc;
                rightDocument  = rightDoc;
                mergeManager   = mm;

                applyDiffModel(result.model());
                loading.set(false);
            } catch (Exception ex) {
                loading.set(false);
                throw new RuntimeException("Comparison failed", ex);
            }
            return null;
        });
    }

    /**
     * Convenience overload — no large-file warning callback.
     *
     * @see #compare(Path, Path, ComparisonOptions, BiConsumer)
     */
    public Future<?> compare(Path left, Path right, ComparisonOptions options) {
        return compare(left, right, options, null);
    }

    /**
     * Re-runs the current comparison with updated {@link ComparisonOptions}.
     *
     * <p>Intended for the options toolbar (task 12.5): when the user changes an
     * encoding override or ignore flag, the view calls this method rather than
     * the full {@link #compare(Path, Path, ComparisonOptions)} overload.
     *
     * @param newOptions updated options to apply
     * @return a Future that completes when the re-comparison is done
     * @throws IllegalStateException if no comparison has been loaded yet
     */
    public Future<?> recompare(ComparisonOptions newOptions) {
        Objects.requireNonNull(newOptions, "newOptions");
        if (leftPath == null || rightPath == null) {
            throw new IllegalStateException("No comparison loaded; call compare() first.");
        }
        return compare(leftPath, rightPath, newOptions);
    }

    /**
     * Returns the options that were used for the most recent comparison,
     * or {@code null} if no comparison has been loaded.
     */
    public ComparisonOptions getComparisonOptions() {
        return currentOptions;
    }

    /**
     * Moves the navigation index to the next difference block.
     *
     * <p><strong>Boundary behaviour</strong>: clamped at the last block; calling this
     * when already at the last block is a no-op (index does not wrap around).
     */
    public void nextDifference() {
        int count = differenceCount.get();
        if (count == 0) return;
        int idx  = currentDifferenceIndex.get();
        int next = (idx < 0) ? 0 : Math.min(idx + 1, count - 1);
        setCurrentDifferenceIndex(next);
    }

    /**
     * Moves the navigation index to the previous difference block.
     *
     * <p><strong>Boundary behaviour</strong>: clamped at the first block (index 0);
     * calling this when already at the first block is a no-op.
     */
    public void previousDifference() {
        int count = differenceCount.get();
        if (count == 0) return;
        int idx = currentDifferenceIndex.get();
        if (idx <= 0) return;
        setCurrentDifferenceIndex(Math.max(idx - 1, 0));
    }

    /**
     * Copies a single difference block in the given direction and re-syncs observable state.
     *
     * @param block     the block to copy (must not be UNCHANGED)
     * @param direction direction of the copy
     * @throws IllegalStateException if no comparison has been loaded
     */
    public void copyBlock(DiffBlock block, MergeDirection direction) {
        Objects.requireNonNull(block,     "block");
        Objects.requireNonNull(direction, "direction");
        requireMergeManager();
        mergeManager.copyBlock(block, direction);
        afterMerge();
    }

    /**
     * Copies all difference blocks in the given direction and re-syncs observable state.
     *
     * @param direction direction of the copy
     * @throws IllegalStateException if no comparison has been loaded
     */
    public void copyAll(MergeDirection direction) {
        Objects.requireNonNull(direction, "direction");
        requireMergeManager();
        mergeManager.copyAll(direction);
        afterMerge();
    }

    /**
     * Undoes the most recent merge operation.
     *
     * @throws IllegalStateException if there is nothing to undo
     */
    public void undo() {
        requireMergeManager();
        mergeManager.undo();
        afterMerge();
    }

    /**
     * Re-applies the most recently undone operation.
     *
     * @throws IllegalStateException if there is nothing to redo
     */
    public void redo() {
        requireMergeManager();
        mergeManager.redo();
        afterMerge();
    }

    /**
     * Saves the left document to {@link #leftPath} and clears the left dirty flag.
     *
     * @throws IllegalStateException if no comparison has been loaded
     */
    public void saveLeft() {
        requireMergeManager();
        writeDocument(leftPath, leftDocument);
        leftDirty.set(false);
    }

    /**
     * Saves the right document to {@link #rightPath} and clears the right dirty flag.
     *
     * @throws IllegalStateException if no comparison has been loaded
     */
    public void saveRight() {
        requireMergeManager();
        writeDocument(rightPath, rightDocument);
        rightDirty.set(false);
    }

    /**
     * Directly replaces the content of one side's document and re-diffs live (REQ-005).
     *
     * <p>This is the low-level hook for the in-place text-editor in the UI (task 12.4).
     * It bypasses the {@link MergeManager}'s undo/redo stack — direct text edits are
     * treated as intentional and are not undoable via the merge undo command.
     *
     * @param leftSide  {@code true} to edit the left document; {@code false} for the right
     * @param newLines  replacement line list (without line terminators)
     * @throws IllegalStateException if no comparison has been loaded
     */
    public void editDocument(boolean leftSide, List<String> newLines) {
        Objects.requireNonNull(newLines, "newLines");
        requireMergeManager();
        EditableDocument doc = leftSide ? leftDocument : rightDocument;
        doc.replaceLines(0, doc.lineCount(), newLines);
        DiffModel newDiff = textDiffEngine.diff(
                leftDocument.lines(), rightDocument.lines(), currentOptions);
        applyDiffModel(newDiff);
        leftDirty.set(leftDocument.isDirty());
        rightDirty.set(rightDocument.isDirty());
        canUndoProp.set(mergeManager.canUndo());
        canRedoProp.set(mergeManager.canRedo());
    }

    /**
     * Saves both documents and clears both dirty flags.
     *
     * @throws IllegalStateException if no comparison has been loaded
     */
    public void saveAll() {
        requireMergeManager();
        writeDocument(leftPath,  leftDocument);
        writeDocument(rightPath, rightDocument);
        leftDirty.set(false);
        rightDirty.set(false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Applies a new diff model and updates all derived properties. */
    private void applyDiffModel(DiffModel model) {
        diffModel.set(model);
        int count = model.differenceCount();
        differenceCount.set(count);
        // Clamp the current index into the new block range
        int idx = currentDifferenceIndex.get();
        if (count == 0) {
            currentDifferenceIndex.set(-1);
        } else if (idx >= count) {
            currentDifferenceIndex.set(count - 1);
        }
        updateNavFlags();
    }

    private void setCurrentDifferenceIndex(int idx) {
        currentDifferenceIndex.set(idx);
        updateNavFlags();
    }

    private void updateNavFlags() {
        int idx   = currentDifferenceIndex.get();
        int count = differenceCount.get();
        atFirstDifference.set(count == 0 || idx <= 0);
        atLastDifference.set(count == 0 || idx < 0 || idx >= count - 1);
    }

    /**
     * Re-syncs all mutable state from the merge manager after a merge / undo / redo.
     */
    private void afterMerge() {
        applyDiffModel(mergeManager.currentDiff());
        leftDirty.set(leftDocument.isDirty());
        rightDirty.set(rightDocument.isDirty());
        canUndoProp.set(mergeManager.canUndo());
        canRedoProp.set(mergeManager.canRedo());
    }

    /** Resets all per-comparison state. Called at the start of each new compare. */
    private void resetMergeState() {
        mergeManager   = null;
        leftDocument   = null;
        rightDocument  = null;
        leftPath       = null;
        rightPath      = null;
        currentOptions = null;
        diffModel.set(null);
        differenceCount.set(0);
        currentDifferenceIndex.set(-1);
        leftDirty.set(false);
        rightDirty.set(false);
        canUndoProp.set(false);
        canRedoProp.set(false);
        updateNavFlags();
    }

    private void requireMergeManager() {
        if (mergeManager == null) {
            throw new IllegalStateException(
                    "No comparison loaded. Call compare() before invoking merge commands.");
        }
    }

    /**
     * Writes {@code doc} to {@code path}, joining lines with the document's line-ending,
     * then marks the document clean.
     */
    private void writeDocument(Path path, EditableDocument doc) {
        String content = String.join(doc.lineEnding().separator(), doc.lines());
        fileIOService.write(path, content, doc.encoding(), doc.lineEnding());
        doc.markClean();
    }
}
