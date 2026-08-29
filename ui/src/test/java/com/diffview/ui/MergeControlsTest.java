package com.diffview.ui;

import com.diffview.core.diff.LineDiffEngine;
import com.diffview.core.diff.TextDiffEngine;
import com.diffview.core.service.ComparisonService;
import com.diffview.infra.concurrent.DirectTaskExecutor;
import com.diffview.infra.io.FileIOService;
import com.diffview.model.ComparisonOptions;
import com.diffview.model.DecodedText;
import com.diffview.model.DiffBlock;
import com.diffview.model.DiffModel;
import com.diffview.model.DiffRow;
import com.diffview.model.FileComparisonResult;
import com.diffview.model.LineEnding;
import com.diffview.model.LineKind;
import com.diffview.viewmodel.FileComparisonViewModel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestFX integration tests for task 12.4 — merge controls in {@link FileComparisonView}.
 *
 * <h3>Test coverage</h3>
 * <ul>
 *   <li>Block copy (left→right) updates the view and decreases the diff count</li>
 *   <li>Copy-all sets diff count to zero</li>
 *   <li>Undo/redo restore previous state</li>
 *   <li>Edit mode: typing in the TextArea and applying re-diffs the view</li>
 *   <li>Save Left calls {@link FileIOService#write}</li>
 *   <li>Unsaved indicator appears after a merge</li>
 *   <li>{@link FileComparisonView#hasUnsavedChanges()} reflects dirty state</li>
 *   <li>Merge buttons disabled before ViewModel is bound</li>
 * </ul>
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
class MergeControlsTest {

    private static final Path LEFT_PATH  = Path.of("/tmp/left.txt");
    private static final Path RIGHT_PATH = Path.of("/tmp/right.txt");

    // Injected by MockitoExtension
    @Mock ComparisonService comparisonService;
    @Mock FileIOService     fileIOService;

    private final TextDiffEngine     diffEngine = new LineDiffEngine();
    private final DirectTaskExecutor executor   = new DirectTaskExecutor();

    private FileComparisonViewModel vm;
    private FileComparisonView      view;

    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        vm   = new FileComparisonViewModel(comparisonService, diffEngine, executor, fileIOService);
        view = new FileComparisonView();
        view.bindViewModel(vm);

        Scene scene = new Scene(view, 900, 600);
        stage.setScene(scene);
        stage.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Sets up mock responses and calls {@code vm.compare()} so tests start with
     * a live comparison: left="line1\ndifferent", right="line1\nchanged".
     * That produces 1 diff block (CHANGED at row index 1).
     */
    private void runCompare(FxRobot robot) {
        DecodedText leftDecoded  = new DecodedText(
                List.of("line1", "different"),
                StandardCharsets.UTF_8, false, LineEnding.LF);
        DecodedText rightDecoded = new DecodedText(
                List.of("line1", "changed"),
                StandardCharsets.UTF_8, false, LineEnding.LF);

        // Build the DiffModel the ComparisonService would return
        List<DiffRow>  rows   = List.of(
                DiffRow.unchanged(1, 1, "line1"),
                DiffRow.changed(2, 2, "different", "changed"));
        List<DiffBlock> blocks = List.of(new DiffBlock(1, 1, LineKind.CHANGED));
        DiffModel model = new DiffModel(rows, blocks,
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);

        FileComparisonResult result = new FileComparisonResult(model, LEFT_PATH, RIGHT_PATH);

        when(comparisonService.compareFiles(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(fileIOService.read(any(), any()))
                .thenAnswer(inv -> {
                    Path p = inv.getArgument(0);
                    return p.equals(LEFT_PATH) ? leftDecoded : rightDecoded;
                });

        robot.interact(() ->
                vm.compare(LEFT_PATH, RIGHT_PATH, ComparisonOptions.defaults()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    void mergeButtonsDisabledBeforeViewModelBound() {
        // A second view without a ViewModel bound
        FileComparisonView unbound = new FileComparisonView();
        assertThat(((Button) unbound.lookup("#copyBlockLToRButton")).isDisable()).isTrue();
        assertThat(((Button) unbound.lookup("#undoButton")).isDisable()).isTrue();
        assertThat(((Button) unbound.lookup("#saveLeftButton")).isDisable()).isTrue();
    }

    @Test
    void hasNoUnsavedChangesInitially() {
        assertThat(view.hasUnsavedChanges()).isFalse();
    }

    // ── Block copy ────────────────────────────────────────────────────────────

    @Test
    void copyBlockLToRDecreasesBlockCount(FxRobot robot) {
        runCompare(robot);

        // Navigate to the one diff block
        robot.clickOn("#nextDiffButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Copy left → right
        robot.clickOn("#copyBlockLToRButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Diff count should be 0
        Label counter = (Label) view.lookup("#diffCountLabel");
        assertThat(counter.getText()).isEqualTo("0 / 0");
    }

    @Test
    void copyBlockMakesRightSideDirty(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#nextDiffButton");
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#copyBlockLToRButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Right document was modified → right dirty
        assertThat(vm.isRightDirty()).isTrue();
        assertThat(view.hasUnsavedChanges()).isTrue();
    }

    // ── Copy all ─────────────────────────────────────────────────────────────

    @Test
    void copyAllLToRReducesDiffCountToZero(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();

        Label counter = (Label) view.lookup("#diffCountLabel");
        assertThat(counter.getText()).isEqualTo("0 / 0");
    }

    // ── Undo / redo ───────────────────────────────────────────────────────────

    @Test
    void undoRestoresDiffCount(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#undoButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Diff should be back to 1
        assertThat(vm.getDifferenceCount()).isEqualTo(1);
    }

    @Test
    void undoButtonDisabledWhenNothingToUndo(FxRobot robot) {
        runCompare(robot);
        Button btn = (Button) view.lookup("#undoButton");
        assertThat(btn.isDisable()).isTrue();
    }

    @Test
    void undoButtonEnabledAfterMerge(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();

        Button btn = (Button) view.lookup("#undoButton");
        assertThat(btn.isDisable()).isFalse();
    }

    @Test
    void redoButtonEnabledAfterUndo(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#undoButton");
        WaitForAsyncUtils.waitForFxEvents();

        Button btn = (Button) view.lookup("#redoButton");
        assertThat(btn.isDisable()).isFalse();
    }

    // ── Unsaved indicator ─────────────────────────────────────────────────────

    @Test
    void unsavedIndicatorAppearsInRightPaneTitleAfterCopy(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();

        Label rightTitle = (Label) view.lookup("#rightPaneTitle");
        assertThat(rightTitle.getText()).contains("\u25cf");  // ●
    }

    @Test
    void unsavedIndicatorClearedAfterSave(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#saveRightButton");
        WaitForAsyncUtils.waitForFxEvents();

        Label rightTitle = (Label) view.lookup("#rightPaneTitle");
        assertThat(rightTitle.getText()).doesNotContain("\u25cf");
        assertThat(vm.isRightDirty()).isFalse();
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    @Test
    void saveRightCallsFileIOServiceWrite(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();
        robot.clickOn("#saveRightButton");
        WaitForAsyncUtils.waitForFxEvents();

        verify(fileIOService, atLeastOnce()).write(
                org.mockito.ArgumentMatchers.eq(RIGHT_PATH),
                any(), any(), any());
    }

    // ── Edit mode (re-diffs live) ─────────────────────────────────────────────

    @Test
    void editToggleShowsTextArea(FxRobot robot) {
        runCompare(robot);
        ToggleButton editBtn = (ToggleButton) view.lookup("#editToggleButton");
        assertThat(editBtn.isDisable()).isFalse();

        robot.clickOn("#editToggleButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(view.getLeftPane().isEditMode()).isTrue();
    }

    @Test
    void editingLeftAndApplyingRediffs(FxRobot robot) {
        runCompare(robot);

        // Enter edit mode
        robot.clickOn("#editToggleButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Make left identical to right by editing its TextArea
        robot.interact(() ->
                view.getLeftPane().getEditArea().setText("line1\nchanged"));
        WaitForAsyncUtils.waitForFxEvents();

        // Exit edit mode — triggers editDocument() → re-diff
        robot.clickOn("#editToggleButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Files are now identical → diff count = 0
        assertThat(vm.getDifferenceCount()).isZero();
    }

    // ── hasUnsavedChanges ──────────────────────────────────────────────────────

    @Test
    void hasUnsavedChangesTrueAfterMerge(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(view.hasUnsavedChanges()).isTrue();
    }

    @Test
    void hasUnsavedChangesFalseAfterSaveAll(FxRobot robot) {
        runCompare(robot);
        robot.clickOn("#copyAllLToRButton");
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> vm.saveAll());
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(view.hasUnsavedChanges()).isFalse();
    }
}
