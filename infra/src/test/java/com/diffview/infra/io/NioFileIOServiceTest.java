package com.diffview.infra.io;

import com.diffview.infra.encoding.JUniversalChardetDetector;
import com.diffview.model.DecodedText;
import com.diffview.model.LineEnding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NioFileIOServiceTest {

    @TempDir
    Path tempDir;

    private NioFileIOService service;

    @BeforeEach
    void setUp() {
        service = new NioFileIOService(new JUniversalChardetDetector());
    }

    // ── read ──────────────────────────────────────────────────────────────

    @Nested
    class Read {

        @Test
        void readsUtf8TextAndSplitsLines() throws Exception {
            // Use content with multi-byte UTF-8 chars so the detector can identify UTF-8
            String content = "héllo wörld\nscène deux\ntroisième\n";
            Path file = writeRaw(content, StandardCharsets.UTF_8);
            DecodedText result = service.read(file, null);

            assertThat(result.lines()).containsExactly("héllo wörld", "scène deux", "troisième", "");
            assertThat(result.encoding().name()).isEqualToIgnoringCase("UTF-8");
            assertThat(result.hasBom()).isFalse();
            assertThat(result.lineEnding()).isEqualTo(LineEnding.LF);
        }

        @Test
        void detectsCrlfLineEnding() throws Exception {
            Path file = writeRaw("a\r\nb\r\nc\r\n", StandardCharsets.UTF_8);
            DecodedText result = service.read(file, null);

            assertThat(result.lineEnding()).isEqualTo(LineEnding.CRLF);
            assertThat(result.lines()).containsExactly("a", "b", "c", "");
        }

        @Test
        void stripsUtf8BomAndSetsHasBomTrue() throws Exception {
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] payload = "hello\nworld".getBytes(StandardCharsets.UTF_8);
            byte[] full = concat(bom, payload);
            Path file = tempDir.resolve("bom.txt");
            Files.write(file, full);

            DecodedText result = service.read(file, null);

            assertThat(result.hasBom()).isTrue();
            assertThat(result.lines()).containsExactly("hello", "world");
            assertThat(result.encoding()).isEqualTo(StandardCharsets.UTF_8);
        }

        @Test
        void honorsEncodingOverride() throws Exception {
            // Write Latin-1 (ISO-8859-1) bytes: é = 0xE9
            byte[] bytes = "caf\u00E9\ncr\u00E8me".getBytes(StandardCharsets.ISO_8859_1);
            Path file = tempDir.resolve("latin1.txt");
            Files.write(file, bytes);

            DecodedText result = service.read(file, StandardCharsets.ISO_8859_1);

            assertThat(result.encoding()).isEqualTo(StandardCharsets.ISO_8859_1);
            assertThat(result.lines().get(0)).isEqualTo("café");
            assertThat(result.lines().get(1)).isEqualTo("crème");
        }

        @Test
        void readsEmptyFileAsEmptyDecodedText() throws Exception {
            Path file = tempDir.resolve("empty.txt");
            Files.write(file, new byte[0]);

            DecodedText result = service.read(file, null);
            assertThat(result.lines()).isEmpty();
        }

        @Test
        void nullPathThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> service.read(null, null));
        }

        @Test
        void nonExistentFileThrows() {
            assertThatExceptionOfType(UncheckedIOException.class)
                    .isThrownBy(() -> service.read(tempDir.resolve("no-such.txt"), null));
        }
    }

    // ── write ─────────────────────────────────────────────────────────────

    @Nested
    class Write {

        @Test
        void writesUtf8WithLf() throws Exception {
            Path file = tempDir.resolve("out.txt");
            service.write(file, "alpha\nbeta\ngamma", StandardCharsets.UTF_8, LineEnding.LF);

            String read = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(read).isEqualTo("alpha\nbeta\ngamma");
        }

        @Test
        void normalisesLineEndingsToCrlf() throws Exception {
            Path file = tempDir.resolve("out-crlf.txt");
            service.write(file, "x\ny\nz", StandardCharsets.UTF_8, LineEnding.CRLF);

            byte[] bytes = Files.readAllBytes(file);
            String read = new String(bytes, StandardCharsets.UTF_8);
            assertThat(read).isEqualTo("x\r\ny\r\nz");
        }

        @Test
        void normalisesExistingCrlfToLf() throws Exception {
            Path file = tempDir.resolve("out-lf.txt");
            service.write(file, "a\r\nb\r\nc", StandardCharsets.UTF_8, LineEnding.LF);

            String read = Files.readString(file, StandardCharsets.UTF_8);
            assertThat(read).isEqualTo("a\nb\nc");
        }

        @Test
        void createsFileIfNotExisting() {
            Path file = tempDir.resolve("new.txt");
            assertThat(file).doesNotExist();
            service.write(file, "hello", StandardCharsets.UTF_8, LineEnding.LF);
            assertThat(file).exists();
        }

        @Test
        void overwritesExistingFile() throws Exception {
            Path file = tempDir.resolve("over.txt");
            Files.writeString(file, "original content", StandardCharsets.UTF_8);
            service.write(file, "new", StandardCharsets.UTF_8, LineEnding.LF);
            assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("new");
        }

        @Test
        void nullPathThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> service.write(null, "x", StandardCharsets.UTF_8, LineEnding.LF));
        }

        @Test
        void writeToReadOnlyFileThrows() throws Exception {
            Path file = tempDir.resolve("readonly.txt");
            Files.writeString(file, "original", StandardCharsets.UTF_8);
            makeReadOnly(file);

            try {
                assertThatExceptionOfType(UncheckedIOException.class)
                        .isThrownBy(() -> service.write(file, "new", StandardCharsets.UTF_8, LineEnding.LF));
            } finally {
                makeWritable(file); // restore so TempDir cleanup works
            }
        }
    }

    // ── round-trip ────────────────────────────────────────────────────────

    @Nested
    class RoundTrip {

        @Test
        void utf8LfRoundTrip() {
            Path file = tempDir.resolve("rt-utf8.txt");
            String original = "first line\nsecond line\nthird line";

            service.write(file, original, StandardCharsets.UTF_8, LineEnding.LF);
            DecodedText result = service.read(file, null);

            assertThat(result.content()).isEqualTo(original);
            assertThat(result.lineEnding()).isEqualTo(LineEnding.LF);
        }

        @Test
        void utf8CrlfRoundTrip() {
            Path file = tempDir.resolve("rt-crlf.txt");
            String original = "line A\r\nline B\r\nline C";

            service.write(file, original, StandardCharsets.UTF_8, LineEnding.CRLF);
            DecodedText result = service.read(file, null);

            assertThat(result.content()).isEqualTo(original);
            assertThat(result.lineEnding()).isEqualTo(LineEnding.CRLF);
        }

        @Test
        void latin1RoundTrip() {
            Path file = tempDir.resolve("rt-latin1.txt");
            String original = "café\ncrème brûlée";

            service.write(file, original, StandardCharsets.ISO_8859_1, LineEnding.LF);
            DecodedText result = service.read(file, StandardCharsets.ISO_8859_1);

            assertThat(result.content()).isEqualTo(original);
            assertThat(result.encoding()).isEqualTo(StandardCharsets.ISO_8859_1);
        }
    }

    // ── isBinary ──────────────────────────────────────────────────────────

    @Nested
    class IsBinary {

        @Test
        void textFileIsNotBinary() throws Exception {
            Path file = writeRaw("just plain text\nno nul bytes", StandardCharsets.UTF_8);
            assertThat(service.isBinary(file)).isFalse();
        }

        @Test
        void fileWithNulByteIsBinary() throws Exception {
            byte[] bytes = {'h', 'e', 'l', 'l', 'o', 0x00, 'w', 'o', 'r', 'l', 'd'};
            Path file = tempDir.resolve("binary.bin");
            Files.write(file, bytes);

            assertThat(service.isBinary(file)).isTrue();
        }

        @Test
        void emptyFileIsNotBinary() throws Exception {
            Path file = tempDir.resolve("empty.bin");
            Files.write(file, new byte[0]);
            assertThat(service.isBinary(file)).isFalse();
        }

        @Test
        void nulInFirstProbeWindowDetectedAsBinary() throws Exception {
            // NUL at the very last byte of the probe window
            byte[] bytes = new byte[NioFileIOService.BINARY_PROBE_SIZE];
            bytes[NioFileIOService.BINARY_PROBE_SIZE - 1] = 0x00;
            Path file = tempDir.resolve("probe-edge.bin");
            Files.write(file, bytes);

            assertThat(service.isBinary(file)).isTrue();
        }

        @Test
        void nulBeyondProbeWindowNotDetected() throws Exception {
            // NUL only AFTER the probe window — should NOT be detected as binary
            byte[] bytes = new byte[NioFileIOService.BINARY_PROBE_SIZE + 1];
            java.util.Arrays.fill(bytes, (byte) 'A');
            bytes[NioFileIOService.BINARY_PROBE_SIZE] = 0x00; // beyond probe
            Path file = tempDir.resolve("probe-beyond.bin");
            Files.write(file, bytes);

            assertThat(service.isBinary(file)).isFalse();
        }

        @Test
        void nullPathThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> service.isBinary(null));
        }
    }

    // ── NioFileIOService static helpers ───────────────────────────────────

    @Nested
    class StaticHelpers {

        @Test
        void splitLinesHandlesLf() {
            assertThat(NioFileIOService.splitLines("a\nb\nc"))
                    .containsExactly("a", "b", "c");
        }

        @Test
        void splitLinesHandlesCrlf() {
            assertThat(NioFileIOService.splitLines("a\r\nb\r\nc"))
                    .containsExactly("a", "b", "c");
        }

        @Test
        void splitLinesPreservesTrailingEmpty() {
            assertThat(NioFileIOService.splitLines("a\nb\n"))
                    .containsExactly("a", "b", "");
        }

        @Test
        void splitLinesOnEmptyString() {
            assertThat(NioFileIOService.splitLines("")).isEmpty();
        }

        @Test
        void normaliseLineEndingsConvertsToLf() {
            assertThat(NioFileIOService.normaliseLineEndings("a\r\nb\rc\nd", LineEnding.LF))
                    .isEqualTo("a\nb\nc\nd");
        }

        @Test
        void normaliseLineEndingsConvertsToCrlf() {
            assertThat(NioFileIOService.normaliseLineEndings("a\nb\nc", LineEnding.CRLF))
                    .isEqualTo("a\r\nb\r\nc");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Path writeRaw(String content, Charset charset) throws IOException {
        Path file = tempDir.resolve("test-" + System.nanoTime() + ".txt");
        Files.write(file, content.getBytes(charset));
        return file;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private static void makeReadOnly(Path path) throws IOException {
        // Try DOS attribute first (Windows), fall back to POSIX setWritable
        try {
            Files.getFileAttributeView(path, DosFileAttributeView.class).setReadOnly(true);
        } catch (UnsupportedOperationException e) {
            path.toFile().setWritable(false);
        }
    }

    private static void makeWritable(Path path) throws IOException {
        try {
            Files.getFileAttributeView(path, DosFileAttributeView.class).setReadOnly(false);
        } catch (UnsupportedOperationException e) {
            path.toFile().setWritable(true);
        }
    }
}
