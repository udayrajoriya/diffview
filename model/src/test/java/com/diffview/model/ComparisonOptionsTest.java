package com.diffview.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ComparisonOptionsTest {

    @Nested
    class Defaults {

        @Test
        void defaultsHaveNoIgnoreFlags() {
            ComparisonOptions opts = ComparisonOptions.defaults();
            assertThat(opts.ignoreWhitespace()).isFalse();
            assertThat(opts.ignoreLineEndings()).isFalse();
            assertThat(opts.ignoreCase()).isFalse();
        }

        @Test
        void defaultsHaveNullEncodings() {
            ComparisonOptions opts = ComparisonOptions.defaults();
            assertThat(opts.leftEncodingOverride()).isNull();
            assertThat(opts.rightEncodingOverride()).isNull();
        }

        @Test
        void defaultsHaveTenMibWarnThreshold() {
            assertThat(ComparisonOptions.defaults().largeFileWarnBytes())
                    .isEqualTo(10L * 1024 * 1024);
        }

        @Test
        void defaultsHasNoAnyIgnoreFlag() {
            assertThat(ComparisonOptions.defaults().hasAnyIgnoreFlag()).isFalse();
        }
    }

    @Nested
    class WitherMethods {

        @Test
        void withIgnoreWhitespace_returnsNewInstance() {
            ComparisonOptions original = ComparisonOptions.defaults();
            ComparisonOptions modified = original.withIgnoreWhitespace(true);

            assertThat(modified.ignoreWhitespace()).isTrue();
            assertThat(original.ignoreWhitespace()).isFalse(); // original unchanged
            assertThat(modified.hasAnyIgnoreFlag()).isTrue();
        }

        @Test
        void withIgnoreLineEndings_toggled() {
            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreLineEndings(true);
            assertThat(opts.ignoreLineEndings()).isTrue();
            assertThat(opts.hasAnyIgnoreFlag()).isTrue();
        }

        @Test
        void withIgnoreCase_toggled() {
            ComparisonOptions opts = ComparisonOptions.defaults().withIgnoreCase(true);
            assertThat(opts.ignoreCase()).isTrue();
            assertThat(opts.hasAnyIgnoreFlag()).isTrue();
        }

        @Test
        void withLeftEncodingOverride_setsCharset() {
            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withLeftEncodingOverride(StandardCharsets.UTF_16);
            assertThat(opts.leftEncodingOverride()).isEqualTo(StandardCharsets.UTF_16);
            assertThat(opts.rightEncodingOverride()).isNull();
        }

        @Test
        void withRightEncodingOverride_setsCharset() {
            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withRightEncodingOverride(StandardCharsets.ISO_8859_1);
            assertThat(opts.rightEncodingOverride()).isEqualTo(StandardCharsets.ISO_8859_1);
        }

        @Test
        void withLargeFileWarnBytes_updatesThreshold() {
            ComparisonOptions opts = ComparisonOptions.defaults().withLargeFileWarnBytes(0L);
            assertThat(opts.largeFileWarnBytes()).isZero();
        }

        @Test
        void chaining_preservesOtherFields() {
            ComparisonOptions opts = ComparisonOptions.defaults()
                    .withIgnoreWhitespace(true)
                    .withIgnoreCase(true)
                    .withLargeFileWarnBytes(5_000_000L);

            assertThat(opts.ignoreWhitespace()).isTrue();
            assertThat(opts.ignoreLineEndings()).isFalse();
            assertThat(opts.ignoreCase()).isTrue();
            assertThat(opts.largeFileWarnBytes()).isEqualTo(5_000_000L);
        }
    }

    @Nested
    class Validation {

        @Test
        void negativeLargeFileBytesThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ComparisonOptions(false, false, false, null, null, -1L))
                    .withMessageContaining("largeFileWarnBytes");
        }
    }
}
