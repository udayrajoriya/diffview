package com.diffview.ui;

import com.diffview.model.HighlightColors;
import com.diffview.model.ThemeMode;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TestFX tests for task 11.2 — {@link ThemeManager} theme switching and
 * highlight-color CSS variable injection.
 */
@ExtendWith(ApplicationExtension.class)
class ThemeManagerTest {

    private Scene scene;
    private Pane  root;

    @Start
    void start(Stage stage) {
        root  = new Pane();
        scene = new Scene(root, 200, 100);
        stage.setScene(scene);
        stage.show();
    }

    @AfterEach
    void resetGlobalStylesheet(FxRobot robot) {
        // Restore to Modena (not null) so subsequent test classes see a fully-loaded
        // CSS engine rather than an un-initialised stylesheet state.
        robot.interact(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    // ── Theme stylesheet selection ────────────────────────────────────────────

    @Test
    void sheetForLightContainsLightThemeName() {
        String sheet = ThemeManager.sheetFor(ThemeMode.LIGHT);
        assertThat(sheet)
                .as("LIGHT sheet URL must reference the light variant")
                .isNotNull()
                .containsIgnoringCase("primer-light");
    }

    @Test
    void sheetForDarkContainsDarkThemeName() {
        String sheet = ThemeManager.sheetFor(ThemeMode.DARK);
        assertThat(sheet)
                .as("DARK sheet URL must reference the dark variant")
                .isNotNull()
                .containsIgnoringCase("primer-dark");
    }

    @Test
    void lightAndDarkStylesheetsAreDifferent() {
        assertThat(ThemeManager.sheetFor(ThemeMode.LIGHT))
                .as("LIGHT and DARK stylesheets must differ")
                .isNotEqualTo(ThemeManager.sheetFor(ThemeMode.DARK));
    }

    @Test
    void systemSheetIsNonNull() {
        // SYSTEM resolves to either LIGHT or DARK depending on OS preference
        assertThat(ThemeManager.sheetFor(ThemeMode.SYSTEM))
                .as("SYSTEM theme must resolve to a non-null sheet")
                .isNotNull();
    }

    // ── applyTheme changes the global user-agent stylesheet ───────────────────

    @Test
    void applyLightThemeSetsLightStylesheet(FxRobot robot) {
        robot.interact(() -> ThemeManager.applyTheme(ThemeMode.LIGHT));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(Application.getUserAgentStylesheet())
                .as("Applying LIGHT must set the light AtlantaFX stylesheet")
                .isEqualTo(ThemeManager.sheetFor(ThemeMode.LIGHT));
    }

    @Test
    void applyDarkThemeSetsDarkStylesheet(FxRobot robot) {
        robot.interact(() -> ThemeManager.applyTheme(ThemeMode.DARK));
        WaitForAsyncUtils.waitForFxEvents();
        assertThat(Application.getUserAgentStylesheet())
                .as("Applying DARK must set the dark AtlantaFX stylesheet")
                .isEqualTo(ThemeManager.sheetFor(ThemeMode.DARK));
    }

    @Test
    void switchingFromLightToDarkChangesStylesheet(FxRobot robot) {
        robot.interact(() -> ThemeManager.applyTheme(ThemeMode.LIGHT));
        String lightSheet = Application.getUserAgentStylesheet();

        robot.interact(() -> ThemeManager.applyTheme(ThemeMode.DARK));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(Application.getUserAgentStylesheet())
                .as("Switching to DARK must change the active stylesheet")
                .isNotEqualTo(lightSheet);
    }

    // ── colorStyle produces correct CSS variable string ───────────────────────

    @Test
    void colorStyleContainsAllThreeVariables() {
        HighlightColors colors = new HighlightColors("#aabbcc", "#112233", "#998877");
        String style = ThemeManager.colorStyle(colors);

        assertThat(style).contains("-comp-changed: #aabbcc");
        assertThat(style).contains("-comp-added: #112233");
        assertThat(style).contains("-comp-removed: #998877");
    }

    @Test
    void colorStyleWithDefaultColorsIsNonEmpty() {
        String style = ThemeManager.colorStyle(HighlightColors.defaults());
        assertThat(style).isNotBlank();
    }

    // ── applyColors injects variables on scene root ───────────────────────────

    @Test
    void applyColorsInjectsVariablesOnSceneRoot(FxRobot robot) {
        HighlightColors colors = new HighlightColors("#cc1111", "#11cc11", "#1111cc");
        robot.interact(() -> ThemeManager.applyColors(colors, scene));
        WaitForAsyncUtils.waitForFxEvents();

        String style = root.getStyle();
        assertThat(style).as("root style must contain -comp-changed").contains("-comp-changed");
        assertThat(style).as("root style must contain -comp-added").contains("-comp-added");
        assertThat(style).as("root style must contain -comp-removed").contains("-comp-removed");
        assertThat(style).as("changed color value").contains("#cc1111");
        assertThat(style).as("added color value").contains("#11cc11");
        assertThat(style).as("removed color value").contains("#1111cc");
    }

    @Test
    void applyColorsOverwritesPreviousStyle(FxRobot robot) {
        HighlightColors first  = new HighlightColors("red", "green", "blue");
        HighlightColors second = new HighlightColors("orange", "lime", "purple");

        robot.interact(() -> ThemeManager.applyColors(first,  scene));
        robot.interact(() -> ThemeManager.applyColors(second, scene));
        WaitForAsyncUtils.waitForFxEvents();

        String style = root.getStyle();
        assertThat(style).contains("orange");
        assertThat(style).contains("lime");
        assertThat(style).contains("purple");
        // First values replaced
        assertThat(style).doesNotContain(": red");
        assertThat(style).doesNotContain(": green");
        assertThat(style).doesNotContain(": blue");
    }
}
