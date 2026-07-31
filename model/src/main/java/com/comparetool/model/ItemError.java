package com.comparetool.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Records a non-fatal error that occurred while processing a specific item during
 * folder comparison (REQ-016.1 — fail-soft per item).
 *
 * <p>When the comparison engine encounters an inaccessible file or directory, it records
 * an {@code ItemError} for that path and continues comparing the remaining items rather
 * than aborting the whole operation.
 *
 * @param relativePath path of the item that caused the error, relative to the comparison roots
 * @param code         categorised {@link ErrorCode}
 * @param message      human-readable description of the error
 */
public record ItemError(Path relativePath, ErrorCode code, String message) {

    public ItemError {
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(code,         "code must not be null");
        Objects.requireNonNull(message,      "message must not be null");
    }
}
