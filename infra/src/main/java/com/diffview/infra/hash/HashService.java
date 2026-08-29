package com.diffview.infra.hash;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Computes content hashes for file-equality checks.
 *
 * <p>Implementations must apply a size short-circuit in {@link #contentEquals}:
 * if the two files have different sizes they cannot have equal content, so no
 * hashing is performed.
 */
public interface HashService {

    /**
     * Computes the digest of the entire file at {@code path}.
     *
     * @param path path to the file
     * @return raw digest bytes (length depends on algorithm, e.g. 32 bytes for SHA-256)
     * @throws UncheckedIOException if the file cannot be read
     */
    byte[] hash(Path path);

    /**
     * Returns the hex-encoded digest of the file (lower-case).
     *
     * <p>Default implementation calls {@link #hash(Path)} and hex-encodes the result.
     */
    default String hashHex(Path path) {
        return HexFormat.of().formatHex(hash(path));
    }

    /**
     * Returns {@code true} if the files at {@code a} and {@code b} have byte-for-byte
     * identical content.
     *
     * <p>A <em>size short-circuit</em> is applied: if the two files have different sizes
     * this method returns {@code false} immediately without reading any bytes.
     *
     * @throws UncheckedIOException if either file cannot be read
     */
    boolean contentEquals(Path a, Path b);
}
