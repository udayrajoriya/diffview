package com.comparetool.model;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Options that control how two directory trees are compared.
 *
 * @param content              options applied when comparing individual file pairs
 * @param matchMode            strategy for deciding whether two paired files are identical
 * @param timestampTolerance   maximum time difference to still consider timestamps equal
 *                             (used only when {@code matchMode == SIZE_AND_TIMESTAMP})
 * @param includeMasks         glob patterns — only files matching at least one pattern are included;
 *                             empty list means include all
 * @param excludeMasks         glob patterns — files matching any pattern are excluded
 * @param manualIgnores        set of relative paths the user has explicitly marked to ignore
 * @param showOnlyDifferences  if {@code true}, identical items are hidden in the UI tree
 */
public record FolderComparisonOptions(
        ComparisonOptions content,
        FileMatchMode matchMode,
        Duration timestampTolerance,
        List<String> includeMasks,
        List<String> excludeMasks,
        Set<Path> manualIgnores,
        boolean showOnlyDifferences) {

    /** Default timestamp tolerance: 2 seconds. */
    public static final Duration DEFAULT_TIMESTAMP_TOLERANCE = Duration.ofSeconds(2);

    public FolderComparisonOptions {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(matchMode, "matchMode must not be null");
        Objects.requireNonNull(timestampTolerance, "timestampTolerance must not be null");
        Objects.requireNonNull(includeMasks, "includeMasks must not be null");
        Objects.requireNonNull(excludeMasks, "excludeMasks must not be null");
        Objects.requireNonNull(manualIgnores, "manualIgnores must not be null");
        if (timestampTolerance.isNegative()) {
            throw new IllegalArgumentException("timestampTolerance must not be negative");
        }
        includeMasks = List.copyOf(includeMasks);
        excludeMasks = List.copyOf(excludeMasks);
        manualIgnores = Set.copyOf(manualIgnores);
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Returns a sensible default: default content options, {@link FileMatchMode#SIZE_AND_TIMESTAMP},
     * 2-second timestamp tolerance, no masks, no manual ignores, show all items.
     */
    public static FolderComparisonOptions defaults() {
        return new FolderComparisonOptions(
                ComparisonOptions.defaults(),
                FileMatchMode.SIZE_AND_TIMESTAMP,
                DEFAULT_TIMESTAMP_TOLERANCE,
                List.of(),
                List.of(),
                Set.of(),
                false);
    }

    // ── Wither helpers ────────────────────────────────────────────────────

    public FolderComparisonOptions withContent(ComparisonOptions value) {
        return new FolderComparisonOptions(value, matchMode, timestampTolerance,
                includeMasks, excludeMasks, manualIgnores, showOnlyDifferences);
    }

    public FolderComparisonOptions withMatchMode(FileMatchMode value) {
        return new FolderComparisonOptions(content, value, timestampTolerance,
                includeMasks, excludeMasks, manualIgnores, showOnlyDifferences);
    }

    public FolderComparisonOptions withTimestampTolerance(Duration value) {
        return new FolderComparisonOptions(content, matchMode, value,
                includeMasks, excludeMasks, manualIgnores, showOnlyDifferences);
    }

    public FolderComparisonOptions withIncludeMasks(List<String> value) {
        return new FolderComparisonOptions(content, matchMode, timestampTolerance,
                value, excludeMasks, manualIgnores, showOnlyDifferences);
    }

    public FolderComparisonOptions withExcludeMasks(List<String> value) {
        return new FolderComparisonOptions(content, matchMode, timestampTolerance,
                includeMasks, value, manualIgnores, showOnlyDifferences);
    }

    public FolderComparisonOptions withManualIgnores(Set<Path> value) {
        return new FolderComparisonOptions(content, matchMode, timestampTolerance,
                includeMasks, excludeMasks, value, showOnlyDifferences);
    }

    public FolderComparisonOptions withShowOnlyDifferences(boolean value) {
        return new FolderComparisonOptions(content, matchMode, timestampTolerance,
                includeMasks, excludeMasks, manualIgnores, value);
    }
}
