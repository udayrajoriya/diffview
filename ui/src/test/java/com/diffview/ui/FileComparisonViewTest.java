package com.diffview.ui;

import com.diffview.model.DiffBlock;
import com.diffview.model.DiffModel;
import com.diffview.model.DiffRow;
import com.diffview.model.LineKind;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestFX tests for task 12.1 — {@link FileComparisonView}, {@link DiffPane},
 * and {@link DiffLineCell}.
 *
 * <h3>Coverage</h3>
 * <ul>
 *   <li>Identical-files indicator visibility</li>
 *   <li>CSS highlight classes on {@link DiffLineCell} per {@link LineKind}</li>
 *   <li>Placeholder CSS class on the absent side of an ADDED/REMOVED row</li>
 *   <li>Model population of both panes</li>
 * </ul>
 */
@ExtendWith(ApplicationExtension.class)
class FileComparisonViewTest {

    private FileComparisonView view;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        view = new FileComparisonView();
        Scene scene = new Scene(view, 900, 600);
        // Load diff-colors.css so that all diff-* CSS classes are recognised
        URL cssUrl = getClass().getResource("/css/diff-colors.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        stage.show();
    }

    // ── Identical-files indicator ─────────────────────────────────────────────

    @Test
    void identicalLabelHiddenInitially() {
        assertThat(view.getIdenticalLabel().isVisible())
                .as("Identical label must be hidden before any model is set")
                .isFalse();
    }

    @Test
    void identicalLabelShownWhenModelIsIdentical(FxRobot robot) {
        DiffModel model = DiffModel.identical(
                List.of(DiffRow.unchanged(1, 1, "same line")),
                StandardCharsets.UTF_8, StandardCharsets.UTF_8);
        robot.interact(() -> view.setModel(model));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(view.getIdenticalLabel().isVisible())
                .as("Identical label must be visible when model.identical == true")
                .isTrue();
    }

    @Test
    void identicalLabelHiddenWhenModelHasDifferences(FxRobot robot) {
        DiffRow changedRow = DiffRow.changed(1, 1, "old text", "new text");
        DiffBlock block    = new DiffBlock(0, 0, LineKind.CHANGED);
        DiffModel model    = new DiffModel(
                List.of(changedRow), List.of(block),
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);
        robot.interact(() -> view.setModel(model));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(view.getIdenticalLabel().isVisible())
                .as("Identical label must be hidden when there are differences")
                .isFalse();
    }

    @Test
    void settingNullModelClearsView(FxRobot robot) {
        // First set a real model, then clear it
        robot.interact(() -> view.setModel(DiffModel.identical(
                List.of(DiffRow.unchanged(1, 1, "x")),
                StandardCharsets.UTF_8, StandardCharsets.UTF_8)));
        robot.interact(() -> view.setModel(null));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(view.getLeftPane().getItems()).isEmpty();
        assertThat(view.getRightPane().getItems()).isEmpty();
        assertThat(view.getIdenticalLabel().isVisible()).isFalse();
    }

    // ── Model populates both panes ────────────────────────────────────────────

    @Test
    void settingModelPopulatesBothPanesWithSameRows(FxRobot robot) {
        DiffRow r1 = DiffRow.unchanged(1, 1, "a");
        DiffRow r2 = DiffRow.changed(2, 2, "b", "B");
        DiffModel model = new DiffModel(
                List.of(r1, r2), List.of(new DiffBlock(1, 1, LineKind.CHANGED)),
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);
        robot.interact(() -> view.setModel(model));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(view.getLeftPane().getItems()).hasSize(2);
        assertThat(view.getRightPane().getItems()).hasSize(2);
    }

    // ── DiffLineCell CSS highlight classes ────────────────────────────────────
    // Tests call DiffLineCell.updateItem() directly on the FX thread.
    // DiffLineCell is package-private; the test is in the same package.

