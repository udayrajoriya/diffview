package com.diffview.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DecodedTextTest {

    @Nested
    class Construction {

        @Test
        void storesAllFields() {
            DecodedText dt = new DecodedText(
                    List.of("line1", "line2"),
                    StandardCharsets.UTF_8,
                    false,
                    LineEnding.LF);

            assertThat(dt.lines()).containsExactly("line1", "line2");
            assertThat(dt.encoding()).isEqualTo(StandardCharsets.UTF_8);
            assertThat(dt.hasBom()).isFalse();
            assertThat(dt.lineEnding()).isEqualTo(LineEnding.LF);
        }

        @Test
        void linesListIsImmutable() {
            DecodedText dt = new DecodedText(List.of("a"), StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThatExceptionOfType(UnsupportedOperationException.class)
                    .isThrownBy(() -> dt.lines().add("b"));
        }

        @Test
        void nullLinesThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DecodedText(null, StandardCharsets.UTF_8, false, LineEnding.LF));
        }

        @Test
        void nullEncodingThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DecodedText(List.of(), null, false, LineEnding.LF));
        }

        @Test
        void nullLineEndingThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DecodedText(List.of(), StandardCharsets.UTF_8, false, null));
        }
    }

    @Nested
    class ContentMethod {

        @Test
        void joinsLinesWithLfSeparator() {
            DecodedText dt = new DecodedText(
                    List.of("alpha", "beta", "gamma"),
                    StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.content()).isEqualTo("alpha\nbeta\ngamma");
        }

        @Test
        void joinsLinesWithCrlfSeparator() {
            DecodedText dt = new DecodedText(
                    List.of("x", "y"),
                    StandardCharsets.UTF_8, false, LineEnding.CRLF);
            assertThat(dt.content()).isEqualTo("x\r\ny");
        }

        @Test
        void singleLineNoTrailingSeparator() {
            DecodedText dt = new DecodedText(
                    List.of("only"),
                    StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.content()).isEqualTo("only");
        }

        @Test
        void emptyLinesListProducesEmptyString() {
            DecodedText dt = new DecodedText(List.of(), StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.content()).isEmpty();
        }
    }

    @Nested
    class HelperMethods {

        @Test
        void lineCountMatchesListSize() {
            DecodedText dt = new DecodedText(
                    List.of("a", "b", "c"), StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.lineCount()).isEqualTo(3);
        }

        @Test
        void isEmptyForEmptyLines() {
            DecodedText dt = new DecodedText(List.of(), StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.isEmpty()).isTrue();
        }

        @Test
        void isEmptyForSingleEmptyLine() {
            DecodedText dt = new DecodedText(List.of(""), StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.isEmpty()).isTrue();
        }

        @Test
        void isNotEmptyForNonBlankContent() {
            DecodedText dt = new DecodedText(List.of("hello"), StandardCharsets.UTF_8, false, LineEnding.LF);
            assertThat(dt.isEmpty()).isFalse();
        }

        @Test
        void bomFlagPreserved() {
            DecodedText dt = new DecodedText(List.of("x"), StandardCharsets.UTF_8, true, LineEnding.LF);
            assertThat(dt.hasBom()).isTrue();
        }
    }
}
