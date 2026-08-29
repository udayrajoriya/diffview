package com.comparetool.ui;

import com.comparetool.core.diff.LineDiffEngine;
import com.comparetool.core.service.ComparisonService;
import com.comparetool.infra.concurrent.DirectTaskExecutor;
import com.comparetool.infra.io.FileIOService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.ui.CompareRequest;
import com.comparetool.model.DecodedText;
import com.comparetool.model.DiffBlock;
import com.comparetool.model.DiffModel;
import com.comparetool.model.DiffRow;
import com.comparetool.model.FileComparisonResult;
import com.comparetool.model.LineEnding;
import com.comparetool.model.LineKind;
import com.comparetool.viewmodel.FileComparisonViewModel;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TestFX tests for task 16.1 — keyboard operability and accessible naming (REQ-015).
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>F5 in {@link SelectionBar} triggers the compare action (REQ-015.1)</li>
 *   <li>Ctrl+S in {@link FileComparisonView} saves dirty documents (REQ-015.1)</li>
 *   <li>Ctrl+Right / Ctrl+Left copy the current diff block (REQ-015.1)</li>
 *   <li>{@link DiffLineCell} shows kind symbols (+, -, ~) as non-color cues (REQ-015.2)</li>
 *   <li>Interactive controls expose accessible names (REQ-015.3)</li>
 * </ul>
 *
 * <p><strong>Extension order</strong>: {@code MockitoExtension} must come before
 * {@code ApplicationExtension} to ensure {@code @Mock} fields are initialised
 * before the JavaFX stage is created.
 */
@ExtendWith({MockitoExtension.class, ApplicationExtension.class})
class KeyboardAccessibilityTest {

    private static final Path LEFT_PATH  = Path.of("/tmp/left.txt");
    private static final Path RIGHT_PATH = Path.of("/tmp/right.txt");

    @Mock ComparisonService comparisonService;
    @Mock FileIOService     fileIOService;

    private final DirectTaskExecutor executor = new DirectTaskExecutor();

    private FileComparisonViewModel vm;
    private FileComparisonView      fcView;
    private SelectionBar            selectionBar;

    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        vm          = new FileComparisonViewModel(comparisonService, new LineDiffEngine(),
                executor, fileIOService);
        fcView      = new FileComparisonView();
        fcView.bindViewModel(vm);

        selectionBar = new SelectionBar();

        // Use a simple VBox so both views are in the same scene
        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(selectionBar, fcView);
        javafx.scene.layout.VBox.setVgrow(fcView, javafx.scene.layout.Priority.ALWAYS);

