package com.diffview.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of a single file or directory entry collected during a folder scan.
 *
 * @param absolutePath  the full absolute path on disk
 * @param relativePath  path relative to the comparison root (used to align left/right pairs)
 * @param directory     {@code true} if this entry is a directory
 * @param size          size in bytes; 0 for directories
 * @param lastModified  last-modified instant as reported by the file system
 */
public record FileMeta(
        Path absolutePath,
        Path relativePath,
        boolean directory,
        long size,
        Instant lastModified) {

    public FileMeta {
        Objects.requireNonNull(absolutePath, "absolutePath must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(lastModified, "lastModified must not be null");
        if (size < 0) {
            throw new IllegalArgumentException("size must be >= 0, got: " + size);
        }
    }

    /**
     * Convenience factory for a file entry (non-directory).
     */
    public static FileMeta file(Path absolutePath, Path relativePath, long size, Instant lastModified) {
        return new FileMeta(absolutePath, relativePath, false, size, lastModified);
    }

    /**
     * Convenience factory for a directory entry (size is always 0).
     */
    public static FileMeta directory(Path absolutePath, Path relativePath, Instant lastModified) {
        return new FileMeta(absolutePath, relativePath, true, 0L, lastModified);
    }
}
