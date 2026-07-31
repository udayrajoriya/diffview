package com.comparetool.infra.hash;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * {@link HashService} implementation that uses streaming SHA-256 digests.
 *
 * <h3>Size short-circuit</h3>
 * {@link #contentEquals} compares file sizes via {@link Files#size(Path)} before computing
 * any digests. If the sizes differ the method returns {@code false} immediately.
 *
 * <h3>Streaming</h3>
 * {@link #hash} reads the file in {@value #BUFFER_SIZE}-byte chunks through a
 * {@link DigestInputStream} to avoid loading the entire file into memory.
 */
public class Sha256HashService implements HashService {

    /** Read-buffer size used when streaming files through the digest. */
    static final int BUFFER_SIZE = 64 * 1024; // 64 KiB

    /** SHA-256 algorithm name, as accepted by {@link MessageDigest#getInstance(String)}. */
    private static final String ALGORITHM = "SHA-256";

    @Override
    public byte[] hash(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            MessageDigest digest = newDigest();
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buf = new byte[BUFFER_SIZE];
                while (dis.read(buf) != -1) {
                    // Data is consumed by the DigestInputStream automatically.
                }
            }
            return digest.digest();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to hash file: " + path, e);
        }
    }

    @Override
    public boolean contentEquals(Path a, Path b) {
        Objects.requireNonNull(a, "path a must not be null");
        Objects.requireNonNull(b, "path b must not be null");
        try {
            // Size short-circuit — if sizes differ, content cannot be equal.
            long sizeA = Files.size(a);
            long sizeB = Files.size(b);
            if (sizeA != sizeB) {
                return false;
            }
            // Same size — compare hashes.
            return Arrays.equals(hash(a), hash(b));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to compare files: " + a + " vs " + b, e);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE specification — this can never happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
