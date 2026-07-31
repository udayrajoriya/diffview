package com.comparetool.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DiffRowTest {

    // -----------------------------------------------------------------------
    // Placeholder invariants (key task-2.1 requirement)
    // -----------------------------------------------------------------------

    @Nested
    class PlaceholderInvariants {

        @Test
        void addedRowHasNullLeftLineNumber() {
            DiffRow row = DiffRow.added(5, "new line");
            assertThat(row.leftLineNumber()).isNull();
            assertThat(row.rightLineNumber()).isEqualTo(5);
        }

        @Test
        void addedRowIsLeftPlaceholder() {
            DiffRow row = DiffRow.added(1, "text");
            assertThat(row.isLeftPlaceholder()).isTrue();
            assertThat(row.isRightPlaceholder()).isFalse();
            assertThat(row.isPlaceholder()).isTrue();
        }

        @Test
        void addedRowHasEmptyLeftText() {
            DiffRow row = DiffRow.added(1, "content");
            assertThat(row.leftText()).isEmpty();
            assertThat(row.rightText()).isEqualTo("content");
        }

        @Test
        void removedRowHasNullRightLineNumber() {
            DiffRow row = DiffRow.removed(3, "old line");
            assertThat(row.leftLineNumber()).isEqualTo(3);
            assertThat(row.rightLineNumber()).isNull();
        }

        @Test
        void removedRowIsRightPlaceholder() {
            DiffRow row = DiffRow.removed(1, "text");
            assertThat(row.isRightPlaceholder()).isTrue();
            assertThat(row.isLeftPlaceholder()).isFalse();
            assertThat(row.isPlaceholder()).isTrue();
        }

        @Test
        void removedRowHasEmptyRightText() {
            DiffRow row = DiffRow.removed(1, "content");
            assertThat(row.rightText()).isEmpty();
            assertThat(row.leftText()).isEqualTo("content");
        }

        @Test
        void unchangedRowIsNotAPlaceholder() {
            DiffRow row = DiffRow.unchanged(2, 2, "same");
            assertThat(row.isPlaceholder()).isFalse();
            assertThat(row.isLeftPlaceholder()).isFalse();
            assertThat(row.isRightPlaceholder()).isFalse();
        }

        @Test
        void bothLineNumbersNullThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new DiffRow(null, null, "", "", LineKind.ADDED, null, null))
                    .withMessageContaining("at least one non-null line number");
        }
    }

    // -----------------------------------------------------------------------
    // Factory method correctness
    // -----------------------------------------------------------------------

    @Nested
    class FactoryMethods {

        @Test
        void unchangedFactoryProducesCorrectKindAndMirroredText() {
            DiffRow row = DiffRow.unchanged(1, 1, "hello");
            assertThat(row.kind()).isEqualTo(LineKind.UNCHANGED);
            assertThat(row.leftText()).isEqualTo("hello");
            assertThat(row.rightText()).isEqualTo("hello");
        }

        @Test
        void changedFactoryProducesCorrectKindAndDifferentTexts() {
            DiffRow row = DiffRow.changed(4, 4, "old", "new");
            assertThat(row.kind()).isEqualTo(LineKind.CHANGED);
            assertThat(row.leftText()).isEqualTo("old");
            assertThat(row.rightText()).isEqualTo("new");
            assertThat(row.leftLineNumber()).isEqualTo(4);
            assertThat(row.rightLineNumber()).isEqualTo(4);
        }

        @Test
        void addedFactoryKindIsAdded() {
            assertThat(DiffRow.added(1, "x").kind()).isEqualTo(LineKind.ADDED);
        }

        @Test
        void removedFactoryKindIsRemoved() {
            assertThat(DiffRow.removed(1, "x").kind()).isEqualTo(LineKind.REMOVED);
        }
    }

    // -----------------------------------------------------------------------
    // Immutability of span lists
    // -----------------------------------------------------------------------

    @Nested
    class SpanImmutability {

        @Test
        void spansListIsUnmodifiableAfterConstruction() {
            List<InlineSpan> mutable = new java.util.ArrayList<>();
            mutable.add(new InlineSpan(0, 3));
            DiffRow row = new DiffRow(1, 1, "ab", "ac", LineKind.CHANGED, mutable, List.of());

            mutable.add(new InlineSpan(5, 8));                // mutate source list
            assertThat(row.leftSpans()).hasSize(1);           // record is unaffected
        }

        @Test
        void nullSpanListDefaultsToEmptyList() {
            DiffRow row = new DiffRow(1, 1, "a", "b", LineKind.CHANGED, null, null);
            assertThat(row.leftSpans()).isEmpty();
            assertThat(row.rightSpans()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Null-safety guards
    // -----------------------------------------------------------------------

    @Test
    void nullKindThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DiffRow(1, 1, "a", "b", null, null, null));
    }

    @Test
    void nullLeftTextThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DiffRow(1, 1, null, "b", LineKind.UNCHANGED, null, null));
    }

    @Test
    void nullRightTextThrows() {
        assertThatNullPointerException()
                .isThrownBy(() -> new DiffRow(1, 1, "a", null, LineKind.UNCHANGED, null, null));
    }
}
