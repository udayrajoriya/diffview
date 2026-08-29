package com.comparetool.viewmodel;

import com.comparetool.infra.persist.SettingsRepository;
import com.comparetool.model.AppSettings;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.HighlightColors;
import com.comparetool.model.ThemeMode;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Objects;

/**
 * ViewModel for the application settings panel (MVVM, task 10.3).
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Loads {@link AppSettings} from the injected {@link SettingsRepository} on construction.</li>
 *   <li>Exposes each major setting as an observable JavaFX property so the UI can bind to it.</li>
 *   <li>On any setter call, saves the updated settings back to the repository <em>and</em> fires
 *       the relevant property change so bound controls update immediately.</li>
 * </ul>
 *
 * <h3>Threading</h3>
 * <p>All operations are synchronous.  The ViewModel is designed to be called on the JavaFX
 * Application Thread; the repository save is fast (small JSON write) and does not require
 * background execution.
 *
 * <h3>No JavaFX nodes</h3>
 * <p>Only {@code javafx.beans.property} types from {@code javafx.base} are referenced — no
 * {@code Node} subclasses — so this class is instantiable and testable without a running
 * JavaFX Application.
 */
public final class SettingsViewModel {

    // ── observable state ──────────────────────────────────────────────────────
    /** The full settings object — always consistent with the individual properties below. */
    private final SimpleObjectProperty<AppSettings>           settings
            = new SimpleObjectProperty<>();
    /** Current UI theme. */
    private final SimpleObjectProperty<ThemeMode>             theme
            = new SimpleObjectProperty<>();
    /** Diff highlight colors. */
    private final SimpleObjectProperty<HighlightColors>       colors
            = new SimpleObjectProperty<>();
    /** Default comparison options used when opening a new file diff. */
    private final SimpleObjectProperty<ComparisonOptions>     defaultComparison
            = new SimpleObjectProperty<>();
    /** Default options used when opening a new folder diff. */
    private final SimpleObjectProperty<FolderComparisonOptions> defaultFolderOptions
            = new SimpleObjectProperty<>();

    // ── injected ──────────────────────────────────────────────────────────────
    private final SettingsRepository repository;

    // ── constructor ───────────────────────────────────────────────────────────

    /**
     * Creates the ViewModel and immediately loads persisted settings from {@code repository}.
     * If no settings file exists (or it is corrupt), {@link AppSettings#defaults()} are used.
     *
     * @param repository the settings persistence layer; must not be {@code null}
     */
    public SettingsViewModel(SettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
        reload();
    }

    // ── commands / setters ────────────────────────────────────────────────────

    /**
     * Changes the UI theme, saves to the repository, and fires the
     * {@link #themeProperty()} and {@link #settingsProperty()} change events.
     *
     * @param value the new theme; must not be {@code null}
     */
    public void setTheme(ThemeMode value) {
        Objects.requireNonNull(value, "value");
        saveAndApply(settings.get().withTheme(value));
    }

    /**
     * Changes the highlight colors, saves to the repository, and fires the
     * {@link #colorsProperty()} and {@link #settingsProperty()} change events.
     *
     * @param value the new colors; must not be {@code null}
     */
    public void setColors(HighlightColors value) {
        Objects.requireNonNull(value, "value");
        saveAndApply(settings.get().withColors(value));
    }

    /**
     * Changes the default file comparison options, saves to the repository, and fires
     * the {@link #defaultComparisonProperty()} and {@link #settingsProperty()} change events.
     *
     * @param value the new options; must not be {@code null}
     */
    public void setDefaultComparison(ComparisonOptions value) {
        Objects.requireNonNull(value, "value");
        saveAndApply(settings.get().withDefaultComparison(value));
    }

    /**
     * Changes the default folder comparison options, saves to the repository, and fires
     * the {@link #defaultFolderOptionsProperty()} and {@link #settingsProperty()} change events.
     *
     * @param value the new options; must not be {@code null}
     */
    public void setDefaultFolderOptions(FolderComparisonOptions value) {
        Objects.requireNonNull(value, "value");
        saveAndApply(settings.get().withDefaultFolderOptions(value));
    }

    /**
     * Reloads settings from the repository, refreshing all observable properties.
     * Useful after external changes (e.g. another process wrote a new config file).
     */
    public void reload() {
        applySettings(repository.load());
    }

    // ── read-only property accessors ──────────────────────────────────────────

    /**
     * The full {@link AppSettings} object.  Always consistent with the individual
     * properties ({@link #themeProperty()}, {@link #colorsProperty()}, etc.).
     */
    public ReadOnlyObjectProperty<AppSettings> settingsProperty() { return settings; }

    /** @return the current settings value (never {@code null} after construction) */
    public AppSettings getSettings() { return settings.get(); }

    /** The UI theme observable property. */
    public ReadOnlyObjectProperty<ThemeMode> themeProperty() { return theme; }

    /** @return the current theme */
    public ThemeMode getTheme() { return theme.get(); }

    /** The diff highlight colors observable property. */
    public ReadOnlyObjectProperty<HighlightColors> colorsProperty() { return colors; }

    /** @return the current highlight colors */
    public HighlightColors getColors() { return colors.get(); }

    /** The default file comparison options observable property. */
    public ReadOnlyObjectProperty<ComparisonOptions> defaultComparisonProperty() {
        return defaultComparison;
    }

    /** @return the current default comparison options */
    public ComparisonOptions getDefaultComparison() { return defaultComparison.get(); }

    /** The default folder comparison options observable property. */
    public ReadOnlyObjectProperty<FolderComparisonOptions> defaultFolderOptionsProperty() {
        return defaultFolderOptions;
    }

    /** @return the current default folder options */
    public FolderComparisonOptions getDefaultFolderOptions() { return defaultFolderOptions.get(); }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Saves {@code updated} to the repository then pushes it to all observable properties.
     * The save happens first so that if the write fails (UncheckedIOException), the
     * properties are not modified (fail-fast, no partial state).
     */
    private void saveAndApply(AppSettings updated) {
        repository.save(updated);
        applySettings(updated);
    }

    /**
     * Pushes {@code s} to every observable property without saving — used on initial load
     * and {@link #reload()}.
     */
    private void applySettings(AppSettings s) {
        settings.set(s);
        theme.set(s.theme());
        colors.set(s.colors());
        defaultComparison.set(s.defaultComparison());
        defaultFolderOptions.set(s.defaultFolderOptions());
    }
}
