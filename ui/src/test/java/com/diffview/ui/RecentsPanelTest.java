package com.diffview.ui;

import com.diffview.model.RecentComparison;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestFX tests for task 14.1 — {@link RecentsPanel}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>REQ-014.2: recent comparisons are displayed</li>
 *   <li>REQ-014.3: clicking an available entry fires the open callback</li>
 *   <li>REQ-014.4: unavailable paths are shown without crashing and cannot be opened</li>
 * </ul>
 */
@ExtendWith(ApplicationExtension.class)
class RecentsPanelTest {

    private RecentsPanel panel;

    @BeforeEach
    void ensureModena(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        panel = new RecentsPanel();
        stage.setScene(new Scene(panel, 500, 250));
        stage.show();
    }

    // ── Control presence ──────────────────────────────────────────────────────

    @Test
    void recentsListFoundById(FxRobot robot) {
        assertThat(panel.lookup("#recentsList")).isNotNull();
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @Test
    void emptyRecentsListHasZeroItems(FxRobot robot) {
        robot.interact(() -> panel.setRecents(List.of()));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> assertThat(panel.getListView().getItems()).isEmpty());
    }

    // ── Populated state ───────────────────────────────────────────────────────

    @Test
    void twoRecentsProduceTwoListItems(FxRobot robot) {
        RecentComparison r1 = RecentComparison.of(
                Path.of("/a/left.txt"), Path.of("/a/right.txt"), false);
        RecentComparison r2 = RecentComparison.of(
                Path.of("/b/dirA"), Path.of("/b/dirB"), true);
        robot.interact(() -> panel.setRecents(List.of(r1, r2)));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> assertThat(panel.getListView().getItems()).hasSize(2));
    }

    @Test
    void listItemsAreOrderedAsSupplied(FxRobot robot) {
        RecentComparison first  = RecentComparison.of(
                Path.of("/first/l"), Path.of("/first/r"), false);
        RecentComparison second = RecentComparison.of(
                Path.of("/second/l"), Path.of("/second/r"), false);
        robot.interact(() -> panel.setRecents(List.of(first, second)));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> {
            assertThat(panel.getListView().getItems().get(0)).isEqualTo(first);
            assertThat(panel.getListView().getItems().get(1)).isEqualTo(second);
        });
    }

    // ── Available entry — open callback (REQ-014.3) ───────────────────────────

    @Test
    void openSelectedRecentFiresCallbackForAvailableEntry(
            @TempDir Path tmpDir, FxRobot robot) throws IOException {

        Path left  = tmpDir.resolve("left.txt");
        Path right = tmpDir.resolve("right.txt");
        Files.writeString(left, "L");
        Files.writeString(right, "R");
        RecentComparison recent = RecentComparison.of(left, right, false);

        List<RecentComparison> opened = new ArrayList<>();
        robot.interact(() -> {
            panel.setRecents(List.of(recent));
            panel.setAvailabilityChecker(r -> true); // force available
            panel.setOnOpenRecent(opened::add);
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> {
            panel.getListView().getSelectionModel().select(0);
            panel.openSelectedRecent();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(opened).hasSize(1);
        assertThat(opened.get(0)).isEqualTo(recent);
    }

    @Test
    void setRecentsUpdatesExistingList(FxRobot robot) {
        RecentComparison first = RecentComparison.of(
                Path.of("/a/l"), Path.of("/a/r"), false);
        robot.interact(() -> panel.setRecents(List.of(first)));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> assertThat(panel.getListView().getItems()).hasSize(1));

        // Replace with a different list
        RecentComparison second = RecentComparison.of(
                Path.of("/b/l"), Path.of("/b/r"), true);
        robot.interact(() -> panel.setRecents(List.of(second, first)));
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() -> assertThat(panel.getListView().getItems()).hasSize(2));
    }

    // ── Unavailable entry (REQ-014.4) ─────────────────────────────────────────

    @Test
    void unavailableEntryDoesNotCrash(FxRobot robot) {
        // Paths do not exist on disk
        RecentComparison missing = new RecentComparison(
                Path.of("/no/such/left.txt"),
                Path.of("/no/such/right.txt"),
                false,
                Instant.now());

        // Must not throw
        robot.interact(() -> {
            panel.setRecents(List.of(missing));
            // Use the real default checker (file doesn't exist → unavailable)
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> assertThat(panel.getListView().getItems()).hasSize(1));
    }

    @Test
    void openSelectedDoesNotFireCallbackForUnavailableEntry(FxRobot robot) {
        RecentComparison missing = new RecentComparison(
                Path.of("/no/such/left.txt"),
                Path.of("/no/such/right.txt"),
                false,
                Instant.now());

        List<RecentComparison> opened = new ArrayList<>();
        robot.interact(() -> {
            panel.setRecents(List.of(missing));
            panel.setAvailabilityChecker(r -> false); // mark as unavailable
            panel.setOnOpenRecent(opened::add);
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> {
            panel.getListView().getSelectionModel().select(0);
            panel.openSelectedRecent();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(opened)
                .as("Callback must NOT fire for unavailable entries")
                .isEmpty();
    }

    @Test
    void customAvailabilityCheckerIsUsed(@TempDir Path tmpDir, FxRobot robot)
            throws IOException {

        // Create real files so default checker would say "available"
        Path left  = tmpDir.resolve("l.txt");
        Path right = tmpDir.resolve("r.txt");
        Files.writeString(left, "l");
        Files.writeString(right, "r");
        RecentComparison recent = RecentComparison.of(left, right, false);

        List<RecentComparison> opened = new ArrayList<>();
        robot.interact(() -> {
            panel.setRecents(List.of(recent));
            panel.setAvailabilityChecker(r -> false); // override: force unavailable
            panel.setOnOpenRecent(opened::add);
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> {
            panel.getListView().getSelectionModel().select(0);
            panel.openSelectedRecent();
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(opened)
                .as("Custom checker returning false must block the callback")
                .isEmpty();
    }

    @Test
    void openSelectedWithNoSelectionDoesNotThrow(FxRobot robot) {
        robot.interact(() -> {
            panel.setRecents(List.of());
            panel.setOnOpenRecent(r -> { throw new AssertionError("must not fire"); });
            panel.openSelectedRecent(); // no item selected — must be a no-op
        });
    }
}
