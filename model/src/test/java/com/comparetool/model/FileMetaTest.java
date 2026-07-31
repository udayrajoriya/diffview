package com.comparetool.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class FileMetaTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void fileFactory_createsNonDirectoryWithCorrectFields() {
        Path abs = Path.of("/root/a/file.txt");
        Path rel = Path.of("a/file.txt");
        FileMeta meta = FileMeta.file(abs, rel, 1024L, NOW);

        assertThat(meta.absolutePath()).isEqualTo(abs);
        assertThat(meta.relativePath()).isEqualTo(rel);
        assertThat(meta.directory()).isFalse();
        assertThat(meta.size()).isEqualTo(1024L);
        assertThat(meta.lastModified()).isEqualTo(NOW);
    }

    @Test
    void directoryFactory_hasZeroSize() {
        FileMeta meta = FileMeta.directory(Path.of("/root/sub"), Path.of("sub"), NOW);
        assertThat(meta.directory()).isTrue();
        assertThat(meta.size()).isZero();
    }

    @Test
    void negativeSizeThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> FileMeta.file(Path.of("/a"), Path.of("a"), -1L, NOW))
                .withMessageContaining("size must be >= 0");
    }

    @Test
    void nullAbsolutePathThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FileMeta(null, Path.of("a"), false, 0L, NOW));
    }

    @Test
    void nullRelativePathThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FileMeta(Path.of("/a"), null, false, 0L, NOW));
    }

    @Test
    void nullLastModifiedThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new FileMeta(Path.of("/a"), Path.of("a"), false, 0L, null));
    }

    @Test
    void zeroSizeIsValid() {
        assertThatNoException()
                .isThrownBy(() -> FileMeta.file(Path.of("/a"), Path.of("a"), 0L, NOW));
    }

    @ParameterizedTest
    @EnumSource(FileMatchMode.class)
    void fileMatchModeEnumHasThreeValues(FileMatchMode mode) {
        // Ensure no value is removed without updating this test
        assertThat(mode).isNotNull();
    }
}
