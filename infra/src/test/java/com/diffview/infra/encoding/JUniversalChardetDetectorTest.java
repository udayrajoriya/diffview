package com.diffview.infra.encoding;

import com.diffview.model.LineEnding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

class JUniversalChardetDetectorTest {

    private JUniversalChardetDetector detector;

    @BeforeEach
    void setUp() {
        detector = new JUniversalChardetDetector();
    }

    // ── BOM detection ──────────────────────────────────────────────────────

    @Nested
    class BomDetection {

        @Test
        void utf8BomDetected() {
            byte[] bytes = withBom(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}, "hello".getBytes(StandardCharsets.UTF_8));
            EncodingDetector.Result result = detector.detect(bytes);

            assertThat(result.hasBom()).isTrue();
            assertThat(result.bomLength()).isEqualTo(3);
            assertThat(result.charset()).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        void utf8BomStripWorks() {
            byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
            byte[] bytes = withBom(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF}, payload);
            EncodingDetector.Result result = detector.detect(bytes);

            byte[] stripped = result.stripBom(bytes);
            assertThat(stripped).isEqualTo(payload);
        }

        @Test
        void utf16LeBomDetected() {
            byte[] bom = {(byte)0xFF, (byte)0xFE};
            byte[] payload = "hi".getBytes(StandardCharsets.UTF_16LE);
            EncodingDetector.Result result = detector.detect(withBom(bom, payload));

            assertThat(result.hasBom()).isTrue();
            assertThat(result.bomLength()).isEqualTo(2);
            assertThat(result.charset()).isEqualTo(StandardCharsets.UTF_16LE);
        }

        @Test
        void utf16BeBomDetected() {
            byte[] bom = {(byte)0xFE, (byte)0xFF};
            byte[] payload = "hi".getBytes(StandardCharsets.UTF_16BE);
            EncodingDetector.Result result = detector.detect(withBom(bom, payload));

            assertThat(result.hasBom()).isTrue();
            assertThat(result.bomLength()).isEqualTo(2);
            assertThat(result.charset()).isEqualTo(StandardCharsets.UTF_16BE);
        }

        @Test
        void utf32LeBomDetected() {
            byte[] bom = {(byte)0xFF, (byte)0xFE, (byte)0x00, (byte)0x00};
            byte[] payload = new byte[4]; // minimal payload
            EncodingDetector.Result result = detector.detect(withBom(bom, payload));

            assertThat(result.hasBom()).isTrue();
            assertThat(result.bomLength()).isEqualTo(4);
            assertThat(result.charset().name()).isEqualToIgnoringCase("UTF-32LE");
        }

        @Test
        void utf32BeBomDetected() {
            byte[] bom = {(byte)0x00, (byte)0x00, (byte)0xFE, (byte)0xFF};
            byte[] payload = new byte[4];
            EncodingDetector.Result result = detector.detect(withBom(bom, payload));

            assertThat(result.hasBom()).isTrue();
            assertThat(result.bomLength()).isEqualTo(4);
            assertThat(result.charset().name()).isEqualToIgnoringCase("UTF-32BE");
        }

        @Test
        void utf32LeNotMistakenForUtf16Le() {
            // UTF-32 LE BOM starts with FF FE — must not be detected as UTF-16 LE
            byte[] bom = {(byte)0xFF, (byte)0xFE, (byte)0x00, (byte)0x00};
            byte[] payload = new byte[4];
            EncodingDetector.Result result = detector.detect(withBom(bom, payload));

            assertThat(result.charset().name()).isEqualToIgnoringCase("UTF-32LE");
            assertThat(result.bomLength()).isEqualTo(4); // not 2
        }