        Scene scene = new Scene(root, 900, 700);
        stage.setScene(scene);
        stage.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Populates the mock service and calls vm.compare() to get a live 1-block diff. */
    private void runCompare(FxRobot robot) {
        DecodedText leftDecoded  = new DecodedText(
                List.of("same", "left-only"),
                StandardCharsets.UTF_8, false, LineEnding.LF);
        DecodedText rightDecoded = new DecodedText(
                List.of("same", "right-only"),
                StandardCharsets.UTF_8, false, LineEnding.LF);

        List<DiffRow>   rows   = List.of(
                DiffRow.unchanged(1, 1, "same"),
                DiffRow.changed(2, 2, "left-only", "right-only"));
        List<DiffBlock> blocks = List.of(new DiffBlock(1, 1, LineKind.CHANGED));
        DiffModel       model  = new DiffModel(rows, blocks,
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);
        FileComparisonResult result = new FileComparisonResult(model, LEFT_PATH, RIGHT_PATH);

        when(comparisonService.compareFiles(any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));
        when(fileIOService.read(any(), any()))
                .thenAnswer(inv -> {
                    Path p = inv.getArgument(0);
                    return p.equals(LEFT_PATH) ? leftDecoded : rightDecoded;
                });

        robot.interact(() -> vm.compare(LEFT_PATH, RIGHT_PATH, ComparisonOptions.defaults()));
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ── F5 shortcut (REQ-015.1 — compare) ────────────────────────────────────

    @Test
    void f5ShortcutTriggersCompare(FxRobot robot) {
        List<CompareRequest> captured = new ArrayList<>();
        selectionBar.setOnCompare(captured::add);

        robot.interact(() -> {
            selectionBar.getLeftField() .setText(System.getProperty("java.io.tmpdir"));
            selectionBar.getRightField().setText(System.getProperty("java.io.tmpdir"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.press(KeyCode.F5).release(KeyCode.F5);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(captured).as("F5 should fire compare when both fields are populated")
                .isNotEmpty();
    }

    @Test
    void f5DoesNothingWhenFieldsAreEmpty(FxRobot robot) {
        List<CompareRequest> captured = new ArrayList<>();
        selectionBar.setOnCompare(captured::add);

        // Fields are empty by default
        robot.press(KeyCode.F5).release(KeyCode.F5);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(captured).as("F5 should not fire compare when fields are empty")
                .isEmpty();
    }

    // ── Ctrl+S shortcut (REQ-015.1 — save) ───────────────────────────────────

    @Test
    void ctrlSShortcutSavesDirtyDocuments(FxRobot robot) {
        runCompare(robot);

        // Navigate to the diff block and copy left → right (makes right dirty)
        robot.press(KeyCode.F7).release(KeyCode.F7);
        WaitForAsyncUtils.waitForFxEvents();
        robot.press(KeyCode.CONTROL, KeyCode.RIGHT).release(KeyCode.RIGHT, KeyCode.CONTROL);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(vm.isRightDirty()).as("right should be dirty after copy").isTrue();

        robot.press(KeyCode.CONTROL, KeyCode.S).release(KeyCode.S, KeyCode.CONTROL);
        WaitForAsyncUtils.waitForFxEvents();

        // FileIOService.write should have been called for the right document
        verify(fileIOService, atLeastOnce()).write(any(), any(), any(), any());
    }

    // ── Ctrl+Right / Ctrl+Left (REQ-015.1 — merge direction shortcuts) ────────

    @Test
    void ctrlRightCopiesCurrentBlockLeftToRight(FxRobot robot) {
        runCompare(robot);

        // Navigate to the diff block first
        robot.press(KeyCode.F7).release(KeyCode.F7);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fcView.getNavigator().currentBlock())
                .as("a block should be selected after F7").isNotNull();

        robot.press(KeyCode.CONTROL, KeyCode.RIGHT).release(KeyCode.RIGHT, KeyCode.CONTROL);
        WaitForAsyncUtils.waitForFxEvents();

        // After copying, the diff count should drop to 0
        assertThat(vm.getDifferenceCount())
                .as("diff count should be 0 after copying the only block").isZero();
    }

    @Test
    void ctrlLeftCopiesCurrentBlockRightToLeft(FxRobot robot) {
        runCompare(robot);

        robot.press(KeyCode.F7).release(KeyCode.F7);
        WaitForAsyncUtils.waitForFxEvents();

        robot.press(KeyCode.CONTROL, KeyCode.LEFT).release(KeyCode.LEFT, KeyCode.CONTROL);
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(vm.getDifferenceCount())
                .as("diff count should be 0 after copying the only block R→L").isZero();
    }

    // ── Non-color kind-gutter symbols (REQ-015.2) ─────────────────────────────

    @Test
    void kindSymbolForChangedLineIsWavyDash() {
        assertThat(DiffLineCell.kindSymbol(LineKind.CHANGED)).isEqualTo("~");
    }

    @Test
    void kindSymbolForAddedLineIsPlus() {
        assertThat(DiffLineCell.kindSymbol(LineKind.ADDED)).isEqualTo("+");
    }

    @Test
    void kindSymbolForRemovedLineIsMinus() {
        assertThat(DiffLineCell.kindSymbol(LineKind.REMOVED)).isEqualTo("-");
    }

    @Test
    void kindSymbolForUnchangedLineIsEmpty() {
        assertThat(DiffLineCell.kindSymbol(LineKind.UNCHANGED)).isEmpty();
    }

    // ── Accessible names (REQ-015.3) ──────────────────────────────────────────

    @Test
    void compareButtonHasAccessibleName() {
        assertThat(selectionBar.getCompareButton().getAccessibleText())
                .as("compare button accessible text").isNotBlank();
    }

    @Test
    void leftBrowseButtonHasAccessibleName(FxRobot robot) {
        javafx.scene.control.Button leftBrowse =
                (javafx.scene.control.Button) selectionBar.lookup("#leftBrowse");
        assertThat(leftBrowse.getAccessibleText())
                .as("left browse button accessible text").isNotBlank();
    }

    @Test
    void rightBrowseButtonHasAccessibleName(FxRobot robot) {
        javafx.scene.control.Button rightBrowse =
                (javafx.scene.control.Button) selectionBar.lookup("#rightBrowse");
        assertThat(rightBrowse.getAccessibleText())
                .as("right browse button accessible text").isNotBlank();
    }

    @Test
    void mergeButtonsHaveAccessibleNames() {
        assertThat(fcView.lookup("#copyBlockLToRButton"))
                .isInstanceOfSatisfying(javafx.scene.control.Button.class,
                        b -> assertThat(b.getAccessibleText()).isNotBlank());
        assertThat(fcView.lookup("#copyAllLToRButton"))
                .isInstanceOfSatisfying(javafx.scene.control.Button.class,
                        b -> assertThat(b.getAccessibleText()).isNotBlank());
        assertThat(fcView.lookup("#saveAllButton"))
                .isInstanceOfSatisfying(javafx.scene.control.Button.class,
                        b -> assertThat(b.getAccessibleText()).isNotBlank());
    }

    @Test
    void navButtonsHaveAccessibleNames() {
        assertThat(fcView.lookup("#prevDiffButton"))
                .isInstanceOfSatisfying(javafx.scene.control.Button.class,
                        b -> assertThat(b.getAccessibleText()).isNotBlank());
        assertThat(fcView.lookup("#nextDiffButton"))
                .isInstanceOfSatisfying(javafx.scene.control.Button.class,
                        b -> assertThat(b.getAccessibleText()).isNotBlank());
    }
}
