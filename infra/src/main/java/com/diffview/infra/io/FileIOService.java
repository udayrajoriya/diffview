package com.comparetool.infra.io;

import com.comparetool.model.DecodedText;
import com.comparetool.model.LineEnding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

/**
 * Reads and writes text files, preserving encoding and line-ending style.
 *
 * <p>All methods throw {@link UncheckedIOException} to wrap checked {@link IOException},
 * allowing use in lambdas and streams without boilerplate.
 */
public interface FileIOService {

    /**
     * Reads a text file and returns a {@link DecodedText} containing the split lines,
     * the encoding used, whether a BOM was present, and the dominant line-ending style.
     *
     * <p>If {@code encodingOverride} is non-null it is used directly for decoding (after
     * stripping any detected BOM); otherwise the encoding is auto-detected.
     *
     * @param path             path to the file to read
     * @param encodingOverride charset to use for decoding, or {@code null} for auto-detect
     * @return decoded file content
     * @throws UncheckedIOException if the file cannot be read
     */
    DecodedText read(Path path, Charset encodingOverride);

    /**
     * Writes {@code content} to {@code path}, normalising all line endings to
     * {@code lineEnding} before encoding with {@code charset}.
     *
     * <p>The file is created if it does not exist, or overwritten if it does.
     *
     * @param path       destination path
     * @param content    text to write (may contain mixed line endings; they will be normalised)
     * @param charset    encoding to use for the output bytes
     * @param lineEnding line-ending convention to apply to the output
     * @throws UncheckedIOException if the file cannot be written (e.g. read-only, no permission)
     */
    void write(Path path, String content, Charset charset, LineEnding lineEnding);

    /**
     * Returns {@code true} if the file is likely binary, determined by scanning the first
     * {@value NioFileIOService#BINARY_PROBE_SIZE} bytes for a NUL ({@code 0x00}) byte.
     *
     * @param path path to the file to probe
     * @throws UncheckedIOException if the file cannot be read
     */
    boolean isBinary(Path path);
}
