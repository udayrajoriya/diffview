package com.diffview.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestFX headless tests for task 11.1 — {@link SelectionBar} and {@link MainShell}.
 *
 * <p>Headless mode is configured via JVM args in {@code ui/build.gradle.kts}:
 * {@code -Dtestfx.headless=true -Dprism.order=sw …}
 */
@ExtendWith(ApplicationExtension.class)
class MainShellTest {

    private SelectionBar selectionBar;

    // ── @Start (runs on FX thread before each test) ───────────────────────────

    @Start
    void start(Stage stage) {
        selectionBar = new SelectionBar();
        stage.setScene(new Scene(selectionBar, 800, 120));
        stage.show();
    }
    @BeforeEach
    void ensureStableStylesheet(FxRobot robot) {
        // Reset to Modena so that each test starts with a fully functional CSS engine,
        // regardless of what ThemeManagerTest may have left behind.
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }
    // ── Compare button enable/disable ─────────────────────────────────────────

    @Test
    void compareButtonDisabledWhenBothFieldsEmpty() {
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(selectionBar.getCompareButton().isDisabled())
                .as("Compare button must be disabled when both fields are empty")
                .isTrue();
    }

    @Test
    void compareButtonEnabledWhenBothFieldsHaveText(FxRobot robot) {
        // Use setText() via interact() — more reliable than robot.write() for
        // strings containing backslashes or shift-key characters.
        robot.interact(() -> {
            selectionBar.getLeftField().setText("some-left-path");
            selectionBar.getRightField().setText("some-right-path");
        });
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(selectionBar.getCompareButton().isDisabled())
                .as("Compare button must be enabled when both fields are non-empty")
                .isFalse();
    }

    @Test
    void compareButtonDisabledWhenOnlyLeftFieldHasText(FxRobot robot) {
        robot.clickOn("#leftField").write("C:\\some\\left");
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(selectionBar.getCompareButton().isDisabled())
                .as("Compare button must stay disabled when only one field is filled")
                .isTrue();
    }

    // ── Type-mismatch messaging ───────────────────────────────────────────────

    /**
     * A file path on the left and a directory path on the right must produce a visible
     * type-mismatch error; the Compare callback must NOT be invoked.
     */
    @Test
    void typeMismatchShowsErrorAndBlocksCallback(@TempDir Path tempDir, FxRobot robot)
            throws IOException {
        Path file = Files.createFile(tempDir.resolve("sample.txt"));
        Path dir  = tempDir; // tempDir itself is a directory

        List<CompareRequest> fired = new ArrayList<>();
        robot.interact(() -> selectionBar.setOnCompare(fired::add));

        robot.interact(() -> {
            selectionBar.getLeftField().setText(file.toString());
            selectionBar.getRightField().setText(dir.toString());
        });
        robot.clickOn("#compareButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(selectionBar.getMessageLabel().isVisible())
                .as("Error label must be visible on type mismatch")
                .isTrue();
        assertThat(selectionBar.getMessageLabel().getText())
                .as("Error message must mention 'mismatch'")
                .containsIgnoringCase("mismatch");
        assertThat(fired)
                .as("onCompare callback must NOT fire on type mismatch")
                .isEmpty();
    }

    /**
     * A path that does not exist must produce an error message.
     */
    @Test
    void nonExistentPathShowsError(FxRobot robot) {
        robot.interact(() -> {
            selectionBar.getLeftField().setText("C:\\definitely\\does\\not\\exist\\a.txt");
            selectionBar.getRightField().setText("C:\\definitely\\does\\not\\exist\\b.txt");
        });
        robot.clickOn("#compareButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(selectionBar.getMessageLabel().isVisible())
                .as("Error label must be visible when path does not exist")
                .isTrue();
    }

    // ── Drag-and-drop ─────────────────────────────────────────────────────────

    /**
     * {@link SelectionBar#setPathFromDrop(boolean, String)} is the same code path
     * invoked by the OS drag-and-drop handler.  Calling it directly from the FX thread
     * exercises the drop logic without needing an OS-level drag gesture.
     */
    @Test
    void dropOnLeftFieldPopulatesLeftField(@TempDir Path tempDir, FxRobot robot) {
        Path droppedFile = tempDir.resolve("dragged.txt");
        robot.interact(() -> selectionBar.setPathFromDrop(true, droppedFile.toString()));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(selectionBar.getLeftField().getText())
                .as("Left field must show the dropped path")
                .isEqualTo(droppedFile.toString());
    }

    @Test
    void dropOnRightFieldPopulatesRightField(@TempDir Path tempDir, FxRobot robot) {
        robot.interact(() -> selectionBar.setPathFromDrop(false, tempDir.toString()));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(selectionBar.getRightField().getText())
                .as("Right field must show the dropped path")
                .isEqualTo(tempDir.toString());
    }

    // ── Valid comparison routing ──────────────────────────────────────────────

    @Test
    void validFilePairFiresCallbackWithFolderFalse(@TempDir Path tempDir, FxRobot robot)
            throws IOException {
        Path left  = Files.createFile(tempDir.resolve("left.txt"));
        Path right = Files.createFile(tempDir.resolve("right.txt"));

        List<CompareRequest> fired = new ArrayList<>();
        robot.interact(() -> selectionBar.setOnCompare(fired::add));

        robot.interact(() -> {
            selectionBar.getLeftField().setText(left.toString());
            selectionBar.getRightField().setText(right.toString());
        });
        robot.clickOn("#compareButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fired).hasSize(1);
        assertThat(fired.get(0).folder()).isFalse();
        assertThat(fired.get(0).left()).isEqualTo(left);
        assertThat(fired.get(0).right()).isEqualTo(right);
    }

    @Test
    void validFolderPairFiresCallbackWithFolderTrue(@TempDir Path tempDir, FxRobot robot)
            throws IOException {
        Path leftDir  = Files.createDirectory(tempDir.resolve("leftDir"));
        Path rightDir = Files.createDirectory(tempDir.resolve("rightDir"));

        List<CompareRequest> fired = new ArrayList<>();
        robot.interact(() -> selectionBar.setOnCompare(fired::add));

        robot.interact(() -> {
            selectionBar.getLeftField().setText(leftDir.toString());
            selectionBar.getRightField().setText(rightDir.toString());
        });
        robot.clickOn("#compareButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(fired).hasSize(1);
        assertThat(fired.get(0).folder()).isTrue();
    }
}
