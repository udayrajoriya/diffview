package com.diffview.core.ignore;

import com.diffview.model.FolderComparisonOptions;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Default {@link IgnoreRuleEngine} implementation backed by Java NIO glob
 * {@link PathMatcher}s and a mutable manual-ignore set.
 *
 * <h3>Pattern evaluation</h3>
 * <ul>
 *   <li>Patterns ending with {@code /} or {@code \} are <em>directory-only</em>:
 *       they are never applied to regular files.</li>
 *   <li>Patterns that contain a path separator ({@code /}, {@code \}) or the
 *       {@code **} wildcard are matched against the entry's full relative path.</li>
 *   <li>All other patterns (simple globs such as {@code *.tmp}) are matched
 *       against the entry's <em>filename</em> (last path component) only,
 *       so they apply at any depth without needing a {@code **} prefix.</li>
 * </ul>
 *
 * <p>Pattern matching is case-sensitive on case-sensitive file systems and
 * case-insensitive on case-insensitive ones, following the behaviour of
 * {@link java.nio.file.FileSystems#getDefault()}.
 */
public final class DefaultIgnoreRuleEngine implements IgnoreRuleEngine {

    private final List<String> includeMasks;
    private final List<String> excludeMasks;
    private final Set<Path>    manualIgnores; // mutable; supports ignoreItem / unignoreItem

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates an engine with the given mask lists and no initial manual ignores.
     *
     * @param includeMasks glob patterns for items to include; empty means include all
     * @param excludeMasks glob patterns for items to exclude; empty means exclude none
     */
    public DefaultIgnoreRuleEngine(List<String> includeMasks, List<String> excludeMasks) {
        this(includeMasks, excludeMasks, Set.of());
    }

    /**
     * Creates an engine with the given mask lists and a pre-populated set of
     * manual ignores (e.g. loaded from persisted state).
     *
     * @param includeMasks        glob patterns for items to include
     * @param excludeMasks        glob patterns for items to exclude
     * @param initialManualIgnores initial set of manually-ignored relative paths
     */
    public DefaultIgnoreRuleEngine(List<String> includeMasks,
                                   List<String> excludeMasks,
                                   Set<Path> initialManualIgnores) {
        this.includeMasks  = List.copyOf(Objects.requireNonNull(includeMasks,  "includeMasks"));
        this.excludeMasks  = List.copyOf(Objects.requireNonNull(excludeMasks,  "excludeMasks"));
        this.manualIgnores = new HashSet<>(Objects.requireNonNull(initialManualIgnores, "initialManualIgnores"));
    }

    /**
     * Convenience factory that reads masks and initial ignores from a
     * {@link FolderComparisonOptions} instance.
     */
    public static DefaultIgnoreRuleEngine from(FolderComparisonOptions options) {
        Objects.requireNonNull(options, "options");
        return new DefaultIgnoreRuleEngine(
                options.includeMasks(),
                options.excludeMasks(),
                options.manualIgnores());
    }

    // ── IgnoreRuleEngine ──────────────────────────────────────────────────────

    @Override
    public boolean isExcluded(Path relativePath, boolean isDirectory) {
        Objects.requireNonNull(relativePath, "relativePath");

        // 1. Manual ignores — checked first
        if (manualIgnores.contains(relativePath)) return true;

        // 2. Exclude masks — exclude always beats include
        for (String mask : excludeMasks) {
            if (matchesMask(mask, relativePath, isDirectory)) return true;
        }

        // 3. Include masks — if non-empty and the entry matches none → excluded
        if (!includeMasks.isEmpty()) {
            for (String mask : includeMasks) {
                if (matchesMask(mask, relativePath, isDirectory)) return false; // explicitly included
            }
            return true; // no include mask matched
        }

        return false;
    }

    @Override
    public void ignoreItem(Path relativePath) {
        manualIgnores.add(Objects.requireNonNull(relativePath, "relativePath"));
    }

    @Override
    public void unignoreItem(Path relativePath) {
        manualIgnores.remove(relativePath);
    }

    @Override
    public boolean isManuallyIgnored(Path relativePath) {
        return manualIgnores.contains(relativePath);
    }

    // ── Pattern matching ──────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code rawMask} matches the given entry.
     *
     * <p>A trailing {@code /} or {@code \} marks the mask as directory-only.
     * After stripping that suffix the remaining pattern is compiled as a
     * {@code glob:} {@link PathMatcher} and evaluated against either the
     * entry's filename or its full relative path depending on whether the
     * pattern contains a path separator or {@code **}.
     */
    private static boolean matchesMask(String rawMask, Path relativePath, boolean isDirectory) {
        boolean dirOnly = rawMask.endsWith("/") || rawMask.endsWith("\\");
        String pattern  = dirOnly ? rawMask.substring(0, rawMask.length() - 1) : rawMask;

        // Directory-only masks must not be applied to regular files
        if (dirOnly && !isDirectory) return false;

        // Path-based or recursive patterns are matched against the full relative path;
        // simple name patterns are matched against the filename only.
        boolean isPathPattern = pattern.contains("/")
                || pattern.contains("\\")
                || pattern.contains("**");

        Path matchTarget = isPathPattern ? relativePath : relativePath.getFileName();
        if (matchTarget == null) return false; // root path has no filename

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(matchTarget);
        } catch (IllegalArgumentException ignored) {
            // Malformed glob pattern — treat as non-matching rather than crashing
            return false;
        }
    }
}
