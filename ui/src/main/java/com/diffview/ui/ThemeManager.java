package com.diffview.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.diffview.model.HighlightColors;
import com.diffview.model.ThemeMode;
import com.diffview.viewmodel.SettingsViewModel;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;

import java.util.Objects;

/**
 * Applies AtlantaFX themes and injects highlight-color CSS variables into a
 * {@link Scene}.
 *
 * <h3>Theme switching</h3>
 * <p>{@code LIGHT} → {@code PrimerLight}, {@code DARK} → {@code PrimerDark},
 * {@code SYSTEM} → follows the OS color scheme (falls back to {@code LIGHT}
 * when the preference is unavailable).
 *
 * <h3>Highlight colors</h3>
 * <p>Custom highlight colors are injected as CSS looked-up color variables on
 * the scene root's inline style so that every descendant can reference them:
 * <pre>
 *   -comp-changed: &lt;css-color&gt;;
 *   -comp-added:   &lt;css-color&gt;;
 *   -comp-removed: &lt;css-color&gt;;
 * </pre>
 *
 * <h3>Binding to a {@link SettingsViewModel}</h3>
 * <p>Call {@link #bind(SettingsViewModel, Scene)} once after the scene is
 * created to keep the theme and colors in sync with persisted user preferences.
 */
public final class ThemeManager {

    // Pre-compute stylesheet paths once; they never change at runtime.
    private static final String SHEET_LIGHT = new PrimerLight().getUserAgentStylesheet();
    private static final String SHEET_DARK  = new PrimerDark().getUserAgentStylesheet();

    private ThemeManager() {}

    // ── Theme ─────────────────────────────────────────────────────────────────

    /**
     * Applies the AtlantaFX user-agent stylesheet for {@code mode}.
     * Must be called on the JavaFX Application Thread.
     */
    public static void applyTheme(ThemeMode mode) {
        Application.setUserAgentStylesheet(sheetFor(mode));
    }

    // ── Highlight colors ──────────────────────────────────────────────────────

    /**
     * Sets the three CSS looked-up color variables on the scene root so that
     * {@code diff-colors.css} styles (and any other CSS that references them)
     * pick up the user's custom palette.
     */
    public static void applyColors(HighlightColors colors, Scene scene) {
        Objects.requireNonNull(scene,  "scene");
        Objects.requireNonNull(colors, "colors");
        scene.getRoot().setStyle(colorStyle(colors));
    }

    // ── ViewModel binding ─────────────────────────────────────────────────────

    /**
     * Binds the theme and highlight colors to the given {@link SettingsViewModel}
     * and applies the current values immediately.
     *
     * <p>Listeners are added to {@code vm.themeProperty()} and
     * {@code vm.colorsProperty()}; each change is applied on the FX thread via
     * {@link Platform#runLater}.
     *
     * @param vm    the settings view model providing theme and color preferences
     * @param scene the scene whose root receives the color-variable inline style
     */
    public static void bind(SettingsViewModel vm, Scene scene) {
        Objects.requireNonNull(vm,    "vm");
        Objects.requireNonNull(scene, "scene");

        vm.themeProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> applyTheme(newVal)));

        vm.colorsProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> applyColors(newVal, scene)));

        // Apply current values immediately (already on FX thread during startup)
        applyTheme(vm.getTheme());
        applyColors(vm.getColors(), scene);
    }

    // ── Package-private helpers (also called from tests) ──────────────────────

    /**
     * Returns the user-agent stylesheet URL for the given {@link ThemeMode}.
     * Never returns {@code null}.
     */
    static String sheetFor(ThemeMode mode) {
        return switch (mode) {
            case DARK   -> SHEET_DARK;
            case SYSTEM -> isSystemDark() ? SHEET_DARK : SHEET_LIGHT;
            case LIGHT  -> SHEET_LIGHT;
        };
    }

    /**
     * Builds the inline-style string that defines the three CSS looked-up
     * color variables from the given {@link HighlightColors}.
     */
    static String colorStyle(HighlightColors colors) {
        return "-comp-changed: " + colors.changed() + "; "
             + "-comp-added: "   + colors.added()   + "; "
             + "-comp-removed: " + colors.removed() + ";";
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean isSystemDark() {
        // Platform.getPreferences() / ColorScheme requires JavaFX 22+.
        // With JavaFX 21 we conservatively default to light until upgraded.
        return false;
    }
}
