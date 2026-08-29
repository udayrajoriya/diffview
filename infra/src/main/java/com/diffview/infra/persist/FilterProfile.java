package com.diffview.infra.persist;

import java.util.List;
import java.util.Objects;

/**
 * A named, portable set of include/exclude glob masks that can be exported to
 * and imported from a JSON file (REQ-11.7).
 *
 * <p>Filter profiles allow users to save frequently used mask combinations
 * (e.g. "Java sources only", "Skip build artefacts") and share them across
 * machines or team members.
 *
 * @param name         human-readable profile name (must not be blank)
 * @param includeMasks glob patterns for items to include; empty means include all
 * @param excludeMasks glob patterns for items to exclude; empty means exclude none
 */
public record FilterProfile(
        String name,
        List<String> includeMasks,
        List<String> excludeMasks) {

    public FilterProfile {
        Objects.requireNonNull(name,         "name must not be null");
        Objects.requireNonNull(includeMasks, "includeMasks must not be null");
        Objects.requireNonNull(excludeMasks, "excludeMasks must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        includeMasks = List.copyOf(includeMasks);
        excludeMasks = List.copyOf(excludeMasks);
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Creates a profile with no masks (effectively include-all / exclude-none). */
    public static FilterProfile empty(String name) {
        return new FilterProfile(name, List.of(), List.of());
    }
}