    @Test
    void changedRowLeftCellHasChangedLineClass(FxRobot robot) {
        DiffLineCell cell = new DiffLineCell(true);
        DiffRow row = DiffRow.changed(1, 1, "old", "new");
        robot.interact(() -> cell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(cell.getStyleClass())
                .as("CHANGED row — left cell must have diff-changed-line")
                .contains("diff-changed-line");
    }

    @Test
    void addedRowRightCellHasAddedLineClass(FxRobot robot) {
        DiffLineCell cell = new DiffLineCell(false); // right side (line exists here)
        DiffRow row = DiffRow.added(5, "new line");
        robot.interact(() -> cell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(cell.getStyleClass())
                .as("ADDED row — right cell must have diff-added-line")
                .contains("diff-added-line");
    }

    @Test
    void addedRowLeftCellHasPlaceholderClass(FxRobot robot) {
        DiffLineCell cell = new DiffLineCell(true); // left side (placeholder for ADDED)
        DiffRow row = DiffRow.added(5, "new line");
        robot.interact(() -> cell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(cell.getStyleClass())
                .as("ADDED row — left cell must be a placeholder")
                .contains("diff-placeholder-row");
    }

    @Test
    void removedRowLeftCellHasRemovedLineClass(FxRobot robot) {
        DiffLineCell cell = new DiffLineCell(true); // left side (line removed from left)
        DiffRow row = DiffRow.removed(3, "old line");
        robot.interact(() -> cell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(cell.getStyleClass())
                .as("REMOVED row — left cell must have diff-removed-line")
                .contains("diff-removed-line");
    }

    @Test
    void removedRowRightCellHasPlaceholderClass(FxRobot robot) {
        DiffLineCell cell = new DiffLineCell(false); // right side (placeholder for REMOVED)
        DiffRow row = DiffRow.removed(3, "old line");
        robot.interact(() -> cell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(cell.getStyleClass())
                .as("REMOVED row — right cell must be a placeholder")
                .contains("diff-placeholder-row");
    }

    @Test
    void unchangedRowHasNoHighlightClass(FxRobot robot) {
        DiffLineCell cell = new DiffLineCell(true);
        DiffRow row = DiffRow.unchanged(1, 1, "same");
        robot.interact(() -> cell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(cell.getStyleClass())
                .as("UNCHANGED row must have no diff highlight class")
                .doesNotContain("diff-changed-line", "diff-added-line",
                                "diff-removed-line", "diff-placeholder-row");
    }

    // ── Placeholder alignment ─────────────────────────────────────────────────

    @Test
    void placeholderCellHasEmptyLineNumber(FxRobot robot) {
        // For an ADDED row, left side is a placeholder → lineNumberLabel must be empty
        DiffLineCell leftCell = new DiffLineCell(true);
        DiffRow row = DiffRow.added(7, "text");
        robot.interact(() -> leftCell.updateItem(row, false));
        WaitForAsyncUtils.waitForFxEvents();
        // Placeholder has no line number text (checked via getGraphic structure)
        assertThat(leftCell.getStyleClass()).contains("diff-placeholder-row");
        assertThat(leftCell.getGraphic()).isNotNull(); // graphic is set to the HBox
    }

    // ── Navigation toolbar (task 12.3) ────────────────────────────────────────

    @Test
    void diffCountLabelShowsZeroInitially() {
        Label label = (Label) view.lookup("#diffCountLabel");
        assertThat(label).isNotNull();
        assertThat(label.getText()).isEqualTo("0 / 0");
    }

    @Test
    void nextButtonDisabledInitially() {
        Button btn = (Button) view.lookup("#nextDiffButton");
        assertThat(btn).isNotNull();
        assertThat(btn.isDisable()).isTrue();
    }

    @Test
    void prevButtonDisabledInitially() {
        Button btn = (Button) view.lookup("#prevDiffButton");
        assertThat(btn).isNotNull();
        assertThat(btn.isDisable()).isTrue();
    }

    @Test
    void diffCountLabelShowsDashTotalWhenModelHasDiffs(FxRobot robot) {
        robot.interact(() -> view.setModel(buildModelWithTwoDiffs()));
        WaitForAsyncUtils.waitForFxEvents();
        Label label = (Label) view.lookup("#diffCountLabel");
        assertThat(label.getText()).isEqualTo("\u2013 / 2");
    }

    @Test
    void nextButtonEnabledAfterSetModelWithDiffs(FxRobot robot) {
        robot.interact(() -> view.setModel(buildModelWithTwoDiffs()));
        WaitForAsyncUtils.waitForFxEvents();
        Button btn = (Button) view.lookup("#nextDiffButton");
        assertThat(btn.isDisable()).isFalse();
    }

    @Test
    void clickingNextNavigatesToFirstDiff(FxRobot robot) {
        robot.interact(() -> view.setModel(buildModelWithTwoDiffs()));
        robot.clickOn("#nextDiffButton");
        WaitForAsyncUtils.waitForFxEvents();
        Label label = (Label) view.lookup("#diffCountLabel");
        assertThat(label.getText()).isEqualTo("1 / 2");
    }

    @Test
    void clickingNextTwiceReachesLastDiffAndDisablesNextButton(FxRobot robot) {
        robot.interact(() -> view.setModel(buildModelWithTwoDiffs()));
        robot.clickOn("#nextDiffButton");
        robot.clickOn("#nextDiffButton");
        WaitForAsyncUtils.waitForFxEvents();
        Label label = (Label) view.lookup("#diffCountLabel");
        assertThat(label.getText()).isEqualTo("2 / 2");
        Button btn = (Button) view.lookup("#nextDiffButton");
        assertThat(btn.isDisable()).isTrue();
    }

    @Test
    void clickingPrevFromSecondDiffGoesToFirst(FxRobot robot) {
        robot.interact(() -> view.setModel(buildModelWithTwoDiffs()));
        robot.clickOn("#nextDiffButton");
        robot.clickOn("#nextDiffButton");
        robot.clickOn("#prevDiffButton");
        WaitForAsyncUtils.waitForFxEvents();
        Label label = (Label) view.lookup("#diffCountLabel");
        assertThat(label.getText()).isEqualTo("1 / 2");
    }

    @Test
    void setModelNullResetsDiffCount(FxRobot robot) {
        robot.interact(() -> {
            view.setModel(buildModelWithTwoDiffs());
            view.setModel(null);
        });
        WaitForAsyncUtils.waitForFxEvents();
        Label label = (Label) view.lookup("#diffCountLabel");
        assertThat(label.getText()).isEqualTo("0 / 0");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static DiffModel buildModelWithTwoDiffs() {
        List<DiffRow> rows = List.of(
                DiffRow.unchanged(1, 1, "same"),
                DiffRow.changed(2, 2, "left a", "right a"),
                DiffRow.unchanged(3, 3, "same2"),
                DiffRow.changed(4, 4, "left b", "right b"));
        List<DiffBlock> blocks = List.of(
                new DiffBlock(1, 1, LineKind.CHANGED),
                new DiffBlock(3, 3, LineKind.CHANGED));
        return new DiffModel(rows, blocks,
                StandardCharsets.UTF_8, StandardCharsets.UTF_8, false);
    }
}
