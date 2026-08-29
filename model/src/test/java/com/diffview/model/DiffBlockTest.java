package com.diffview.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class DiffBlockTest {

    // -----------------------------------------------------------------------
    // Valid construction (key task-2.1 requirement: block index ranges)
    // -----------------------------------------------------------------------

    @Nested
    class ValidRanges {

        @Test
        void singleRowBlockIsValid() {
            DiffBlock block = new DiffBlock(0, 0, LineKind.CHANGED);
            assertThat(block.firstRowIndex()).isZero();
            assertThat(block.lastRowIndex()).isZero();
            assertThat(block.rowCount()).isEqualTo(1);
            assertThat(block.isSingleRow()).isTrue();
        }

        @Test
        void multiRowBlockIsValid() {
            DiffBlock block = new DiffBlock(3, 7, LineKind.ADDED);
            assertThat(block.rowCount()).isEqualTo(5);
            assertThat(block.isSingleRow()).isFalse();
        }

        @Test
        void rowCountIsLastMinusFirstPlusOne() {
            DiffBlock block = new DiffBlock(10, 14, LineKind.REMOVED);
            assertThat(block.rowCount()).isEqualTo(5);
        }

        @ParameterizedTest(name = "first={0}, last={1}")
        @CsvSource({"0, 0", "0, 100", "5, 5", "99, 200"})
        void variousValidRanges(int first, int last) {
            assertThatCode(() -> new DiffBlock(first, last, LineKind.CHANGED))
                    .doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // Invalid construction (key task-2.1 requirement: block index ranges)
    // -----------------------------------------------------------------------

    @Nested
    class InvalidRanges {

        @Test
        void negativeFirstIndexThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DiffBlock(-1, 0, LineKind.CHANGED))
                    .withMessageContaining("firstRowIndex");
        }

        @Test
        void lastBeforeFirstThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DiffBlock(5, 3, LineKind.ADDED))
                    .withMessageContaining("lastRowIndex");
        }

        @ParameterizedTest(name = "first={0}, last={1}")
        @CsvSource({"-5, -1", "3, 2", "10, 9"})
        void variousInvalidRanges(int first, int last) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DiffBlock(first, last, LineKind.REMOVED));
        }

        @Test
        void nullKindThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> new DiffBlock(0, 1, null));
        }
    }

    // -----------------------------------------------------------------------
    // Specific LineKind values
    // -----------------------------------------------------------------------

    @Test
    void acceptsAllNonUnchangedKinds() {
        assertThatCode(() -> new DiffBlock(0, 0, LineKind.CHANGED)).doesNotThrowAnyException();
        assertThatCode(() -> new DiffBlock(0, 0, LineKind.ADDED)).doesNotThrowAnyException();
        assertThatCode(() -> new DiffBlock(0, 0, LineKind.REMOVED)).doesNotThrowAnyException();
    }
}
