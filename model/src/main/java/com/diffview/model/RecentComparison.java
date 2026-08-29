package com.comparetool.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * A single entry in the recent-comparisons list.
 *
 * @param left        path of the left file or directory
 * @param right       path of the right file or directory
 * @param folder      {@code true} if this was a folder comparison; {@code false} for a file comparison
 * @param lastOpened  timestamp when this comparison was last opened
 */
public record RecentComparison(
        Path left,
        Path right,
        boolean folder,
        Instant lastOpened) {

    public RecentComparison {
        Objects.requireNonNull(left, "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
        Objects.requireNonNull(lastOpened, "lastOpened must not be null");
    }

    /** Convenience factory that stamps {@code lastOpened} as now. */
    public static RecentComparison of(Path left, Path right, boolean folder) {
        return new RecentComparison(left, right, folder, Instant.now());
    }
}
