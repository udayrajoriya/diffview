package com.comparetool.model;

import java.nio.charset.Charset;
import java.util.Objects;

/**
 * Options that control how two text files (or text lines) are compared.
 *
 * <p>All fields are immutable. Use the {@code withX(...)} methods to obtain a modified copy.
 *
 * @param ignoreWhitespace      if {@code true}, leading/trailing and intra-token whitespace
 *                              differences are ignored during equality checks
 * @param ignoreLineEndings     if {@code true}, {@code \r\n} and {@code \n} are treated equally
 * @param ignoreCase            if {@code true}, character case differences are ignored
 * @param leftEncodingOverride  override the auto-detected encoding for the left file;
 *                              {@code null} means auto-detect
 * @param rightEncodingOverride override the auto-detected encoding for the right file;
 *                              {@code null} means auto-detect
 * @param largeFileWarnBytes    file size threshold in bytes above which the UI warns the user;
 *                              0 disables the warning
 */
public record ComparisonOptions(
        boolean ignoreWhitespace,
        boolean ignoreLineEndings,
        boolean ignoreCase,
        Charset leftEncodingOverride,
        Charset rightEncodingOverride,
        long largeFileWarnBytes) {

    /** Default large-file warning threshold: 10 MiB. */
    public static final long DEFAULT_LARGE_FILE_WARN_BYTES = 10L * 1024 * 1024;

    public ComparisonOptions {
        if (largeFileWarnBytes < 0) {
            throw new IllegalArgumentException("largeFileWarnBytes must be >= 0, got: " + largeFileWarnBytes);
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────

    /**
     * Returns a sensible default: no ignores, no encoding overrides,
     * 10 MiB large-file warning threshold.
     */
    public static ComparisonOptions defaults() {
        return new ComparisonOptions(false, false, false, null, null, DEFAULT_LARGE_FILE_WARN_BYTES);
    }

    // ── Wither helpers ────────────────────────────────────────────────────

    public ComparisonOptions withIgnoreWhitespace(boolean value) {
        return new ComparisonOptions(value, ignoreLineEndings, ignoreCase,
                leftEncodingOverride, rightEncodingOverride, largeFileWarnBytes);
    }

    public ComparisonOptions withIgnoreLineEndings(boolean value) {
        return new ComparisonOptions(ignoreWhitespace, value, ignoreCase,
                leftEncodingOverride, rightEncodingOverride, largeFileWarnBytes);
    }

    public ComparisonOptions withIgnoreCase(boolean value) {
        return new ComparisonOptions(ignoreWhitespace, ignoreLineEndings, value,
                leftEncodingOverride, rightEncodingOverride, largeFileWarnBytes);
    }

    public ComparisonOptions withLeftEncodingOverride(Charset charset) {
        return new ComparisonOptions(ignoreWhitespace, ignoreLineEndings, ignoreCase,
                charset, rightEncodingOverride, largeFileWarnBytes);
    }

    public ComparisonOptions withRightEncodingOverride(Charset charset) {
        return new ComparisonOptions(ignoreWhitespace, ignoreLineEndings, ignoreCase,
                leftEncodingOverride, charset, largeFileWarnBytes);
    }

    public ComparisonOptions withLargeFileWarnBytes(long bytes) {
        return new ComparisonOptions(ignoreWhitespace, ignoreLineEndings, ignoreCase,
                leftEncodingOverride, rightEncodingOverride, bytes);
    }

    /** Returns {@code true} if any ignore flag is active. */
    public boolean hasAnyIgnoreFlag() {
        return ignoreWhitespace || ignoreLineEndings || ignoreCase;
    }
}
