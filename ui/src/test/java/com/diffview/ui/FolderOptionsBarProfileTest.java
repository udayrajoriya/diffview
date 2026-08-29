package com.diffview.ui;

import com.diffview.infra.persist.FilterProfile;
import com.diffview.infra.persist.JacksonFilterProfileRepository;
import com.diffview.model.FileMatchMode;
import com.diffview.model.FolderComparisonOptions;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestFX tests for task 14.1 — profile export / import in {@link FolderOptionsBar}
 * (REQ-014.5, REQ-011.7).
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Export and import button IDs are present in the scene.</li>
 *   <li>Clicking Export fires the callback with the current profile.</li>
 *   <li>Clicking Import with a pre-exported file restores the mask fields.</li>
 * </ul>
 */
@ExtendWith(ApplicationExtension.class)
class FolderOptionsBarProfileTest {

    private FolderOptionsBar bar;

    @BeforeEach
    void ensureModena(FxRobot robot) {
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Start
    void start(Stage stage) {
        bar = new FolderOptionsBar();
        stage.setScene(new Scene(bar, 900, 60));
        stage.show();
    }

    // ── Control presence ──────────────────────────────────────────────────────

    @Test
    void exportProfileButtonFoundById(FxRobot robot) {
        assertThat(bar.lookup("#exportProfileButton")).isNotNull();
    }

    @Test
    void importProfileButtonFoundById(FxRobot robot) {
        assertThat(bar.lookup("#importProfileButton")).isNotNull();
    }

    // ── Export callback (REQ-014.5) ───────────────────────────────────────────

    @Test
    void exportCallbackReceivesCurrentMasks(FxRobot robot) {
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("*.java, *.kt");
            bar.getExcludeMasksField().setText("*.class");
        });
        WaitForAsyncUtils.waitForFxEvents();

        List<FilterProfile> exported = new ArrayList<>();
        robot.interact(() -> bar.setOnExportProfile(exported::add));

        robot.clickOn("#exportProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).includeMasks()).containsExactlyInAnyOrder("*.java", "*.kt");
        assertThat(exported.get(0).excludeMasks()).containsExactly("*.class");
    }

    @Test
    void exportCallbackNotFiredWhenHandlerIsNull(FxRobot robot) {
        robot.interact(() -> bar.setOnExportProfile(null));
        // Must not throw
        robot.clickOn("#exportProfileButton");
        WaitForAsyncUtils.waitForFxEvents();
    }

    // ── Import round-trip (REQ-014.5) ─────────────────────────────────────────

    @Test
    void exportThenImportRestoresIncludeMasks(@TempDir Path tmpDir, FxRobot robot)
            throws IOException {

        JacksonFilterProfileRepository repo = new JacksonFilterProfileRepository();
        Path profileFile = tmpDir.resolve("profile.json");

        // Set masks and export
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("*.java");
            bar.getExcludeMasksField().setText("*.class");
            bar.setOnExportProfile(p -> repo.export(p, profileFile));
        });
        robot.clickOn("#exportProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        // Clear the fields
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("");
            bar.getExcludeMasksField().setText("");
        });
        WaitForAsyncUtils.waitForFxEvents();

        // Import and verify
        robot.interact(() ->
            bar.setImportProfileSupplier(() -> Optional.of(repo.importProfile(profileFile)))
        );
        robot.clickOn("#importProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
            assertThat(bar.getIncludeMasksField().getText()).contains("*.java")
        );
    }

    @Test
    void exportThenImportRestoresExcludeMasks(@TempDir Path tmpDir, FxRobot robot) {
        JacksonFilterProfileRepository repo = new JacksonFilterProfileRepository();
        Path profileFile = tmpDir.resolve("profile.json");

        robot.interact(() -> {
            bar.getIncludeMasksField().setText("*.txt");
            bar.getExcludeMasksField().setText("build, .git");
            bar.setOnExportProfile(p -> repo.export(p, profileFile));
        });
        robot.clickOn("#exportProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> bar.getExcludeMasksField().setText(""));
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
            bar.setImportProfileSupplier(() -> Optional.of(repo.importProfile(profileFile)))
        );
        robot.clickOn("#importProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
            assertThat(bar.getExcludeMasksField().getText())
                    .containsIgnoringCase("build")
        );
    }

    @Test
    void importWithEmptyOptionalDoesNotChangeMasks(FxRobot robot) {
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("*.java");
            bar.setImportProfileSupplier(Optional::empty);
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.clickOn("#importProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
            assertThat(bar.getIncludeMasksField().getText()).isEqualTo("*.java")
        );
    }

    @Test
    void importNotFiredWhenSupplierIsNull(FxRobot robot) {
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("*.java");
            bar.setImportProfileSupplier(null);
        });
        // Must not throw
        robot.clickOn("#importProfileButton");
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() ->
            assertThat(bar.getIncludeMasksField().getText()).isEqualTo("*.java")
        );
    }

    // ── buildFilterProfile helper ─────────────────────────────────────────────

    @Test
    void buildFilterProfileReflectsCurrentFields(FxRobot robot) {
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("*.java, *.groovy");
            bar.getExcludeMasksField().setText("*Test.java");
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> {
            FilterProfile p = bar.buildFilterProfile();
            assertThat(p.includeMasks()).containsExactlyInAnyOrder("*.java", "*.groovy");
            assertThat(p.excludeMasks()).containsExactly("*Test.java");
        });
    }

    @Test
    void buildFilterProfileWithEmptyFieldsProducesEmptyLists(FxRobot robot) {
        robot.interact(() -> {
            bar.getIncludeMasksField().setText("");
            bar.getExcludeMasksField().setText("");
        });
        WaitForAsyncUtils.waitForFxEvents();

        robot.interact(() -> {
            FilterProfile p = bar.buildFilterProfile();
            assertThat(p.includeMasks()).isEmpty();
            assertThat(p.excludeMasks()).isEmpty();
        });
    }

    // ── Match mode + tolerance preserved after options round-trip ─────────────

    @Test
    void setBaseOptionsPopulatesMatchModeCombo(FxRobot robot) {
        robot.interact(() ->
            bar.setBaseOptions(FolderComparisonOptions.defaults().withMatchMode(FileMatchMode.CONTENT))
        );
        WaitForAsyncUtils.waitForFxEvents();
        robot.interact(() ->
            assertThat(bar.getMatchModeCombo().getValue()).isEqualTo(FileMatchMode.CONTENT)
        );
    }
}
