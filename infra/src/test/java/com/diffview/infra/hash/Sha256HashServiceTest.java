package com.comparetool.infra.hash;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class Sha256HashServiceTest {

    @TempDir
    Path tempDir;

    private Sha256HashService service;

    @BeforeEach
    void setUp() {
        service = new Sha256HashService();
    }

    // ── hash ──────────────────────────────────────────────────────────────

    @Nested
    class Hash {

        @Test
        void produces32Bytes() throws Exception {
            Path file = write("hello world");
            assertThat(service.hash(file)).hasSize(32);
        }

        @Test
        void identicalContentProducesIdenticalHash() throws Exception {
            Path a = write("same content");
            Path b = write("same content");
            assertThat(service.hash(a)).isEqualTo(service.hash(b));
        }

        @Test
        void differentContentProducesDifferentHash() throws Exception {
            Path a = write("content A");
            Path b = write("content B");
            assertThat(service.hash(a)).isNotEqualTo(service.hash(b));
        }

        @Test
        void emptyFileProducesKnownSha256() throws Exception {
            // SHA-256 of empty input is a well-known constant
            Path file = tempDir.resolve("empty.txt");
            Files.write(file, new byte[0]);

            String hex = service.hashHex(file);
            // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
            assertThat(hex).isEqualToIgnoringCase(
                    "e3b0c44298fc1c149afbf4c8996fb924" +
                    "27ae41e4649b934ca495991b7852b855");
        }

        @Test
        void hashIsStableAcrossMultipleCalls() throws Exception {
            Path file = write("stability check");
            byte[] first = service.hash(file);
            byte[] second = service.hash(file);
            assertThat(first).isEqualTo(second);
        }

        @Test
        void hashHexIsLowerCaseHexString() throws Exception {
            Path file = write("hex check");
            String hex = service.hashHex(file);
            assertThat(hex).matches("[0-9a-f]{64}");
        }

        @Test
        void largeFileHashesWithoutOom() throws Exception {
            // Write a file larger than BUFFER_SIZE to exercise streaming
            byte[] chunk = new byte[Sha256HashService.BUFFER_SIZE];
            Arrays.fill(chunk, (byte) 'X');
            Path file = tempDir.resolve("large.bin");
            try (var out = Files.newOutputStream(file)) {
                for (int i = 0; i < 5; i++) out.write(chunk); // 5 × 64 KiB = 320 KiB
            }
            assertThat(service.hash(file)).hasSize(32);
        }

        @Test
        void nullPathThrows() {
            assertThatNullPointerException().isThrownBy(() -> service.hash(null));
        }

        @Test
        void nonExistentFileThrows() {
            assertThatExceptionOfType(UncheckedIOException.class)
                    .isThrownBy(() -> service.hash(tempDir.resolve("no-such.txt")));
        }
    }

    // ── contentEquals ─────────────────────────────────────────────────────

    @Nested
    class ContentEquals {

        @Test
        void identicalContentIsEqual() throws Exception {
            Path a = write("equal content");
            Path b = write("equal content");
            assertThat(service.contentEquals(a, b)).isTrue();
        }

        @Test
        void differentContentIsNotEqual() throws Exception {
            Path a = write("content A\n");
            Path b = write("content B\n");
            assertThat(service.contentEquals(a, b)).isFalse();
        }

        @Test
        void differentSizeShortCircuits_withoutHashing() throws Exception {
            // Files with different sizes must never be equal
            Path a = write("short");
            Path b = write("much longer content here");
            assertThat(service.contentEquals(a, b)).isFalse();
        }

        @Test
        void sameContentDifferentTimestampIsEqual() throws Exception {
            // Timestamp must NOT influence content equality
            Path a = write("timestamped content");
            Path b = write("timestamped content");

            // Shift last-modified on b by 1 hour
            Files.setLastModifiedTime(b, FileTime.from(Instant.now().plusSeconds(3600)));

            assertThat(service.contentEquals(a, b)).isTrue();
        }

        @Test
        void emptyFilesAreEqual() throws Exception {
            Path a = tempDir.resolve("empty-a.txt");
            Path b = tempDir.resolve("empty-b.txt");
            Files.write(a, new byte[0]);
            Files.write(b, new byte[0]);
            assertThat(service.contentEquals(a, b)).isTrue();
        }

        @Test
        void singleByteDifferenceDetected() throws Exception {
            byte[] data = "abcdefghij".getBytes(StandardCharsets.UTF_8);
            byte[] modified = data.clone();
            modified[5] = 'Z';

            Path a = tempDir.resolve("byte-a.bin");
            Path b = tempDir.resolve("byte-b.bin");
            Files.write(a, data);
            Files.write(b, modified);

            assertThat(service.contentEquals(a, b)).isFalse();
        }

        @Test
        void sameFileEqualsItself() throws Exception {
            Path file = write("self comparison");
            assertThat(service.contentEquals(file, file)).isTrue();
        }

        @Test
        void nullPathAThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> service.contentEquals(null, tempDir.resolve("b.txt")));
        }

        @Test
        void nullPathBThrows() throws Exception {
            Path a = write("a");
            assertThatNullPointerException()
                    .isThrownBy(() -> service.contentEquals(a, null));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────

    private Path write(String content) throws Exception {
        Path file = tempDir.resolve("file-" + System.nanoTime() + ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
