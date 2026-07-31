package com.comparetool.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class LineEndingTest {

    @Nested
    class Separators {

        @Test
        void lfSeparator() {
            assertThat(LineEnding.LF.separator()).isEqualTo("\n");
        }

        @Test
        void crlfSeparator() {
            assertThat(LineEnding.CRLF.separator()).isEqualTo("\r\n");
        }

        @Test
        void crSeparator() {
            assertThat(LineEnding.CR.separator()).isEqualTo("\r");
        }
    }

    @Nested
    class Detection {

        @Test
        void detectsLf() {
            assertThat(LineEnding.detect("line1\nline2\nline3")).isEqualTo(LineEnding.LF);
        }

        @Test
        void detectsCrlf() {
            assertThat(LineEnding.detect("line1\r\nline2\r\nline3")).isEqualTo(LineEnding.CRLF);
        }

        @Test
        void detectsCr() {
            assertThat(LineEnding.detect("line1\rline2\rline3")).isEqualTo(LineEnding.CR);
        }

        @Test
        void dominantWinsOverMinority() {
            // 3 CRLF, 1 LF → CRLF wins
            assertThat(LineEnding.detect("a\r\nb\r\nc\r\nd\n")).isEqualTo(LineEnding.CRLF);
        }

        @Test
        void nullTextDefaultsToLf() {
            assertThat(LineEnding.detect(null)).isEqualTo(LineEnding.LF);
        }

        @Test
        void emptyTextDefaultsToLf() {
            assertThat(LineEnding.detect("")).isEqualTo(LineEnding.LF);
        }

        @Test
        void noLineEndingsDefaultsToLf() {
            assertThat(LineEnding.detect("no newlines here")).isEqualTo(LineEnding.LF);
        }
    }

    @ParameterizedTest
    @EnumSource(LineEnding.class)
    void allValuesHaveNonEmptySeparator(LineEnding le) {
        assertThat(le.separator()).isNotEmpty();
    }
}
