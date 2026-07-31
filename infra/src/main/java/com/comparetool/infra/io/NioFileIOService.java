package com.comparetool.infra.io;

import com.comparetool.infra.encoding.EncodingDetector;
import com.comparetool.model.DecodedText;
import com.comparetool.model.LineEnding;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * {@link FileIOService} implementation that uses {@code java.nio.file} for I/O
 * and delegates encoding detection to an injected {@link EncodingDetector}.
 *
 * <h3>Binary detection</h3>
 * The first {@value #BINARY_PROBE_SIZE} bytes are scanned for a NUL ({@code 0x00}) byte.
 * Any NUL indicates a binary file.
 *
 * <h3>Line splitting</h3>
 * Content is split on {@code \r\n}, {@code \r}, or {@code \n}. A trailing newline
 * produces a trailing empty element in the list, mirroring the file's structure.
 */
public class NioFileIOService implements FileIOService {

    /** Number of bytes read at the start of a file for binary probing. */
    static final int BINARY_PROBE_SIZE = 8192;

    private final EncodingDetector encodingDetector;

    public NioFileIOService(EncodingDetector encodingDetector) {
        this.encodingDetector = Objects.requireNonNull(encodingDetector,
                "encodingDetector must not be null");
    }

    // ── read ──────────────────────────────────────────────────────────────

    @Override
    public DecodedText read(Path path, Charset encodingOverride) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            byte[] rawBytes = Files.readAllBytes(path);

            EncodingDetector.Result detection = encodingDetector.detect(rawBytes);
            boolean hasBom = detection.hasBom();

            // Strip BOM before decoding regardless of whether we use the detected or override charset.
            byte[] bytes = detection.stripBom(rawBytes);

            Charset charset = (encodingOverride != null) ? encodingOverride : detection.charset();
            String content = new String(bytes, charset);

            LineEnding lineEnding = LineEnding.detect(content);
            List<String> lines = splitLines(content);

            return new DecodedText(lines, charset, hasBom, lineEnding);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + path, e);
        }
    }

    // ── write ─────────────────────────────────────────────────────────────

    @Override
    public void write(Path path, String content, Charset charset, LineEnding lineEnding) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        Objects.requireNonNull(lineEnding, "lineEnding must not be null");
        try {
            String normalised = normaliseLineEndings(content, lineEnding);
            byte[] bytes = normalised.getBytes(charset);
            Files.write(path, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file: " + path, e);
        }
    }

    // ── isBinary ──────────────────────────────────────────────────────────

    @Override
    public boolean isBinary(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        try {
            byte[] probe = readProbe(path, BINARY_PROBE_SIZE);
            for (byte b : probe) {
                if (b == 0x00) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to probe file: " + path, e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Splits text into lines using any standard line-terminator ({@code \r\n},
     * {@code \r}, {@code \n}). A trailing newline produces a trailing empty element.
     * Using limit {@code -1} prevents {@code String.split} from discarding trailing empties.
     */
    static List<String> splitLines(String content) {
        if (content.isEmpty()) {
            return List.of();
        }
        String[] parts = content.split("\\r\\n|\\r|\\n", -1);
        return List.of(parts);
    }

    /**
     * Replaces all occurrences of {@code \r\n}, {@code \r}, and {@code \n} with the
     * separator of the given {@link LineEnding}.
     */
    static String normaliseLineEndings(String content, LineEnding lineEnding) {
        return content.replaceAll("\\r\\n|\\r|\\n", lineEnding.separator());
    }

    private static byte[] readProbe(Path path, int maxBytes) throws IOException {
        try (var in = Files.newInputStream(path)) {
            byte[] buf = new byte[maxBytes];
            int total = 0;
            int read;
            while (total < maxBytes && (read = in.read(buf, total, maxBytes - total)) != -1) {
                total += read;
            }
            return (total == maxBytes) ? buf : Arrays.copyOf(buf, total);
        }
    }
}
