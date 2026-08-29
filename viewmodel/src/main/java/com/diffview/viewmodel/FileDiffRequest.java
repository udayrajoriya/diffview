package com.diffview.viewmodel;

import com.diffview.model.ComparisonOptions;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents the request emitted by {@link FolderComparisonViewModel#openFileDiff()} to
 * instruct the UI which diff pane state to display.
 *
 * <ul>
 *   <li>{@link Actual}      — both sides are present; open a normal diff pane.</li>
 *   <li>{@link Placeholder} — only one side exists; show a placeholder (greyed-out) pane.</li>
 * </ul>
 */
public sealed interface FileDiffRequest permits FileDiffRequest.Actual, FileDiffRequest.Placeholder {

    /**
     * A real two-sided diff.
     *
     * @param left    absolute path to the left file
     * @param right   absolute path to the right file
     * @param options comparison options to apply
     */
    record Actual(Path left, Path right, ComparisonOptions options) implements FileDiffRequest {
        public Actual {
            Objects.requireNonNull(left,    "left");
            Objects.requireNonNull(right,   "right");
            Objects.requireNonNull(options, "options");
        }
    }

    /**
     * A one-sided placeholder — the other side is absent.
     *
     * @param side     absolute path to the side that exists
     * @param leftSide {@code true} if the existing side is the left; {@code false} if it is the right
     */
    record Placeholder(Path side, boolean leftSide) implements FileDiffRequest {
        public Placeholder {
            Objects.requireNonNull(side, "side");
        }
    }
}
