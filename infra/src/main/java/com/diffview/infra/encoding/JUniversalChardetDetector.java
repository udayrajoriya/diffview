package com.diffview.infra.encoding;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@link EncodingDetector} implementation backed by Mozilla's Universal Charset Detector
 * (juniversalchardet).
 *
 * <p>BOM recognition takes priority over statistical detection and covers:
 * <ul>
 *   <li>UTF-32 LE: {@code FF FE 00 00} (checked before UTF-16 LE)</li>
 *   <li>UTF-32 BE: {@code 00 00 FE FF}</li>
 *   <li>UTF-16 LE: {@code FF FE}</li>
 *   <li>UTF-16 BE: {@code FE FF}</li>
 *   <li>UTF-8:     {@code EF BB BF}</li>
 * </ul>
 *
 * <p>When neither BOM nor statistical detection yields a result, {@link StandardCharsets#UTF_8}
 * is used as the fallback.
 */
public class JUniversalChardetDetector implements EncodingDetector {

    /** Fallback encoding returned when detection is inconclusive. */
    public static final Charset FALLBACK = StandardCharsets.UTF_8;

    @Override
    public Result detect(byte[] rawBytes) {
        if (rawBytes == null || rawBytes.length == 0) {
            return new Result(FALLBACK, false, 0);
        }

        // 1. BOM check — takes absolute priority over statistical detection.
        Result bomResult = detectBom(rawBytes);
        if (bomResult != null) {
            return bomResult;
        }

        // 2. Statistical detection via juniversalchardet.
        String detected = detectCharsetName(rawBytes);
        if (detected != null && !detected.isBlank()) {
            try {
                return new Result(Charset.forName(detected), false, 0);
            } catch (Exception ignored) {
                // Unknown charset name from detector — fall through to fallback.
            }
        }

        // 3. Fallback.
        return new Result(FALLBACK, false, 0);
    }

    // ── BOM detection ─────────────────────────────────────────────────────

    /** Wraps the juniversalchardet stream API for byte-array input.
     *  Only the leading sample is scanned — charset detection converges quickly,
     *  so this keeps detection fast on very large files. */
    private static String detectCharsetName(byte[] bytes) {
        // Cap detection to the first 1 MiB; scanning the whole file is wasteful and
        // real-world files do not switch encoding partway through. Decoding still
        // uses the full byte array, so this does not affect decoded output.
        final int len = Math.min(bytes.length, 1 << 20);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes, 0, len)) {
            return UniversalDetector.detectCharset(in);
        } catch (IOException e) {
            return null; // ByteArrayInputStream never throws, but satisfy the compiler
        }
    }


    /**
     * Returns a {@link Result} if a known BOM is found at the start of {@code bytes},
     * or {@code null} if no BOM is present.
     *
     * <p>UTF-32 patterns must be checked before UTF-16 because the UTF-32 LE BOM
     * ({@code FF FE 00 00}) starts with the UTF-16 LE BOM ({@code FF FE}).
     */
    private static Result detectBom(byte[] b) {
        int len = b.length;

        // UTF-32 LE: FF FE 00 00
        if (len >= 4
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE
                && (b[2] & 0xFF) == 0x00 && (b[3] & 0xFF) == 0x00) {
            return new Result(Charset.forName("UTF-32LE"), true, 4);
        }

        // UTF-32 BE: 00 00 FE FF
        if (len >= 4
                && (b[0] & 0xFF) == 0x00 && (b[1] & 0xFF) == 0x00
                && (b[2] & 0xFF) == 0xFE && (b[3] & 0xFF) == 0xFF) {
            return new Result(Charset.forName("UTF-32BE"), true, 4);
        }

        // UTF-8: EF BB BF
        if (len >= 3
                && (b[0] & 0xFF) == 0xEF
                && (b[1] & 0xFF) == 0xBB
                && (b[2] & 0xFF) == 0xBF) {
            return new Result(StandardCharsets.UTF_8, true, 3);
        }

        // UTF-16 LE: FF FE
        if (len >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new Result(StandardCharsets.UTF_16LE, true, 2);
        }

        // UTF-16 BE: FE FF
        if (len >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new Result(StandardCharsets.UTF_16BE, true, 2);
        }

        return null;
    }
}
