package com.comparetool.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class InlineSpanTest {

    // -----------------------------------------------------------------------
    // Valid construction
    // -----------------------------------------------------------------------

    @Test
    void constructsWithValidOffsets() {
        InlineSpan span = new InlineSpan(3, 7);
        assertThat(span.startOffset()).isEqualTo(3);
        assertThat(span.endOffset()).isEqualTo(7);
        assertThat(span.length()).isEqualTo(4);
    }

    @Test
    void zeroLengthSpanIsValid() {
        InlineSpan span = new InlineSpan(5, 5);
        assertThat(span.length()).isZero();
        assertThat(span.isEmpty()).isTrue();
    }

    @Test
    void spanStartingAtZeroIsValid() {
        assertThatCode(() -> new InlineSpan(0, 10)).doesNotThrowAnyException();
    }

    @Test
    void nonEmptySpanIsNotEmpty() {
        assertThat(new InlineSpan(0, 1).isEmpty()).isFalse();
    }

    // -----------------------------------------------------------------------
    // Invalid construction
    // -----------------------------------------------------------------------

    @Test
    void negativeStartOffsetThrows() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InlineSpan(-1, 5))
                .withMessageContaining("startOffset");
    }

    @ParameterizedTest(name = "start={0}, end={1}")
    @CsvSource({"3, 2", "10, 0", "5, 4"})
    void endBeforeStartThrows(int start, int end) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new InlineSpan(start, end))
                .withMessageContaining("endOffset");
    }
}
