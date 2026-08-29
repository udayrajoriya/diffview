package com.diffview.core.ignore;

import java.nio.file.Path;

/**
 * Decides whether a folder entry (file or directory) should be excluded from a
 * comparison based on glob include/exclude masks and per-item manual ignores.
 *
 * <h3>Rule evaluation order</h3>
 * <ol>
 *   <li>If the entry is manually ignored → excluded.</li>
 *   <li>If it matches any exclude mask → excluded.</li>
 *   <li>If include masks are non-empty and it matches none of them → excluded.</li>
 *   <li>Otherwise → included.</li>
 * </ol>
 *
 * <p>This ordering means <em>exclude always beats include</em>: an entry that
 * satisfies both an exclude mask and an include mask is excluded.
 */
public interface IgnoreRuleEngine {

    /**
     * Returns {@code true} if the entry at {@code relativePath} should be
     * excluded from the comparison.
     *
     * @param relativePath the entry's path relative to the comparison root
     * @param isDirectory  {@code true} if the entry is a directory
     */
    boolean isExcluded(Path relativePath, boolean isDirectory);

    /**
     * Adds {@code relativePath} to the set of manually-ignored entries.
     * Subsequent calls to {@link #isExcluded} will return {@code true} for it.
     */
    void ignoreItem(Path relativePath);

    /**
     * Removes {@code relativePath} from the set of manually-ignored entries.
     */
    void unignoreItem(Path relativePath);

    /**
     * Returns {@code true} if {@code relativePath} was added via
     * {@link #ignoreItem} and not yet removed by {@link #unignoreItem}.
     */
    boolean isManuallyIgnored(Path relativePath);
}