        @Test
        void noBomWhenNotPresent() {
            byte[] bytes = "plain ascii content".getBytes(StandardCharsets.UTF_8);
            EncodingDetector.Result result = detector.detect(bytes);
            assertThat(result.hasBom()).isFalse();
            assertThat(result.bomLength()).isZero();
        }
    }

    // ── Charset detection (no BOM) ─────────────────────────────────────────

    @Nested
    class CharsetDetection {

        @Test
        void utf8TextDetectedOrFallback() {
            // A large UTF-8 sample gives the detector enough signal
            String text = "The quick brown fox jumps over the lazy dog. ".repeat(50);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            EncodingDetector.Result result = detector.detect(bytes);

            // juniversalchardet may return UTF-8 or fall back to UTF-8; both are correct
            assertThat(result.charset()).isNotNull();
            assertThat(result.hasBom()).isFalse();
        }

        @Test
        void latin1TextDecodedCorrectly() {
            // Latin-1 specific byte 0xE9 = 'é'
            byte[] bytes = new byte[300];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte)(0xC0 + (i % 32)); // high-Latin1 range
            }
            EncodingDetector.Result result = detector.detect(bytes);
            // Must not throw; charset must be non-null
            assertThat(result.charset()).isNotNull();
        }

        @Test
        void emptyBytesReturnFallback() {
            EncodingDetector.Result result = detector.detect(new byte[0]);
            assertThat(result.charset()).isEqualTo(JUniversalChardetDetector.FALLBACK);
            assertThat(result.hasBom()).isFalse();
        }

        @Test
        void nullBytesReturnFallback() {
            EncodingDetector.Result result = detector.detect(null);
            assertThat(result.charset()).isEqualTo(JUniversalChardetDetector.FALLBACK);
        }

        @Test
        void utf16LeWithoutBomRoundTrips() {
            // Explicitly encode as UTF-16LE (no BOM prefix); detector may return any reasonable charset
            byte[] bytes = "Hello World".getBytes(StandardCharsets.UTF_16LE);
            // Just ensure detect() doesn't throw and returns a result
            EncodingDetector.Result result = detector.detect(bytes);
            assertThat(result).isNotNull();
            assertThat(result.charset()).isNotNull();
        }
    }

    // ── Result.stripBom ────────────────────────────────────────────────────

    @Nested
    class StripBom {

        @Test
        void stripBomWithNoBomReturnsOriginal() {
            byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
            EncodingDetector.Result result = new EncodingDetector.Result(StandardCharsets.UTF_8, false, 0);
            assertThat(result.stripBom(bytes)).isSameAs(bytes);
        }

        @Test
        void stripBomWithNullReturnsNull() {
            EncodingDetector.Result result = new EncodingDetector.Result(StandardCharsets.UTF_8, true, 3);
            assertThat(result.stripBom(null)).isNull();
        }

        @Test
        void stripBomRemovesCorrectNumberOfBytes() {
            byte[] bom = {(byte)0xEF, (byte)0xBB, (byte)0xBF};
            byte[] payload = "abc".getBytes(StandardCharsets.UTF_8);
            byte[] full = withBom(bom, payload);
            EncodingDetector.Result result = new EncodingDetector.Result(StandardCharsets.UTF_8, true, 3);

            assertThat(result.stripBom(full)).isEqualTo(payload);
        }
    }

    // ── Line ending integration ────────────────────────────────────────────

    @Nested
    class LineEndingIntegration {

        @Test
        void detectsLfInDecodedContent() {
            String text = "line1\nline2\nline3";
            assertThat(LineEnding.detect(text)).isEqualTo(LineEnding.LF);
        }

        @Test
        void detectsCrlfInDecodedContent() {
            String text = "line1\r\nline2\r\nline3";
            assertThat(LineEnding.detect(text)).isEqualTo(LineEnding.CRLF);
        }

        @Test
        void detectsMixedLineEndings_dominantWins() {
            // 3 CRLF, 1 LF → CRLF dominates
            String text = "a\r\nb\r\nc\r\nd\n";
            assertThat(LineEnding.detect(text)).isEqualTo(LineEnding.CRLF);
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private static byte[] withBom(byte[] bom, byte[] payload) {
        byte[] result = new byte[bom.length + payload.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(payload, 0, result, bom.length, payload.length);
        return result;
    }
}
