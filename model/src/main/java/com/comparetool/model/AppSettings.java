package com.comparetool.model;

import java.util.List;
import java.util.Objects;

/**
 * Top-level application settings — persisted to JSON by {@code SettingsRepository}.
 *
 * @param defaultComparison    comparison options used when opening a new file diff
 * @param defaultFolderOptions options used when opening a new folder diff
 * @param theme                UI theme (light / dark / follow OS)
 * @param colors               diff highlight colors
 * @param recents              recently opened comparisons, most-recent first
 * @param layout               last-saved window geometry
 */
public record AppSettings(
        ComparisonOptions defaultComparison,
        FolderComparisonOptions defaultFolderOptions,
        ThemeMode theme,
        HighlightColors colors,
        List<RecentComparison> recents,
        WindowLayout layout) {

    /** Maximum number of recent comparisons retained. */
    public static final int MAX_RECENTS = 20;

    public AppSettings {
        Objects.requireNonNull(defaultComparison, "defaultComparison must not be null");
        Objects.requireNonNull(defaultFolderOptions, "defaultFolderOptions must not be null");
        Objects.requireNonNull(theme, "theme must not be null");
        Objects.requireNonNull(colors, "colors must not be null");
        Objects.requireNonNull(recents, "recents must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
        recents = List.copyOf(recents);
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /** Returns fully-default settings ready for first-run. */
    public static AppSettings defaults() {
        return new AppSettings(
                ComparisonOptions.defaults(),
                FolderComparisonOptions.defaults(),
                ThemeMode.SYSTEM,
                HighlightColors.defaults(),
                List.of(),
                WindowLayout.defaults());
    }

    // ── Wither helpers ────────────────────────────────────────────────────

    public AppSettings withDefaultComparison(ComparisonOptions value) {
        return new AppSettings(value, defaultFolderOptions, theme, colors, recents, layout);
    }

    public AppSettings withDefaultFolderOptions(FolderComparisonOptions value) {
        return new AppSettings(defaultComparison, value, theme, colors, recents, layout);
    }

    public AppSettings withTheme(ThemeMode value) {
        return new AppSettings(defaultComparison, defaultFolderOptions, value, colors, recents, layout);
    }

    public AppSettings withColors(HighlightColors value) {
        return new AppSettings(defaultComparison, defaultFolderOptions, theme, value, recents, layout);
    }

    public AppSettings withRecents(List<RecentComparison> value) {
        return new AppSettings(defaultComparison, defaultFolderOptions, theme, colors, value, layout);
    }

    public AppSettings withLayout(WindowLayout value) {
        return new AppSettings(defaultComparison, defaultFolderOptions, theme, colors, recents, value);
    }

    /**
     * Returns a copy with the given recent comparison prepended to the list,
     * trimmed to {@link #MAX_RECENTS} entries.
     */
    public AppSettings withAddedRecent(RecentComparison recent) {
        Objects.requireNonNull(recent, "recent must not be null");
        List<RecentComparison> updated = new java.util.ArrayList<>();
        updated.add(recent);
        recents.stream()
                .filter(r -> !r.left().equals(recent.left()) || !r.right().equals(recent.right()))
                .limit(MAX_RECENTS - 1)
                .forEach(updated::add);
        return withRecents(updated);
    }
}
