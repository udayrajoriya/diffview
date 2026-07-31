package com.comparetool.infra.encoding;

import java.nio.charset.Charset;

/**
 * Detects the character encoding of raw file bytes, including BOM recognition.
 *
 * <p>Implementations may use heuristic detectors (e.g. juniversalchardet) and must
 * handle BOM markers for UTF-8, UTF-16 LE/BE, and UTF-32 LE/BE.
 */
public interface EncodingDetector {

    /**
     * Analyses the supplied bytes and returns a {@link Result} describing the detected
     * charset and whether a BOM was found.
     *
     * <p>Implementations must never return {@code null}; they must fall back to
     * {@code UTF-8} when detection is inconclusive.
     *
     * @param rawBytes the raw file bytes (may be the full file or a representative prefix)
     * @return detection result; never null
     */
    Result detect(byte[] rawBytes);

    /**
     * Detection outcome: the inferred charset, whether a BOM was present, and the
     * number of BOM bytes at the start of the file (0 if no BOM).
     *
     * @param charset   detected or fallback charset; never null
     * @param hasBom    whether a BOM was found
     * @param bomLength number of bytes occupied by the BOM (0, 2, 3, or 4)
     */
    record Result(Charset charset, boolean hasBom, int bomLength) {

        public Result {
            java.util.Objects.requireNonNull(charset, "charset must not be null");
            if (bomLength < 0) throw new IllegalArgumentException("bomLength must be >= 0");
        }

        /**
         * Returns a copy of {@code rawBytes} with the BOM stripped, or the original
         * array if {@code bomLength == 0}.
         */
        public byte[] stripBom(byte[] rawBytes) {
            if (bomLength == 0 || rawBytes == null || rawBytes.length < bomLength) {
                return rawBytes;
            }
            byte[] stripped = new byte[rawBytes.length - bomLength];
            System.arraycopy(rawBytes, bomLength, stripped, 0, stripped.length);
            return stripped;
        }
    }
}
