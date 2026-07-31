package com.comparetool.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The top-level result returned by
 * {@code ComparisonService#compareFiles(Path, Path, ComparisonOptions)}.
 *
 * <p>Pairs the computed {@link DiffModel} with the source paths that produced it,
 * so consumers can reference back to the originals without threading paths
 * through every downstream call.
 *
 * @param model the computed diff model (never {@code null}).
 * @param left  absolute path of the left (first-selected) file (never {@code null}).
 * @param right absolute path of the right (second-selected) file (never {@code null}).
 */
public record FileComparisonResult(DiffModel model, Path left, Path right) {

    public FileComparisonResult {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(left,  "left must not be null");
        Objects.requireNonNull(right, "right must not be null");
    }
}
