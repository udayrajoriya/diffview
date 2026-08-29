package com.diffview.ui;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Describes a pending comparison triggered from the {@link SelectionBar}.
 *
 * @param left   absolute path to the left-side item (file or directory)
 * @param right  absolute path to the right-side item (file or directory)
 * @param folder {@code true} if both sides are directories; {@code false} if both are files
 */
public record CompareRequest(Path left, Path right, boolean folder) {
    public CompareRequest {
        Objects.requireNonNull(left,  "left");
        Objects.requireNonNull(right, "right");
    }
}
