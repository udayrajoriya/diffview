package com.diffview.ui;

import com.diffview.model.DiffRow;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * TestFX tests for task 12.2 — {@link ScrollSyncManager} and the
 * sync-scroll toggle in {@link FileComparisonView}.
 *
 * <h3>Strategy</h3>
 * <p>Tests use two raw {@link ListView}{@code <DiffRow>}s (instead of going
 * through {@link FileComparisonView}) so the {@link ScrollSyncManager} can be
 * verified in isolation.  The separate {@code syncToggle*} tests verify the
 * wiring inside {@link FileComparisonView}.
 */
@ExtendWith(ApplicationExtension.class)
class ScrollSyncManagerTest {

    // ── Shared state set up in @Start ──────────────────────────────────────────
    private ListView<DiffRow> leftList;
    private ListView<DiffRow> rightList;
    private ScrollSyncManager manager;
    private FileComparisonView fcView;  // for toggle-button tests

    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        // Two ListViews wide enough that scrollbars appear with >20 rows
        leftList  = new ListView<>();
        rightList = new ListView<>();
        leftList.setId("leftList");
        rightList.setId("rightList");
        leftList.setFixedCellSize(DiffPane.CELL_HEIGHT);
        rightList.setFixedCellSize(DiffPane.CELL_HEIGHT);

        ObservableList<DiffRow> items = buildRows(60);
        leftList.setItems(items);
        rightList.setItems(FXCollections.observableArrayList(items));

        HBox.setHgrow(leftList,  Priority.ALWAYS);
        HBox.setHgrow(rightList, Priority.ALWAYS);
        HBox box = new HBox(leftList, rightList);

        manager = new ScrollSyncManager(leftList, rightList);

        // FileComparisonView in a separate hidden scene for toggle tests
        fcView = new FileComparisonView();
        fcView.setModel(buildDiffModel(60));

        stage.setScene(new Scene(box, 800, 400));
        stage.show();
    }

    // ── Manager initialization ────────────────────────────────────────────────

    @Test
    void managerIsReadyAfterSceneIsShown(FxRobot robot) {
        waitForManager(robot);
        assertThat(manager.isReady())
                .as("ScrollSyncManager must be ready after the scene is shown")
                .isTrue();
    }

    @Test
    void isSyncedByDefault() {
        assertThat(manager.isSynced())
                .as("Scroll sync must be enabled by default")
                .isTrue();
    }

    // ── Synchronized scrolling ────────────────────────────────────────────────

    @Test
    void scrollingLeftPaneSyncsRightPaneWhenEnabled(FxRobot robot) {
        waitForManager(robot);

        robot.interact(() -> manager.getLeftScrollBar().setValue(0.6));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(manager.getRightScrollBar().getValue())
                .as("Right scrollbar must match left when sync is enabled")
                .isCloseTo(0.6, within(0.01));
    }

    @Test
    void scrollingRightPaneSyncsLeftPaneWhenEnabled(FxRobot robot) {
        waitForManager(robot);

        robot.interact(() -> manager.getRightScrollBar().setValue(0.3));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(manager.getLeftScrollBar().getValue())
                .as("Left scrollbar must match right when sync is enabled")
                .isCloseTo(0.3, within(0.01));
    }

    // ── Toggle disables synchronization ──────────────────────────────────────

    @Test
    void disablingSyncAllowsIndependentScroll(FxRobot robot) {
        waitForManager(robot);

        // Reset both scrollbars to 0
        robot.interact(() -> {
            manager.getLeftScrollBar().setValue(0.0);
            manager.getRightScrollBar().setValue(0.0);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Disable sync
        robot.interact(() -> manager.setSynced(false));
        WaitForAsyncUtils.waitForFxEvents();

        // Scroll left only
        robot.interact(() -> manager.getLeftScrollBar().setValue(0.7));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(manager.getRightScrollBar().getValue())
                .as("Right scrollbar must NOT move when sync is disabled")
                .isCloseTo(0.0, within(0.01));
    }

    @Test
    void reenablingSyncRestoresBidirectionalBinding(FxRobot robot) {
        waitForManager(robot);

        // Disable sync, move left independently
        robot.interact(() -> {
            manager.getLeftScrollBar().setValue(0.0);
            manager.getRightScrollBar().setValue(0.0);
            manager.setSynced(false);
            manager.getLeftScrollBar().setValue(0.5);
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Re-enable sync: now scrolling left must move right
        robot.interact(() -> {
            manager.setSynced(true);
            manager.getLeftScrollBar().setValue(0.4);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(manager.getRightScrollBar().getValue())
                .as("Right scrollbar must follow left after re-enabling sync")
                .isCloseTo(0.4, within(0.01));
    }

    // ── Sync toggle button in FileComparisonView ──────────────────────────────

    @Test
    void syncScrollButtonExistsAndIsSelectedByDefault(FxRobot robot) {
        ToggleButton btn = fcView.getSyncScrollButton();
        assertThat(btn).isNotNull();
        assertThat(btn.isSelected())
                .as("Sync scroll toggle must be ON by default")
                .isTrue();
    }

    @Test
    void syncScrollButtonHasCorrectId() {
        assertThat(fcView.getSyncScrollButton().getId())
                .isEqualTo("syncScrollButton");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Waits for the skin to be applied and the {@link ScrollSyncManager} to
     * find both scrollbars.  Uses repeated FX-event flushes since the
     * manager uses {@code Platform.runLater} internally.
     */
    private void waitForManager(FxRobot robot) {
        // Two pulses: (1) layout + skin, (2) Platform.runLater in initScrollBars
        WaitForAsyncUtils.waitForFxEvents();
        WaitForAsyncUtils.waitForFxEvents();
    }

    private static ObservableList<DiffRow> buildRows(int count) {
        List<DiffRow> rows = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            rows.add(DiffRow.unchanged(i, i, "line " + i));
        }
        return FXCollections.observableArrayList(rows);
    }

    private static com.diffview.model.DiffModel buildDiffModel(int count) {
        List<DiffRow> rows = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            rows.add(DiffRow.unchanged(i, i, "line " + i));
        }
        return com.diffview.model.DiffModel.identical(
                rows,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.charset.StandardCharsets.UTF_8);
    }
}
