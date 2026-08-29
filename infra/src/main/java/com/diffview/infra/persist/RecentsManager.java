package com.comparetool.infra.persist;

import com.comparetool.model.RecentComparison;

import java.nio.file.Path;
import java.util.List;

/**
 * Manages the recently-opened comparisons list stored inside {@link com.comparetool.model.AppSettings}.
 *
 * <h3>Deduplication</h3>
 * <p>When a comparison is added that has the same left+right paths as an existing entry, the old
 * entry is removed and the new one is prepended (most-recent-first).  The list is trimmed to
 * {@link com.comparetool.model.AppSettings#MAX_RECENTS} entries.
 *
 * <h3>Availability</h3>
 * <p>{@link #isAvailable(RecentComparison)} checks whether both paths still exist on disk.
 * This is evaluated lazily (on demand) so that the caller can decide whether to show
 * unavailable entries as greyed out or to hide them entirely.
 */
public interface RecentsManager {

    /**
     * Prepends {@code recent} to the persisted recents list, deduplicating by left+right paths
     * and trimming to {@link com.comparetool.model.AppSettings#MAX_RECENTS}.
     *
     * @param recent the comparison to record; must not be {@code null}
     */
    void addRecent(RecentComparison recent);

    /**
     * Removes any entry whose left <em>and</em> right paths match the given values.
     * A no-op if no matching entry exists.
     *
     * @param left  left path of the entry to remove
     * @param right right path of the entry to remove
     */
    void removeRecent(Path left, Path right);

    /**
     * Returns all recent comparisons in most-recent-first order.
     *
     * @return immutable snapshot of the current recents list; never {@code null}
     */
    List<RecentComparison> getRecents();

    /**
     * Returns {@code true} if both paths of {@code recent} exist on the file system.
     *
     * <p>A {@code false} result means the entry is "unavailable" — at least one path
     * has been moved, renamed, or deleted since it was last accessed.
     *
     * @param recent the entry to check; must not be {@code null}
     * @return {@code true} if both paths exist
     */
    boolean isAvailable(RecentComparison recent);
}
